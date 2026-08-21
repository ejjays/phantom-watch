package dev.phantomwatch.player

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.phantomwatch.BuildConfig
import dev.phantomwatch.core.AppLog
import dev.phantomwatch.data.Stream
import dev.phantomwatch.subs.DeepgramLive
import dev.phantomwatch.subs.PreRecorded
import dev.phantomwatch.subs.SubtitleCue
import dev.phantomwatch.subs.Subtitles
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Generates subtitles for titles that ship none by streaming the player's own decoded audio to a
 * streaming ASR. The provider does its own endpointing, so cue times come from its word timestamps.
 */
internal object AiSubtitles {
    // OFF pays nothing; AUTO only fills in when the provider ships no track; FORCE always wins.
    // AUTO is only a starting state: the gear shows the engine as on or off, never "auto"
    enum class Mode { OFF, AUTO, FORCE }

    var mode by mutableStateOf(Mode.OFF)
        private set
    var text by mutableStateOf("")
        private set

    private var realTrack by mutableStateOf(false)

    val enabled: Boolean get() = mode != Mode.OFF
    val force: Boolean get() = mode == Mode.FORCE

    // what the gear row shows: on only when the engine is actually the thing on screen
    val showing: Boolean get() = when (mode) {
        Mode.OFF -> false
        Mode.FORCE -> true
        Mode.AUTO -> !realTrack
    }

    val available: Boolean get() = BuildConfig.DEEPGRAM_API_KEY.isNotBlank()

    private const val GRACE_MS = 700L
    // a line only exists once the provider closes the segment, so this timer is the lag: 1s cuts
    // measured 1.2s behind at 93% recall, 6s cuts put every line up to six seconds behind
    private const val FINALIZE_FRAMES = 10
    private const val SESSION_MS = 9 * 60_000L
    private const val PREFS = "phantom_ai"
    private const val KEY_MODE = "mode"
    private const val KEY_ON = "enabled"
    private const val RETRY_MS = 10_000L
    // v6: spoken-language output; older caches hold English-only fragments of foreign audio
    private const val CACHE_VER = 6

    private var exo: ExoPlayer? = null
    private var appContext: Context? = null
    private var scope: CoroutineScope? = null
    private var cacheFile: File? = null
    private var liveFile: File? = null
    private var modeKey: String? = null
    private var streamRef: Stream? = null
    private var ticker: Job? = null
    private var job: Job? = null
    private var batch: Job? = null
    private var batchFor: File? = null
    // a feature-length batch is minutes of upload plus processing; it must outlive the player
    // screen, or closing the film throws away everything it paid for
    private val bg = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listener: Player.Listener? = null
    private val cues = mutableListOf<SubtitleCue>()
    private val events = ConcurrentLinkedQueue<DeepgramLive.Ev>()

    private var anchor = 0L

    // the ticker owns the player; the socket thread only reads this copy
    @Volatile
    private var posMs = 0L

    // a line is only drawn once the provider has finished it, like a normal subtitle file; it
    // then stays up for its own length instead of inside a window that has already gone by
    @Volatile
    private var arrived: SubtitleCue? = null
    private var active: SubtitleCue? = null
    private var activeUntil = 0L

    @Volatile
    private var restart = false

    // set from the CC button: turning the real track off is the user asking for the AI text
    @Volatile
    private var subsOff = false

    // true while the AI text cannot be seen (real track selected, CC off, engine off); the
    // socket is closed so no audio is uploaded for a line nobody reads
    @Volatile
    private var parked = false
    private var beat = 0L

    fun setSubsOff(value: Boolean) {
        subsOff = value
    }

    // one tap: on means "AI instead of the provider track", off means "no engine, no upload"
    fun toggle() = set(if (showing) Mode.OFF else Mode.FORCE)

    fun set(next: Mode) {
        val value = if (available) next else Mode.OFF
        if (mode == value) return
        mode = value
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putString(modeKey ?: KEY_MODE, value.name)?.apply()
        AppLog.d("ai subs mode=${value.name.lowercase()} cached=${cacheFile?.isFile == true}")
        if (value == Mode.OFF) {
            // no engine means no upload: a batch already in flight is money spent for nothing
            batch?.cancel()
            batch = null
            batchFor = null
            stop()
        } else {
            maybeStart()
            streamRef?.let { preRecorded(it) }
        }
    }

