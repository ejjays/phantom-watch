package dev.phantomwatch

import android.os.Bundle
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.frostwire.jlibtorrent.TorrentHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import dev.phantomwatch.ui.BrowseState
import dev.phantomwatch.ui.MoviesHome
import androidx.compose.animation.AnimatedContent
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
        RemoteConfig.load(this)
        JsProviders.init(applicationContext)
        Social.init(applicationContext)
        Thread { RemoteConfig.refresh(applicationContext) }.start()
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
        val gone = isFinishing || !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        if (!isInPictureInPictureMode && gone) pauseForPipClose("mode")
    }

    override fun onStop() {
        super.onStop()
        if (PipCtl.inPip) pauseForPipClose("stop")
    }

    private fun pauseForPipClose(why: String) {
        AppLog.d("pip close pause $why")
        Playback.player?.pause()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val p = PipCtl.player
        AppLog.d("pip leave active=${PipCtl.active} player=${p != null}")
        if (android.os.Build.VERSION.SDK_INT >= 31) return
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
            pipParams(playing, auto = on)?.let {
                setPictureInPictureParams(it)
                AppLog.d("pip arm ok on=$on playing=$playing")
            }
        } catch (e: Exception) {
            AppLog.d("pip arm fail on=$on ${e.message}")
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
        if (android.os.Build.VERSION.SDK_INT >= 31) b.setAutoEnterEnabled(auto)
        b.build()
    } catch (e: Exception) {
        AppLog.d("pip params fail ${e.message}")
        null
    }
}

internal sealed interface Screen {
    data object Home : Screen
    data class Player(val title: String, val stream: Stream?, val ctl: DlCtl?, val entry: TEntry?, val media: Title, val season: String, val episode: String, val back: Screen) : Screen
    data class Torrents(val title: Title, val season: String, val episode: String) : Screen
    data class Fetch(val title: Title, val season: String, val episode: String, val entry: TEntry) : Screen
}

private const val ACT_PIP_TOGGLE = "dev.phantomwatch.PIP_TOGGLE"
private const val ACT_PIP_CLOSE = "dev.phantomwatch.PIP_CLOSE"

private fun Screen.navLabel(): String = when (this) {
    is Screen.Player -> "Player(back=${back.navLabel()})"
    else -> this::class.simpleName ?: "?"
}

object PipCtl {
    var active = false
    var inPip by mutableStateOf(false)
    var playing by mutableStateOf(false)
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
    var screen by remember { mutableStateOf<Screen>(Playback.lastScreen ?: Screen.Home) }
    val browse = remember { BrowseState() } // survives the trip to the player
    var forward by remember { mutableStateOf(true) }
    var instant by remember { mutableStateOf(false) }
    var autoRelease by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var crash by remember { mutableStateOf(CrashReport.pending(context)) }
    LaunchedEffect(screen, PipCtl.playing) {
        (context as? MainActivity)?.armPip(screen is Screen.Player, PipCtl.playing)
    }
    LaunchedEffect(Unit) {
        SubsApi.init(context)
        Torrents.DeadHosts.init(context)
        withContext(Dispatchers.IO) {
            try {
                TorrentEngine.ensure()
            } catch (_: Exception) {
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        LaunchedEffect(screen) {
            instant = false // snap applies to one hop only
            AppLog.d("nav -> ${screen.navLabel()}")
        }
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
                    browse = browse,
                    onWatch = { t, seas, ep ->
                        forward = true
                        instant = true
                        autoRelease = true
                        // back: tv lands on the episode list, movies skip the detail sheet
                        browse.backToEpisodes = t.kind == "tv"
                        if (t.kind != "tv") browse.openTitle = null
                        screen = Screen.Player(t.title, null, null, null, t, seas, ep, Screen.Home)
                    },
                )
                is Screen.Player -> {
                    Playback.lastScreen = s
                    PlayerScreen(
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
                            screen = s.back
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
                                // show the source list instead of auto-restarting another dead pick
                                autoRelease = false
                                screen = Screen.Torrents(s.media, s.season, s.episode)
                            }
                        },
                        onPickList = {
                            forward = false
                            autoRelease = false
                            screen = Screen.Torrents(s.media, s.season, s.episode)
                        },
                        onEpisode = { seas, ep ->
                            forward = true
                            instant = true
                            autoRelease = true
                            browse.backToEpisodes = s.media.kind == "tv"
                            screen = Screen.Player(s.media.title, null, null, null, s.media, seas, ep, s.back)
                        },
                        onOpenTitle = { t ->
                            forward = false
                            s.ctl?.stop()
                            browse.openTitle = t
                            browse.backToEpisodes = false
                            screen = Screen.Home
                        },
                    )
                }
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
                        screen = Screen.Player(s.title.title, stream, null, e, s.title, s.season, s.episode, Screen.Torrents(s.title, s.season, s.episode))
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
                            screen = Screen.Player(s.title.title, stream, ctl, s.entry, s.title, s.season, s.episode, Screen.Fetch(s.title, s.season, s.episode, s.entry))
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
        if ((BuildConfig.DEBUG || BuildConfig.LOGUI) && !PipCtl.inPip) LogFab()
    }
    crash?.let { text ->
        AlertDialog(
            onDismissRequest = { crash = null; CrashReport.clear(context) },
            title = { Text("Last crash") },
            text = {
                Text(
                    text.take(700),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, text)
                    }
                    val sent = runCatching {
                        context.startActivity(
                            android.content.Intent.createChooser(send, "Send crash report"),
                        )
                    }.isSuccess
                    if (sent) {
                        crash = null
                        CrashReport.clear(context)
                    }
                }) { Text("Send") }
            },
            dismissButton = {
                TextButton(onClick = { crash = null; CrashReport.clear(context) }) { Text("Dismiss") }
            },
        )
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
                    TextButton(onClick = { AppLog.clear() }) { Text("Clear") }
                    TextButton(onClick = {
                        clip.setText(AnnotatedString(AppLog.lines.joinToString("\n")))
                    }) { Text("Copy all") }
                    TextButton(onClick = { open = false }) { Text("Close") }
                }
            },
        )
    }
}

