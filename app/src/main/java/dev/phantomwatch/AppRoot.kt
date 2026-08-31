package dev.phantomwatch

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.phantomwatch.core.AppLog
import dev.phantomwatch.core.CrashReport
import dev.phantomwatch.data.Aliases
import dev.phantomwatch.player.Pip
import dev.phantomwatch.player.Playback
import dev.phantomwatch.player.PlayerScreen
import dev.phantomwatch.player.Screen
import dev.phantomwatch.player.SourcesScreen
import dev.phantomwatch.player.TorrentFetchScreen
import dev.phantomwatch.player.TrailerBridge
import dev.phantomwatch.player.navLabel
import dev.phantomwatch.subs.SubsApi
import dev.phantomwatch.torrent.Seedbox
import dev.phantomwatch.torrent.TORRENTS_ENABLED
import dev.phantomwatch.torrent.TorrentEngine
import dev.phantomwatch.torrent.Torrents
import dev.phantomwatch.ui.BrowseState
import dev.phantomwatch.ui.HomeScreen
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AppRoot() {
    var screen by remember { mutableStateOf<Screen>(Playback.lastScreen ?: Screen.Home) }
    val browse = remember { BrowseState() }
    var forward by remember { mutableStateOf(true) }
    var instant by remember { mutableStateOf(false) }
    var autoRelease by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var crash by remember { mutableStateOf(CrashReport.pending(context)) }
    LaunchedEffect(screen, Pip.playing) {
        (context as? MainActivity)?.armPip(screen is Screen.Player, Pip.playing)
    }
    LaunchedEffect(Unit) {
        SubsApi.init(context)
        Torrents.DeadHosts.init(context)
        TrailerBridge.warm()
        if (TORRENTS_ENABLED) {
            withContext(Dispatchers.IO) {
                try {
                    TorrentEngine.ensure()
                } catch (_: Exception) {
                }
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        LaunchedEffect(screen) {
            instant = false
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
                is Screen.Home -> HomeScreen(
                    browse = browse,
                    onWatch = { t, seas, ep ->
                        val m = Aliases.resolve(t)
                        forward = true
                        instant = true
                        autoRelease = true
                        browse.backToEpisodes = m.kind == "tv"
                        if (m.kind != "tv") browse.openTitle = null
                        screen = Screen.Player(m.title, null, null, null, m, seas, ep, Screen.Home)
                    },
                )
                is Screen.Player -> {
                    Playback.lastScreen = s
                    PlayerScreen(
                        title = s.title,
                        media = s.media,
                        initial = s.stream,
                        download = s.download,
                        season = s.season,
                        episode = s.episode,
                        onBack = {
                            s.download?.stop()
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
                                s.download?.stop()
                                forward = false
                                autoRelease = false
                                screen = Screen.Sources(s.media, s.season, s.episode)
                            } else {
                                deadUrl?.let { Torrents.DeadReleases.urls.add(it) }
                                s.download?.stop()
                                forward = false
                                autoRelease = false
                                screen = Screen.Sources(s.media, s.season, s.episode)
                            }
                        },
                        onPickList = {
                            forward = false
                            autoRelease = false
                            screen = Screen.Sources(s.media, s.season, s.episode)
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
                            s.download?.stop()
                            browse.openTitle = t
                            browse.backToEpisodes = false
                            screen = Screen.Home
                        },
                    )
                }
                is Screen.Sources -> SourcesScreen(
                    title = s.title,
                    season = s.season,
                    episode = s.episode,
                    autoplay = autoRelease,
                    onPickTorrent = { e ->
                        forward = true
                        screen = Screen.TorrentFetch(s.title, s.season, s.episode, e)
                    },
                    onStreamReady = { stream, e ->
                        forward = true
                        instant = true
                        screen = Screen.Player(
                            s.title.title, stream, null, e, s.title, s.season, s.episode,
                            Screen.Sources(s.title, s.season, s.episode),
                        )
                    },
                    onBack = {
                        forward = false
                        screen = Screen.Home
                    },
                )
                is Screen.TorrentFetch -> {
                    val dir = remember(s.entry.infoHash) {
                        java.io.File(context.cacheDir, "torrents/${s.entry.infoHash}")
                    }
                    TorrentFetchScreen(
                        title = s.title,
                        season = s.season,
                        episode = s.episode,
                        entry = s.entry,
                        dir = dir,
                        onPlay = { stream, download ->
                            forward = true
                            screen = Screen.Player(
                                s.title.title, stream, download, s.entry, s.title, s.season, s.episode,
                                Screen.TorrentFetch(s.title, s.season, s.episode, s.entry),
                            )
                        },
                        onBack = {
                            forward = false
                            autoRelease = false
                            screen = Screen.Sources(s.title, s.season, s.episode)
                        },
                    )
                }
            }
        }
        if ((BuildConfig.DEBUG || BuildConfig.LOGUI) && !Pip.inPip) LogFab()
    }
    crash?.let { text ->
        AlertDialog(
            onDismissRequest = { crash = null; CrashReport.clear(context) },
            title = { Text(stringResource(R.string.approot_crash_title)) },
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
                            android.content.Intent.createChooser(send, context.getString(R.string.approot_crash_chooser)),
                        )
                    }.isSuccess
                    if (sent) {
                        crash = null
                        CrashReport.clear(context)
                    }
                }) { Text(stringResource(R.string.approot_crash_send)) }
            },
            dismissButton = {
                TextButton(onClick = { crash = null; CrashReport.clear(context) }) { Text(stringResource(R.string.approot_crash_dismiss)) }
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
        ) { Text(stringResource(R.string.approot_log), style = MaterialTheme.typography.labelSmall) }
    }
    if (open) {
        val clip = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.approot_logs_title, AppLog.lines.size)) },
            text = {
                androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    items(AppLog.lines) { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { AppLog.clear() }) { Text(stringResource(R.string.approot_logs_clear)) }
                    TextButton(onClick = {
                        clip.setText(AnnotatedString(AppLog.lines.joinToString("\n")))
                    }) { Text(stringResource(R.string.approot_logs_copy)) }
                    TextButton(onClick = { open = false }) { Text(stringResource(R.string.common_close)) }
                }
            },
        )
    }
}