    fun attach(
        context: Context,
        player: ExoPlayer,
        stream: Stream,
        scope: CoroutineScope,
        season: String,
        episode: String,
    ) {
        stop()
        exo = player
        appContext = context.applicationContext
        this.scope = scope
        streamRef = stream
        subsOff = false
        // subKey is unset, so imdb alone would key the whole series: every episode would load
        // the previous one's cues
        val base = stream.subKey.ifEmpty { stream.imdb ?: "" }
        val key = if (base.isBlank()) "" else
            listOf(base, season, episode).filter { it.isNotBlank() }.joinToString("-")
        cacheFile = if (key.isBlank()) null else File(context.cacheDir, "ai-v$CACHE_VER-$key.vtt")
        liveFile = if (key.isBlank()) null else File(context.cacheDir, "ai-v$CACHE_VER-live-$key.vtt")
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // the choice belongs to the show, not the episode: toggling it every episode is not a choice
        modeKey = if (base.isBlank()) null else "$KEY_MODE-$base"
        // forcing the engine on is a per-title call; carrying it to the next film quietly
        // replaced a studio track that was already there
        val saved = modeKey?.let { prefs.getString(it, null) }
            ?: prefs.getString(KEY_MODE, null)?.takeIf { it != Mode.FORCE.name }
        mode = when {
            !available -> Mode.OFF
            saved != null -> Mode.values().firstOrNull { it.name == saved } ?: Mode.OFF
            // older builds stored a boolean; on meant "fill in when the provider has nothing"
            prefs.getBoolean(KEY_ON, false) -> Mode.AUTO
            else -> Mode.OFF
        }
        // a batch for another title is wasted bandwidth, but one for this title has to survive
        // closing and reopening the player, or it never finishes
        if (batchFor != cacheFile) {
            batch?.cancel()
            batch = null
            batchFor = null
        }
        synchronized(cues) {
            cues.clear()
            // only the batch file covers the whole episode; the live one is a partial stopgap
            (cacheFile?.takeIf { it.isFile } ?: liveFile?.takeIf { it.isFile })?.let {
                cues.addAll(runCatching { Subtitles.parseCues(it.readText()) }.getOrDefault(emptyList()))
            }
        }
        AppLog.d("ai subs attach cues=${cues.size} enabled=$enabled")
        maybeStart()
        preRecorded(stream)
    }

    // one accurate pass over the whole episode; it lands a minute or so in and takes over from
    // the live stream, which is only ever a stopgap
    private fun preRecorded(stream: Stream) {
        // the batch pass walks an HLS manifest; a direct MP4 has none and would be pulled whole
        if (!stream.isHls) return
        if (!enabled || !available || cacheFile?.isFile == true || batch?.isActive == true) return
        val dir = appContext?.cacheDir ?: return
        batchFor = cacheFile
        batch = bg.launch {
            // the player reports its tracks a beat after attach; paying for a transcript of an
            // episode that already has working subs is pure waste
            delay(3000)
            if (parked) {
                AppLog.d("ai subs batch skipped, provider track")
                return@launch
            }
            val lang = withContext(Dispatchers.Main) { DeepgramLive.languageOf(audioLanguage()) }
            AppLog.d("ai subs batch start lang=$lang")
            val lines = PreRecorded.transcribe(
                stream.url, stream.headers, stream.referer, lang, BuildConfig.DEEPGRAM_API_KEY, dir,
            )
            if (lines.isNullOrEmpty()) return@launch
            // the player may have moved to another title while this was transcribing
            if (batchFor != cacheFile) return@launch
            job?.cancel()
            job = null
            arrived = null
            active = null
            activeUntil = 0L
            synchronized(cues) {
                cues.clear()
                cues.addAll(lines)
            }
            cacheFile?.let { f ->
                runCatching { f.writeText(Subtitles.renderCues(lines)) }
                liveFile?.delete()
            }
            AppLog.d("ai subs batch cues=${lines.size} live stopped")
        }
    }

    private fun maybeStart() {
        if (!enabled || !available) return
        val s = scope ?: return
        // toggling the gear off stops this ticker; turning it back on has to bring it back
        if (ticker == null) ticker = s.launch { while (isActive) { overlay(); delay(100) } }
        // a batch file covers the whole episode, so a live pass would only overwrite it with
        // later, coarser cues; a live file is partial and still needs filling in
        if (job != null || cacheFile?.isFile == true) return
        job = s.launch(Dispatchers.IO) { stream() }
    }

    private fun stop() {
        job?.cancel()
        job = null
        ticker?.cancel()
        ticker = null
        listener?.let { exo?.removeListener(it) }
        listener = null
        AiAudio.sink = null
        parked = false
        realTrack = false
        arrived = null
        active = null
        activeUntil = 0L
        text = ""
        events.clear()
    }

