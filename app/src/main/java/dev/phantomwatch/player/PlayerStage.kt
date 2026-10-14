package dev.phantomwatch.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phantomwatch.R
import dev.phantomwatch.core.AppLog
import dev.phantomwatch.data.Dub
import dev.phantomwatch.ui.Cyan
import dev.phantomwatch.ui.Mono
import dev.phantomwatch.ui.Rubik
import dev.phantomwatch.ui.Slate200
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ZoneTap { Left, Center, Right }

enum class SeekSide { Left, Right }

data class SeekMark(val side: SeekSide, val total: Int)

fun playerClock(ms: Long): String {
    val s = (ms / 1000).toInt().coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PlayerStage(
    title: String,
    fullscreen: Boolean,
    seekBar: Boolean = fullscreen,
    isPlaying: Boolean,
    stalled: Boolean,
    positionMs: Long,
    durationMs: Long,
    intro: LongRange? = null,
    onSkipIntro: () -> Unit = {},
    outro: LongRange? = null,
    onNextEpisode: (() -> Unit)? = null,
    dubs: List<Dub> = emptyList(),
    currentDub: String? = null,
    onPickDub: (Dub) -> Unit = {},
    qualities: List<Rung> = emptyList(),
    quality: Int = 0,
    qualityNow: String? = null,
    onPickQuality: (Int) -> Unit = {},
    speed: Float = 1f,
    onPickSpeed: (Float) -> Unit = {},
    bufferedMs: Long,
    controlsVisible: Boolean,
    flash: String?,
    swipeHud: SwipeHud? = null,
    seekMark: SeekMark?,
    seekInset: Dp = 0.dp,
    topPad: Dp? = null,
    paneH: Dp? = null,
    shiftPx: () -> Float = { 0f },
    videoShiftPx: () -> Float = shiftPx,
    sheetZoneTopPx: () -> Float = { Float.MAX_VALUE },
    onSheetDrag: (Float) -> Unit = {},
    onSheetDragEnd: (Float) -> Unit = {},
    titleBelow: Boolean = false,
    lockButton: Boolean = true,
    rotateButton: Boolean = false,
    locked: Boolean,
    subsOn: Boolean,
    canSubs: Boolean,
    canSettings: Boolean = true,
    muted: Boolean = false,
    showSpinner: Boolean = true,
    onScrub: (Float?) -> Unit = {},
    preview: (@Composable (Modifier) -> Unit)? = null,
    onBack: () -> Unit,
    onToggleMute: () -> Unit = {},
    onFullscreen: () -> Unit,
    onTogglePlay: () -> Unit,
    onToggleLock: () -> Unit,
    onRotate: () -> Unit = {},
    onSeek: (Float) -> Unit,
    onKeySeek: ((SeekSide) -> Unit)? = null,
    onInteract: () -> Unit,
    onZoneTap: (ZoneTap) -> Unit,
    onToggleSubs: () -> Unit,
    onSwipe: (Boolean, Float) -> Unit,
    onSwipeEnd: () -> Unit,
    video: @Composable (Modifier) -> Unit,
) {
    val zone = rememberUpdatedState(sheetZoneTopPx)
    var headerH by remember { mutableFloatStateOf(0f) }
    var settings by remember { mutableStateOf(false) }
    val keys = onKeySeek
    val stageFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (keys != null) stageFocus.requestFocus() }
    // the stage sits on the window edge in fullscreen and on phones in landscape; the portrait pane
    // is already pushed below the status bar, so it must not pad again
    val cfg = LocalConfiguration.current
    val landscapePhone = cfg.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE &&
        cfg.smallestScreenWidthDp < 600
    val portrait = cfg.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
    val atEdge = fullscreen || landscapePhone
    val topInset = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
    // only a stage that owns the window edge has to give the status bar its swipe strip
    val edgePx = with(LocalDensity.current) { (if (atEdge) topInset else 0.dp).toPx() }
    // one bottom inset, never two: the caller's own lift and atEdge must not stack
    val navPad = if (atEdge) WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() else 0.dp
    val edgeSides = (if (atEdge) WindowInsets.navigationBars else WindowInsets(0))
        .union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(locked) {
                var startSide = 0
                var accX = 0f
                var accY = 0f
                var vertical = false
                var sheetZone = false
                var fromEdge = false
                val vel = VelocityTracker()
                detectDragGestures(
                    onDragStart = {
                        val mid = size.width / 2f
                        val dead = 40.dp.toPx()
                        fromEdge = it.y < edgePx
                        startSide = when {
                            fromEdge -> 0
                            it.x < mid - dead -> 1
                            it.x > mid + dead -> 2
                            else -> 0
                        }
                        accX = 0f
                        accY = 0f
                        vertical = false
                        sheetZone = !fromEdge && it.y >= zone.value()
                        vel.resetTracking()
                    },
                    onDragEnd = {
                        if (sheetZone) onSheetDragEnd(vel.calculateVelocity().y)
                        else if (vertical) onSwipeEnd()
                    },
                    onDragCancel = {
                        if (sheetZone) onSheetDragEnd(0f)
                        else if (vertical) onSwipeEnd()
                    },
                    onDrag = { change, drag ->
                        if (!fromEdge) {
                            if (sheetZone) {
                                vel.addPointerInputChange(change)
                                change.consume()
                                onSheetDrag(drag.y)
                            } else if (!locked) {
                                accX += drag.x
                                accY += drag.y
                                if (!vertical && abs(accY) > abs(accX) * 1.5f && abs(accY) > 24f) vertical = true
                                if (vertical && startSide != 0) {
                                    change.consume()
                                    onSwipe(startSide == 1, drag.y)
                                }
                            }
                        }
                    },
                )
            }
            .focusRequester(stageFocus)
            .focusTarget()
            .onPreviewKeyEvent { e ->
                if (keys == null || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                onInteract()
                when {
                    e.key == Key.Escape && settings -> {
                        settings = false
                        true
                    }
                    settings || locked -> false
                    e.key == Key.DirectionLeft -> {
                        keys(SeekSide.Left)
                        true
                    }
                    e.key == Key.DirectionRight -> {
                        keys(SeekSide.Right)
                        true
                    }
                    e.key == Key.M -> {
                        onToggleMute()
                        true
                    }
                    e.key == Key.F -> {
                        onFullscreen()
                        true
                    }
                    else -> false
                }
            }
            .onKeyEvent { e ->
                if (keys == null || settings || locked || e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.Spacebar, Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                        onInteract()
                        onTogglePlay()
                        true
                    }
                    else -> false
                }
            },
    ) {
        video(Modifier.fillMaxSize().offset { IntOffset(0, -(videoShiftPx() / 2f).roundToInt()) })
        Row(Modifier.fillMaxSize()) {
            ZoneTap.entries.forEach { side ->
                Box(
                    Modifier.weight(1f).fillMaxSize()
                        .focusProperties { canFocus = false }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onZoneTap(side) },
                        ),
                )
            }
        }
        // The swipe readout owns the screen while it is up, exactly like YouTube: the chrome fades
        // so the pill never lands on a button.
        val chrome = controlsVisible && !locked && swipeHud == null
        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn(tween(250, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(250, easing = FastOutSlowInEasing)),
        ) {
            Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
                    .offset { IntOffset(0, headerH.roundToInt()) },
            )
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .offset { IntOffset(0, -shiftPx().roundToInt()) }
                    .padding(horizontal = 16.dp).padding(bottom = 8.dp + maxOf(seekInset, navPad))
                    .windowInsetsPadding(edgeSides),
            ) {
                if (seekBar) {
                    PlayerSeekBar(
                        positionMs = positionMs,
                        durationMs = durationMs,
                        bufferedMs = bufferedMs,
                        slim = false,
                        locked = locked,
                        thumb = true,
                        onSeek = onSeek,
                        onInteract = onInteract,
                        onScrub = onScrub,
                        preview = preview,
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${playerClock(positionMs)} / ${playerClock(durationMs)}",
                        color = Slate200, fontSize = 11.sp, fontFamily = Mono,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier.size(48.dp).focusRing().clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onToggleMute() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume_on),
                            contentDescription = if (muted) stringResource(R.string.player_unmute) else stringResource(R.string.player_mute),
                            tint = Color.White, modifier = Modifier.size(20.dp),
                        )
                    }
                    Box(
                        Modifier.size(48.dp).focusRing().clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onFullscreen() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(if (fullscreen) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen),
                            contentDescription = if (fullscreen) stringResource(R.string.player_exit_fullscreen) else stringResource(R.string.player_enter_fullscreen),
                            tint = Color.White, modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            }
        }
        AnimatedVisibility(
            visible = chrome && !stalled,
            enter = fadeIn(tween(250, easing = FastOutSlowInEasing)) + scaleIn(
                tween(300, easing = FastOutSlowInEasing), initialScale = 0.85f,
            ),
            exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.9f),
        ) {
            Box(
                Modifier.fillMaxSize().offset { IntOffset(0, -(videoShiftPx() / 2f).roundToInt()) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f))
                        .focusRing().clickable { onTogglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isPlaying) {
                        Icon(painterResource(R.drawable.ic_pause), contentDescription = stringResource(R.string.player_pause), tint = Color.White, modifier = Modifier.size(26.dp))
                    } else {
                        Icon(painterResource(R.drawable.ic_play), contentDescription = stringResource(R.string.common_play), tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = fullscreen && lockButton && controlsVisible && swipeHud == null,
            enter = fadeIn(tween(250, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(250, easing = FastOutSlowInEasing)),
            modifier = Modifier.align(Alignment.CenterStart)
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(start = 8.dp),
        ) {
            Box(
                Modifier.size(44.dp).focusRing().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onToggleLock() },
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (locked) R.drawable.ic_lock else R.drawable.ic_unlock),
                    contentDescription = if (locked) stringResource(R.string.player_locked) else stringResource(R.string.player_unlocked),
                    tint = Color.White, modifier = Modifier.size(20.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = fullscreen && rotateButton && controlsVisible && swipeHud == null,
            enter = fadeIn(tween(250, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(250, easing = FastOutSlowInEasing)),
            modifier = Modifier.align(Alignment.CenterEnd)
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(end = 8.dp),
        ) {
            Box(
                Modifier.size(44.dp).focusRing().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onRotate() },
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_rotate_screen),
                    contentDescription = stringResource(R.string.player_rotate_landscape),
                    tint = Color.White, modifier = Modifier.size(20.dp),
                )
            }
        }
        // Headers first in child order would sit under the scrim Box below, so it is drawn last and
        // pulled above it with a zIndex instead. A swipe readout takes the top strip for itself, as
        // on YouTube, so the bar fades out instead of sitting under the pill.
        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn(tween(250, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(150, easing = FastOutSlowInEasing)),
        ) {
            // Fullscreen shows the status bar again, so the header has to follow it down — on a phone
            // the bar is tall enough to tuck the title into its empty lower half instead.
            Column(
                Modifier.fillMaxWidth()
                    .onGloballyPositioned { headerH = it.size.height.toFloat() }
                    .then(
                        when {
                            // the pane hands back the height it gave up, so the bar follows it
                            topPad != null -> Modifier.padding(top = topPad)
                            landscapePhone -> Modifier
                                .padding(top = (topInset - 12.dp).coerceAtLeast(0.dp))
                                .windowInsetsPadding(WindowInsets.displayCutout)
                                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                            atEdge -> Modifier
                                .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                                .windowInsetsPadding(WindowInsets.displayCutout)
                                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                            else -> Modifier
                        },
                    ),
            ) {
            PlayerTopBar(
                title = title,
                fullscreen = fullscreen,
                titleBelow = titleBelow,
                canSettings = canSettings,
                topGap = if (landscapePhone) 2.dp else 8.dp,
                large = fullscreen && seekBar && !titleBelow && LocalConfiguration.current.smallestScreenWidthDp >= 600,
                subsOn = subsOn,
                canSubs = canSubs,
                settingsOn = settings,
                onSettings = {
                    settings = !settings
                    onInteract()
                    AppLog.d(if (settings) "settings open" else "settings close")
                },
                onBack = onBack,
                onFullscreen = onFullscreen,
                onToggleSubs = onToggleSubs,
            )
            }
        }
        // The thin progress line only replaces the hidden controls in the non-fullscreen pane.
        if (!seekBar || (!controlsVisible && !fullscreen)) {
        PlayerSeekBar(
            positionMs = positionMs,
            durationMs = durationMs,
            bufferedMs = bufferedMs,
            slim = !controlsVisible,
            locked = locked,
            thumb = false,
            onSeek = onSeek,
            onInteract = onInteract,
            onScrub = onScrub,
            preview = preview,
            modifier = Modifier.align(Alignment.BottomStart)
                .offset { IntOffset(0, -shiftPx().roundToInt()) }
                .padding(bottom = maxOf(seekInset, navPad))
                .windowInsetsPadding(edgeSides),
        )
        }
        if (flash != null) {
            Text(
                flash, color = Color.White, fontSize = 16.sp, fontFamily = Rubik, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = maxHeight / 8)
                    .clip(CircleShape).background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        // Top of the picture, under the status bar whatever the mode: the pane already sits below
        // it, fullscreen/landscape own the window edge, and the theater sheet hands its inset back.
        val hud = swipeHud
        var lastHud by remember { mutableStateOf<SwipeHud?>(null) }
        LaunchedEffect(hud) { if (hud != null) lastHud = hud }
        val shownHud = hud ?: lastHud
        // a portrait pane is the whole phone width, so the readout has to stay a compact fraction of it
        val hudW = if (portrait) minOf(maxWidth * 0.5f, 180.dp) else minOf(maxWidth * 0.62f, 220.dp)
        AnimatedVisibility(
            visible = hud != null,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.TopCenter)
                .padding(top = (topPad ?: if (atEdge) topInset else 0.dp) + 12.dp),
        ) {
            shownHud?.let { SwipeHudPill(it, Modifier.width(hudW)) }
        }
        val mark = seekMark
        var lastMark by remember { mutableStateOf<SeekMark?>(null) }
        LaunchedEffect(mark) { if (mark != null) lastMark = mark }
        val shown = mark ?: lastMark
        AnimatedVisibility(
            visible = mark != null,
            enter = fadeIn(tween(120)) + scaleIn(tween(160), initialScale = 0.9f),
            exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.94f),
            modifier = Modifier.offset { IntOffset(0, -(videoShiftPx() / 2f).roundToInt()) },
        ) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = if (shown?.side == SeekSide.Left) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Box(Modifier.fillMaxWidth(0.5f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    shown?.let { SeekHud(it.side == SeekSide.Left, it.total) }
                }
            }
        }
        val inIntro = intro != null && durationMs > 0 && positionMs in intro && !Pip.inPip
        val inOutro = !inIntro && outro != null && onNextEpisode != null && durationMs > 0 && positionMs in outro && !Pip.inPip
        if (inIntro || inOutro) {
            // the pane's own bottom is under the info sheet, so it has to ride the video shift like
            // the seek bar does or the pill is drawn behind the sheet in portrait
            PlayerPill(
                text = stringResource(if (inIntro) R.string.player_skip_intro else R.string.player_next_episode),
                modifier = Modifier.align(Alignment.BottomEnd)
                    .offset { IntOffset(0, -shiftPx().roundToInt()) }
                    .padding(end = 16.dp, bottom = maxOf(seekInset, navPad) + if (controlsVisible) 64.dp else 16.dp),
            ) { if (inIntro) onSkipIntro() else onNextEpisode?.invoke() }
        }
        if (stalled && showSpinner) {
            Box(
                Modifier.fillMaxSize().offset { IntOffset(0, -(videoShiftPx() / 2f).roundToInt()) },
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Cyan, modifier = Modifier.size(48.dp))
            }
        }
        if (settings) {
            PlayerSettingsMenu(
                topPad = with(LocalDensity.current) { headerH.toDp() } + 4.dp,
                paneH = paneH,
                qualities = qualities,
                quality = quality,
                qualityNow = qualityNow,
                onPickQuality = { onPickQuality(it); settings = false },
                speed = speed,
                onPickSpeed = { onPickSpeed(it); settings = false },
                dubs = dubs,
                current = currentDub,
                onPick = { onPickDub(it); settings = false },
                onDismiss = { settings = false },
            )
        }
    }
}

