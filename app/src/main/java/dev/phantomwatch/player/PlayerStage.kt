package dev.phantomwatch.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.input.pointer.PointerEventPass
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
import dev.phantomwatch.ui.Lilita
import dev.phantomwatch.ui.WorkSans
import dev.phantomwatch.ui.Slate200
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ZoneTap { Left, Center, Right }

// one full stage width of horizontal swipe covers this much runtime
private const val SEEK_SWIPE_MS = 90_000f

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
    recap: LongRange? = null,
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
    mode: Int = 0, safe: Boolean = false, onMode: (Int) -> Unit = {},
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
    settingsBottom: Dp = 0.dp,
    settingsOpen: Boolean? = null,
    onSettingsOpenChange: (Boolean) -> Unit = {},
    shiftPx: () -> Float = { 0f },
    videoShiftPx: () -> Float = shiftPx,
    sheetZoneTopPx: () -> Float = { Float.MAX_VALUE },
    onSheetDrag: (Float) -> Unit = {},
    onSheetDragEnd: (Float) -> Unit = {},
    titleBelow: Boolean = false,
    lockButton: Boolean = true,
    sidebarOpen: Boolean = false,
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
    onEpisodes: () -> Unit = {},
    canEpisodes: Boolean = false,
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
    var ownSettings by remember { mutableStateOf(false) }
    val settings = settingsOpen ?: ownSettings
    val hostedSettings = settingsOpen != null
    fun setSettings(open: Boolean) {
        if (!hostedSettings) ownSettings = open
        onSettingsOpenChange(open)
    }
    val keys = onKeySeek
    val at = StageAt(intro, recap, outro, durationMs, positionMs)
    val stageFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (keys != null) stageFocus.requestFocus() }
    val cfg = LocalConfiguration.current
    val landscapePhone = cfg.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE &&
        cfg.smallestScreenWidthDp < 600
    val portrait = cfg.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
    val atEdge = fullscreen || landscapePhone
    val ctrlIcon = if (landscapePhone) 24.dp else 20.dp
    val ctrlBox = if (landscapePhone) 56.dp else 48.dp
    val ctrlGap = if (landscapePhone) 6.dp else 0.dp
    val clockSize = if (landscapePhone) 13.sp else 11.sp
    val topInset = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
    val edgePx = with(LocalDensity.current) { (if (atEdge) topInset else 0.dp).toPx() }
    // one bottom inset, never two: caller lift and atEdge must not stack
    val navPad = if (atEdge) WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding() else 0.dp
    val bottomPad = if (sidebarOpen) 0.dp else navPad
    val edgeSides = (if (atEdge) WindowInsets.navigationBarsIgnoringVisibility else WindowInsets(0))
        .union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    val liveMode = rememberUpdatedState(mode)
    val liveSafe = rememberUpdatedState(safe)
    val livePos = rememberUpdatedState(positionMs)
    val liveDur = rememberUpdatedState(durationMs)
    // fraction swipe is previewing, null when finger is off picture
    var seekSwipe by remember { mutableStateOf<Float?>(null) }
    // held after release so readout fades on frame finger left it on
    var swipeShown by remember { mutableStateOf(0f) }
    // frame kept up until picture lands on new position, seek moves clock at once but
    // decoder lags and old frame flashes in gap
    var seekHold by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(seekHold) {
        val h = seekHold ?: return@LaunchedEffect
        val target = (h * liveDur.value).toLong()
        delay(200)
        repeat(20) { if (abs(livePos.value - target) > 900) delay(60) }
        if (seekHold == h) seekHold = null
    }
    var seekBack by remember { mutableStateOf<Long?>(null) }
    var seekBackJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    fun seekBarTo(f: Float) {
        if (durationMs > 0) seekBack = positionMs
        seekBackJob?.cancel()
        seekBackJob = scope.launch {
            delay(4000)
            seekBack = null
        }
        onSeek(f)
    }
    fun endSeekSwipe() {
        val f = seekSwipe
        seekSwipe = null
        onScrub(null)
        if (f != null) {
            seekHold = f
            onSeek(f)
        }
    }
    // second finger makes pinch, first finger drag would seek under it or change brightness
    val pinch = remember { intArrayOf(0) }
    fun dropSeekSwipe() {
        seekSwipe = null
        onScrub(null)
    }
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent(PointerEventPass.Initial).changes.count { it.pressed }
                        if (down > 1) {
                            pinch[0] = 1
                            if (seekSwipe != null) dropSeekSwipe()
                        } else if (down == 0) {
                            pinch[0] = 0
                        }
                    }
                }
            }
            .pointerInput(locked) {
                var startSide = 0
                var accX = 0f
                var accY = 0f
                var vertical = false
                var horizontal = false
                var seekFrom = 0f
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
                        horizontal = false
                        seekFrom = if (liveDur.value > 0) {
                            (livePos.value.toFloat() / liveDur.value).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        sheetZone = !fromEdge && it.y >= zone.value()
                        vel.resetTracking()
                    },
                    onDragEnd = {
                        if (pinch[0] != 0) {
                            dropSeekSwipe()
                            onSwipeEnd()
                        } else if (sheetZone) onSheetDragEnd(vel.calculateVelocity().y)
                        else if (horizontal) endSeekSwipe()
                        else if (vertical) onSwipeEnd()
                    },
                    onDragCancel = {
                        if (pinch[0] != 0) {
                            dropSeekSwipe()
                            onSwipeEnd()
                        } else if (sheetZone) onSheetDragEnd(0f)
                        else if (horizontal) endSeekSwipe()
                        else if (vertical) onSwipeEnd()
                    },
                    onDrag = { change, drag ->
                        if (!fromEdge) {
                            if (sheetZone) {
                                vel.addPointerInputChange(change)
                                change.consume()
                                onSheetDrag(drag.y)
                            } else if (!locked && pinch[0] == 0) {
                                accX += drag.x
                                accY += drag.y
                                if (!vertical && !horizontal) {
                                    if (abs(accY) > abs(accX) * 1.5f && abs(accY) > 24f) {
                                        vertical = true
                                    } else if (abs(accX) > abs(accY) * 1.5f && abs(accX) > 24f) {
                                        horizontal = true
                                    }
                                }
                                if (vertical && startSide != 0) {
                                    change.consume()
                                    onSwipe(startSide == 1, drag.y)
                                } else if (horizontal) {
                                    change.consume()
                                    val dur = liveDur.value
                                    if (dur > 0) {
                                        // full stage width is fixed span, predictable at any length
                                        val span = size.width.toFloat().coerceAtLeast(1f)
                                        val f = (seekFrom + accX / span * SEEK_SWIPE_MS / dur).coerceIn(0f, 1f)
                                        seekSwipe = f
                                        swipeShown = f
                                        onScrub(f)
                                    }
                                }
                            }
                        }
                    },
                )
            }
            .then(if (landscapePhone && !locked) Modifier.pinchZoom({ liveMode.value }, { liveSafe.value }, onMode) else Modifier)
            .focusRequester(stageFocus)
            .focusTarget()
            .onPreviewKeyEvent { e ->
                if (keys == null || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                onInteract()
                when {
                    e.key == Key.Escape && settings -> {
                        setSettings(false)
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
                    e.key == Key.N && at.inOutro -> {
                        onNextEpisode?.invoke()
                        true
                    }
                    e.key == Key.S && at.inIntro -> {
                        onSkipIntro()
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
        CompositionLocalProvider(LocalSeekSwipe provides (seekSwipe ?: seekHold)) {
            video(Modifier.fillMaxSize().offset { IntOffset(0, -(videoShiftPx() / 2f).roundToInt()) })
        }
        // frame painted by video, only place that knows where pinch zoom put picture,
        // this layer is bar and clock
        AnimatedVisibility(
            visible = seekSwipe != null,
            enter = fadeIn(tween(120, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(220, easing = FastOutSlowInEasing)),
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
        ) {
            val at = (swipeShown * liveDur.value).toLong()
            // bar keeps shape its mode rests on, chrome bar above button row in fullscreen,
            // slim edge bar everywhere else
            Column(
                Modifier.fillMaxWidth()
                    .offset { IntOffset(0, -shiftPx().roundToInt()) }
                    .then(
                        if (fullscreen) {
                            Modifier.padding(horizontal = 16.dp)
                                .padding(bottom = 8.dp + maxOf(seekInset, bottomPad))
                        } else {
                            Modifier.padding(bottom = maxOf(seekInset, bottomPad))
                        },
                    )
                    .windowInsetsPadding(edgeSides),
            ) {
                Text(
                    playerClock(at),
                    color = Color.White, fontSize = 16.sp, fontFamily = WorkSans, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
                PlayerSeekBar(
                    positionMs = at,
                    durationMs = liveDur.value,
                    bufferedMs = bufferedMs,
                    slim = !fullscreen,
                    // stage owns finger, bar here is readout and must not take it
                    locked = true,
                    thumb = fullscreen,
                    onSeek = {},
                    onInteract = {},
                    onScrub = {},
                    preview = null,
                )
                // button row sits under bar in chrome, its height keeps bar on line it always
                // sits on rather than dropping to edge
                if (fullscreen) Spacer(Modifier.height(ctrlBox))
            }
        }
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
        val chrome = controlsVisible && !locked && swipeHud == null && seekSwipe == null
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
                    .padding(horizontal = 16.dp).padding(bottom = 8.dp + maxOf(seekInset, bottomPad))
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
                        onSeek = ::seekBarTo,
                        onInteract = onInteract,
                        onScrub = onScrub,
                        preview = preview,
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ctrlGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${playerClock(positionMs)} / ${playerClock(durationMs)}",
                        color = Slate200, fontSize = clockSize, fontFamily = WorkSans,
                        modifier = Modifier.weight(1f),
                    )
                    if (fullscreen && landscapePhone && canEpisodes) {
                        Row(
                            Modifier.height(ctrlBox).widthIn(min = ctrlBox)
                                .clip(RoundedCornerShape(28.dp))
                                .focusRing(RoundedCornerShape(28.dp)).clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(color = Color.White.copy(alpha = 0.22f)),
                            ) { onEpisodes() }
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                stringResource(R.string.player_episodes_short),
                                color = Color.White, fontSize = 12.sp, fontFamily = WorkSans,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                painterResource(R.drawable.ic_episodes),
                                contentDescription = stringResource(R.string.player_episodes),
                                tint = Color.White, modifier = Modifier.size(ctrlIcon),
                            )
                        }
                    }
                    Box(
                        Modifier.size(ctrlBox).clip(CircleShape).focusRing().clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(color = Color.White.copy(alpha = 0.22f)),
                        ) { onToggleMute() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume_on),
                            contentDescription = if (muted) stringResource(R.string.player_unmute) else stringResource(R.string.player_mute),
                            tint = Color.White, modifier = Modifier.size(ctrlIcon),
                        )
                    }
                    Box(
                        Modifier.size(ctrlBox).clip(CircleShape).focusRing().clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = Color.White.copy(alpha = 0.22f)),
                    ) { onFullscreen() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(if (fullscreen) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen),
                            contentDescription = if (fullscreen) stringResource(R.string.player_exit_fullscreen) else stringResource(R.string.player_enter_fullscreen),
                            tint = Color.White, modifier = Modifier.size(ctrlIcon),
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
            modifier = Modifier.offset { IntOffset(0, -(videoShiftPx() / 2f).roundToInt()) },
        ) {
            Box(
                Modifier.fillMaxSize(),
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
            visible = fullscreen && lockButton && !sidebarOpen && controlsVisible && swipeHud == null,
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
        // first in child order would sit under scrim Box
        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn(tween(250, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(150, easing = FastOutSlowInEasing)),
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .onGloballyPositioned { headerH = it.size.height.toFloat() }
                    .then(
                        when {
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
                    setSettings(!settings)
                    onInteract()
                    AppLog.d(if (!settings) "settings open" else "settings close")
                },
                onBack = onBack,
                onFullscreen = onFullscreen,
                onToggleSubs = onToggleSubs,
            )
            }
        }
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
                .padding(bottom = maxOf(seekInset, bottomPad))
                .windowInsetsPadding(edgeSides),
        )
        }
        if (flash != null) {
            Text(
                flash, color = Color.White, fontSize = 16.sp, fontFamily = WorkSans, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = maxHeight / 8)
                    .clip(CircleShape).background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        val hud = swipeHud
        var lastHud by remember { mutableStateOf<SwipeHud?>(null) }
        LaunchedEffect(hud) { if (hud != null) lastHud = hud }
        val shownHud = hud ?: lastHud
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
        LaunchedEffect(at.inOutro) { if (at.inOutro) AppLog.d("outro pill on ${positionMs / 1000}s") }
        val pillLift by animateDpAsState(
            targetValue = if (controlsVisible) 16.dp + ctrlBox + if (seekBar) 24.dp else 0.dp else 16.dp,
            animationSpec = tween(220, easing = FastOutSlowInEasing),
            label = "skipPillLift",
        )
        if (at.inIntro || at.inOutro) {
            PlayerPill(
                text = stringResource(pillText(at.inRecap, at.inIntro)),
                compact = portrait && !fullscreen,
                modifier = Modifier.align(Alignment.BottomEnd)
                    .offset { IntOffset(0, -shiftPx().roundToInt()) }
                    .padding(end = 16.dp, bottom = maxOf(seekInset, bottomPad) + pillLift)
                    .windowInsetsPadding(edgeSides),
            ) { if (at.inIntro || at.inRecap) onSkipIntro() else onNextEpisode?.invoke() }
        }
        seekBack?.let { back ->
            if (durationMs > 0) {
                PlayerPill(
                    text = stringResource(R.string.player_seek_back),
                    icon = R.drawable.ic_revert,
                    iconBefore = true,
                    iconSize = 16.dp,
                    compact = portrait && !fullscreen,
                    modifier = Modifier.align(Alignment.BottomEnd)
                        .offset { IntOffset(0, -shiftPx().roundToInt()) }
                        .padding(end = 16.dp, bottom = maxOf(seekInset, bottomPad) + pillLift)
                        .windowInsetsPadding(edgeSides),
                ) {
                    seekBackJob?.cancel()
                    seekBack = null
                    onSeek(back.toFloat() / durationMs)
                }
            }
        }
        if (stalled && showSpinner) {
            Box(
                Modifier.fillMaxSize().offset { IntOffset(0, -(videoShiftPx() / 2f).roundToInt()) },
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Cyan, modifier = Modifier.size(48.dp))
            }
        }
        if (settings && !hostedSettings) {
            PlayerSettingsMenu(
                topPad = with(LocalDensity.current) { headerH.toDp() } + 4.dp,
                paneH = paneH,
                qualities = qualities,
                quality = quality,
                qualityNow = qualityNow,
                onPickQuality = onPickQuality,
                speed = speed,
                onPickSpeed = onPickSpeed,
                dubs = dubs,
                current = currentDub,
                onPick = onPickDub,
                onDismiss = { setSettings(false) },
                bottomPad = settingsBottom,
            )
        }
    }
}
