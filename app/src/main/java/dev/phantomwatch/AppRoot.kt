package dev.phantomwatch

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.phantomwatch.core.AppLog
import dev.phantomwatch.core.CrashReport
import dev.phantomwatch.core.RemoteConfig
import dev.phantomwatch.core.Telemetry
import dev.phantomwatch.data.Aliases
import dev.phantomwatch.data.Settings
import dev.phantomwatch.data.Resume
import dev.phantomwatch.player.Pip
import dev.phantomwatch.player.PlayerUi
import dev.phantomwatch.player.Playback
import dev.phantomwatch.player.PlayerScreen
import dev.phantomwatch.player.Screen
import dev.phantomwatch.player.SourcesScreen
import dev.phantomwatch.player.TrailerBridge
import dev.phantomwatch.player.navLabel
import dev.phantomwatch.subs.SubsApi
import dev.phantomwatch.provider.DeadHosts
import dev.phantomwatch.provider.DeadReleases
import dev.phantomwatch.ui.BrowseState
import dev.phantomwatch.ui.DetailPane
import dev.phantomwatch.ui.HomeLiquidTabs
import dev.phantomwatch.ui.settings.SettingsAction
import dev.phantomwatch.ui.settings.SettingsData
import dev.phantomwatch.ui.settings.SettingsScreen
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
    var youRev by remember { mutableStateOf(0) }
    var crash by remember { mutableStateOf(CrashReport.pending(context)) }
    LaunchedEffect(screen, Pip.playing) {
        (context as? MainActivity)?.armPip(screen is Screen.Player, Pip.playing)
    }
    LaunchedEffect(Unit) {
        Telemetry.appContext = context.applicationContext
        SubsApi.init(context)
        DeadHosts.init(context)
        TrailerBridge.warm()
    }
    Box(Modifier.fillMaxSize()) {
        val tabBackdrop = rememberLayerBackdrop()
        LaunchedEffect(screen) {
            instant = false
            AppLog.d("nav -> ${screen.navLabel()}")
        }
        AnimatedContent(
            targetState = screen,
            modifier = Modifier.layerBackdrop(tabBackdrop),
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
                        screen = Screen.Player(m.title, null, m, seas, ep, Screen.Home)
                    },
                    loadRecent = {
                        if (Settings.on(context, Settings.CONTINUE_WATCHING, true)) Resume.recent(context) else emptyList()
                    },
                    onRemoveRecent = { Resume.remove(context, it.kind, it.id, it.season, it.episode) },
                    onRestoreRecent = { Resume.restore(context, it) },
                    onExitApp = { (context as? android.app.Activity)?.finish() },
                    searchGhost = { ghost, run ->
                        val comp by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.lottie_search))
                        key(run) {
                            LottieAnimation(
                                composition = comp,
                                iterations = 1,
                                isPlaying = true,
                                modifier = Modifier.size(ghost),
                            )
                        }
                    },
                    detailPane = { item, onClose, onPlay, startEpisodes ->
                        DetailPane(item, onClose, onPlay, startEpisodes, onClose)
                    },
                    youScreen = {
                        val settings = remember(youRev) { liveSettings(context) }
                        SettingsScreen(
                            SettingsData(
                                version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                providerCount = providerCount(),
                                settings = settings,
                                onValue = { key, value -> applySetting(context, key, value); youRev++ },
                                onAction = { action ->
                                    when (action) {
                                        SettingsAction.ClearHistory -> {
                                            Resume.clear(context)
                                            browse.recent = emptyList()
                                            toast(context, R.string.you_history_cleared)
                                        }
                                        SettingsAction.Updates -> openUrl(context, RELEASE_URL + "/releases")
                                        SettingsAction.Licences -> openUrl(context, RELEASE_URL + "#licence")
                                        SettingsAction.Privacy -> openUrl(context, RELEASE_URL + "#privacy")
                                        SettingsAction.Feedback -> openUrl(context, RELEASE_URL + "/issues")
                                    }
                                },
                            ),
                        )
                    },
                )
                is Screen.Player -> {
                    Playback.lastScreen = s
                    PlayerScreen(
                        title = s.title,
                        media = s.media,
                        initial = s.stream,
                        season = s.season,
                        episode = s.episode,
                        startFullscreen = s.fullscreen,
                        onBack = {
                            forward = false
                            screen = s.back
                        },
                        onGiveUp = { deadUrl ->
                            deadUrl?.let { DeadReleases.urls.add(it) }
                            forward = false
                            autoRelease = false
                            screen = Screen.Sources(s.media, s.season, s.episode)
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
                            PlayerUi.token++
                            screen = Screen.Player(s.media.title, null, s.media, seas, ep, s.back, fullscreen = PlayerUi.fullscreen)
                        },
                        onOpenTitle = { t ->
                            forward = false
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
                    onStreamReady = { stream ->
                        forward = true
                        instant = true
                        screen = Screen.Player(
                            s.title.title, stream, s.title, s.season, s.episode,
                            Screen.Sources(s.title, s.season, s.episode),
                        )
                    },
                    onBack = {
                        forward = false
                        screen = Screen.Home
                    },
                )
            }
        }
        AnimatedVisibility(
            visible = screen is Screen.Home && browse.openTitle == null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(150)),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp)
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.displayCutout))
                .padding(bottom = 8.dp)
                .widthIn(max = 480.dp),
        ) {
            HomeLiquidTabs(
                backdrop = tabBackdrop,
                selectedTabIndex = { browse.tab },
                onSelect = browse::selectTab,
            )
        }
        if ((BuildConfig.DEBUG || BuildConfig.LOGUI) && !PlayerUi.hidesChrome) LogFab()
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

