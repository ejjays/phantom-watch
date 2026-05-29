package dev.phantomwatch

import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.SystemClock
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.effect.Contrast
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.ScrubbingModeParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.frostwire.jlibtorrent.TorrentHandle
import dev.phantomwatch.ui.PlayerPanel
import dev.phantomwatch.ui.PlayerStage
import dev.phantomwatch.ui.SeekMark
import dev.phantomwatch.ui.SeekSide
import dev.phantomwatch.ui.ZoneTap
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun configsFor(subs: List<Sub>, firstUri: Uri? = null): List<MediaItem.SubtitleConfiguration> {
    return VidLove.englishFirst(subs).mapIndexed { i, sub ->
        val uri = if (i == 0 && firstUri != null) firstUri else Uri.parse(sub.url)
        MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(VidLove.mimeOf(uri.toString()))
            .setLanguage("en")
            .setLabel(sub.label)
            .apply {
                if (sub.label.contains("english", true)) {
                    setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                }
            }
            .build()
    }
}

@OptIn(UnstableApi::class)
private fun itemFor(
    url: String,
    hls: Boolean,
    cfgs: List<MediaItem.SubtitleConfiguration>,
    meta: MediaMetadata? = null,
): MediaItem {
    val low = url.lowercase()
    val b = MediaItem.Builder()
        .setUri(url)
        .setSubtitleConfigurations(cfgs)
    when {
        hls || ".m3u8" in low -> b.setMimeType(MimeTypes.APPLICATION_M3U8)
        else -> Unit
    }
    if (meta != null) b.setMediaMetadata(meta)
    return b.build()
}

internal fun looksEnglish(name: String): Boolean {
    val l = name.lowercase()
    return l.substringBefore(":").startsWith("en") || "english" in l
}

internal fun rankEmbed(names: List<String>): Int {
    var best = -1
    var bestScore = Int.MIN_VALUE
    names.forEachIndexed { i, n ->
        if (!looksEnglish(n)) return@forEachIndexed
        val l = n.lowercase()
        var s = 0
        if ("dialog" in l || "full" in l || "complete" in l) s += 10
        if ("sign" in l || "song" in l || "s&s" in l) s -= 10
        if (s > bestScore) {
            bestScore = s
            best = i
        }
    }
    return best
}

private fun detectLang(cues: List<VidLove.Cue>): String? {
    if (cues.isEmpty()) return null
    val step = maxOf(1, cues.size / 60)
    var ja = 0
    var ko = 0
    var zh = 0
    var en = 0
    var th = 0
    var ar = 0
    var ru = 0
    var n = 0
    var i = 0
    while (i < cues.size && n < 60) {
        val t = cues[i].text.replace(Regex("\\{[^}]*\\}"), "")
        if (t.isNotBlank()) {
            n++
            when {
                t.contains(Regex("[\u3040-\u30ff]")) -> ja++
                Regex("[\uac00-\ud7af]").containsMatchIn(t) -> ko++
                t.contains(Regex("[\u0e00-\u0e7f]")) -> th++
                t.contains(Regex("[\u0600-\u06ff]")) -> ar++
                t.contains(Regex("[\u0400-\u04ff]")) -> ru++
                t.contains(Regex("[\u4e00-\u9fff]")) -> zh++
                t.contains(Regex("[a-zA-Z]")) -> en++
            }
        }
        i += step
    }
    if (n == 0) return null
    val best = mapOf("ja" to ja, "ko" to ko, "th" to th, "ar" to ar, "ru" to ru, "zh" to zh, "en" to en)
        .maxByOrNull { it.value }
    return best?.takeIf { it.value > 0 }?.key
}

private fun mediaHeaders(stream: Stream): Map<String, String> {
    val ref = if (stream.url.startsWith("file")) emptyMap() else mapOf(
        "Referer" to stream.referer,
        "Origin" to "https://${Uri.parse(stream.referer).host}",
    )
    return mapOf("User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Safari/537.36") +
        ref + (stream.auth?.let { mapOf("Authorization" to it) } ?: emptyMap())
}

private fun buildMediaFactory(
    context: android.content.Context,
    stream: Stream,
    headers: Map<String, String>,
    ctl: DlCtl?,
): androidx.media3.exoplayer.source.DefaultMediaSourceFactory {
    val retry = androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(3)
    if (stream.url.startsWith("file")) {
        // incomplete torrent file: hide the length so Exo plays progressively
        // instead of seeking cue tables that are not downloaded yet
        return androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
            DataSource.Factory {
                val inner = FileDataSource()
                object : DataSource {
                    override fun open(dataSpec: DataSpec): Long {
                        inner.open(dataSpec)
                        return C.LENGTH_UNSET.toLong()
                    }
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                        inner.read(buffer, offset, length)
                    override fun getUri(): android.net.Uri? = inner.uri
                    override fun getResponseHeaders(): Map<String, List<String>> = inner.responseHeaders
                    override fun close() = inner.close()
                    override fun addTransferListener(transferListener: TransferListener) =
                        inner.addTransferListener(transferListener)
                }
            },
        ).setLoadErrorHandlingPolicy(retry)
    }
    return androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
        DefaultHttpDataSource.Factory()
            .setUserAgent(headers.getValue("User-Agent"))
            .setDefaultRequestProperties(headers),
    ).setLoadErrorHandlingPolicy(retry)
}

@OptIn(UnstableApi::class)
private fun sourceFor(
    context: android.content.Context,
    stream: Stream,
    subs: List<Sub>,
    firstUri: Uri?,
    ctl: DlCtl?,
    meta: MediaMetadata?,
): MediaSource = buildMediaFactory(context, stream, mediaHeaders(stream), ctl)
    .createMediaSource(itemFor(stream.url, stream.isHls, configsFor(subs, firstUri), meta))

@OptIn(UnstableApi::class)
private fun buildPreviewPlayer(context: android.content.Context, stream: Stream): ExoPlayer {
    val headers = mediaHeaders(stream)
    val selector = DefaultTrackSelector(context).apply {
        parameters = buildUponParameters()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }
    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(2000, 5000, 1000, 1000)
        .build()
    return ExoPlayer.Builder(context)
        .setTrackSelector(selector)
        .setMediaSourceFactory(buildMediaFactory(context, stream, headers, null))
        .setLoadControl(loadControl)
        .build()
        .apply {
            volume = 0f
            setScrubbingModeParameters(ScrubbingModeParameters.Builder().setUseDecodeOnlyFlag(false).build())
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    AppLog.d("preview err ${error.errorCodeName}")
                }
            })
            setMediaItem(itemFor(stream.url, stream.isHls, emptyList()))
            prepare()
        }
}

