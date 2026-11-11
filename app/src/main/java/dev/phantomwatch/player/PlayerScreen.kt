package dev.phantomwatch.player

import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.SystemClock
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
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
import dev.phantomwatch.Enhance
import dev.phantomwatch.core.AppLog
import dev.phantomwatch.core.RemoteConfig
import dev.phantomwatch.core.Telemetry
import dev.phantomwatch.data.Dub
import dev.phantomwatch.data.Resume
import dev.phantomwatch.data.Stream
import dev.phantomwatch.data.Settings
import dev.phantomwatch.data.Sub
import dev.phantomwatch.data.Title
import dev.phantomwatch.data.Tmdb
import dev.phantomwatch.R
import dev.phantomwatch.data.withDub
import dev.phantomwatch.provider.JsProviders
import dev.phantomwatch.provider.Providers
import dev.phantomwatch.provider.Resolve
import dev.phantomwatch.subs.MkvSubs
import dev.phantomwatch.provider.DeadHosts
import dev.phantomwatch.provider.DeadReleases
import dev.phantomwatch.data.DetailCache
import dev.phantomwatch.data.DubPref
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
internal fun PlayerScreen(title: String, media: Title, initial: Stream?, season: String, episode: String, onBack: () -> Unit, onGiveUp: (String?) -> Unit, onPickList: () -> Unit, onEpisode: (String, String) -> Unit = { _, _ -> }, onOpenTitle: (Title) -> Unit = {}, startFullscreen: Boolean = false) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val token = remember { PlayerUi.token }
    val resumeScope = rememberCoroutineScope()
    LaunchedEffect(startFullscreen) {
        PlayerUi.fullscreen = startFullscreen
        if (startFullscreen && context.resources.configuration.smallestScreenWidthDp < 600) {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }
    val meta = remember(title, media, season, episode) {
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(if (season.isNotEmpty() && episode.isNotEmpty()) "S$season E$episode" else media.year.orEmpty())
            .apply { media.poster?.let { setArtworkUri(Uri.parse(it)) } }
            .build()
    }
    val epMeta = remember(media.id, season, episode) {
        DetailCache.episodes(media.kind, media.id, season.toIntOrNull() ?: -1)?.firstOrNull { it.n.toString() == episode }
    }
    val overview = remember(media.id, epMeta) {
        epMeta?.overview?.ifBlank { null } ?: DetailCache.detail(media.kind, media.id)?.overview?.ifBlank { null }
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
            // prefer non-banned; fall back to full set so user can still pick
            val notDead = bundle.formats.filter { it.url !in DeadReleases.urls }
            val live = notDead.filter { !DeadHosts.blocked(it.url) }.ifEmpty { notDead }
            AppLog.d("direct ${live.size} formats subs=${bundle.subs.size}")
            if (live.isEmpty()) {
                resolveErr = context.getString(if (JsProviders.engineDown) R.string.player_engine_down else R.string.player_no_streams)
                return@LaunchedEffect
            }
            val best = live.first()
            val streamSubs = Providers.subsFor(best, bundle.subs)
            AppLog.d("direct play provider=${Providers.providerName(best)} label=${best.label} height=${best.height} hls=${best.isHls} enSubs=${best.enSubs} enAudio=${best.enAudio} streamSubs=${streamSubs.size}")
            val base = Stream(best.url, best.isHls, streamSubs, best.referer, null, imdb = bundle.imdb, headers = best.headers, enSubs = best.enSubs, intro = best.intro, dub = best.dub)
            resolved = Providers.restore(base.copy(dubs = Providers.dubsOf(live, best, streamSubs, Providers.langName(DetailCache.detail(media.kind, media.id)?.language) ?: "Sub")), DubPref.get(context, media.kind, media.id))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            resolveErr = e.message ?: context.getString(R.string.player_resolve_failed)
        }
    }
    val rs = resolved
    var gaveUpSent by remember { mutableStateOf(false) }
    var directErrs by remember { mutableStateOf(0) }
    var netErr by remember { mutableStateOf(false) }
    var resumeJob: Job? by remember { mutableStateOf(null) }
    var forcedNames by remember { mutableStateOf<List<String>?>(null) }
    var latestTracks by remember { mutableStateOf<androidx.media3.common.Tracks?>(null) }
    var nowLabel by remember { mutableStateOf<String?>(null) }
    val q = remember(latestTracks) { qualityOf(latestTracks) }
    var embedNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var embedIdx by remember { mutableStateOf(-1) }
    var embedGroup by remember { mutableStateOf<androidx.media3.common.TrackGroup?>(null) }
    var speed by remember { mutableStateOf(1f) }
    var speedNote by remember { mutableStateOf<String?>(null) }
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
            fullscreen = startFullscreen,
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
        Pip.active = true
        Pip.player = exo
        onDispose {
            dev.phantomwatch.lan.LanShare.stop()
            val mine = exo.currentMediaItem?.localConfiguration?.uri?.toString() == live.url
            if (mine) { Pip.active = false; if (Pip.player === exo) Pip.player = null }
            if (mine && (stats.startup > 0 || stats.err.isNotEmpty())) {
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
            if (mine && stats.startup > 0 && Settings.on(context, Settings.SAVE_HISTORY, true)) {
                Resume.save(context, media, season, episode, exo.currentPosition, exo.duration)
            }
            if (mine) exo.pause() // leaving the screen silences it but keeps the media card
        }
    }
    fun attachSubs(
        subs: List<Sub>,
        firstUri: Uri? = null,
        src: Stream = stream,
        pos: Long = exo.currentPosition,
        autoplay: Boolean = true,
    ) {
        runCatching {
            exo.setMediaSource(sourceFor(context, src, subs, firstUri, meta), pos)
            exo.prepare()
            if (autoplay) exo.play()
        }.onFailure { AppLog.d("attach fail ${it.message}") }
    }
    LaunchedEffect(speed) { exo.setPlaybackSpeed(speed) }
    LaunchedEffect(speedNote) { if (speedNote != null) { delay(1200); speedNote = null } }
    val subsScope = rememberCoroutineScope()
    val subs = remember(exo) {
        SubsController(
            context = context,
            exo = exo,
            streamProvider = { live },
            season = season,
            episode = episode,
            scope = subsScope,
            tracks = { latestTracks },
            attach = { s, u, p, a -> attachSubs(s, u, live, p, a) },
        )
    }
    LaunchedEffect(latestTracks) {
        val t = latestTracks ?: return@LaunchedEffect
        val texts = t.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        val supported = texts.associate { it.mediaTrackGroup to it.isSupported }
        val flat = texts.flatMap { grp ->
            val mg = grp.mediaTrackGroup
            (0 until mg.length).map { i -> Triple(mg, i, trackName(mg.getFormat(i))) }
        }
        if (flat.isEmpty()) { embedNames = emptyList(); embedGroup = null; return@LaunchedEffect }
        val names = flat.map { it.third }
        var wi = rankEmbed(names)
        if (subs.osTrack != null) {
            val nonOs = names.indexOfFirst { it != subs.osTrack }
            if (nonOs >= 0) wi = nonOs
        }
        if (wi < 0 && names.size == 1) wi = 0
        val dg = if (wi >= 0) flat[wi].first else flat.first().first
        val dnames = (0 until dg.length).map { i -> trackName(dg.getFormat(i)) }
        embedGroup = dg
        if (dnames != embedNames) embedNames = dnames
        // media3 drops an override whose group is rebuilt, so re-apply until a track sticks
        val anySel = texts.any { it.isSelected }
        if (stream.subs.isEmpty() && !anySel && !subs.subsOff) {
            if (names != forcedNames) {
                forcedNames = names
                AppLog.d("embed force pick $wi from $names")
            }
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
                    AppLog.d("subs source=stream ${names.getOrNull(wi)} sup=${supported[g]}")
                }
            }
        }
    }
    fun attach(pos: Long) {
        stats.attachAt = SystemClock.elapsedRealtime()
        val all = live.subs + subs.aiSubUrl?.let { listOf(Sub("AI English", it)) }.orEmpty()
        attachSubs(all, null, live, pos)
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
        if (exo.currentMediaItem == null || exo.playbackState != Player.STATE_IDLE) return
        netErr = false
        val pos = exo.currentPosition
        AppLog.d("net resume $src at ${pos / 1000}s")
        attach(pos)
    }
    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(v: androidx.media3.common.VideoSize) {
                if (v.height > 0) {
                    nowLabel = qualityLabel(v.width, v.height)
                    AppLog.d("video size ${v.width}x${v.height}")
                }
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                latestTracks = tracks
                val audios = tracks.groups
                    .filter { it.type == C.TRACK_TYPE_AUDIO }
                    .mapNotNull { g ->
                        if (g.length == 0) return@mapNotNull null
                        val f = g.getTrackFormat(0)
                        "${f.language}:${f.sampleMimeType?.substringAfterLast('/')} ${f.channelCount}ch${if (g.isSelected) "*" else ""}${if (!g.isSupported) " UNSUP" else ""}"
                    }
                AppLog.d("audio [${audios.joinToString()}]")
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
                if (exo.currentMediaItem?.localConfiguration?.uri?.toString() != live.url) return
                if (state == Player.STATE_READY) { netErr = false; directErrs = 0 }
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
                if (exo.currentMediaItem?.localConfiguration?.uri?.toString() != live.url) return
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    stats.lastSeekAt = SystemClock.elapsedRealtime()
                    AppLog.d("seek ${oldPosition.positionMs / 1000}s to ${newPosition.positionMs / 1000}s")
                }
                if (reason != Player.DISCONTINUITY_REASON_SEEK) return
                resumeJob?.cancel()
            }

            override fun onPlayerError(error: PlaybackException) {
                if (exo.currentMediaItem?.localConfiguration?.uri?.toString() != live.url) return
                val cause = error.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
                stats.err = "${error.errorCodeName} http=${cause?.responseCode ?: 0}".take(80)
                val chain = generateSequence<Throwable>(error) { it.cause }.take(3).joinToString(" <- ") {
                    "${it.javaClass.simpleName}: ${it.message}"
                }
                AppLog.d("player error ${error.errorCodeName} http=${cause?.responseCode} pos=${exo.currentPosition / 1000}s buf=${exo.bufferedPosition / 1000}s dur=${exo.duration / 1000}s")
                AppLog.d("player cause $chain")
                AppLog.d("player stack ${android.util.Log.getStackTraceString(error).take(3000)}")
                noteDeadHost(chain, cause?.responseCode, live.url)
                if (stats.startup == 0L) Providers.dubBase(live)?.let { b -> AppLog.d("dub fell back to base"); live = b; attachSubs(b.subs, null, b, exo.currentPosition); return }
                if (chain.contains("FileNotFound") || chain.contains("ENOENT")) {
                    try {
                        val f = android.net.Uri.parse(stream.url).path?.let { java.io.File(it) }
                        AppLog.d("file check exists=${f?.exists()} len=${f?.length()}")
                    } catch (_: Exception) {
                    }
                }
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
                        // the bundle is cached for 5 min: reuse it rather than hit every provider again
                        val fresh = try {
                            withContext(Dispatchers.IO) {
                                Providers.nextStream(media, season, episode, live.url, fresh = directErrs > 1)
                            }
                        } catch (_: Exception) {
                            null
                        }
                        if (fresh == null) {
                            AppLog.d("player relink none")
                            return@launch
                        }
                        live = fresh.copy(dubs = live.dubs)
                        AppLog.d("player relink hls=${fresh.isHls} enSubs=${fresh.enSubs} subs=${fresh.subs.size} at ${errPos / 1000}s")
                        attach(errPos)
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
    LaunchedEffect(live.url) { subs.runOsSubs() }
    LaunchedEffect(live.url) { subs.sync() }
    LaunchedEffect(live.url) { subs.watchSelection() }
    val subSize = remember { Settings.sizeFraction(Settings.choice(context, Settings.SUBS_SIZE, Settings.SIZE_MEDIUM)) }
    val subBg = remember { Settings.bgAlpha(Settings.choice(context, Settings.SUBS_BG, Settings.BG_DIM)) }
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
        if (fullest >= 0 && pickEmbedOverride(exo, embedGroup, fullest, "auto", embedNames)) embedIdx = fullest
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
    var touched by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(startFullscreen) }
    var muted by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var episodesOpen by remember { mutableStateOf(false) }
    var episodeSwitch by remember { mutableStateOf(false) }
    var episodesDetail by remember(media.id) { mutableStateOf(DetailCache.detail(media.kind, media.id)) }
    LaunchedEffect(episodesOpen) {
        if (!episodesOpen || episodesDetail != null) return@LaunchedEffect
        episodesDetail = withContext(Dispatchers.IO) {
            runCatching { Tmdb.detail(RemoteConfig.tmdbKey(), media.kind, media.id) }.getOrNull()
        }?.also { DetailCache.put(it) }
    }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    LaunchedEffect(live.url, durationMs > 0) { live = SkipTimes.fill(live, media.id, season, episode, durationMs) }
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
    fun skipIntro() { live.intro?.last?.let { exo.seekTo(it) } }
    fun playDub(d: Dub) {
        if (d.url == live.url) return
        val pos = exo.currentPosition
        val next = live.withDub(d)
        AppLog.d("dub ${d.label} subs=${next.subs.size} ${next.subs.firstOrNull()?.url?.substringAfterLast('/') ?: "none"} at ${pos / 1000}s")
        live = next
        DubPref.set(context, media.kind, media.id, if (d.base) null else d.label)
        attachSubs(next.subs, null, next, pos)
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
        touched = true
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
            c.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            c.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }
    val baseOrientation = remember {
        if (startFullscreen) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        else activity?.requestedOrientation ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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
            context.contentResolver, android.provider.Settings.System.ACCELEROMETER_ROTATION, 0,
        ) == 1
    } catch (_: Exception) { false }
    fun toggleFullscreen() {
        fullscreen = !fullscreen
        if (smallScreen) activity?.requestedOrientation =
            if (fullscreen) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
    }
    val portraitNow = cfg.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
    // bars ride the controls, but not before the first tap: the auto-show at load must not pop them
    LaunchedEffect(fullscreen, cfg.orientation, controls, episodesOpen, episodeSwitch, touched) {
        fullscreenBars(fullscreen && (portraitNow || !controls || episodesOpen || episodeSwitch || !touched))
    }
    LaunchedEffect(fullscreen) { PlayerUi.fullscreen = fullscreen }
    var orientJob: Job? by remember { mutableStateOf(null) }
    val fsNow = rememberUpdatedState(fullscreen)
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
                // enable() reports the current angle at once, so acting on it flips fullscreen on open
                val first = lastZone == -1
                lastZone = zone
                if (first) return
                orientJob?.cancel()
                if (zone != 1) return
                orientJob = resumeScope.launch {
                    delay(800)
                    if (Pip.inPip) return@launch
                    if (!fsNow.value && smallScreen && rotationEnabled()) toggleFullscreen()
                }
            }
        }
    }
    DisposableEffect(Unit) {
        orientListener.enable()
        onDispose {
            orientJob?.cancel()
            orientListener.disable()
            if (PlayerUi.token == token) PlayerUi.fullscreen = false
        }
    }
    BackHandler {
        if (episodesOpen) episodesOpen = false
        else if (fullscreen) toggleFullscreen()
        else onBack()
    }
    DisposableEffect(stream.url) {
        onDispose {
            if (PlayerUi.token == token) {
                activity?.requestedOrientation = baseOrientation
                fullscreenBars(false)
            }
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
            Pip.playing = exo.isPlaying
            stalled = exo.playbackState == Player.STATE_BUFFERING
            if (stats.startup > 0 && SystemClock.elapsedRealtime() - lastSaveAt > 10000) {
                lastSaveAt = SystemClock.elapsedRealtime()
                if (Settings.on(context, Settings.SAVE_HISTORY, true)) {
                    Resume.save(context, media, season, episode, positionMs, durationMs)
                }
            }
            delay(250)
        }
    }
    PlaybackWatchdog(exo, stalled, stats.startup > 0, live) {
        val s = Providers.nextStream(media, season, episode, live.url)
        if (s != null) { live = s; attach(0) } else onGiveUp(live.url)
    }
    val playerView = LocalView.current
    DisposableEffect(playing) {
        playerView.keepScreenOn = playing
        onDispose { playerView.keepScreenOn = false }
    }
    LaunchedEffect(fullscreen) { if (!fullscreen) locked = false }
    LaunchedEffect(locked, cfg.orientation) {
        if (locked) activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
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
    val next = rememberNextUp(media, season, episode)
    @Composable
    fun stage(
        seekInset: androidx.compose.ui.unit.Dp,
        fsUi: Boolean,
        seekBar: Boolean = fsUi,
        shiftPx: () -> Float = { 0f },
        videoShiftPx: () -> Float = shiftPx,
        sheetZoneTopPx: () -> Float = { Float.MAX_VALUE },
        onSheetDrag: (Float) -> Unit = {},
        onSheetDragEnd: (Float) -> Unit = {},
        titleBelow: Boolean = false,
        lockButton: Boolean = true,
        rotateButton: Boolean = false, onRotate: () -> Unit = {},
        subStyle: Pair<Float, Float>? = null,
        subTopPx: Float? = null,
        subBg: Float = 0.80f,
        onFullscreen: () -> Unit = { toggleFullscreen(); poke() },
        maxVideoH: androidx.compose.ui.unit.Dp? = null,
        topPad: androidx.compose.ui.unit.Dp? = null,
    ) {
        PlayerStage(
            title = if (season.isNotEmpty() && episode.isNotEmpty()) "$title · S$season E$episode" else title,
        fullscreen = fsUi, seekBar = seekBar,
        isPlaying = playing,
        stalled = stalled,
        positionMs = positionMs,
        durationMs = durationMs,
        intro = live.intro, dubs = live.dubs,
        outro = outroWindow(live.outro, durationMs, next != null),
        onNextEpisode = next?.let { up -> { onEpisode(up.season, up.episode) } },
        currentDub = live.dub, onPickDub = ::playDub,
        qualities = q.rungs, quality = q.current, qualityNow = nowLabel, onPickQuality = { pickQuality(exo, q.group, it) },
        speed = speed, onPickSpeed = { speed = it; speedNote = "Speed ${speedLabel(it)}"; AppLog.d("speed ${it}x") },
        bufferedMs = bufferedMs, paneH = maxVideoH,
        controlsVisible = controls && !Pip.inPip,
        flash = speedNote,
        swipeHud = swipeHud,
        seekMark = seekMark,
        seekInset = seekInset,
        topPad = topPad,
        shiftPx = shiftPx,
        videoShiftPx = videoShiftPx,
        sheetZoneTopPx = sheetZoneTopPx,
        onSheetDrag = onSheetDrag,
        onSheetDragEnd = onSheetDragEnd,
        titleBelow = titleBelow,
        lockButton = lockButton,
        sidebarOpen = episodesOpen,
        rotateButton = rotateButton, onRotate = onRotate,
        locked = locked,
        subsOn = !subs.subsOff,
        canSubs = AiSubtitles.enabled || live.subs.isNotEmpty() || embedNames.isNotEmpty() || subs.osTrack != null || subs.aiSubUrl != null,
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
        onEpisodes = { episodesOpen = !episodesOpen; poke() },
        canEpisodes = media.kind != "movie",
        onFullscreen = onFullscreen,
        onTogglePlay = ::togglePlay,
        onToggleLock = {
            locked = !locked
            if (locked) controls = false
            else {
                if (fullscreen && orientListener.lastZone == 0) toggleFullscreen()
                else if (!fullscreen) activity?.requestedOrientation = baseOrientation
                poke()
            }
        },
        onSeek = ::seekTo, onKeySeek = ::accSeek,
        onSkipIntro = {
            if (Settings.on(context, Settings.SKIP_INTRO, true)) skipIntro()
        },
        onInteract = ::poke,
        onZoneTap = ::handleZone,
        onToggleSubs = {
            subs.toggle()
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
                subBgAlpha = subBg,
                glow = !episodesOpen,
                onEnhSurface = { enhOut = it; if (it == null) enhBound = null },
                modifier = mod,
            )
        },
        )
    }
    val wide = cfg.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val tablet = cfg.screenWidthDp >= 840
    Box(Modifier.fillMaxSize()) {
    if (Settings.on(context, Settings.AUTOPLAY, true)) {
        NextUpHost(exo, media, season, episode, onEpisode)
    }
    if (Pip.inPip) {
        stage(0.dp, false)
    } else if (tablet || wide) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val sideW = minOf(320.dp, maxWidth * 0.36f)
            val openW by animateDpAsState(
                if (episodesOpen && !tablet) sideW else 0.dp,
                tween(220),
                label = "episodes",
            )
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    PlayerWide(
                        media, season, episode, overview, videoAspect, fullscreen, tablet,
                        onEpisode, onOpenTitle,
                    ) { inset, style -> stage(inset, fullscreen, seekBar = true, subStyle = style) }
                }
                Box(Modifier.width(openW).fillMaxHeight().clipToBounds().background(Color.Black)) {
                    if (openW > 0.dp) {
                        Box(
                            Modifier.fillMaxSize().windowInsetsPadding(
                                WindowInsets.systemBars.union(WindowInsets.displayCutout)
                                    .only(WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.Right),
                            ),
                        ) {
                            EpisodesSidebar(
                                detail = episodesDetail,
                                season = season,
                                episode = episode,
                                onClose = { episodesOpen = false },
                                onEpisode = { s, e ->
                                    if (!episodeSwitch) {
                                        episodeSwitch = true
                                        episodesOpen = false
                                        resumeScope.launch {
                                            delay(220)
                                            onEpisode(s, e)
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
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
                    videoHpx * subSize,
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
                            topPad = statusTop * sheetProg,
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
                            subBg = subBg,
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
        if (DOWNLOAD_BUTTON_ENABLED && !Pip.inPip) {
            PlayerDownloadFab(stream, onOpen = {
                downloadOpen = true
            })
        }
        if (!Pip.inPip) PlayerFailedOverlay(netErr, onPickList, onBack)
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