@Composable
private fun PlayerTopBar(
    title: String,
    fullscreen: Boolean,
    titleBelow: Boolean,
    canSettings: Boolean,
    topGap: Dp,
    large: Boolean,
    subsOn: Boolean,
    canSubs: Boolean,
    settingsOn: Boolean = false,
    onSettings: () -> Unit = {},
    onBack: () -> Unit,
    onFullscreen: () -> Unit,
    onToggleSubs: () -> Unit,
) {
    val subsActive = subsOn && canSubs
    // 48dp boxes: a 20dp glyph with padding around it only taps inside the glyph itself
    val iconBox = Modifier.size(48.dp)
    Column(
        Modifier.fillMaxWidth()
            .padding(top = if (titleBelow) 4.dp else topGap).padding(end = if (large) 16.dp else 8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.padding(start = 4.dp).then(iconBox).focusRing().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { if (fullscreen) onFullscreen() else onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (fullscreen) R.drawable.ic_chevron_down else R.drawable.ic_back),
                    contentDescription = if (fullscreen) stringResource(R.string.player_exit_fullscreen) else stringResource(R.string.player_close),
                    tint = Color.White, modifier = Modifier.size(if (large) 24.dp else 20.dp),
                )
            }
            if (fullscreen && !titleBelow) {
                Text(
                    title, color = Color.White,
                    fontSize = if (large) 17.sp else 15.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Box(
                iconBox.focusRing().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = canSubs,
                ) { onToggleSubs() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (subsActive) R.drawable.ic_cc_on else R.drawable.ic_cc_off),
                    contentDescription = if (subsActive) stringResource(R.string.player_subs_off) else stringResource(R.string.player_subs_on),
                    tint = Color.White.copy(alpha = if (canSubs) 1f else 0.4f),
                    modifier = Modifier.size(if (large) 24.dp else 20.dp),
                )
            }
            Box(
                iconBox.focusRing().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = canSettings,
                ) { onSettings() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_settings),
                    contentDescription = stringResource(R.string.player_settings),
                    tint = if (settingsOn) Cyan else Color.White.copy(alpha = if (canSettings) 1f else 0.4f),
                    modifier = Modifier.size(if (large) 24.dp else 20.dp),
                )
            }
        }
        if (titleBelow) {
            Text(
                title, color = Color.White, fontSize = 15.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp, top = 2.dp),
            )
        }
    }
}