@OptIn(UnstableApi::class)
@Composable
internal fun PlayerScreen(title: String, media: Title, initial: Stream?, ctl: DlCtl?, season: String, episode: String, onBack: () -> Unit, onGiveUp: (String?) -> Unit, onPickList: () -> Unit) {
    val context = LocalContext.current
    val resumeScope = rememberCoroutineScope()
    val meta = remember(title, media, season, episode) {
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(if (season.isNotEmpty() && episode.isNotEmpty()) "S$season E$episode" else media.year.orEmpty())
            .apply { media.poster?.let { setArtworkUri(Uri.parse(it)) } }
            .build()
    }
    var resolved by remember { mutableStateOf(initial) }
    var resolveErr by remember { mutableStateOf<String?>(null) }
    var retryN by remember { mutableStateOf(0) }
    LaunchedEffect(media.id, media.kind, season, episode, retryN) {
        if (resolved != null) return@LaunchedEffect
        try {
            val bundle = withContext(Dispatchers.IO) {
                Resolve.streams(media, season, episode, fresh = retryN > 0)
            }
            val live = bundle.formats.filter { it.url !in Torrents.DeadReleases.urls }
            AppLog.d("direct ${live.size} formats subs=${bundle.subs.size}")
            if (live.isEmpty()) {
                resolveErr = "No direct streams found"
                return@LaunchedEffect
            }
            val best = live.first()
            val streamSubs = Providers.subsFor(best, bundle.subs)
            AppLog.d("direct play provider=${Providers.providerName(best)} label=${best.label} height=${best.height} hls=${best.isHls} enSubs=${best.enSubs} enAudio=${best.enAudio} streamSubs=${streamSubs.size}")
            resolved = Stream(best.url, best.isHls, streamSubs, best.referer, null, imdb = bundle.imdb)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            resolveErr = e.message ?: "resolve failed"
        }
    }
    val rs = resolved
    var gaveUpSent by remember { mutableStateOf(false) }
    var directErrs by remember { mutableStateOf(0) }
    var netErr by remember { mutableStateOf(false) }
    var resumeJob: Job? by remember { mutableStateOf(null) }
    var lastErrPos by remember { mutableStateOf(-1L) }
    var lastErrDl by remember { mutableStateOf(0L) }
    var sameSpotStreak by remember { mutableStateOf(0) }
    var buildError by remember { mutableStateOf<String?>(null) }
    var forcedNames by remember { mutableStateOf<List<String>?>(null) }
    var latestTracks by remember { mutableStateOf<androidx.media3.common.Tracks?>(null) }
    var embedNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var embedIdx by remember { mutableStateOf(-1) }
    var osTrack by remember { mutableStateOf<String?>(null) }
    var aiSubUrl by remember { mutableStateOf<String?>(null) }
    var embedGroup by remember { mutableStateOf<androidx.media3.common.TrackGroup?>(null) }
    var frontierMs by remember { mutableStateOf(0L) }
    var fillNote by remember { mutableStateOf<String?>(null) }
    var lastSubSel by remember { mutableStateOf("") }
    LaunchedEffect(title) { AppLog.d("screen $title") }
    val player: ExoPlayer? = remember {
        try {
            Playback.ensure(context).also { AppLog.d("built") }
        } catch (e: Exception) {
            buildError = "${e.javaClass.simpleName}: ${e.message}"
            AppLog.d("build crash ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }
    if (rs == null || player == null) {
        if (player == null && buildError != null) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Player failed: $buildError")
                Button(onClick = onBack) { Text("Back") }
            }
            return
        }
        BackHandler(onBack = onBack)
        val cfg = LocalConfiguration.current
        val stageH = (minOf(cfg.screenWidthDp, cfg.screenHeightDp).toFloat() * 9 / 16).dp
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Box(Modifier.fillMaxWidth().statusBarsPadding().height(stageH)) {
                PlayerStage(
                    title = title, fullscreen = false, isPlaying = false, stalled = true,
                    positionMs = 0, durationMs = 0, bufferedMs = 0, controlsVisible = true,
                    flash = resolveErr, seekMark = null, seekInset = 0.dp, locked = false,
                    subsOn = false, canSubs = false,
                    onBack = onBack, onFullscreen = {}, onTogglePlay = {}, onToggleLock = {},
                    onSeek = {}, onInteract = {}, onZoneTap = {}, onToggleSubs = {},
                    onSwipe = { _, _ -> }, onSwipeEnd = {},
                    video = { mod -> Box(mod.fillMaxSize().background(Color.Black)) },
                )
            }
            PlayerPanel(title = media.title, year = media.year, rating = media.rating)
            if (resolveErr != null) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    Button(onClick = { resolveErr = null; retryN++ }) { Text("Retry") }
                    TextButton(onClick = onPickList) { Text("Choose") }
                }
            }
        }
        return
    }
    val stream = rs
    var live by remember { mutableStateOf(rs) }
    val exo = player
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    var gestureNote by remember { mutableStateOf<String?>(null) }
    var volAcc by remember { mutableStateOf(0f) }
    var enhReady by remember { mutableStateOf(false) }
    var enhSession by remember { mutableStateOf<com.google.android.gms.media.effect.enhancement.EnhancementSession?>(null) }
    var enhSize by remember { mutableStateOf(0 to 0) }
    var enhOut by remember { mutableStateOf<Surface?>(null) }
    var enhBound by remember { mutableStateOf<Surface?>(null) }
    var enhSubView by remember { mutableStateOf<SubtitleView?>(null) }
    LaunchedEffect(stream.url) { Enhance.probe(context) { enhReady = it } }
    DisposableEffect(exo) {
        PipCtl.active = true
        PipCtl.player = exo
        onDispose {
            PipCtl.active = false
            if (PipCtl.player === exo) PipCtl.player = null
            exo.stop() // leaving the player screen ends playback; background playback stays in the media session
        }
    }
    LaunchedEffect(latestTracks) {
        val t = latestTracks ?: return@LaunchedEffect
        fun nameOf(f: androidx.media3.common.Format): String {
            val lab = f.label ?: ""
            return if (lab.isNotEmpty()) "${f.language ?: "und"}:$lab" else (f.language ?: "und")
        }
        val flat = t.groups.filter { it.type == C.TRACK_TYPE_TEXT }.flatMap { grp ->
            val mg = grp.mediaTrackGroup
            (0 until mg.length).map { i -> Triple(mg, i, nameOf(mg.getFormat(i))) }
        }
        if (flat.isEmpty()) return@LaunchedEffect
        val names = flat.map { it.third }
        var wi = rankEmbed(names)
        if (wi < 0 && names.size == 1) wi = 0
        val dg = if (wi >= 0) flat[wi].first else flat.first().first
        val dnames = (0 until dg.length).map { i -> nameOf(dg.getFormat(i)) }
        embedGroup = dg
        if (dnames != embedNames) embedNames = dnames
        if (stream.subs.isEmpty() && names != forcedNames) {
            forcedNames = names
            AppLog.d("embed force pick $wi from $names")
            if (wi >= 0) {
                val (g, idx) = flat[wi]
                val sel = exo.trackSelector as? DefaultTrackSelector
                if (sel != null && names.getOrNull(wi) != osTrack) {
                    embedIdx = idx
                    sel.parameters = sel.buildUponParameters()
                        .setOverrideForType(
                            androidx.media3.common.TrackSelectionOverride(g, listOf(idx)),
                        )
                        .build()
                    AppLog.d("subs source=stream ${names.getOrNull(wi)}")
                }
            }
        }
    }
    fun attach(pos: Long) {
        try {
            val subs = live.subs + aiSubUrl?.let { listOf(Sub("AI English", it)) }.orEmpty()
            exo.setMediaSource(sourceFor(context, live, subs, null, ctl, meta), pos)
            exo.prepare()
            exo.play()
        } catch (e: Exception) {
            AppLog.d("attach fail ${e.message}")
        }
    }
    LaunchedEffect(resolved) {
        val r = resolved ?: return@LaunchedEffect
        val same = exo.currentMediaItem?.localConfiguration?.uri?.toString() == r.url
        if (same && exo.playbackState != Player.STATE_IDLE) return@LaunchedEffect
        attach(if (same) exo.currentPosition else 0L)
    }
    fun netResume(src: String) {
        if (exo.playbackState != Player.STATE_IDLE) return
        netErr = false
        val pos = exo.currentPosition
        AppLog.d("net resume $src at ${pos / 1000}s")
        attach(pos)
    }
    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                latestTracks = tracks
                val audios = tracks.groups
                    .filter { it.type == C.TRACK_TYPE_AUDIO }
                    .mapNotNull { g ->
                        if (g.length == 0) return@mapNotNull null
                        val f = g.getTrackFormat(0)
                        "${f.language}:${f.sampleMimeType?.substringAfterLast('/')} ${f.channelCount}ch${if (g.isSelected) "*" else ""}${if (!g.isSupported) " UNSUP" else ""}"
                    }
                if (audios.isNotEmpty()) {
                    AppLog.d("audio [${audios.joinToString()}]")
                }
                val texts = tracks.groups
                    .filter { it.type == C.TRACK_TYPE_TEXT }
                    .mapNotNull { g ->
                        if (g.length == 0) return@mapNotNull null
                        val f = g.getTrackFormat(0)
                        "${f.language}:${f.label}${if (g.isSelected) "*" else ""}"
                    }
                if (texts.isNotEmpty()) {
                    AppLog.d("embed subs [${texts.joinToString()}]")
                }
                val videos = tracks.groups
                    .filter { it.type == C.TRACK_TYPE_VIDEO }
                    .mapNotNull { g ->
                        if (g.length == 0) return@mapNotNull null
                        val idx = (0 until g.length).firstOrNull { g.isTrackSelected(it) } ?: 0
                        val f = g.getTrackFormat(idx)
                        "${f.width}x${f.height}:${f.sampleMimeType?.substringAfterLast('/')}@${f.frameRate}fps${if (g.isSelected) "*" else ""}"
                    }
                if (videos.isNotEmpty()) {
                    AppLog.d("video [${videos.joinToString()}]")
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) netErr = false
                val name = when (state) {
                    Player.STATE_IDLE -> "idle"
                    Player.STATE_BUFFERING -> "buffering"
                    Player.STATE_READY -> "ready"
                    Player.STATE_ENDED -> "ended"
                    else -> "$state"
                }
                AppLog.d("player $name")
            }

            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    AppLog.d("seek ${oldPosition.positionMs / 1000}s to ${newPosition.positionMs / 1000}s")
                }
                if (reason != Player.DISCONTINUITY_REASON_SEEK) return
                resumeJob?.cancel()
                val f = frontierMs
                val dur = exo.duration.takeIf { it > 0 } ?: 0L
                if (f > 0 && dur > 0 && f < dur - 5000 && newPosition.positionMs > f + 5000) {
                    exo.seekTo(f)
                    AppLog.d("seek clamped ${newPosition.positionMs / 1000}s to frontier ${f / 1000}s")
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val cause = error.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
                val chain = generateSequence<Throwable>(error) { it.cause }.take(3).joinToString(" <- ") {
                    "${it.javaClass.simpleName}: ${it.message}"
                }
                AppLog.d("player error ${error.errorCodeName} http=${cause?.responseCode} pos=${exo.currentPosition / 1000}s buf=${exo.bufferedPosition / 1000}s dur=${exo.duration / 1000}s")
                AppLog.d("player cause $chain")
                AppLog.d("player stack ${android.util.Log.getStackTraceString(error).take(3000)}")
                if (cause?.responseCode == 429 || cause?.responseCode == 403) {
                    Torrents.DeadHosts.mark(stream.url)
                    AppLog.d("player dead host ${Torrents.DeadHosts.hostOf(stream.url)}")
                }
                if (chain.contains("FileNotFound") || chain.contains("ENOENT")) {
                    try {
                        val f = android.net.Uri.parse(stream.url).path?.let { java.io.File(it) }
                        AppLog.d("file check exists=${f?.exists()} len=${f?.length()}")
                    } catch (_: Exception) {
                    }
                    val c0 = ctl
                    if (c0 != null) {
                        c0.stop()
                        AppLog.d("retries stopped, file gone")
                        try {
                            android.widget.Toast.makeText(context, "file gone, reopen the title", android.widget.Toast.LENGTH_LONG).show()
                        } catch (_: Exception) {
                        }
                    }
                }
                val c = ctl
                if (c != null && c.active) {
                    val errPos = exo.currentPosition
                    val errDl = try {
                        c.handle.status().totalDone()
                    } catch (_: Exception) {
                        0L
                    }
                    if (lastErrPos >= 0 && errPos - lastErrPos in -5000..5000 && errDl - lastErrDl in -2000000..2000000) {
                        sameSpotStreak += 1
                    } else {
                        sameSpotStreak = 0
                    }
                    lastErrPos = errPos
                    lastErrDl = errDl
                }
                if (c != null && c.active && c.tries < 12 && sameSpotStreak < 3) {
                    c.tries += 1
                    AppLog.d("player resume ${c.tries} awaiting dense window")
                    resumeJob?.cancel()
                    resumeJob = resumeScope.launch {
                        val before = try {
                            c.handle.status().totalDone()
                        } catch (_: Exception) {
                            0L
                        }
                        val pos = exo.currentPosition
                        val dur = exo.duration.takeIf { it > 0 } ?: 0L
                        val t0 = System.currentTimeMillis()
                        var dense = false
                        while (System.currentTimeMillis() - t0 < 180000) {
                            if (!c.active) return@launch
                            if (dur > 0 && c.fileSize > 0 && c.fileIndex >= 0) {
                                try {
                                    val ti = c.handle.torrentFile()
                                    var start = 0L
                                    for (i in 0 until c.fileIndex) start += ti.files().fileSize(i)
                                    val plen = ti.pieceLength().toLong()
                                    val fromAt = start + (pos.toDouble() / dur * c.fileSize).toLong()
                                    val toAt = start + minOf(c.fileSize, ((pos + 30000).toDouble() / dur * c.fileSize).toLong())
                                    val bits = c.handle.status(TorrentHandle.QUERY_PIECES).pieces()
                                    var q = (fromAt / plen).toInt()
                                    val last = (toAt / plen).toInt()
                                    var missing = false
                                    while (q <= last && q < bits.size()) {
                                        if (!bits.getBit(q)) {
                                            missing = true
                                            try {
                                                c.handle.setPieceDeadline(q, 7000)
                                            } catch (_: Exception) {
                                            }
                                        }
                                        q++
                                    }
                                    if (!missing) break
                                } catch (_: Exception) {
                                }
                            } else {
                                val now = try {
                                    c.handle.status().totalDone()
                                } catch (_: Exception) {
                                    before
                                }
                                if (now > before + 5000000) break
                            }
                            delay(2000)
                        }
                        if (!c.active) return@launch
                        try {
                            val dl = try {
                                c.handle.status().totalDone() / 1048576
                            } catch (_: Exception) {
                                -1
                            }
                            AppLog.d("player resume at ${pos / 1000}s dl=${dl}MB")
                            val subs = stream.subs + aiSubUrl?.let { listOf(Sub("AI English", it)) }.orEmpty()
                            exo.setMediaSource(sourceFor(context, stream, subs, null, ctl, meta), pos)
                            exo.prepare()
                            exo.play()
                        } catch (_: Exception) {
                        }
                    }
                } else if (c != null && c.active) {
                    AppLog.d("player gave up after ${c.tries} retries")
                    if (!gaveUpSent && (exo.currentPosition < 10_000 || sameSpotStreak >= 3)) {
                        gaveUpSent = true
                        AppLog.d("player unplayable, back to releases")
                        onGiveUp(stream.url)
                    }
                } else if (c == null && !TORRENTS_ENABLED) {
                    directErrs += 1
                    netErr = true
                    val errPos = exo.currentPosition
                    AppLog.d("player direct err $directErrs")
                    if (directErrs >= 3 && !gaveUpSent && errPos < 60_000) {
                        gaveUpSent = true
                        AppLog.d("player direct dead, next source")
                        onGiveUp(live.url)
                    } else if (directErrs <= 3 && (errPos >= 60_000 || errPos < 10_000)) {
                        resumeJob?.cancel()
                        resumeJob = resumeScope.launch {
                            val fresh = try {
                                withContext(Dispatchers.IO) {
                                    Providers.directAll(media.id, media.kind, season, episode, media.title)
                                }.first.firstOrNull { it.url !in Torrents.DeadReleases.urls }
                            } catch (e: Exception) {
                                null
                            }
                            if (fresh == null) {
                                AppLog.d("player relink none")
                                return@launch
                            }
                            live = live.copy(url = fresh.url, isHls = fresh.isHls, referer = fresh.referer, subs = Providers.subsFor(fresh, live.subs))
                            AppLog.d("player relink ${fresh.label} hls=${fresh.isHls} at ${errPos / 1000}s")
                            attach(errPos)
                        }
                    }
                }
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            (context as? android.app.Activity)?.window?.let { w ->
                val lp = w.attributes
                if (lp.screenBrightness >= 0f) {
                    lp.screenBrightness = -1f
                    w.attributes = lp
                }
            }
            try {
                enhSession?.release()
            } catch (_: Exception) {
            }
            enhSession = null
            try {
                exo.setVideoSurface(null)
                exo.setVideoEffects(listOf(Contrast(0.1f)))
            } catch (_: Exception) {
            }
        }
    }
    DisposableEffect(exo) {
        val conn = context.getSystemService(ConnectivityManager::class.java)
        if (conn == null) return@DisposableEffect onDispose {}
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                resumeScope.launch { netResume("net") }
            }
        }
        try {
            conn.registerDefaultNetworkCallback(cb)
        } catch (_: Exception) {
        }
        onDispose {
            try {
                conn.unregisterNetworkCallback(cb)
            } catch (_: Exception) {
            }
        }
    }
    var subsOff by remember { mutableStateOf(false) }
    var subDelayMs by remember { mutableStateOf(0L) }
    var subRaw by remember { mutableStateOf<String?>(null) }
    val nudgeScope = rememberCoroutineScope()
    suspend fun readSubText(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val path = try {
                android.net.Uri.parse(url).path?.takeIf { url.startsWith("file") || it.startsWith("/") }
            } catch (_: Exception) {
                null
            }
            if (path != null) return@withContext try {
                java.io.File(path).readText()
            } catch (_: Exception) {
                null
            }
            val req = okhttp3.Request.Builder().url(url)
                .apply { mediaHeaders(stream).forEach { (k, v) -> header(k, v) } }
                .build()
            val call = VidLove.client().newCall(req)
            call.timeout().deadline(30, java.util.concurrent.TimeUnit.SECONDS)
            call.execute().use { res ->
                if (res.code !in 200..299) return@withContext null
                res.body?.string()
            }
        } catch (_: Exception) {
            null
        }
    }
    fun applyDelay() {
        if (stream.subs.isEmpty()) return
        nudgeScope.launch {
            var raw = subRaw
            if (raw == null) {
                raw = readSubText(VidLove.englishFirst(stream.subs).first().url)
                if (raw == null) {
                    AppLog.d("subs download failed")
                    return@launch
                }
                subRaw = raw
            }
            val f = withContext(Dispatchers.IO) {
                VidLove.shiftSubFile(raw, subDelayMs, context.cacheDir)
            }
            if (f == null) {
                AppLog.d("subs shift failed")
                return@launch
            }
            val pos = exo.currentPosition
            val wasPlaying = exo.isPlaying
            exo.setMediaSource(sourceFor(context, stream, stream.subs, Uri.fromFile(f), ctl, meta), pos)
            exo.prepare()
            if (wasPlaying) exo.play()
        }
    }
    fun nudge(delta: Long) {
        if (stream.subs.isEmpty()) return
        subDelayMs += delta
        AppLog.d("subs delay ${subDelayMs}ms")
        applyDelay()
    }
    var aiStatus by remember { mutableStateOf<String?>(null) }
    var aiRunning by remember { mutableStateOf(false) }
    var osStatus by remember { mutableStateOf<String?>(null) }
    var osRunning by remember { mutableStateOf(false) }
    var aiCues by remember { mutableStateOf<List<VidLove.Cue>>(emptyList()) }
    var lastNeed by remember { mutableStateOf(0) }
    suspend fun showAi() {
        val all = aiCues.sortedBy { it.start }
        val f = withContext(Dispatchers.IO) { VidLove.cuesToFile(all, context.cacheDir) } ?: return
        aiSubUrl = Uri.fromFile(f).toString()
        aiStatus = "AI ✓ ${all.size}"
        AppLog.d("ai cues=${all.size}")
        val merged = (stream.subs + Sub("AI English", Uri.fromFile(f).toString())).distinctBy { it.url }
        val pos = exo.currentPosition
        val wasPlaying = exo.isPlaying
        exo.setMediaSource(sourceFor(context, stream, merged, null, ctl, meta), pos)
        exo.prepare()
        if (wasPlaying) exo.play()
        if (stream.subKey.isNotEmpty() && all.size >= 5) {
            withContext(Dispatchers.IO) {
                SubsApi.store(stream.subKey, "AI English", VidLove.renderCues(all), "ai")
            }
        }
    }
    fun showOsSub(f: java.io.File, hash: String) {
        val merged = (stream.subs + Sub("English", Uri.fromFile(f).toString())).distinctBy { it.url }
        osTrack = "en:English"
        val pos = exo.currentPosition
        val wasPlaying = exo.isPlaying
        exo.setMediaSource(sourceFor(context, stream, merged, null, ctl, meta), pos)
        exo.prepare()
        if (wasPlaying) exo.play()
        osStatus = "OS ✓"
        AppLog.d("subs source=OpenSubtitles $hash")
    }
    fun runOsSubs() {
        if (osRunning) return
        osRunning = true
        nudgeScope.launch {
            try {
                val key = BuildConfig.OS_API_KEY
                if (key.isEmpty()) {
                    AppLog.d("os no api key")
                    return@launch
                }
                delay(8000)
                if (stream.subs.isNotEmpty()) {
                    AppLog.d("os skipped, stream subs")
                    return@launch
                }
                val hasEnEmbed = latestTracks?.groups?.any { g ->
                    g.type == C.TRACK_TYPE_TEXT && (0 until g.length).any { i ->
                        val f = g.getTrackFormat(i)
                        looksEnglish("${f.language ?: "und"}:${f.label ?: ""}")
                    }
                } == true
                if (hasEnEmbed) {
                    AppLog.d("os skipped, embed subs")
                    return@launch
                }
                osStatus = "OS…"
                val headers = mediaHeaders(stream)
                val hh = withContext(Dispatchers.IO) { OsSubs.hashOf(stream.url, headers) }
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
                        showOsSub(hf, hash)
                        return@launch
                    }
                    AppLog.d("os no hash match")
                } else {
                    AppLog.d("os unhashable")
                }
                if (stream.imdb.isNullOrEmpty()) {
                    AppLog.d("os no imdb, stop")
                    osStatus = null
                    return@launch
                }
                AppLog.d("os imdb ${stream.imdb}")
                val im = stream.imdb
                val ck = if (season.isEmpty()) "imdb-$im" else "imdb-$im-s$season-e$episode"
                OsSubs.cached(context.cacheDir, ck)?.let { f ->
                    showOsSub(f, ck)
                    return@launch
                }
                val f = withContext(Dispatchers.IO) {
                    OsSubs.searchImdb(key, im, season, episode)?.let { id ->
                        OsSubs.downloadText(key, id)?.let { OsSubs.toFile(it, context.cacheDir, ck) }
                    }
                }
                if (f == null) {
                    AppLog.d("os imdb none")
                    osStatus = null
                    return@launch
                }
                showOsSub(f, ck)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                osStatus = null
                AppLog.d("os crash ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }
    fun runQwenSubs() {
        if (aiRunning || !stream.url.startsWith("file")) return
        aiRunning = true
        nudgeScope.launch {
            try {
                aiStatus = "Qwen…"
                val keys = withContext(Dispatchers.IO) { AiSubs.keys() }
                if (keys.isEmpty()) {
                    aiStatus = null
                    AppLog.d("ai qwen no groq keys, set GROQ_API_KEY")
                    return@launch
                }
                val path = try {
                    android.net.Uri.parse(stream.url).path?.takeIf { it.startsWith("/") }
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
                        aiStatus = "Qwen ✓"
                        AppLog.d("ai qwen +$added from=$from total=${aiCues.size}")
                        acted = true
                        break
                    }
                }
                if (!acted) {
                    AppLog.d(if (current) "ai subs up to date" else "ai qwen no usable embed subs")
                    if (!current) aiStatus = null
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                aiRunning = false
                throw e
            } catch (e: Exception) {
                aiStatus = null
                AppLog.d("ai qwen crash ${e.message}")
            } finally {
                aiRunning = false
            }
        }
    }
    var syncStatus by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(stream.url) {
        try {
            if (stream.inSync) {
                syncStatus = "matched"
                AppLog.d("sync skipped, same-release subs")
                return@LaunchedEffect
            }
            runQwenSubs()
            val c = ctl
            if (c != null && !stream.inSync) {
                var waited = 0
                var lastAiDl = 0L
                while (waited < 600000) {
                    delay(15000)
                    if (!c.active) return@LaunchedEffect
                    val done = try {
                        c.handle.status().totalDone()
                    } catch (_: Exception) {
                        -1L
                    }
                    val total = try {
                        c.handle.status().totalWanted()
                    } catch (_: Exception) {
                        -1L
                    }
                    if (total > 0 && done >= total) {
                        var tries = 0
                        while (tries < 6 && aiCues.size < lastNeed) {
                            AppLog.d("ai retry on complete file")
                            runQwenSubs()
                            var w = 0
                            while (aiRunning && w < 12) {
                                delay(15000)
                                w++
                            }
                            tries++
                        }
                        return@LaunchedEffect
                    }
                    if (done > lastAiDl + 15000000) {
                        lastAiDl = done
                        AppLog.d("ai retry at ${done / 1048576}MB")
                        runQwenSubs()
                    }
                    waited += 15000
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.d("sync crash ${e.javaClass.simpleName}: ${e.message}")
            syncStatus = null
        }
    }
    fun applySubs() {
        val sel = exo.trackSelector as? DefaultTrackSelector ?: return
        sel.parameters = sel.buildUponParameters()
            .setRendererDisabled(C.TRACK_TYPE_TEXT, subsOff)
            .build()
        AppLog.d(if (subsOff) "subs off" else "subs on (${stream.subs.size})")
    }
    LaunchedEffect(stream.url) {
        runOsSubs()
    }
    LaunchedEffect(stream.url) {
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
    LaunchedEffect(stream.url) {
        while (true) {
            try {
                val dur = exo.duration.takeIf { it > 0 } ?: 0L
                if (dur > 0) {
                    val frac = withContext(Dispatchers.IO) {
                        if (stream.auth != null) {
                            val hash = stream.url.substringAfter("/files/").substringBefore("/")
                            val cfg = Seedbox.cfg()
                            if (cfg == null || hash.length < 10) return@withContext -1.0
                            val states = Seedbox.pieceStates(cfg, hash)
                            if (states.isEmpty()) return@withContext -1.0
                            states.takeWhile { it == 2 }.size.toDouble() / states.size
                        } else if (ctl != null && ctl.fileSize > 0 && ctl.fileIndex >= 0) {
                            try {
                                TorrentEngine.densePrefix(ctl.handle, ctl.fileIndex, ctl.fileSize).toDouble() / ctl.fileSize
                            } catch (_: Exception) {
                                -1.0
                            }
                        } else {
                            1.0
                        }
                    }
                    if (frac >= 0) {
                        frontierMs = (frac * dur).toLong()
                        fillNote = if (frac < 0.995) {
                            if (stream.auth != null) "Cloud filling • full seek when done" else "Still downloading • full seek when done"
                        } else {
                            null
                        }
                    }
                }
            } catch (_: Exception) {
            }
            delay(5000)
        }
    }
    fun pickEmbed(idx: Int, why: String) {
        val group = embedGroup ?: return
        if (idx !in 0 until group.length) return
        try {
            val sel = exo.trackSelector as? DefaultTrackSelector ?: return
            sel.parameters = sel.buildUponParameters().setOverrideForType(
                androidx.media3.common.TrackSelectionOverride(group, listOf(idx)),
            ).build()
            embedIdx = idx
            AppLog.d("embed $why $idx ${embedNames.getOrNull(idx)}")
        } catch (e: Exception) {
            AppLog.d("embed pick fail ${e.message}")
        }
    }
    fun cycleEmbed() {
        val n = embedNames.size
        if (n == 0) return
        val next = if (embedIdx < 0) (if (n > 1) 1 else 0) else (embedIdx + 1) % n
        pickEmbed(next, "cycle")
    }
    var langDone by remember { mutableStateOf(false) }
    LaunchedEffect(embedNames) {
        if (langDone || stream.subs.isNotEmpty() || embedNames.isEmpty()) return@LaunchedEffect
        if (!stream.url.startsWith("file")) return@LaunchedEffect
        langDone = true
        val path = try {
            android.net.Uri.parse(stream.url).path
        } catch (_: Exception) {
            null
        }
        if (path == null || !path.startsWith("/")) return@LaunchedEffect
        val tracks = withContext(Dispatchers.IO) { MkvSubs.extract(path) }
        if (tracks.isEmpty()) return@LaunchedEffect
        val langs = tracks.map { t -> detectLang(t.cues) ?: "und" }
        AppLog.d("embed lang $langs")
        embedNames = embedNames.mapIndexed { i, n -> "$n:${langs.getOrNull(i) ?: "und"}" }
        var fullest = -1
        var fullestCues = -1
        tracks.forEachIndexed { i, t ->
            if (langs.getOrNull(i) == "en" && t.cues.size > fullestCues) {
                fullestCues = t.cues.size
                fullest = i
            }
        }
        if (fullest >= 0) pickEmbed(fullest, "auto")
    }

    DisposableEffect(exo) {
        val lc = (context as? ComponentActivity)?.lifecycle ?: return@DisposableEffect onDispose {}
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME && netErr) resumeScope.launch { netResume("fg") }
        }
        lc.addObserver(obs)
        onDispose { lc.removeObserver(obs) }
    }
    var controls by remember { mutableStateOf(true) }
    var fullscreen by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var bufferedMs by remember { mutableStateOf(0L) }
    var videoW by remember { mutableStateOf(0) }
    var videoH by remember { mutableStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var stalled by remember { mutableStateOf(true) }
    var seekMark by remember { mutableStateOf<SeekMark?>(null) }
    var hideJob: Job? by remember { mutableStateOf(null) }
    var lastTapAt by remember { mutableStateOf(0L) }
    var lastTapSide by remember { mutableStateOf<ZoneTap?>(null) }
    var tapId by remember { mutableStateOf(0) }
    var accSide by remember { mutableStateOf<SeekSide?>(null) }
    var accTotal by remember { mutableStateOf(0) }
    var accAt by remember { mutableStateOf(0L) }
    fun poke() {
        controls = true
        hideJob?.cancel()
        hideJob = resumeScope.launch {
            delay(3000)
            controls = false
        }
    }
    fun togglePlay() {
        if (exo.playbackState == Player.STATE_IDLE) netResume("tap")
        else if (exo.isPlaying) exo.pause() else exo.play()
        poke()
    }
    fun seekTo(f: Float) {
        val d = exo.duration
        if (d > 0) exo.seekTo((f * d).toLong())
        poke()
    }
    fun accSeek(side: SeekSide) {
        val dir = if (side == SeekSide.Left) -10000L else 10000L
        val now = System.currentTimeMillis()
        accTotal = if (accSide == side && now - accAt < 1000) accTotal + 10 else 10
        accSide = side
        accAt = now
        exo.seekTo((exo.currentPosition + dir).coerceAtLeast(0))
        seekMark = SeekMark(side, accTotal)
        resumeScope.launch {
            delay(1000)
            if (System.currentTimeMillis() - accAt >= 1000) seekMark = null
        }
    }
    fun singleTap() {
        if (controls) controls = false else poke()
    }
    fun handleZone(side: ZoneTap) {
        if (locked) {
            poke()
            return
        }
        val now = System.currentTimeMillis()
        val prevSide = lastTapSide
        val prevAt = lastTapAt
        lastTapAt = now
        lastTapSide = side
        if (side == ZoneTap.Center) {
            if (prevSide == ZoneTap.Center && now - prevAt < 320) togglePlay() else singleTap()
            return
        }
        if (prevSide == side && now - prevAt < 320) {
            tapId += 1
            accSeek(if (side == ZoneTap.Left) SeekSide.Left else SeekSide.Right)
            return
        }
        tapId += 1
        val id = tapId
        resumeScope.launch {
            delay(330)
            if (tapId == id) singleTap()
        }
    }
    fun fullscreenBars(fs: Boolean) {
        val w = (context as? android.app.Activity)?.window ?: return
        val c = androidx.core.view.WindowCompat.getInsetsController(w, w.decorView)
        if (fs) {
            c.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            c.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            c.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }
    val activity = context as? android.app.Activity
    val baseOrientation = remember {
        activity?.requestedOrientation ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    val cfg = LocalConfiguration.current
    val smallScreen = cfg.smallestScreenWidthDp < 600
    fun rotationEnabled() = try {
        android.provider.Settings.System.getInt(
            context.contentResolver,
            android.provider.Settings.System.ACCELEROMETER_ROTATION,
            0,
        ) == 1
    } catch (_: Exception) {
        false
    }
    fun toggleFullscreen() {
        fullscreen = !fullscreen
        if (smallScreen) activity?.requestedOrientation =
            if (fullscreen) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
        fullscreenBars(fullscreen)
    }
    var orientJob: Job? by remember { mutableStateOf(null) }
    val fsNow = rememberUpdatedState(fullscreen)
    val lockedNow = rememberUpdatedState(locked)
    val orientListener = remember {
        object : android.view.OrientationEventListener(context) {
            var lastZone = -1
            override fun onOrientationChanged(deg: Int) {
                val zone = when {
                    deg < 0 -> -1
                    deg in 60..120 || deg in 240..300 -> 1
                    deg <= 30 || deg >= 330 || deg in 150..210 -> 0
                    else -> -1
                }
                if (zone == -1 || zone == lastZone) return
                lastZone = zone
                orientJob?.cancel()
                orientJob = resumeScope.launch {
                    delay(800)
                    if (PipCtl.inPip) return@launch
                    if (zone == 0 && fsNow.value && !lockedNow.value) {
                        toggleFullscreen()
                    } else if (zone == 1 && !fsNow.value && rotationEnabled()) {
                        toggleFullscreen()
                    }
                }
            }
        }
    }
    DisposableEffect(Unit) {
        orientListener.enable()
        onDispose {
            orientJob?.cancel()
            orientListener.disable()
        }
    }
    BackHandler {
        if (fullscreen) toggleFullscreen() else onBack()
    }
    DisposableEffect(stream.url) {
        onDispose {
            activity?.requestedOrientation = baseOrientation
            fullscreenBars(false)
        }
    }
    LaunchedEffect(stream.url) {
        locked = false
        poke()
        while (true) {
            positionMs = exo.currentPosition
            durationMs = exo.duration.coerceAtLeast(0)
            bufferedMs = exo.bufferedPosition
            videoW = exo.videoSize.width
            videoH = exo.videoSize.height
            playing = exo.isPlaying
            PipCtl.playing = exo.isPlaying
            stalled = exo.playbackState == Player.STATE_BUFFERING
            delay(250)
        }
    }
    val playerView = LocalView.current
    DisposableEffect(playing) {
        playerView.keepScreenOn = playing
        onDispose { playerView.keepScreenOn = false }
    }
    LaunchedEffect(fullscreen) {
        if (!fullscreen) locked = false
    }
    LaunchedEffect(locked, cfg.orientation) {
        if (locked) {
            activity?.requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }
    val videoAspect = if (videoW > 0 && videoH > 0) videoW.toFloat() / videoH else 16f / 9f
    LaunchedEffect(videoW, videoH, enhReady, enhOut, enhSession) {
        val out = enhOut
        if (!enhReady || videoW <= 0 || videoH <= 0 || out == null || !out.isValid) return@LaunchedEffect
        val cur = enhSession
        if (cur != null && enhSize.first == videoW && enhSize.second == videoH) {
            if (enhBound !== out) {
                try {
                    cur.setOutputSurface(out, Enhance.options(videoW, videoH), Enhance.frames())
                    enhBound = out
                } catch (e: Exception) {
                    AppLog.d("enh rebind fail ${e.message}")
                }
            }
            return@LaunchedEffect
        }
        enhSession = null
        enhBound = null
        Enhance.open(context, videoW, videoH) { s ->
            if (s == null || !out.isValid) {
                try {
                    s?.release()
                } catch (_: Exception) {
                }
                return@open
            }
            try {
                cur?.release()
            } catch (_: Exception) {
            }
            enhSession = s
            enhSize = videoW to videoH
            try {
                exo.setVideoSurface(s.getInputSurface())
                s.setOutputSurface(out, Enhance.options(videoW, videoH), Enhance.frames())
                enhBound = out
                try {
                    exo.setVideoEffects(emptyList())
                } catch (_: Exception) {
                }
                AppLog.d("enh on ${videoW}x$videoH")
            } catch (e: Exception) {
                AppLog.d("enh attach fail ${e.message}")
                try {
                    s.release()
                } catch (_: Exception) {
                }
                try {
                    exo.setVideoEffects(listOf(Contrast(0.1f)))
                } catch (_: Exception) {
                }
                if (enhSession === s) enhSession = null
            }
        }
    }
    DisposableEffect(enhSession, enhSubView) {
        val v = enhSubView
        if (enhSession == null || v == null) return@DisposableEffect onDispose {}
        val l = object : Player.Listener {
            override fun onCues(cues: List<Cue>) {
                v.setCues(cues)
            }
        }
        exo.addListener(l)
        onDispose { exo.removeListener(l) }
    }
    var previewPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var previewAt by remember { mutableStateOf(0L) }
    DisposableEffect(live.url) {
        try {
            previewPlayer = buildPreviewPlayer(context, live)
        } catch (e: Exception) {
            AppLog.d("preview build fail ${e.message}")
        }
        onDispose {
            try {
                previewPlayer?.release()
            } catch (_: Exception) {
            }
            previewPlayer = null
        }
    }
    fun scrubTo(f: Float?) {
        val p = previewPlayer ?: return
        try {
            if (f == null) {
                p.setScrubbingModeEnabled(false)
                return
            }
            val now = SystemClock.uptimeMillis()
            if (now - previewAt < 120) return
            previewAt = now
            p.setScrubbingModeEnabled(true)
            if (durationMs > 0) p.seekTo((f * durationMs).toLong())
        } catch (_: Exception) {
        }
    }
    @Composable
    fun stage(seekInset: androidx.compose.ui.unit.Dp, matchH: Boolean, fsUi: Boolean) {
        PlayerStage(
            title = title,
        fullscreen = fsUi,
        isPlaying = playing,
        stalled = stalled,
        positionMs = positionMs,
        durationMs = durationMs,
        bufferedMs = bufferedMs,
        controlsVisible = controls && !PipCtl.inPip,
        flash = gestureNote ?: fillNote,
        seekMark = seekMark,
        seekInset = seekInset,
        locked = locked,
        subsOn = !subsOff,
        canSubs = stream.subs.isNotEmpty() || embedNames.isNotEmpty(),
        onScrub = ::scrubTo,
        preview = { mod ->
            AndroidView(
                factory = { ctx -> TextureView(ctx).also { previewPlayer?.setVideoTextureView(it) } },
                onRelease = { previewPlayer?.clearVideoTextureView(it) },
                modifier = mod.aspectRatio(videoAspect.coerceIn(1.1f, 2.4f)),
            )
        },
        onBack = onBack,
        onFullscreen = { toggleFullscreen(); poke() },
        onTogglePlay = ::togglePlay,
        onToggleLock = {
            locked = !locked
            if (locked) {
                controls = false
            } else {
                if (fullscreen && orientListener.lastZone == 0) toggleFullscreen()
                else if (!fullscreen) activity?.requestedOrientation = baseOrientation
                poke()
            }
        },
        onSeek = ::seekTo,
        onInteract = ::poke,
        onZoneTap = ::handleZone,
        onToggleSubs = {
            subsOff = !subsOff
            applySubs()
            poke()
        },
        onSwipe = { xFrac, dy ->
            if (xFrac < 0.4f) {
                val w = (context as? android.app.Activity)?.window
                if (w != null) {
                    val lp = w.attributes
                    val cur = lp.screenBrightness.takeIf { it >= 0f } ?: 0.5f
                    val nb = (cur - dy / 900f).coerceIn(0.05f, 1f)
                    lp.screenBrightness = nb
                    w.attributes = lp
                    gestureNote = "Brightness ${(nb * 100).roundToInt()}%"
                }
            } else if (xFrac > 0.6f && audio != null) {
                volAcc -= dy
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                var v = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                while (volAcc > 40) {
                    volAcc -= 40
                    v += 1
                }
                while (volAcc < -40) {
                    volAcc += 40
                    v -= 1
                }
                v = v.coerceIn(0, max)
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
                gestureNote = "Volume ${v * 100 / max}%"
            }
        },
        onSwipeEnd = {
            gestureNote = null
            volAcc = 0f
        },
        video = { mod ->
            if (enhSession != null) {
                Box(mod.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AndroidView(
                        factory = { ctx ->
                            SurfaceView(ctx).apply {
                                holder.addCallback(object : SurfaceHolder.Callback {
                                    override fun surfaceCreated(h: SurfaceHolder) {
                                        enhOut = h.surface
                                    }

                                    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {}

                                    override fun surfaceDestroyed(h: SurfaceHolder) {
                                        enhOut = null
                                        enhBound = null
                                    }
                                })
                            }
                        },
                        modifier = Modifier.aspectRatio(videoAspect, matchH),
                    )
                    AndroidView(
                        factory = { ctx -> SubtitleView(ctx).also { enhSubView = it } },
                        update = {
                            it.translationY =
                                -(14 * it.resources.displayMetrics.density)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = exo
                            useController = false
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                        }
                    },
                    update = {
                        it.resizeMode =
                            if (fsUi && !PipCtl.inPip) androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            else androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        it.subtitleView?.translationY =
                            -(14 * it.resources.displayMetrics.density)
                    },
                    modifier = mod,
                )
            }
            },
        )
    }
    val wide = cfg.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    if (PipCtl.inPip) {
        stage(0.dp, videoAspect < 16f / 9f, false)
    } else if (wide) {
        val fsW = maxOf(cfg.screenWidthDp, cfg.screenHeightDp).toFloat()
        val fsH = minOf(cfg.screenWidthDp, cfg.screenHeightDp).toFloat()
        val fsVideoH = if (videoAspect >= fsW / fsH) fsW / videoAspect else fsH
        AnimatedVisibility(visible = true, enter = fadeIn()) {
            stage((((fsH - fsVideoH) / 2).coerceAtLeast(0f)).dp, videoAspect < fsW / fsH, true)
        }
    } else {
        val portraitW = minOf(cfg.screenWidthDp, cfg.screenHeightDp).toFloat()
        val stageH = (portraitW * 9 / 16).dp
        AnimatedVisibility(visible = true, enter = fadeIn()) {
            Column(Modifier.fillMaxSize().background(Color.Black)) {
                Box(Modifier.fillMaxWidth().statusBarsPadding().height(stageH)) {
                    stage(0.dp, videoAspect < 16f / 9f, false)
                }
                PlayerPanel(title = media.title, year = media.year, rating = media.rating)
            }
        }
    }
}