private const val RELEASE_URL = "https://github.com/ejjays/phantom-watch"

private fun providerCount(): Int = RemoteConfig.providerNames().size

private fun liveSettings(ctx: android.content.Context): Map<String, Int> = mapOf(
    Settings.SUBS_ON to if (Settings.on(ctx, Settings.SUBS_ON, true)) 1 else 0,
    Settings.SUBS_SIZE to Settings.choice(ctx, Settings.SUBS_SIZE, Settings.SIZE_MEDIUM),
    Settings.SUBS_BG to Settings.choice(ctx, Settings.SUBS_BG, Settings.BG_DIM),
    Settings.SAVE_HISTORY to if (Settings.on(ctx, Settings.SAVE_HISTORY, true)) 1 else 0,
    Settings.CONTINUE_WATCHING to if (Settings.on(ctx, Settings.CONTINUE_WATCHING, true)) 1 else 0,
    Settings.AUTOPLAY to if (Settings.on(ctx, Settings.AUTOPLAY, true)) 1 else 0,
    Settings.SKIP_INTRO to if (Settings.on(ctx, Settings.SKIP_INTRO, true)) 1 else 0,
    Settings.AUTO_REFRESH to if (Settings.on(ctx, Settings.AUTO_REFRESH, true)) 1 else 0,
    Settings.SHARE_USAGE to if (Telemetry.enabled(ctx)) 1 else 0,
)

private fun applySetting(ctx: android.content.Context, key: String, value: Int) {
    when (key) {
        Settings.SUBS_SIZE, Settings.SUBS_BG -> Settings.setChoice(ctx, key, value)
        Settings.SHARE_USAGE -> Telemetry.setEnabled(ctx, value != 0)
        else -> Settings.setOn(ctx, key, value != 0)
    }
}

private fun toast(ctx: android.content.Context, res: Int) {
    android.widget.Toast.makeText(ctx, ctx.getString(res), android.widget.Toast.LENGTH_SHORT).show()
}

private fun openUrl(ctx: android.content.Context, url: String) {
    runCatching {
        ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
    }
}