@Composable
private fun PlayerSeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    slim: Boolean,
    locked: Boolean,
    thumb: Boolean,
    onSeek: (Float) -> Unit,
    onInteract: () -> Unit,
    onScrub: (Float?) -> Unit,
    preview: (@Composable (Modifier) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var scrub by remember { mutableStateOf<Float?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var trackW by remember { mutableStateOf(0) }
    val frac = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shown = (scrub ?: frac).coerceIn(0f, 1f)
    val bufFrac = if (durationMs > 0) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    LaunchedEffect(positionMs) {
        if (dragging) return@LaunchedEffect
        scrub?.let { s ->
            if (durationMs > 0 && abs(positionMs - s * durationMs) < 1500) scrub = null
        }
    }
    fun seekAt(xPx: Float, live: Boolean): Float? {
        if (trackW <= 0 || durationMs <= 0) return null
        val f = (xPx / trackW).coerceIn(0f, 1f)
        scrub = f
        onInteract()
        if (live) onSeek(f)
        return f
    }
    val playedColor by animateColorAsState(
        if (slim) Color.White else Cyan, tween(300), label = "seekPlayed",
    )
    Column(modifier.fillMaxWidth()) {
        val at = scrub
        if (!slim && at != null && durationMs > 0) {
            if (preview != null) {
                ScrubPreview(
                    positionMs = (at * durationMs).toLong(),
                    shown = shown,
                    trackW = trackW,
                    preview = preview,
                )
            } else {
                Text(
                    playerClock((at * durationMs).toLong()),
                    color = Color.White, fontSize = 12.sp, fontFamily = Mono,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                )
            }
        }
        Box(
            Modifier.fillMaxWidth().height(if (slim) 12.dp else 24.dp)
                .onGloballyPositioned { trackW = it.size.width }
                .then(
                    if (locked) Modifier
                    else Modifier.pointerInput(trackW, durationMs) {
                        detectTapGestures { offset -> seekAt(offset.x, true) }
                    }.pointerInput(trackW, durationMs) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                dragging = true
                                onScrub(seekAt(offset.x, false))
                            },
                            onDragEnd = {
                                dragging = false
                                onScrub(null)
                                scrub?.let { onSeek(it) }
                                onInteract()
                            },
                            onDragCancel = {
                                dragging = false
                                onScrub(null)
                                scrub?.let { onSeek(it) }
                                onInteract()
                            },
                            onHorizontalDrag = { change, _ -> onScrub(seekAt(change.position.x, false)) },
                        )
                    },
                ),
        ) {
            val trackH = if (slim) 2.dp else 3.dp
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(trackH)
                    .clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.25f)),
            ) {
                Box(
                    Modifier.align(Alignment.CenterStart).fillMaxWidth(bufFrac).height(trackH)
                        .background(Color.White.copy(alpha = 0.4f)),
                )
                Box(
                    Modifier.align(Alignment.CenterStart).fillMaxWidth(shown).height(trackH)
                        .clip(RoundedCornerShape(topEnd = 1.5.dp, bottomEnd = 1.5.dp))
                        .background(playedColor),
                )
            }
            if (!slim && thumb && scrub != null && trackW > 0) {
                val thumbOff = with(LocalDensity.current) {
                    val r = 7.dp.roundToPx()
                    if (trackW <= 0) 0
                    else (shown * trackW).roundToInt().coerceIn(r, (trackW - r).coerceAtLeast(r)) - r
                }
                Box(
                    Modifier.align(Alignment.BottomStart)
                        .offset { IntOffset(thumbOff, 5.dp.roundToPx()) }
                        .size(14.dp).clip(CircleShape).background(Cyan),
                )
            }
        }
    }
}