    private suspend fun stream() {
        // bounded: a stalled consumer must drop audio, never grow the heap; 6 s is already more
        // than the live path can use
        val frames = Channel<ShortArray>(64)
        // a cancelled stream must not clear the sink the next one already installed
        val tap: (ShortArray) -> Unit = { frames.trySend(it) }
        val l = object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    restart = true
                    active = null
                    activeUntil = 0L
                }
            }
        }
        listener = l
        var live: DeepgramLive? = null
        try {
            AiAudio.sink = tap
            withContext(Dispatchers.Main) { exo?.addListener(l) }
            var sessionStart = SystemClock.elapsedRealtime()
            var lastTry = 0L
            var sinceCut = 0
            for (frame in frames) {
                drain()
                val now = SystemClock.elapsedRealtime()
                if (live != null && !live.isOpen) live = null
                if (parked) {
                    // nothing is drawn, so nothing is uploaded: a closed socket costs nothing
                    live?.close()
                    live = null
                    lastTry = 0L
                    restart = false
                    continue
                }
                if (live == null) {
                    if (now - lastTry >= RETRY_MS) {
                        // a backlog built up while the socket was down; play it live, not late
                        while (frames.tryReceive().isSuccess) Unit
                        live = open()
                        sessionStart = now
                        lastTry = now
                        sinceCut = 0
                    }
                } else if (restart || now - sessionStart > SESSION_MS) {
                    live.close()
                    live = open()
                    sessionStart = now
                    lastTry = now
                    sinceCut = 0
                }
                restart = false
                live?.send(frame)
                if (++sinceCut >= FINALIZE_FRAMES) {
                    live?.finalize()
                    sinceCut = 0
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.d("ai subs stream ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            live?.close()
            withContext(NonCancellable + Dispatchers.Main) { exo?.removeListener(l) }
            if (AiAudio.sink === tap) AiAudio.sink = null
            frames.close()
        }
    }

    private suspend fun open(): DeepgramLive? {
        val (at, tag) = withContext(Dispatchers.Main) {
            (exo?.currentPosition ?: 0L) to audioLanguage()
        }
        anchor = at
        val lang = DeepgramLive.languageOf(tag)
        val live = DeepgramLive(BuildConfig.DEEPGRAM_API_KEY, lang) { events.add(it) }
        val ok = live.open()
        AppLog.d("ai subs session lang=$lang ok=$ok at ${anchor / 1000}s")
        return if (ok) live else null
    }

    // tracks report the language the provider put on the selected audio rendition
    private fun audioLanguage(): String? {
        val p = exo ?: return null
        val g = p.currentTracks.groups.firstOrNull {
            it.type == C.TRACK_TYPE_AUDIO && it.isSelected
        } ?: return null
        for (i in 0 until g.length) {
            g.getTrackFormat(i).language?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    private fun drain() {
        while (true) {
            val ev = events.poll() ?: return
            when (ev) {
                is DeepgramLive.Ev.Failed -> AppLog.d("ai subs socket ${ev.why}")
                DeepgramLive.Ev.Ready -> Unit
                is DeepgramLive.Ev.Final -> {
                    AppLog.d("ai subs final ${ev.text.take(40)}")
                    addCue(
                        SubtitleCue(
                            anchor + (ev.start * 1000).toLong(),
                            anchor + (ev.end * 1000).toLong(),
                            ev.text,
                        ),
                    )
                }
            }
        }
    }

    private fun addCue(cue: SubtitleCue) {
        synchronized(cues) {
            // re-watching a stretch replaces its old cues instead of stacking duplicates
            cues.removeAll { it.start < cue.end && cue.start < it.end }
            cues.add(cue)
            cues.sortBy { it.start }
        }
        AppLog.d("ai subs cue ${cue.start / 1000}s..${cue.end / 1000}s ${cue.text.take(40)}")
        arrived = cue
        save()
    }

    private fun save() {
        val f = liveFile ?: return
        val snapshot = synchronized(cues) { cues.toList() }
        runCatching { f.writeText(Subtitles.renderCues(snapshot)) }
    }

    private fun overlay() {
        val p = exo ?: return
        // a heap that only ever climbs is a leak; this line is what the crash report shows
        val now = SystemClock.elapsedRealtime()
        if (now - beat > 30_000) {
            beat = now
            val rt = Runtime.getRuntime()
            AppLog.d("ai subs mem=${(rt.totalMemory() - rt.freeMemory()) / 1_000_000}MB cues=${cues.size}")
        }
        val real = p.currentTracks.groups.any { it.type == C.TRACK_TYPE_TEXT && it.isSelected }
        realTrack = real
        val park = mode == Mode.OFF || subsOff || (real && mode == Mode.AUTO)
        if (park != parked) {
            parked = park
            AppLog.d(if (park) "ai subs parked" else "ai subs resumed")
        }
        if (park) {
            arrived = null
            active = null
            activeUntil = 0L
            if (text.isNotEmpty()) text = ""
            return
        }
        val pos = p.currentPosition
        posMs = pos
        // the provider only hands over a line when the speaker stops, which is always after that
        // line began; show it from now for its own length, and fall back to the saved timeline
        arrived?.let {
            arrived = null
            active = it
            activeUntil = pos + (it.end - it.start).coerceIn(1500L, 10_000L)
        }
        val live = active?.takeIf { pos < activeUntil }?.text
        // while the live pass is re-transcribing this stretch, the saved cues for it are stale
        // and flash between the new lines; only a finished batch file is a stable timeline
        val cached = if (live == null && job == null) {
            synchronized(cues) { cues.lastOrNull { pos >= it.start && pos < it.end + GRACE_MS } }?.text
        } else {
            null
        }
        val next = live ?: cached.orEmpty()
        if (next != text) {
            text = next
            AppLog.d("ai subs show=${next.take(30)}")
        }
    }

}
