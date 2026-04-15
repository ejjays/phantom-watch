package dev.phantomwatch

import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Bundle
import android.media.AudioManager
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.effect.Contrast
import androidx.media3.ui.SubtitleView
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.frostwire.jlibtorrent.TorrentHandle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import dev.phantomwatch.ui.Mono
import dev.phantomwatch.ui.MoviesHome
import dev.phantomwatch.ui.PlayerPanel
import dev.phantomwatch.ui.PlayerStage
import dev.phantomwatch.ui.SeekMark
import dev.phantomwatch.ui.SeekSide
import dev.phantomwatch.ui.Slate400
import dev.phantomwatch.ui.ZoneTap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_PhantomWatch)
        super.onCreate(savedInstanceState)
        PipCtl.inPip = false
        androidx.core.content.ContextCompat.registerReceiver(
            this, pipRecv,
            android.content.IntentFilter().apply {
                addAction(ACT_PIP_TOGGLE)
                addAction(ACT_PIP_CLOSE)
            },
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) { App() }
            }
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(pipRecv)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PipCtl.inPip = isInPictureInPictureMode
        AppLog.d("pip mode $isInPictureInPictureMode")
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val p = PipCtl.player
        AppLog.d("pip leave active=${PipCtl.active} player=${p != null}")
        if (PipCtl.active && p != null) {
            try {
                val ok = pipParams(p.isPlaying)?.let { enterPictureInPictureMode(it) } ?: false
                AppLog.d("pip enter $ok")
            } catch (e: Exception) {
                AppLog.d("pip fail ${e.message}")
            }
        }
    }

    fun armPip(on: Boolean, playing: Boolean) {
        try {
            pipParams(playing, auto = on)?.let { setPictureInPictureParams(it) }
        } catch (e: Exception) {
            AppLog.d("pip arm fail ${e.message}")
        }
    }

    private val pipRecv = object : android.content.BroadcastReceiver() {
        override fun onReceive(ctx: android.content.Context, intent: android.content.Intent) {
            val p = PipCtl.player ?: return
            when (intent.action) {
                ACT_PIP_TOGGLE -> {
                    try {
                        if (p.isPlaying) p.pause() else p.play()
                        pipParams(p.isPlaying)?.let { setPictureInPictureParams(it) }
                    } catch (_: Exception) {
                    }
                }
                ACT_PIP_CLOSE -> {
                    try {
                        p.pause()
                    } catch (_: Exception) {
                    }
                    finish()
                }
            }
        }
    }

    private fun pipParams(playing: Boolean, auto: Boolean = false): android.app.PictureInPictureParams? = try {
        val toggle = android.app.RemoteAction(
            android.graphics.drawable.Icon.createWithResource(
                this,
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            ),
            if (playing) "Pause" else "Play", "",
            android.app.PendingIntent.getBroadcast(
                this, 1,
                android.content.Intent(ACT_PIP_TOGGLE).setPackage(packageName),
                android.app.PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        val close = android.app.RemoteAction(
            android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
            "Close", "",
            android.app.PendingIntent.getBroadcast(
                this, 2,
                android.content.Intent(ACT_PIP_CLOSE).setPackage(packageName),
                android.app.PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        val b = android.app.PictureInPictureParams.Builder()
            .setAspectRatio(android.util.Rational(16, 9))
            .setActions(listOf(toggle, close))
        if (auto && android.os.Build.VERSION.SDK_INT >= 31) b.setAutoEnterEnabled(true)
        b.build()
    } catch (_: Exception) {
        null
    }
}

private sealed interface Screen {
    data object Home : Screen
    data class Player(val title: String, val stream: Stream?, val ctl: DlCtl?, val entry: TEntry?, val media: Title, val season: String, val episode: String) : Screen
    data class Torrents(val title: Title, val season: String, val episode: String) : Screen
    data class Fetch(val title: Title, val season: String, val episode: String, val entry: TEntry) : Screen
}

private const val ACT_PIP_TOGGLE = "dev.phantomwatch.PIP_TOGGLE"
private const val ACT_PIP_CLOSE = "dev.phantomwatch.PIP_CLOSE"

object PipCtl {
    var active = false
    var inPip by mutableStateOf(false)
    var player: androidx.media3.exoplayer.ExoPlayer? = null
}

class DlCtl(val handle: TorrentHandle) {
    @Volatile var active = true
    var tries = 0
    var fileIndex = -1
    var fileSize = 0L
    fun stop() {
        active = false
        try {
            handle.pause()
        } catch (_: Exception) {
        }
    }
}

@Composable
private fun App() {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var forward by remember { mutableStateOf(true) }
    var instant by remember { mutableStateOf(false) }
    var autoRelease by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        SubsApi.init(context)
        withContext(Dispatchers.IO) {
            try {
                TorrentEngine.ensure()
            } catch (_: Exception) {
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        LaunchedEffect(screen) { instant = false } // snap applies to one hop only
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                if (instant) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else if (forward) {
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it } + fadeOut())
                } else {
                    (slideInHorizontally { -it } + fadeIn()) togetherWith
                        (slideOutHorizontally { it } + fadeOut())
                }
            },
            label = "screen",
        ) { s ->
            when (s) {
                is Screen.Home -> MoviesHome(
                    onWatch = { t, seas, ep ->
                        forward = true
                        autoRelease = true
                        screen = Screen.Player(t.title, null, null, null, t, seas, ep)
                    },
                )
                is Screen.Player -> PlayerScreen(
                    title = s.title,
                    media = s.media,
                    initial = s.stream,
                    ctl = s.ctl,
                    season = s.season,
                    episode = s.episode,
                    onBack = {
                        s.ctl?.stop()
                        val e = s.entry
                        if (e != null && s.stream?.auth != null) {
                            scope.launch(Dispatchers.IO) {
                                Seedbox.cfg()?.let { Seedbox.delete(it, e.infoHash) }
                            }
                        }
                        forward = false
                        screen = Screen.Home
                    },
                    onGiveUp = { deadUrl ->
                        if (s.entry != null) {
                            Torrents.DeadReleases.hashes.add(s.entry.infoHash)
                            s.ctl?.stop()
                            forward = false
                            autoRelease = false
                            screen = Screen.Torrents(s.media, s.season, s.episode)
                        } else {
                            deadUrl?.let { Torrents.DeadReleases.urls.add(it) }
                            s.ctl?.stop()
                            forward = false
                            autoRelease = true
                            screen = Screen.Torrents(s.media, s.season, s.episode)
                        }
                    },
                    onPickList = {
                        forward = false
                        autoRelease = false
                        screen = Screen.Torrents(s.media, s.season, s.episode)
                    },
                )
                is Screen.Torrents -> TorrentsScreen(
                    title = s.title,
                    season = s.season,
                    episode = s.episode,
                    autoplay = autoRelease,
                    onFetch = {
                        e ->
                        forward = true
                        screen = Screen.Fetch(s.title, s.season, s.episode, e)
                    },
                    onSeedReady = { stream, e ->
                        forward = true
                        instant = true
                        screen = Screen.Player(s.title.title, stream, null, e, s.title, s.season, s.episode)
                    },
                    onBack = {
                        forward = false
                        screen = Screen.Home
                    },
                )
                is Screen.Fetch -> {
                    val dir = remember(s.entry.infoHash) {
                        java.io.File(context.cacheDir, "torrents/${s.entry.infoHash}")
                    }
                    FetchScreen(
                        title = s.title,
                        season = s.season,
                        episode = s.episode,
                        entry = s.entry,
                        dir = dir,
                        onPlay = { stream, ctl ->
                            forward = true
                            screen = Screen.Player(s.title.title, stream, ctl, s.entry, s.title, s.season, s.episode)
                        },
                        onBack = {
                            forward = false
                            autoRelease = false
                            screen = Screen.Torrents(s.title, s.season, s.episode)
                        },
                    )
                }
            }
        }
        if (!PipCtl.inPip) LogFab()
    }
}

