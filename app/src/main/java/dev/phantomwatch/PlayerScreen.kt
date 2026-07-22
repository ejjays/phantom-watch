package dev.phantomwatch

import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
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
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.ScrubbingModeParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.frostwire.jlibtorrent.TorrentHandle
import dev.phantomwatch.ui.Cyan
import dev.phantomwatch.ui.DetailCache
import dev.phantomwatch.ui.PlayerPanel
import dev.phantomwatch.ui.PlayerStage
import dev.phantomwatch.ui.PlayerVideo
import dev.phantomwatch.ui.SeekMark
import dev.phantomwatch.ui.SeekSide
import dev.phantomwatch.ui.SwipeHud
import dev.phantomwatch.ui.WatchRed
import dev.phantomwatch.ui.ZoneTap
import dev.phantomwatch.ui.playerVideoShift
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val DOWNLOAD_BUTTON_ENABLED = false

@OptIn(UnstableApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun PlayerScreen(title: String, media: Title, initial: Stream?, ctl: DlCtl?, season: String, episode: String, onBack: () -> Unit, onGiveUp: (String?) -> Unit, onPickList: () -> Unit, onEpisode: (String, String) -> Unit = { _, _ -> }, onOpenTitle: (Title) -> Unit = {}) {
    val context = LocalContext.current
    val resumeScope = rememberCoroutineScope()
    val meta = remember(title, media, season, episode) {
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(if (season.isNotEmpty() && episode.isNotEmpty()) "S$season E$episode" else media.year.orEmpty())
            .apply { media.poster?.let { setArtworkUri(Uri.parse(it)) } }
            .build()
    }
    val epMeta = remember(media.id, season, episode) {
        DetailCache.episodes(media.id, season.toIntOrNull() ?: -1)?.firstOrNull { it.n.toString() == episode }
    }
    val overview = remember(media.id, epMeta) {
        epMeta?.overview?.ifBlank { null } ?: DetailCache.detail(media.id)?.overview?.ifBlank { null }
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
                resolveErr = if (JsProviders.engineDown) "Provider engine unavailable" else "No direct streams found"
                return@LaunchedEffect
            }
            val best = live.first()
            val streamSubs = Providers.subsFor(best, bundle.subs)
            AppLog.d("direct play provider=${Providers.providerName(best)} label=${best.label} height=${best.height} hls=${best.isHls} enSubs=${best.enSubs} enAudio=${best.enAudio} streamSubs=${streamSubs.size}")
            resolved = Stream(best.url, best.isHls, streamSubs, best.referer, null, imdb = bundle.imdb, headers = best.headers, enSubs = best.enSubs)
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
    val build = remember {
        runCatching { Playback.ensure(context) }
    }
    val player = build.getOrNull()
    val buildError = build.exceptionOrNull()?.let { "${it.javaClass.simpleName}: ${it.message}" }
    LaunchedEffect(player, buildError) {
        if (buildError != null) AppLog.d("build crash $buildError") else AppLog.d("built")
    }
    if (rs == null || player == null) {
        if (player == null && buildError != null) {
            PlayerBuildFailed(buildError, onBack)
            return
        }
        PlayerResolving(
            title = title,
            season = season,
            episode = episode,
            resolveErr = resolveErr,
            media = media,
            overview = overview,
            onBack = onBack,
            onRetry = { resolveErr = null; retryN++ },
            onPickList = onPickList,
            onEpisode = onEpisode,
            onOpenTitle = onOpenTitle,
        )
        return
    }
    val stream = rs
    var live by remember { mutableStateOf(rs) }
    val exo = player
    val stats = remember { PlayStats() }
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    var swipeHud by remember { mutableStateOf<SwipeHud?>(null) }
    var swipeActive by remember { mutableStateOf(false) }
    var volAcc by remember { mutableStateOf(0f) }
    LaunchedEffect(swipeActive) {
        if (!swipeActive && swipeHud != null) {
            delay(500)
            swipeHud = null
        }
    }
    var enhReady by remember { mutableStateOf(false) }
    var enhSession by remember { mutableStateOf<com.google.android.gms.media.effect.enhancement.EnhancementSession?>(null) }
    var enhSize by remember { mutableStateOf(0 to 0) }
    var enhOut by remember { mutableStateOf<Surface?>(null) }
    var enhBound by remember { mutableStateOf<Surface?>(null) }
    var subCues by remember { mutableStateOf("") }
    LaunchedEffect(stream.url) { Enhance.probe(context) { enhReady = it } }
    DisposableEffect(exo) {
        PipCtl.active = true
        PipCtl.player = exo
        onDispose {
            PipCtl.active = false
            if (PipCtl.player === exo) PipCtl.player = null
            if (stats.startup > 0 || stats.err.isNotEmpty()) {
                Telemetry.send(
                    JSONObject()
                        .put("ev", "play")
                        .put("kind", media.kind)
                        .put("id", media.id)
                        .put("s", season)
                        .put("e", episode)
                        .put("startMs", stats.startup)
                        .put("stalls", stats.stalls)
                        .put("stallMs", stats.stallMs)
                        .put("pos", exo.currentPosition / 1000)
                        .put("err", stats.err),
                )
            }
            if (stats.startup > 0) Resume.save(context, media, season, episode, exo.currentPosition, exo.duration)
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
        if (osTrack != null) {
            val nonOs = names.indexOfFirst { it != osTrack }
            if (nonOs >= 0) wi = nonOs
        }
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
                if (sel != null) {
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
    fun attachSubs(
        subs: List<Sub>,
        firstUri: Uri? = null,
        src: Stream = stream,
        pos: Long = exo.currentPosition,
        autoplay: Boolean = true,
    ) {
        try {
            exo.setMediaSource(sourceFor(context, src, subs, firstUri, ctl, meta), pos)
            exo.prepare()
            if (autoplay) exo.play()
        } catch (e: Exception) {
            AppLog.d("attach fail ${e.message}")
        }
    }
    fun attach(pos: Long) {
        stats.attachAt = SystemClock.elapsedRealtime()
        val subs = live.subs + aiSubUrl?.let { listOf(Sub("AI English", it)) }.orEmpty()
        attachSubs(subs, null, live, pos)
    }
    LaunchedEffect(resolved) {
        val r = resolved ?: return@LaunchedEffect
        val same = exo.currentMediaItem?.localConfiguration?.uri?.toString() == r.url
        if (same && exo.playbackState != Player.STATE_IDLE) return@LaunchedEffect
        val saved = if (same) Resume.startAt(exo.currentPosition, exo.duration)
        else Resume.position(context, media.kind, media.id, season, episode)
        if (saved > 0) AppLog.d("resume at ${saved / 1000}s")
        attach(saved)
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
                val now = SystemClock.elapsedRealtime()
                if (state == Player.STATE_READY) {
                    if (stats.startup == 0L && stats.attachAt > 0) stats.startup = now - stats.attachAt
                    if (stats.bufAt > 0) {
                        if (now - stats.lastSeekAt > 5000) {
                            stats.stallMs += now - stats.bufAt
                            stats.stalls++
                        }
                        stats.bufAt = 0
                    }
                } else if (state == Player.STATE_BUFFERING && stats.startup > 0 && now - stats.lastSeekAt > 5000) {
                    stats.bufAt = now
                }
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
                    stats.lastSeekAt = SystemClock.elapsedRealtime()
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
                stats.err = "${error.errorCodeName} http=${cause?.responseCode ?: 0}".take(80)
                val chain = generateSequence<Throwable>(error) { it.cause }.take(3).joinToString(" <- ") {
                    "${it.javaClass.simpleName}: ${it.message}"
                }
                AppLog.d("player error ${error.errorCodeName} http=${cause?.responseCode} pos=${exo.currentPosition / 1000}s buf=${exo.bufferedPosition / 1000}s dur=${exo.duration / 1000}s")
                AppLog.d("player cause $chain")
                AppLog.d("player stack ${android.util.Log.getStackTraceString(error).take(3000)}")
                if (cause?.responseCode == 429 || cause?.responseCode == 403 || chain.contains("UnknownHostException")) {
                    // a dead DNS entry is not transient; ban briefly so fallback stops re-picking it
                    val ttl = if (chain.contains("UnknownHostException")) 1L else 24L
                    Torrents.DeadHosts.mark(stream.url, ttl)
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
                            attachSubs(subs, null, stream, pos)
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
                            live = live.copy(url = fresh.url, isHls = fresh.isHls, referer = fresh.referer, subs = Providers.subsFor(fresh, live.subs), headers = fresh.headers)
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
            attachSubs(stream.subs, Uri.fromFile(f), stream, exo.currentPosition, autoplay = exo.isPlaying)
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
        attachSubs(merged, null, stream, exo.currentPosition, autoplay = exo.isPlaying)
        if (stream.subKey.isNotEmpty() && all.size >= 5) {
            withContext(Dispatchers.IO) {
                SubsApi.store(stream.subKey, "AI English", VidLove.renderCues(all), "ai")
            }
        }
    }
    fun showOsSub(f: java.io.File, hash: String) {
        val label = "English (OpenSubtitles)"
        val merged = (stream.subs + Sub(label, Uri.fromFile(f).toString())).distinctBy { it.url }
        osTrack = "en:$label"
        attachSubs(merged, null, stream, exo.currentPosition, autoplay = exo.isPlaying)
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
                if (stream.enSubs) {
                    AppLog.d("os skipped, provider en subs")
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
                if (!stream.isHls) {
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
        sel.parameters = sel.buildUponParameters().textTrack(subsOff).build()
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
        if (stream.auth == null && ctl == null) return@LaunchedEffect
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
                            -1.0
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
    var muted by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var bufferedMs by remember { mutableStateOf(0L) }
    var videoW by remember { mutableStateOf(0) }
    var videoH by remember { mutableStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var stalled by remember { mutableStateOf(true) }
    var downloadOpen by remember { mutableStateOf(false) }
    var seekMark by remember { mutableStateOf<SeekMark?>(null) }
    var hideJob: Job? by remember { mutableStateOf(null) }
    var lastSaveAt by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
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
    // hidden bars must still reserve space, or an immersive first composition freezes the inset at 0
    val statusTop = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
    var keepTheater by remember { mutableStateOf(false) }
    var sheetLocked by remember { mutableStateOf(false) }
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
    }
    LaunchedEffect(fullscreen, cfg.orientation) { fullscreenBars(fullscreen) }
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
        var stallAt = 0L
        var stalls = 0
        while (true) {
            positionMs = exo.currentPosition
            durationMs = exo.duration.coerceAtLeast(0)
            bufferedMs = exo.bufferedPosition
            videoW = exo.videoSize.width
            videoH = exo.videoSize.height
            playing = exo.isPlaying
            PipCtl.playing = exo.isPlaying
            stalled = exo.playbackState == Player.STATE_BUFFERING
            if (stats.startup > 0 && SystemClock.elapsedRealtime() - lastSaveAt > 10000) {
                lastSaveAt = SystemClock.elapsedRealtime()
                Resume.save(context, media, season, episode, positionMs, durationMs)
            }
            val now = SystemClock.elapsedRealtime()
            if (stalled) {
                if (stallAt == 0L) stallAt = now
            } else if (stallAt != 0L) {
                if (now - stallAt >= 3000) stalls++
                stallAt = 0L
                if (stalls >= 3) {
                    stalls = 0
                    val sel = exo.trackSelector as? DefaultTrackSelector
                    if (sel != null && sel.parameters.forceHighestSupportedBitrate) {
                        sel.parameters = sel.buildUponParameters().setForceHighestSupportedBitrate(false).build()
                        AppLog.d("player abr on")
                    }
                }
            }
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
                AppLog.d("enh on ${videoW}x$videoH")
            } catch (e: Exception) {
                AppLog.d("enh attach fail ${e.message}")
                try {
                    s.release()
                } catch (_: Exception) {
                }
                if (enhSession === s) enhSession = null
            }
        }
    }
    DisposableEffect(exo) {
        val l = object : Player.Listener {
            private var logged = false
            override fun onCues(cues: List<Cue>) {
                if (!logged && cues.isNotEmpty()) {
                    logged = true
                    AppLog.d("subs cues ${cues.size}")
                }
                subCues = cues.joinToString("\n") { it.text?.toString().orEmpty() }.trim()
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
    fun stage(
        seekInset: androidx.compose.ui.unit.Dp,
        fsUi: Boolean,
        shiftPx: () -> Float = { 0f },
        videoShiftPx: () -> Float = shiftPx,
        sheetZoneTopPx: () -> Float = { Float.MAX_VALUE },
        onSheetDrag: (Float) -> Unit = {},
        onSheetDragEnd: (Float) -> Unit = {},
        titleBelow: Boolean = false,
        barsInset: Boolean = true,
        lockButton: Boolean = true,
        rotateButton: Boolean = false, onRotate: () -> Unit = {},
        subStyle: Pair<Float, Float>? = null,
        subTopPx: Float? = null,
        onFullscreen: () -> Unit = { toggleFullscreen(); poke() },
        maxVideoH: androidx.compose.ui.unit.Dp? = null,
    ) {
        PlayerStage(
            title = if (season.isNotEmpty() && episode.isNotEmpty()) "$title · S$season E$episode" else title,
        fullscreen = fsUi,
        isPlaying = playing,
        stalled = stalled,
        positionMs = positionMs,
        durationMs = durationMs,
        bufferedMs = bufferedMs,
        controlsVisible = controls && !PipCtl.inPip,
        flash = fillNote,
        swipeHud = swipeHud,
        seekMark = seekMark,
        seekInset = seekInset,
        shiftPx = shiftPx,
        videoShiftPx = videoShiftPx,
        sheetZoneTopPx = sheetZoneTopPx,
        onSheetDrag = onSheetDrag,
        onSheetDragEnd = onSheetDragEnd,
        titleBelow = titleBelow,
        barsInset = barsInset,
        lockButton = lockButton,
        rotateButton = rotateButton, onRotate = onRotate,
        locked = locked,
        subsOn = !subsOff,
        canSubs = stream.subs.isNotEmpty() || live.subs.isNotEmpty() || embedNames.isNotEmpty() || osTrack != null || aiSubUrl != null,
        muted = muted,
        onScrub = ::scrubTo,
        preview = { mod ->
            AndroidView(
                factory = { ctx -> TextureView(ctx).also { previewPlayer?.setVideoTextureView(it) } },
                onRelease = { previewPlayer?.clearVideoTextureView(it) },
                modifier = mod.aspectRatio(videoAspect.coerceIn(1.1f, 2.4f)),
            )
        },
        onBack = onBack,
        onToggleMute = {
            muted = !muted
            exo.volume = if (muted) 0f else 1f
            poke()
        },
        onFullscreen = onFullscreen,
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
        onSwipe = { bright, dy ->
            swipeActive = true
            val d = context.resources.displayMetrics.density
            if (bright) {
                val w = (context as? android.app.Activity)?.window
                if (w != null) {
                    val lp = w.attributes
                    val cur = lp.screenBrightness.takeIf { it >= 0f }
                        ?: (android.provider.Settings.System.getInt(
                            context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, 128,
                        ) / 255f)
                    val nb = (cur - dy / (100f * d)).coerceIn(0.05f, 1f)
                    lp.screenBrightness = nb
                    w.attributes = lp
                    swipeHud = SwipeHud(true, nb)
                }
            } else if (audio != null) {
                volAcc -= dy
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                val min = if (android.os.Build.VERSION.SDK_INT >= 28) {
                    audio.getStreamMinVolume(AudioManager.STREAM_MUSIC)
                } else {
                    0
                }
                var v = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                val unit = 10f * d
                while (volAcc > unit) {
                    volAcc -= unit
                    v += 1
                }
                while (volAcc < -unit) {
                    volAcc += unit
                    v -= 1
                }
                v = v.coerceIn(min, max)
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
                swipeHud = SwipeHud(false, (v - min).toFloat() / (max - min).coerceAtLeast(1))
            }
        },
        onSwipeEnd = {
            swipeActive = false
            volAcc = 0f
        },
        video = { mod ->
            PlayerVideo(
                exo = exo, enhSession = enhSession, videoAspect = videoAspect, maxVideoH = maxVideoH,
                subCues = subCues,
                subStyle = subStyle,
                subTopPx = subTopPx,
                onEnhSurface = { enhOut = it; if (it == null) enhBound = null },
                modifier = mod,
            )
        },
        )
    }
    val wide = cfg.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    Box(Modifier.fillMaxSize()) {
    NextUpHost(exo, media, season, episode, onEpisode)
    if (PipCtl.inPip) {
        stage(0.dp, false)
    } else if (wide) {
        val fsW = maxOf(cfg.screenWidthDp, cfg.screenHeightDp).toFloat()
        val fsH = minOf(cfg.screenWidthDp, cfg.screenHeightDp).toFloat()
        val fsVideoH = if (videoAspect >= fsW / fsH) fsW / videoAspect else fsH
        AnimatedVisibility(visible = true, enter = fadeIn()) {
            stage((((fsH - fsVideoH) / 2).coerceAtLeast(0f)).dp, true)
        }
    } else {
        val portraitW = minOf(cfg.screenWidthDp, cfg.screenHeightDp).toFloat()
        val portraitH = maxOf(cfg.screenWidthDp, cfg.screenHeightDp).toFloat()
        val stageH = (portraitW / videoAspect.coerceIn(0.4f, 16f / 9f)).dp
            .coerceAtMost((portraitH * 0.6f).dp)
        val density = LocalDensity.current
        AnimatedVisibility(visible = true, enter = fadeIn()) {
            BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
                val restTop = statusTop + stageH
                val panelH = (maxHeight - restTop).coerceAtLeast(0.dp)
                val panelPx = with(density) { panelH.toPx() }
                val restPx = with(density) { restTop.toPx() }

                val flingPx = with(density) { 125.dp.toPx() }
                val snap = tween<Float>(240)
                val sheet = remember(panelPx) {
                    AnchoredDraggableState(
                        initialValue = if (keepTheater) panelPx else 0f,
                        positionalThreshold = { it * 0.5f },
                        velocityThreshold = { flingPx },
                        snapAnimationSpec = snap,
                        decayAnimationSpec = exponentialDecay(),
                    )
                }
                LaunchedEffect(panelPx) {
                    sheet.updateAnchors(DraggableAnchors { 0f at 0f; panelPx at panelPx })
                }
                val scope = rememberCoroutineScope()
                val sheetJob = remember(sheet) { arrayOfNulls<Job>(1) }
                fun sheetShift() = panelPx - (sheet.offset.takeUnless { it.isNaN() } ?: (if (keepTheater) panelPx else 0f))
                fun glide(target: Float) {
                    sheetJob[0]?.cancel()
                    sheetJob[0] = scope.launch { sheet.animateTo(target, snap) }
                }
                val panelTop = remember { floatArrayOf(0f) }
                val downY = remember { floatArrayOf(Float.MAX_VALUE) }
                val headerBottom = remember { floatArrayOf(Float.MAX_VALUE) }
                fun inHeader() = !sheetLocked && downY[0] < headerBottom[0]
                val nested = remember(sheet) {
                    object : NestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                            if (available.y >= 0f || sheet.offset <= 0f || sheet.isAnimationRunning || !inHeader()) return Offset.Zero
                            return Offset(0f, sheet.dispatchRawDelta(available.y))
                        }

                        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                            if (available.y <= 0f || sheet.isAnimationRunning || !inHeader()) return Offset.Zero
                            return Offset(0f, sheet.dispatchRawDelta(available.y))
                        }

                        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                            val o = sheet.offset
                            if (!o.isNaN() && !sheet.isAnimationRunning && inHeader()) {
                                glide(if (available.y > flingPx || o > panelPx / 2f) panelPx else 0f)
                            }
                            return Velocity(0f, available.y)
                        }
                    }
                }
                val theater by remember(sheet) {
                    derivedStateOf {
                        val o = sheet.offset
                        if (o.isNaN()) keepTheater else o >= panelPx - 0.5f
                    }
                }
                val sheetProg by remember(sheet, panelPx) {
                    derivedStateOf {
                        val o = sheet.offset.takeUnless { it.isNaN() } ?: (if (keepTheater) panelPx else 0f)
                        if (panelPx <= 0f) 0f else (o / panelPx).coerceIn(0f, 1f)
                    }
                }
                fun reopen() = glide(0f)
                fun settleSheet(v: Float) {
                    val o = sheet.offset
                    if (o.isNaN()) return
                    glide(if (v < -flingPx || o < panelPx / 2f) 0f else panelPx)
                }
                LaunchedEffect(theater) { keepTheater = theater }
                LaunchedEffect(sheetProg > 0.5f, controls) { fullscreenBars(sheetProg > 0.5f && !controls) }
                BackHandler(theater, ::reopen)
                val stageTop = statusTop * (1f - sheetProg)
                val stageHpx = with(density) { (maxHeight - stageTop).toPx() }
                val videoCap = stageH + (maxHeight - stageH) * sheetProg
                val videoHpx = minOf(
                    with(density) { (portraitW / videoAspect.coerceAtLeast(0.1f)).dp.toPx() },
                    with(density) { videoCap.toPx() },
                )
                val letterbox = ((stageHpx - videoHpx) / 2f).coerceAtLeast(0f)
                val subPx = maxOf(
                    videoHpx * SubtitleView.DEFAULT_TEXT_SIZE_FRACTION,
                    with(density) { (if (theater) 15.dp else 13.dp).toPx() },
                )
                val subStyle = subPx to
                    if (theater) {
                        0f
                    } else {
                        -with(density) { (panelH / 2 + 14.dp).toPx() }
                    }
                val subTopPx = if (theater) {
                    letterbox + videoHpx + with(density) { 6.dp.toPx() }
                } else {
                    null
                }
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().padding(top = stageTop)) {
                        stage(
                            navBottom * sheetProg, theater,
                            shiftPx = { sheetShift() },
                            maxVideoH = videoCap,
                            videoShiftPx = { playerVideoShift(sheetShift(), stageHpx, videoHpx) },
                            onSheetDrag = { dy -> sheetJob[0]?.cancel(); sheet.dispatchRawDelta(dy) },
                            onSheetDragEnd = ::settleSheet,
                            sheetZoneTopPx = {
                                if (theater) {
                                    (stageHpx - with(density) { 120.dp.toPx() })
                                        .coerceAtLeast(stageHpx - letterbox)
                                } else {
                                    Float.MAX_VALUE
                                }
                            },
                            titleBelow = theater,
                            lockButton = false,
                            rotateButton = theater, onRotate = { toggleFullscreen(); poke() },
                            subStyle = subStyle,
                            subTopPx = subTopPx,
                            onFullscreen = {
                                if (theater) reopen() else { toggleFullscreen(); poke() }
                            },
                        )
                    }
                    Box(
                        Modifier.offset {
                            val o = sheet.offset.takeUnless { it.isNaN() }
                                ?: (if (keepTheater) panelPx else 0f)
                            IntOffset(0, (restPx + o).roundToInt())
                        }.fillMaxWidth().height(panelH)
                            .onGloballyPositioned { panelTop[0] = it.positionInWindow().y }
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val down = awaitPointerEvent(PointerEventPass.Initial)
                                            .changes.firstOrNull { it.changedToDownIgnoreConsumed() }
                                        if (down != null) downY[0] = panelTop[0] + down.position.y
                                    }
                                }
                            }
                            .nestedScroll(nested),
                    ) {
                        PlayerPanel(
                            media = media, season = season, episode = episode,
                            overview = overview, onEpisode = onEpisode, onOpenTitle = onOpenTitle,
                            onHeaderBottom = { headerBottom[0] = it },
                            onSheetLock = { sheetLocked = it },
                        )
                    }
                }
            }
        }
    }
        if (DOWNLOAD_BUTTON_ENABLED && !PipCtl.inPip) {
            PlayerDownloadFab(stream, onOpen = {
                downloadOpen = true
            })
        }
    }
    if (downloadOpen) {
        PlayerDownloadDialog(
            media = media,
            season = season,
            episode = episode,
            stream = stream,
            videoH = videoH,
            durationMs = durationMs,
            onDismiss = { downloadOpen = false },
        )
    }
}
