package dev.phantomwatch.player

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import dev.phantomwatch.BuildConfig
import dev.phantomwatch.core.AppLog
import dev.phantomwatch.data.Stream
import dev.phantomwatch.data.Settings
import dev.phantomwatch.data.Sub
import dev.phantomwatch.subs.AiSubs
import dev.phantomwatch.subs.MkvSubs
import dev.phantomwatch.subs.OsSubs
import dev.phantomwatch.subs.SubsApi
import dev.phantomwatch.subs.SubtitleCue
import dev.phantomwatch.subs.Subtitles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class SubsController(
    private val context: Context,
    private val exo: ExoPlayer,
    private val streamProvider: () -> Stream,
    private val season: String,
    private val episode: String,
    private val scope: CoroutineScope,
    private val tracks: () -> Tracks?,
    private val attach: (List<Sub>, Uri?, Long, Boolean) -> Unit,
) {
    private val stream: Stream get() = streamProvider()

    var subsOff by mutableStateOf(!Settings.on(context, Settings.SUBS_ON, true))
        private set
    var aiSubUrl by mutableStateOf<String?>(null)
        private set
    var osTrack by mutableStateOf<String?>(null)
        private set

    private var aiCues: List<SubtitleCue> = emptyList()
    private var aiRunning = false
    private var osJob: Job? = null
    private var osFor = ""
    private var lastNeed = 0
    private var lastSubSel = ""

    fun toggle() {
        subsOff = !subsOff
        AiSubtitles.setSubsOff(subsOff)
        applySubs()
    }

    private fun applySubs() {
        val sel = exo.trackSelector as? DefaultTrackSelector ?: return
        sel.parameters = sel.buildUponParameters().textTrack(subsOff).build()
        AppLog.d(if (subsOff) "subs off" else "subs on (${stream.subs.size})")
    }

    // a sub is attached by rebuilding the media source, so it must never land while the first frame
    // is still loading: that restarts the fetch the watchdog is timing and costs the whole source
    private suspend fun awaitReady(limitMs: Long = 40_000): Boolean {
        val end = SystemClock.elapsedRealtime() + limitMs
        while (SystemClock.elapsedRealtime() < end) {
            val state = exo.playbackState
            if (state == Player.STATE_READY || state == Player.STATE_ENDED) return true
            delay(250)
        }
        return false
    }

    fun runOsSubs() {
        val s = stream
        if (s.url != osFor) {
            osFor = s.url
            osJob?.cancel()
            osJob = null
        }
        if (osJob?.isActive == true) return
        osJob = scope.launch {
            try {
                // a dub speaks a different script than the sub cut OS hashes against: its captions drift
                if (s.dub != null) {
                    AppLog.d("os skipped, dub cut")
                    return@launch
                }
                val key = BuildConfig.OS_API_KEY
                if (key.isEmpty()) {
                    AppLog.d("os no api key")
                    return@launch
                }
                // love subs drift on some encodes; only trust non-love sources
                if (s.enSubs && hasReliableEnSubs(s.subs)) {
                    AppLog.d("os skipped, provider en subs")
                    return@launch
                }
                if (!awaitReady()) {
                    AppLog.d("os skipped, stream never started")
                    return@launch
                }
                if (AiSubtitles.enabled) {
                    AppLog.d("os skipped, ai subs on")
                    return@launch
                }
                if (hasReliableEnSubs(s.subs)) {
                    AppLog.d("os skipped, stream subs")
                    return@launch
                }
                val hasEnEmbed = tracks()?.groups?.any { g ->
                    g.type == C.TRACK_TYPE_TEXT && (0 until g.length).any { i ->
                        val f = g.getTrackFormat(i)
                        looksEnglish("${f.language ?: "und"}:${f.label ?: ""}")
                    }
                } == true
                if (hasEnEmbed && hasReliableEnSubs(s.subs)) {
                    AppLog.d("os skipped, embed subs")
                    return@launch
                }
                val headers = mediaHeaders(s)
                if (!s.isHls) {
                    val hh = withContext(Dispatchers.IO) { OsSubs.hashOf(s.url, headers) }
                    if (hh != null) {
                        val (hash, size) = hh
                        AppLog.d("os hash $hash $size")
                        val hf = OsSubs.cached(context.cacheDir, hash)
                            ?: withContext(Dispatchers.IO) {
                                OsSubs.search(key, hash, size)?.let { id ->
                                    OsSubs.downloadText(key, id)?.let { OsSubs.toFile(it, context.cacheDir, hash) }
                                }
                            }
                        if (hf != null) {
                            showOsSub(hf, hash, s)
                            return@launch
                        }
                        AppLog.d("os no hash match")
                    } else {
                        AppLog.d("os unhashable")
                    }
                }
                val im = s.imdb
                if (im.isNullOrEmpty()) {
                    AppLog.d("os no imdb, stop")
                    return@launch
                }
                AppLog.d("os imdb $im")
                val ck = if (season.isEmpty()) "imdb-$im" else "imdb-$im-s$season-e$episode"
                OsSubs.cached(context.cacheDir, ck)?.let { f ->
                    showOsSub(f, ck, s)
                    return@launch
                }
                val f = withContext(Dispatchers.IO) {
                    OsSubs.searchImdb(key, im, season, episode)?.let { id ->
                        OsSubs.downloadText(key, id)?.let { OsSubs.toFile(it, context.cacheDir, ck) }
                    }
                }
                if (f == null) {
                    AppLog.d("os imdb none")
                    return@launch
                }
                showOsSub(f, ck, s)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.d("os crash ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    private fun showOsSub(f: java.io.File, hash: String, s: Stream) {
        val label = "English (OpenSubtitles)"
        val merged = (s.subs + Sub(label, Uri.fromFile(f).toString())).distinctBy { it.url }
        osTrack = "en:$label"
        attach(merged, null, exo.currentPosition, exo.isPlaying)
        AppLog.d("subs source=OpenSubtitles $hash")
    }

    fun runQwenSubs() {
        if (aiRunning || !stream.url.startsWith("file")) return
        aiRunning = true
        scope.launch {
            try {
                val keys = withContext(Dispatchers.IO) { AiSubs.keys() }
                if (keys.isEmpty()) {
                    AppLog.d("ai qwen no groq keys, set GROQ_API_KEY")
                    return@launch
                }
                val path = try {
                    Uri.parse(stream.url).path?.takeIf { it.startsWith("/") }
                } catch (_: Exception) {
                    null
                } ?: return@launch
                val tracks = withContext(Dispatchers.IO) { MkvSubs.extract(path) }
                lastNeed = maxOf(lastNeed, tracks.maxOfOrNull { it.cues.size } ?: 0)
                var acted = false
                var current = false
                for (t in tracks) {
                    if (t.cues.size < 5) continue
                    val from = detectLang(t.cues)
                    AppLog.d("ai embed ${t.codec} cues=${t.cues.size} lang=${from ?: "?"}")
                    if (from == null || from == "en") continue
                    val fresh = t.cues.filter { n -> aiCues.none { o -> o.start == n.start } }
                    if (fresh.isEmpty()) {
                        current = true
                        continue
                    }
                    var added = 0
                    for (chunk in fresh.chunked(40)) {
                        val tr = withContext(Dispatchers.IO) { AiSubs.translateLines(chunk.map { it.text }, keys, from) }
                        if (tr == null || tr.size != chunk.size) break
                        val en = chunk.mapIndexed { j, c -> c.copy(text = tr[j]) }
                        aiCues = (aiCues + en).sortedBy { it.start }
                        added += en.size
                        showAi()
                    }
                    if (added > 0) {
                        AppLog.d("ai qwen +$added from=$from total=${aiCues.size}")
                        acted = true
                        break
                    }
                }
                if (!acted) AppLog.d(if (current) "ai subs up to date" else "ai qwen no usable embed subs")
            } catch (e: CancellationException) {
                aiRunning = false
                throw e
            } catch (e: Exception) {
                AppLog.d("ai qwen crash ${e.message}")
            } finally {
                aiRunning = false
            }
        }
    }

    private suspend fun showAi() {
        val all = aiCues.sortedBy { it.start }
        val f = withContext(Dispatchers.IO) { Subtitles.cuesToFile(all, context.cacheDir) } ?: return
        aiSubUrl = Uri.fromFile(f).toString()
        AppLog.d("ai cues=${all.size}")
        val merged = (stream.subs + Sub("AI English", Uri.fromFile(f).toString())).distinctBy { it.url }
        attach(merged, null, exo.currentPosition, exo.isPlaying)
        if (stream.subKey.isNotEmpty() && all.size >= 5) {
            withContext(Dispatchers.IO) {
                SubsApi.store(stream.subKey, "AI English", Subtitles.renderCues(all), "ai")
            }
        }
    }

    suspend fun sync() {
        val s = stream
        AiSubtitles.attach(context, exo, s, scope, season, episode)
        try {
            if (s.inSync) {
                AppLog.d("sync skipped, same-release subs")
                return
            }
            runQwenSubs()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.d("sync crash ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    suspend fun watchSelection() {
        while (true) {
            try {
                val sel = exo.currentTracks.groups
                    .filter { it.type == C.TRACK_TYPE_TEXT }
                    .flatMap { g ->
                        (0 until g.mediaTrackGroup.length).map { i ->
                            val f = g.mediaTrackGroup.getFormat(i)
                            "${f.language}:${f.label}${if (g.isSelected) "*" else ""}"
                        }
                    }.joinToString()
                if (sel != lastSubSel) {
                    lastSubSel = sel
                    AppLog.d("subs watch $sel @${exo.currentPosition / 1000}s")
                }
            } catch (_: Exception) {
            }
            delay(15000)
        }
    }
}
