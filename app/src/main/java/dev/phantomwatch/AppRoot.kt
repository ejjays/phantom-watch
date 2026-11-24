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
import androidx.compose.runtime.DisposableEffect
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
import dev.phantomwatch.ui.PhantomInstallDialog
import dev.phantomwatch.ui.WorkSans
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieAnimatable
import com.airbnb.lottie.compose.rememberLottieComposition
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.phantomwatch.agent.PlayRequest
import dev.phantomwatch.core.AppLog
import dev.phantomwatch.core.CrashReport
import dev.phantomwatch.core.PushOpen
import dev.phantomwatch.core.RemoteConfig
import dev.phantomwatch.core.Telemetry
import dev.phantomwatch.core.UpdateChecker
import dev.phantomwatch.data.Aliases
import dev.phantomwatch.data.Settings
import dev.phantomwatch.data.Title
import dev.phantomwatch.data.Resume
import dev.phantomwatch.data.Downloads
import dev.phantomwatch.data.Stream
import dev.phantomwatch.data.WhatsNewEntry
import dev.phantomwatch.data.WhatsNews
import dev.phantomwatch.player.PhantomDownload
import dev.phantomwatch.player.Pip
import dev.phantomwatch.player.PlayerUi
import dev.phantomwatch.player.Playback
import dev.phantomwatch.player.PlayerScreen
import dev.phantomwatch.ui.Route
import dev.phantomwatch.player.SourcesScreen
import dev.phantomwatch.ui.navLabel
import dev.phantomwatch.social.GoogleAuth
import dev.phantomwatch.social.Social
import dev.phantomwatch.provider.DeadHosts
import dev.phantomwatch.provider.DeadReleases
import dev.phantomwatch.data.SettingsKeys
import dev.phantomwatch.ui.BrowseState
import dev.phantomwatch.ui.ClipsScreen
import dev.phantomwatch.ui.DetailPane
import dev.phantomwatch.ui.Geo
import dev.phantomwatch.ui.countryChoiceCode
import dev.phantomwatch.ui.HomeLiquidTabs
import dev.phantomwatch.ui.settings.SettingsAction
import dev.phantomwatch.ui.settings.SettingsData
import dev.phantomwatch.ui.settings.SettingsScreen
import dev.phantomwatch.ui.HomeScreen
import dev.phantomwatch.ui.WhatsNewDialog
import dev.phantomwatch.ui.WhatsNewScreen
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AppRoot() {
    // not playing is dead screen, restoring there resolves forever
    var screen by remember {
        mutableStateOf<Route>(
            Playback.lastScreen?.takeIf { Playback.player?.isPlaying == true } ?: Route.Home,
        )
    }
    val browse = remember { BrowseState() }
    var forward by remember { mutableStateOf(true) }
    var instant by remember { mutableStateOf(false) }
    var autoRelease by remember { mutableStateOf(true) }
    val context = LocalContext.current
    var phantomInstalled by remember { mutableStateOf(PhantomDownload.isInstalled(context)) }
    DisposableEffect(context) {
        val lc = (context as? ComponentActivity)?.lifecycle ?: return@DisposableEffect onDispose { }
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) phantomInstalled = PhantomDownload.isInstalled(context)
        }
        lc.addObserver(obs)
        onDispose { lc.removeObserver(obs) }
    }
    val feedFile = remember { java.io.File(context.cacheDir, "home-feed.json") }
    var youRev by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    var accountBusy by remember { mutableStateOf(false) }
    var accountError by remember { mutableStateOf<String?>(null) }
    val signIn = {
        if (!accountBusy) {
            accountBusy = true
            accountError = null
            scope.launch {
                try {
                    when (val r = GoogleAuth.signIn(context, BuildConfig.GOOGLE_WEB_CLIENT_ID)) {
                        is GoogleAuth.Result.Token -> {
                            val ok = withContext(Dispatchers.IO) { Social.signInWithGoogle(r.idToken) }
                            if (ok) youRev++ else accountError = context.getString(R.string.you_sign_in_failed)
                        }
                        GoogleAuth.Result.Cancelled -> Unit
                        is GoogleAuth.Result.Failed -> {
                            AppLog.d("google sign-in failed: ${r.reason}")
                            accountError = context.getString(R.string.you_sign_in_failed)
                        }
                    }
                } finally {
                    accountBusy = false
                }
            }
        }
    }
    var crash by remember { mutableStateOf(CrashReport.pending(context)) }
    var update by remember { mutableStateOf(UpdateChecker.available(context)) }
    var updateStage by remember { mutableStateOf(0) }
    var settingsCategory by remember { mutableStateOf<String?>(null) }
    var showInstallDialog by remember { mutableStateOf(false) }
    var whatsNewEntries by remember { mutableStateOf(emptyList<WhatsNewEntry>()) }
    var whatsNew by remember { mutableStateOf<WhatsNewEntry?>(null) }
    var whatsNewList by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val entries = withContext(Dispatchers.IO) { WhatsNews.load(context) }
        whatsNewEntries = entries
        if (BuildConfig.DEBUG || WhatsNews.shouldShow(context, entries)) whatsNew = entries.firstOrNull()
    }
    LaunchedEffect(whatsNew) {
        if (whatsNew != null) WhatsNews.markSeen(context)
    }
    LaunchedEffect(Unit) {
        val fresh = withContext(Dispatchers.IO) { UpdateChecker.refresh(context) }
        update = fresh?.takeIf { it.code > UpdateChecker.installedCode(context) }
    }
    LaunchedEffect(browse.tab, update) {
        if (browse.tab == 3 && update != null && updateStage == 0) updateStage = 1
    }
    LaunchedEffect(PushOpen.about) {
        if (!PushOpen.about) return@LaunchedEffect
        PushOpen.consume()
        browse.selectTab(3)
        settingsCategory = "about"
    }
    LaunchedEffect(screen, Pip.playing) {
        (context as? MainActivity)?.armPip(screen is Route.Player, Pip.playing)
    }
    LaunchedEffect(screen) {
        if (screen !is Route.Player && PlayerUi.brightness != null) {
            PlayerUi.brightness = null
            PlayerUi.applyBrightness(context as? MainActivity, null)
        }
    }
    LaunchedEffect(Unit) {
        Telemetry.appContext = context.applicationContext
        DeadHosts.init(context)
    }
    val ask = PlayRequest.pending
    LaunchedEffect(ask) {
        if (ask == null) return@LaunchedEffect
        PlayRequest.consume()
        val t = Title(ask.id, ask.kind, ask.title, null)
        forward = true
        instant = true
        autoRelease = true
        browse.backToEpisodes = ask.kind == "tv"
        if (ask.kind != "tv") browse.openTitle = null
        screen = Route.Player(t.title, null, t, ask.season, ask.episode, Route.Home)
    }
    AnimatedContent(
        targetState = whatsNewList,
        transitionSpec = {
            val forward = targetState > initialState
            val enter = fadeIn(tween(180)) + slideInHorizontally(tween(220)) { if (forward) it / 20 else -it / 20 }
            val exit = fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { if (forward) -it / 24 else it / 24 }
            enter togetherWith exit
        },
        label = "whatsnew",
    ) { showWhatsNew ->
        if (showWhatsNew) {
            WhatsNewScreen(whatsNewEntries) {
                settingsCategory = "about"
                whatsNewList = false
            }
        } else {
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
                is Route.Home -> HomeScreen(
                    browse = browse,
                    onWatch = { t, seas, ep ->
                        val m = Aliases.resolve(t)
                        forward = true
                        instant = true
                        autoRelease = true
                        browse.backToEpisodes = m.kind == "tv"
                        if (m.kind != "tv") browse.openTitle = null
                        screen = Route.Player(m.title, null, m, seas, ep, Route.Home)
                    },
                    loadRecent = { Resume.recent(context) },
                    continueWatching = remember(youRev) {
                        Settings.on(context, Settings.CONTINUE_WATCHING, true)
                    },
                    recentRev = Resume.rev,
                    onRemoveRecent = { Resume.remove(context, it.kind, it.id, it.season, it.episode) },
                    onRestoreRecent = { Resume.restore(context, it) },
                    loadDownloads = {
                        Downloads.importPhantomDownloads(context)
                        Downloads.all(context)
                    },
                    // Resume rev matters too, old downloads borrow progress from matching local playback entry in Downloads.all()
                    downloadsRev = Downloads.rev + Resume.rev,
                    onRemoveDownload = { Downloads.remove(context, it.id) },
                    onRestoreDownload = { r, at -> Downloads.restoreAt(context, r, at) },
                    onPlayDownloaded = { r ->
                        val uri = r.localUri ?: return@HomeScreen
                        forward = true
                        instant = true
                        autoRelease = false
                        val media = Title(id = r.id, kind = r.kind, title = r.title, poster = r.poster)
                        // local URI is already initial Stream: PlayerScreen skips provider resolution
                        val local = Stream(
                            url = uri, isHls = false, subs = emptyList(),
                            referer = "", sampleMp4 = null, complete = true,
                        )
                        // downloads open straight into landscape fullscreen, other sources keep portrait first entry
                        (context as? MainActivity)?.forceLandscape()
                        screen = Route.Player(
                            r.title, local, media, r.season, r.episode,
                            Route.Home, fullscreen = true,
                        )
                    },
                    onExitApp = { (context as? android.app.Activity)?.finish() },
                    searchGhost = { ghost, run ->
                        val comp by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.lottie_search))
                        val anim = rememberLottieAnimatable()
                        val ghostScope = rememberCoroutineScope()
                        var busy by remember { mutableStateOf(false) }
                        LaunchedEffect(run, comp) {
                            val composition = comp ?: return@LaunchedEffect
                            if (busy) return@LaunchedEffect
                            busy = true
                            ghostScope.launch {
                                anim.animate(composition, iterations = 1, initialProgress = 0f)
                                busy = false
                            }
                        }
                        LottieAnimation(
                            composition = comp,
                            progress = { anim.progress },
                            modifier = Modifier.size(ghost),
                        )
                    },
                    detailPane = { item, onClose, onPlay, startEpisodes ->
                        DetailPane(item, onClose, onPlay, startEpisodes, onClose)
                    },
                    clipsScreen = { open ->
                        ClipsScreen(titles = browse.trending, onOpen = open, paused = browse.openTitle != null)
                    },
                    readFeed = { feedFile.takeIf { it.exists() }?.readText() },
                    writeFeed = { feedFile.writeText(it) },
                    youScreen = {
                        val settings = remember(youRev) { liveSettings(context) }
                        Geo.overrideCode = countryChoiceCode(settings[SettingsKeys.COUNTRY] ?: 0)
                        val clipboard = LocalClipboardManager.current
                        SettingsScreen(
                            SettingsData(
                                version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                settings = settings,
                                updateVersion = update?.tag,
                                account = remember(youRev) { Social.account() },
                                accountBusy = accountBusy,
                                accountError = accountError,
                                googleReady = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank(),
                                badgeAbout = update != null && updateStage == 1,
                                badgeUpdate = update != null && updateStage == 2,
                                openCategory = settingsCategory,
                                phantomInstalled = phantomInstalled,
                                onOpenCategory = { id ->
                                    settingsCategory = null
                                    if (id == "about") updateStage = 2
                                },
                                onValue = { key, value -> applySetting(context, key, value); youRev++ },
                                onAction = { action ->
                                    when (action) {
                                        SettingsAction.ClearHistory -> {
                                            Resume.clear(context)
                                            browse.recent = emptyList()
                                            toast(context, R.string.you_history_cleared)
                                        }
                                        SettingsAction.WhatsNew -> whatsNewList = true
                                        SettingsAction.Updates -> scope.launch {
                                            val known = update ?: withContext(Dispatchers.IO) {
                                                UpdateChecker.refresh(context, force = true)
                                            }?.takeIf { it.code > UpdateChecker.installedCode(context) }?.also { update = it }
                                            if (known != null) installRelease(context, known)
                                            else toast(context, R.string.you_about_up_to_date)
                                        }
                                        SettingsAction.Licences -> openUrl(context, RELEASE_URL + "#licence")
                                        SettingsAction.Privacy -> openUrl(context, RELEASE_URL + "#privacy")
                                        SettingsAction.ReportProblem -> {
                                            clipboard.setText(AnnotatedString(reportText()))
                                            openUrl(context, RELEASE_URL + "/issues/new?body=" + android.net.Uri.encode(deviceLine()))
                                            toast(context, R.string.you_report_copied)
                                        }
                                        SettingsAction.GetPhantom ->
                                            if (PhantomDownload.isInstalled(context)) PhantomDownload.open(context)
                                            else showInstallDialog = true
                                    }
                                },
                                onSignIn = signIn,
                                onSignOut = {
                                    Social.signOut()
                                    accountError = null
                                    youRev++
                                },
                            ),
                        )
                    },
                )
                is Route.Player -> {
                    Playback.lastScreen = s
                    PlayerScreen(
                        title = s.title,
                        media = s.media,
                        initial = s.stream,
                        season = s.season,
                        episode = s.episode,
                        startFullscreen = s.fullscreen,
                        onBack = {
                            (context as? MainActivity)?.restoreOrientation()
                            forward = false
                            screen = s.back
                        },
                        onGiveUp = { deadUrl ->
                            deadUrl?.let { DeadReleases.urls.add(it) }
                            forward = false
                            autoRelease = false
                            screen = Route.Sources(s.media, s.season, s.episode)
                        },
                        onPickList = {
                            forward = false
                            autoRelease = false
                            screen = Route.Sources(s.media, s.season, s.episode)
                        },
                        onEpisode = { seas, ep ->
                            forward = true
                            instant = true
                            autoRelease = true
                            browse.backToEpisodes = s.media.kind == "tv"
                            PlayerUi.token++
                            screen = Route.Player(s.media.title, null, s.media, seas, ep, s.back, fullscreen = PlayerUi.fullscreen)
                        },
                        onOpenTitle = { t ->
                            forward = false
                            browse.openTitle = t
                            browse.backToEpisodes = false
                            screen = Route.Home
                        },
                    )
                }
                is Route.Sources -> SourcesScreen(
                    title = s.title,
                    season = s.season,
                    episode = s.episode,
                    autoplay = autoRelease,
                    onStreamReady = { stream ->
                        forward = true
                        instant = true
                        screen = Route.Player(
                            s.title.title, stream, s.title, s.season, s.episode,
                            Route.Sources(s.title, s.season, s.episode),
                        )
                    },
                    onBack = {
                        forward = false
                        screen = Route.Home
                    },
                )
                else -> Box(Modifier.fillMaxSize())
            }
        }
        AnimatedVisibility(
            visible = screen is Route.Home && browse.openTitle == null,
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
                updateBadge = update != null && updateStage == 0,
            )
        }
        if ((BuildConfig.DEBUG || BuildConfig.LOGUI) && !PlayerUi.hidesChrome) LogFab()
        if (showInstallDialog) {
            PhantomInstallDialog(
                onInstall = {
                    showInstallDialog = false
                    PhantomDownload.install(context, job = null)
                },
                onDismiss = { showInstallDialog = false },
            )
        }
        }
    }
    }
    whatsNew?.let { entry ->
        WhatsNewDialog(entry = entry, onDismiss = { whatsNew = null })
    }
    crash?.let { text ->
        AlertDialog(
            onDismissRequest = { crash = null; CrashReport.clear(context) },
            title = { Text(stringResource(R.string.approot_crash_title)) },
            text = {
                Text(
                    text.take(700),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = WorkSans,
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
        var lines by remember { mutableStateOf(AppLog.snapshot()) }
        LaunchedEffect(Unit) {
            while (true) {
                lines = AppLog.snapshot()
                delay(400)
            }
        }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.approot_logs_title, lines.size)) },
            text = {
                androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    items(lines) { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = WorkSans,
                        )
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { AppLog.clear() }) { Text(stringResource(R.string.approot_logs_clear)) }
                    TextButton(onClick = {
                        clip.setText(AnnotatedString(AppLog.snapshot().joinToString("\n")))
                    }) { Text(stringResource(R.string.approot_logs_copy)) }
                    TextButton(onClick = { open = false }) { Text(stringResource(R.string.common_close)) }
                }
            },
        )
    }
}