@Composable
private fun LogFab() {
    var open by remember { mutableStateOf(false) }
    var ox by remember { mutableStateOf(0f) }
    var oy by remember { mutableStateOf(0f) }
    Box(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp),
    ) {
        Button(
            onClick = { open = true },
            modifier = Modifier.align(Alignment.TopEnd)
                .offset { IntOffset(ox.roundToInt(), oy.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        ox += drag.x
                        oy += drag.y
                    }
                },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        ) { Text("LOG", style = MaterialTheme.typography.labelSmall) }
    }
    if (open) {
        val clip = LocalClipboardManager.current
        val scroll = rememberScrollState()
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Logs (${AppLog.lines.size})") },
            text = {
                Box(Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(scroll)) {
                    Text(
                        AppLog.lines.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { AppLog.lines.clear() }) { Text("Clear") }
                    TextButton(onClick = {
                        clip.setText(AnnotatedString(AppLog.lines.joinToString("\n")))
                    }) { Text("Copy all") }
                    TextButton(onClick = { open = false }) { Text("Close") }
                }
            },
        )
    }
}

private fun isWifi(context: android.content.Context): Boolean {
    val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        ?: return true
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
}

private fun cacheBytes(dir: java.io.File): Long {
    return try {
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    } catch (_: Exception) {
        0L
    }
}

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
private fun itemFor(url: String, hls: Boolean, cfgs: List<MediaItem.SubtitleConfiguration>): MediaItem {
    val low = url.lowercase()
    val b = MediaItem.Builder()
        .setUri(url)
        .setSubtitleConfigurations(cfgs)
    when {
        hls || ".m3u8" in low -> b.setMimeType(MimeTypes.APPLICATION_M3U8)
        else -> Unit
    }
    return b.build()
}