@Composable
private fun ScrubPreview(
    positionMs: Long,
    shown: Float,
    trackW: Int,
    preview: @Composable (Modifier) -> Unit,
) {
    val width = 176.dp
    val widthPx = with(LocalDensity.current) { width.roundToPx() }
    val x = if (trackW > widthPx) {
        ((shown * trackW).roundToInt() - widthPx / 2).coerceIn(0, trackW - widthPx)
    } else {
        0
    }
    Box(Modifier.fillMaxWidth().offset(y = 12.dp).padding(bottom = 2.dp)) {
        Column(
            Modifier.offset { IntOffset(x, 0) }.width(width),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Cyan, RoundedCornerShape(8.dp))
                    .background(Color.Black),
            ) {
                preview(Modifier.fillMaxWidth())
                Text(
                    playerClock(positionMs),
                    color = Color.White, fontSize = 10.sp, fontFamily = Mono, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp)
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 3.dp, vertical = 1.dp),
                )
            }
            Canvas(Modifier.size(16.dp, 7.dp)) {
                drawArc(
                    color = Cyan,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(0f, -size.height),
                    size = Size(size.width, size.height * 2f),
                )
            }
            Box(
                Modifier.padding(top = 3.dp).size(5.dp).clip(CircleShape).background(Cyan),
            )
        }
    }
}

@Composable
private fun PlayerPill(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(32.dp).clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            .focusRing(RoundedCornerShape(16.dp)).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(start = 12.dp, end = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text, color = Color.White, fontSize = 12.sp,
            fontFamily = Rubik, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp,
        )
        Spacer(Modifier.width(1.dp))
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp),
        )
    }
}

// taps never move focus in Compose, so this ring only ever shows for a keyboard or D-pad
@Composable
internal fun Modifier.focusRing(shape: Shape = CircleShape): Modifier {
    var on by remember { mutableStateOf(false) }
    return onFocusChanged { on = it.isFocused }
        .then(if (on) Modifier.border(2.dp, Cyan, shape) else Modifier)
}