private const val RELEASE_URL = "https://github.com/ejjays/phantom-watch"

private fun liveSettings(ctx: android.content.Context): Map<String, Int> = mapOf(
    Settings.SUBS_ON to if (Settings.on(ctx, Settings.SUBS_ON, true)) 1 else 0,
    Settings.SUBS_SIZE to Settings.choice(ctx, Settings.SUBS_SIZE, Settings.SIZE_MEDIUM),
    Settings.SEEK_STEP to Settings.choice(ctx, Settings.SEEK_STEP, 1),
    Settings.NEXT_COUNT to Settings.choice(ctx, Settings.NEXT_COUNT, 1),
    Settings.SUBS_BG to Settings.choice(ctx, Settings.SUBS_BG, Settings.BG_DIM),
    Settings.SAVE_HISTORY to if (Settings.on(ctx, Settings.SAVE_HISTORY, true)) 1 else 0,
    Settings.CONTINUE_WATCHING to if (Settings.on(ctx, Settings.CONTINUE_WATCHING, true)) 1 else 0,
    Settings.AUTOPLAY to if (Settings.on(ctx, Settings.AUTOPLAY, true)) 1 else 0,
    Settings.SKIP_INTRO to if (Settings.on(ctx, Settings.SKIP_INTRO, true)) 1 else 0,
    Settings.SHARE_USAGE to if (Telemetry.enabled(ctx)) 1 else 0,
    SettingsKeys.COUNTRY to Settings.choice(ctx, SettingsKeys.COUNTRY, 0),
)

private fun applySetting(ctx: android.content.Context, key: String, value: Int) {
    when (key) {
        Settings.SUBS_SIZE, Settings.SUBS_BG, Settings.SEEK_STEP, Settings.NEXT_COUNT,
        SettingsKeys.COUNTRY -> Settings.setChoice(ctx, key, value)
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

private fun installRelease(ctx: android.content.Context, release: UpdateChecker.Release) {
    runCatching {
        ctx.startActivity(
            android.content.Intent(ctx, UpdateInstallActivity::class.java)
                .putExtra("url", release.url)
                .putExtra("size", release.size)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun deviceLine(): String =
    "PhantomWatch ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · " +
        "${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"

private fun reportText(): String = buildString {
    appendLine(deviceLine())
    appendLine()
    appendLine("What happened:")
    appendLine()
    appendLine("Logs:")
    AppLog.snapshot().takeLast(60).forEach { appendLine(it) }
}