private const val TORRENTS_ENABLED = false

@Composable
private fun TorrentsScreen(title: Title, season: String, episode: String, autoplay: Boolean, onFetch: (TEntry) -> Unit, onSeedReady: (Stream, TEntry?) -> Unit, onBack: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var entries by remember { mutableStateOf<List<TEntry>?>(null) }
    var formats by remember { mutableStateOf<List<VFormat>>(emptyList()) }
    var formatSubs by remember { mutableStateOf<List<Sub>>(emptyList()) }
    var err by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var imdbState by remember { mutableStateOf<String?>(null) }
    var gone by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    LaunchedEffect(title.id, season, episode) {
        try {
            status = "Trying direct streams…"
            val directJob = async(Dispatchers.IO) {
                VidStream.directAll(title.id, title.kind, season, episode, title.title)
            }
            val imdbJob = async(Dispatchers.IO) {
                VidLove.imdbOf(title.id, title.kind, BuildConfig.TMDB_API_KEY)?.removePrefix("tt")
            }
            val all = directJob.await()
            val imdbDigits = imdbJob.await()
            imdbState = imdbDigits
            AppLog.d("imdb ${imdbDigits ?: "none"}")
            val live = all.first.filter { it.url !in Torrents.DeadReleases.urls }
            AppLog.d("direct ${live.size} formats subs=${all.second.size}")
            if (live.isNotEmpty()) {
                formats = live
                formatSubs = all.second
                if (autoplay) {
                    val best = live.first()
                    AppLog.d("direct play ${best.label} hls=${best.isHls}")
                    gone = true
                    onSeedReady(
                        Stream(
                            url = best.url, isHls = best.isHls,
                            subs = all.second,
                            referer = best.referer, sampleMp4 = null,
                            imdb = imdbDigits,
                        ),
                        null,
                    )
                    return@LaunchedEffect
                }
            } else if (!TORRENTS_ENABLED) {
                err = "No direct streams found"
                return@LaunchedEffect
            }
            if (!TORRENTS_ENABLED) return@LaunchedEffect
            val key = BuildConfig.TMDB_API_KEY
            val imdb = withContext(Dispatchers.IO) { VidLove.imdbOf(title.id, title.kind, key) }
            if (imdb == null) {
                err = "no imdb id"
                return@LaunchedEffect
            }
            val found = withContext(Dispatchers.IO) {
                Torrents.query(imdb, title.kind, season, episode)
            }
            if (found.isEmpty()) {
                err = "no torrents"
            } else {
                entries = found
                val pick = Torrents.best(found)
                val seed = Seedbox.cfg()
                if (autoplay && seed != null && pick != null) {
                    status = "Sending to seedbox…"
                    AppLog.d("seed pick ${pick.name.take(60)} ${pick.size} seeds=${pick.seeds}")
                    val sentAt = System.currentTimeMillis()
                    val sent = withContext(Dispatchers.IO) {
                        Seedbox.add(seed, Torrents.magnet(pick), pick.infoHash)
                    }
                    if (!sent) {
                        status = null
                        return@LaunchedEffect
                    }
                    AppLog.d("seed sent +${System.currentTimeMillis() - sentAt}ms")
                    var metaLogged = false
                    var lastProg = 0.0
                    var lastChange = System.currentTimeMillis()
                    val t0 = System.currentTimeMillis()
                    while (System.currentTimeMillis() - t0 < 25 * 60 * 1000 &&
                        System.currentTimeMillis() - lastChange < 3 * 60 * 1000
                    ) {
                        val fs = withContext(Dispatchers.IO) { Seedbox.files(seed, pick.infoHash) }
                        if (fs.isNotEmpty() && !metaLogged) {
                            metaLogged = true
                            AppLog.d("seed meta +${System.currentTimeMillis() - sentAt}ms files=${fs.size}")
                        }
                        val st = withContext(Dispatchers.IO) { Seedbox.stat(seed, pick.infoHash) }
                        val vid = fs.filter {
                            it.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTS
                        }.maxByOrNull { it.size }
                        if (vid != null && vid.size > 0) {
                            if (vid.progress > lastProg) {
                                lastProg = vid.progress
                                lastChange = System.currentTimeMillis()
                            }
                            status = buildString {
                                append("Cloud fetching ${(100 * vid.progress).toInt()}%")
                                val spd = (st?.speed ?: 0L) / 1048576.0
                                if (spd > 0.05) append(" · ${"%.1f".format(spd)}MB/s")
                                val eta = st?.eta ?: -1L
                                if (eta > 0) append(if (eta >= 3600) " · eta ${eta / 3600}h${(eta % 3600) / 60}m" else " · eta ${eta / 60}m${eta % 60}s")
                                append("…")
                            }
                            if (vid.progress >= 0.999) {
                                AppLog.d("seed play ${vid.path.take(60)} ${(vid.size / 1048576)}MB at ${(100 * vid.progress).toInt()}%")
                                onSeedReady(
                                    Stream(
                                        url = Seedbox.fileUrl(seed, pick.infoHash, vid.path),
                                        isHls = false, subs = emptyList(), referer = seed.base,
                                        sampleMp4 = null, auth = Seedbox.authHeader(seed),
                                    ),
                                    pick,
                                )
                                return@LaunchedEffect
                            }
                        } else {
                            status = "Waiting for metadata…"
                        }
                        delay(2000)
                    }
                    status = null
                } else if (autoplay) {
                    pick?.let {
                        AppLog.d("autoplay ${it.name.take(60)} seeds=${it.seeds}")
                        onFetch(it)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            err = e.message
        } finally {
            loading = false
        }
    }
    if ((autoplay && loading || gone) && err == null) {
        val cfg = LocalConfiguration.current
        val stageH = (minOf(cfg.screenWidthDp, cfg.screenHeightDp).toFloat() * 9 / 16).dp
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Box(Modifier.fillMaxWidth().statusBarsPadding().height(stageH)) {
                PlayerStage(
                    title = title.title, fullscreen = false, isPlaying = false, stalled = true,
                    positionMs = 0, durationMs = 0, bufferedMs = 0, controlsVisible = true,
                    flash = null, seekMark = null, seekInset = 0.dp, locked = false,
                    subsOn = false, canSubs = true,
                    onBack = onBack, onFullscreen = {}, onTogglePlay = {}, onToggleLock = {},
                    onSeek = {}, onInteract = {}, onZoneTap = {}, onToggleSubs = {},
                    onSwipe = { _, _ -> }, onSwipeEnd = {},
                    video = { mod -> Box(mod.fillMaxSize().background(Color.Black)) },
                )
            }
            PlayerPanel(title = title.title, year = title.year, rating = title.rating)
        }
        return
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text(title.title, Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.titleMedium)
        }
        when {
            loading -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(status ?: "Finding best release…", style = MaterialTheme.typography.bodySmall)
            }
            err != null && entries == null && formats.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(err ?: "No streams found")
            }
            !TORRENTS_ENABLED -> LazyColumn(Modifier.fillMaxSize()) {
                items(formats, key = { it.url }) { f ->
                    Column(
                        Modifier.fillMaxWidth().clickable {
                            onSeedReady(
                                Stream(
                                    url = f.url, isHls = f.isHls, subs = formatSubs,
                                    referer = f.referer, sampleMp4 = null,
                                    imdb = imdbState,
                                ),
                                null,
                            )
                        }.padding(12.dp),
                    ) {
                        Text(f.label, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            (if (f.height > 0) "${f.height}p · " else "") + if (f.isHls) "HLS" else "MP4",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(entries ?: emptyList(), key = { it.infoHash }) { e ->
                    Column(
                        Modifier.fillMaxWidth().clickable { onFetch(e) }.padding(12.dp),
                    ) {
                        Text(e.name, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${e.size} · 👤 ${e.seeds}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FetchScreen(
    title: Title,
    season: String,
    episode: String,
    entry: TEntry,
    dir: java.io.File,
    onPlay: (Stream, DlCtl) -> Unit,
    onBack: () -> Unit,
) {
    var prog by remember { mutableStateOf(0f) }
    var stat by remember { mutableStateOf("starting…") }
    var err by remember { mutableStateOf<String?>(null) }
    var ctl by remember { mutableStateOf<DlCtl?>(null) }
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    var wifiOk by remember { mutableStateOf(isWifi(context)) }
    if (!wifiOk) {
        AlertDialog(
            onDismissRequest = { onBack() },
            title = { Text("Not on WiFi") },
            text = { Text("Torrents will eat mobile data. Continue anyway?") },
            confirmButton = { TextButton(onClick = { wifiOk = true }) { Text("Download") } },
            dismissButton = { TextButton(onClick = { onBack() }) { Text("Back") } },
        )
        return
    }
    LaunchedEffect(entry.infoHash) {
        try {
            stat = "contacting peers…"
            var h = withContext(Dispatchers.IO) { TorrentEngine.download(entry, dir) }
            ctl = DlCtl(h)
            var vlSubs: List<Sub> = emptyList()
            val subsJob = launch {
                val bundled = withContext(Dispatchers.IO) { Torrents.bundledSubs(dir) }
                val live = mutableListOf<Sub>()
                for (s in bundled) {
                    if (live.size >= 8) break
                    if (withContext(Dispatchers.IO) { Torrents.subAlive(s.url) }) live.add(s)
                    else AppLog.d("subs drop ${s.label}")
                }
                vlSubs = live
                AppLog.d("subs live ${live.map { it.label }}")
            }
            var lastDone = 0L
            var lastChange = System.currentTimeMillis()
            var lastLog = 0L
            var lastArm = 0L
            val metaSince = System.currentTimeMillis()
            var pick: Torrents.VidPick? = null
            while (true) {
                ensureActive()
                if (pick == null) {
                    pick = withContext(Dispatchers.IO) { Torrents.pickIndexed(h, dir) }
                    if (pick == null) {
                        if (System.currentTimeMillis() - metaSince > 60000) {
                            ctl?.stop()
                            err = "no peers sharing this yet, pick another"
                            return@LaunchedEffect
                        }
                        stat = "waiting for metadata…"
                        delay(1000)
                        continue
                    }
                    val p0 = pick
                    if (!p0.file.exists() || p0.file.length() == 0L) {
                        AppLog.d("fetch stale disk, re-adding")
                        withContext(Dispatchers.IO) { TorrentEngine.reset() }
                        h = withContext(Dispatchers.IO) { TorrentEngine.download(entry, dir) }
                        ctl?.stop()
                        ctl = DlCtl(h)
                        pick = null
                        continue
                    }
                    AppLog.d("torrent video ${p0.file.name} ${p0.size / 1048576}MB")
                    withContext(Dispatchers.IO) { TorrentEngine.armStream(h, p0.index) }
                }
                val p = pick
                val st = h.status()
                val fp = try {
                    h.fileProgress()[p.index]
                } catch (_: Exception) {
                    -1L
                }
                val done = if (fp >= 0) fp else st.totalDone()
                prog = if (p.size > 0) (done.toFloat() / p.size).coerceIn(0f, 1f) else 0f
                stat = "${done / 1048576}MB / ${p.size / 1048576}MB · ${st.downloadRate() / 1024}KB/s · ${st.numPeers()} peers"
                if (done > lastDone) {
                    lastDone = done
                    lastChange = System.currentTimeMillis()
                }
                if (System.currentTimeMillis() - lastLog > 10000) {
                    lastLog = System.currentTimeMillis()
                    AppLog.d("fetch ${done / 1048576}MB/${p.size / 1048576}MB ${st.downloadRate() / 1024}KB/s ${st.numPeers()}p")
                }
                // start only once the head is dense, else Exo reads ahead into sparse ranges
                val need = minOf(60000000L, maxOf(25000000L, p.size * 8 / 100))
                val headNeed = minOf(need, 30000000L)
                if (System.currentTimeMillis() - lastArm > 15000) {
                    lastArm = System.currentTimeMillis()
                    withContext(Dispatchers.IO) { TorrentEngine.armStream(h, p.index) }
                }
                val head = withContext(Dispatchers.IO) { TorrentEngine.densePrefix(h, p.index, p.size) }
                if ((done >= need && head >= headNeed) || (done >= p.size && p.size > 0)) {
                    subsJob.join()
                    val subs = vlSubs
                    val trusted = subs.any { it.url.startsWith("file") && !it.url.contains("/subs/") && it.label.contains("english", true) }
                    val full = done >= p.size && p.size > 0
                    AppLog.d("fetch play ${p.file.name} ${done / 1048576}MB head=${head / 1048576}MB subs=${subs.size} ${subs.take(4).joinToString { it.label }} trusted=$trusted full=$full")
                    ctl!!.fileIndex = p.index
                    ctl!!.fileSize = p.size
                    onPlay(
                        Stream(
                            android.net.Uri.fromFile(p.file).toString(),
                            false,
                            subs,
                            "",
                            p.file.absolutePath,
                            trusted,
                            full,
                            if (title.kind == "movie") "movie/${title.id}"
                            else "tv/${title.id}/$season/$episode",
                        ),
                        ctl!!,
                    )
                    return@LaunchedEffect
                }
                if (System.currentTimeMillis() - lastChange > 180000 && done > 0) {
                    err = "stalled, try another"
                    return@LaunchedEffect
                }
                delay(800)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            err = "${e.javaClass.simpleName}: ${e.message}"
            AppLog.d("fetch fail ${e.message}")
        }
    }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(entry.name, maxLines = 2, style = MaterialTheme.typography.titleMedium)
        androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
        androidx.compose.material3.LinearProgressIndicator(progress = { prog }, modifier = Modifier.fillMaxWidth())
        androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
        Text(stat, style = MaterialTheme.typography.bodySmall)
        if (err != null) Text(err!!, style = MaterialTheme.typography.bodySmall)
        androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                ctl?.stop()
                onBack()
            }) { Text(if (err != null) "Back" else "Cancel") }
        }
        Text("stays in cache — keep app open while downloading", style = MaterialTheme.typography.bodySmall)
    }
}

internal fun rankEmbed(names: List<String>): Int {
    var best = -1
    var bestScore = Int.MIN_VALUE
    names.forEachIndexed { i, n ->
        val l = n.lowercase()
        if (!l.substringBefore(":").startsWith("en") && "english" !in l) return@forEachIndexed
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
private fun buildPlayer(context: android.content.Context, stream: Stream, ctl: DlCtl?): ExoPlayer {
    val origin = "https://${Uri.parse(stream.referer).host}"
    val headers = mapOf(
        "Referer" to stream.referer,
        "Origin" to origin,
        "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Safari/537.36",
    ) + (stream.auth?.let { mapOf("Authorization" to it) } ?: emptyMap())
    val selector = DefaultTrackSelector(context).apply {
        parameters = buildUponParameters()
            .setPreferredAudioLanguage("en")
            .setPreferredTextLanguage("en")
            .build()
    }
    val mediaFactory = buildMediaFactory(context, stream, headers, ctl)
    // torrent files: tiny readahead so Exo can't sprint past downloaded pieces
    val loadControl = if (stream.url.startsWith("file")) {
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(5000, 15000, 1000, 2000)
            .build()
    } else {
        DefaultLoadControl()
    }
    return ExoPlayer.Builder(context)
        .setTrackSelector(selector)
        .setMediaSourceFactory(mediaFactory)
        .setLoadControl(loadControl)
        .build()
        .apply {
            setMediaItem(itemFor(stream.url, stream.isHls, configsFor(stream.subs)))
            prepare()
            play()
        }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerScreen(title: String, media: Title, initial: Stream?, ctl: DlCtl?, season: String, episode: String, onBack: () -> Unit, onGiveUp: (String?) -> Unit, onPickList: () -> Unit) {
    val context = LocalContext.current
    val resumeScope = rememberCoroutineScope()
    var resolved by remember { mutableStateOf(initial) }
    var resolveErr by remember { mutableStateOf<String?>(null) }
    var retryN by remember { mutableStateOf(0) }
    LaunchedEffect(media.id, media.kind, season, episode, retryN) {
        if (resolved != null) return@LaunchedEffect
        try {
            val all = withContext(Dispatchers.IO) {
                VidStream.directAll(media.id, media.kind, season, episode, media.title)
            }
            val imdb = withContext(Dispatchers.IO) {
                VidLove.imdbOf(media.id, media.kind, BuildConfig.TMDB_API_KEY)?.removePrefix("tt")
            }
            val live = all.first.filter { it.url !in Torrents.DeadReleases.urls }
            AppLog.d("direct ${live.size} formats subs=${all.second.size}")
            if (live.isEmpty()) {
                resolveErr = "No direct streams found"
                return@LaunchedEffect
            }
            val best = live.first()
            AppLog.d("direct play ${best.label} hls=${best.isHls}")
            resolved = Stream(best.url, best.isHls, all.second, best.referer, null, imdb = imdb)
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
    var aiSubUrl by remember { mutableStateOf<String?>(null) }
    var embedGroup by remember { mutableStateOf<androidx.media3.common.TrackGroup?>(null) }
    var frontierMs by remember { mutableStateOf(0L) }
    var fillNote by remember { mutableStateOf<String?>(null) }
    var lastSubSel by remember { mutableStateOf("") }
    LaunchedEffect(title) { AppLog.d("screen $title") }
    val player: ExoPlayer? = remember(resolved) {
        val r = resolved ?: return@remember null
        try {
            buildPlayer(context, r, ctl).also {
                try {
                    it.setVideoEffects(listOf(Contrast(0.1f)))
                } catch (e: Exception) {
                    AppLog.d("fx fail ${e.message}")
                }
                AppLog.d("built")
            }
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
        val act = context as? MainActivity
        act?.armPip(true, exo.isPlaying)
        onDispose {
            act?.armPip(false, false)
            PipCtl.active = false
            if (PipCtl.player === exo) PipCtl.player = null
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
                if (sel != null) {
                    embedIdx = idx
                    sel.parameters = sel.buildUponParameters()
                        .setOverrideForType(
                            androidx.media3.common.TrackSelectionOverride(g, listOf(idx)),
                        )
                        .build()
                    AppLog.d("embed subs forced $idx")
                }
            }
        }
    }
    fun netResume(src: String) {
        try {
            if (exo.playbackState != Player.STATE_IDLE) return
            netErr = false
            val pos = exo.currentPosition
            val subs = stream.subs + aiSubUrl?.let { listOf(Sub("AI English", it)) }.orEmpty()
            exo.setMediaItem(itemFor(stream.url, stream.isHls, configsFor(subs)), pos)
            exo.prepare()
            exo.play()
            AppLog.d("net resume $src at ${pos / 1000}s")
        } catch (e: Exception) {
            AppLog.d("net resume fail ${e.message}")
        }
    }
    DisposableEffect(exo) {        val listener = object : Player.Listener {
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
                        val f = g.getTrackFormat(0)
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
                            exo.setMediaItem(itemFor(stream.url, stream.isHls, configsFor(subs)), pos)
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
                    AppLog.d("player direct err $directErrs")
                    if (directErrs >= 3 && !gaveUpSent && exo.currentPosition < 60_000) {
                        gaveUpSent = true
                        AppLog.d("player direct dead, next source")
                        onGiveUp(stream.url)
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
            exo.release()
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
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Safari/537.36")
                .apply {
                    if (!url.startsWith("file")) {
                        header("Referer", stream.referer)
                        header("Origin", "https://${android.net.Uri.parse(stream.referer).host}")
                    }
                    stream.auth?.let { header("Authorization", it) }
                }
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
            exo.setMediaItem(
                itemFor(stream.url, stream.isHls, configsFor(stream.subs, Uri.fromFile(f))),
                pos,
            )
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
        exo.setMediaItem(itemFor(stream.url, stream.isHls, configsFor(merged)), pos)
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
        val pos = exo.currentPosition
        val wasPlaying = exo.isPlaying
        exo.setMediaItem(itemFor(stream.url, stream.isHls, configsFor(merged)), pos)
        exo.prepare()
        if (wasPlaying) exo.play()
        osStatus = "OS ✓"
        AppLog.d("os applied $hash")
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
                    AppLog.d("os skipped, have subs")
                    return@launch
                }
                osStatus = "OS…"
                val headers = buildMap {
                    put("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Safari/537.36")
                    if (!stream.url.startsWith("file")) {
                        put("Referer", stream.referer)
                        put("Origin", "https://${Uri.parse(stream.referer).host}")
                    }
                    stream.auth?.let { put("Authorization", it) }
                }
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
                OsSubs.cached(context.cacheDir, "imdb-$im")?.let { f ->
                    showOsSub(f, "imdb-$im")
                    return@launch
                }
                val f = withContext(Dispatchers.IO) {
                    OsSubs.searchImdb(key, im)?.let { id ->
                        OsSubs.downloadText(key, id)?.let { OsSubs.toFile(it, context.cacheDir, "imdb-$im") }
                    }
                }
                if (f == null) {
                    AppLog.d("os imdb none")
                    osStatus = null
                    return@launch
                }
                showOsSub(f, "imdb-$im")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                osStatus = null
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
    LaunchedEffect(playing) { (context as? MainActivity)?.armPip(true, playing) }
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
            delay(5000)
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
        poke()
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
    fun toggleFullscreen() {
        fullscreen = !fullscreen
        (context as? android.app.Activity)?.requestedOrientation =
            if (fullscreen) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        fullscreenBars(fullscreen)
        poke()
    }
    var autoFs by remember { mutableStateOf(false) }
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
                lastZone = zone
                orientJob?.cancel()
                orientJob = resumeScope.launch {
                    delay(800)
                    if (zone == 1 && !fsNow.value) {
                        autoFs = true
                        toggleFullscreen()
                    } else if (zone == 0 && fsNow.value && autoFs) {
                        autoFs = false
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
        if (fullscreen) {
            autoFs = false
            toggleFullscreen()
        } else {
            onBack()
        }
    }
    DisposableEffect(stream.url) {
        onDispose {
            (context as? android.app.Activity)?.requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
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
            stalled = exo.playbackState == Player.STATE_BUFFERING
            delay(250)
        }
    }
    LaunchedEffect(playing) {
        if (playing) poke()
    }
    LaunchedEffect(fullscreen) {
        if (!fullscreen) locked = false
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
    @Composable
    fun stage(seekInset: androidx.compose.ui.unit.Dp, matchH: Boolean) {
        PlayerStage(
            title = title,
        fullscreen = fullscreen,
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
        onBack = onBack,
        onFullscreen = {
            autoFs = false
            toggleFullscreen()
        },
        onTogglePlay = ::togglePlay,
        onToggleLock = {
            locked = !locked
            if (locked) controls = false else poke()
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
                            if (fullscreen && !PipCtl.inPip) androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
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
    val cfg = LocalConfiguration.current
    if (PipCtl.inPip) {
        stage(0.dp, videoAspect < 16f / 9f)
    } else if (fullscreen) {
        val fsW = maxOf(cfg.screenWidthDp, cfg.screenHeightDp).toFloat()
        val fsH = minOf(cfg.screenWidthDp, cfg.screenHeightDp).toFloat()
        val fsVideoH = if (videoAspect >= fsW / fsH) fsW / videoAspect else fsH
        AnimatedVisibility(visible = true, enter = fadeIn()) {
            stage((((fsH - fsVideoH) / 2).coerceAtLeast(0f)).dp, videoAspect < fsW / fsH)
        }
    } else {
        val portraitW = minOf(cfg.screenWidthDp, cfg.screenHeightDp).toFloat()
        val stageH = (portraitW * 9 / 16).dp
        AnimatedVisibility(visible = true, enter = fadeIn()) {
            Column(Modifier.fillMaxSize().background(Color.Black)) {
                Box(Modifier.fillMaxWidth().statusBarsPadding().height(stageH)) {
                    stage(0.dp, videoAspect < 16f / 9f)
                }
                PlayerPanel(title = media.title, year = media.year, rating = media.rating)
            }
        }
    }
}
