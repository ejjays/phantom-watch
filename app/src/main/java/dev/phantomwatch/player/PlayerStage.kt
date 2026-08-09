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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phantomwatch.R
import dev.phantomwatch.ui.Cyan
import dev.phantomwatch.ui.Mono
import dev.phantomwatch.ui.Rubik
import dev.phantomwatch.ui.Slate200
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ZoneTap { Left, Center, Right }

enum class SeekSide { Left, Right }

data class SeekMark(val side: SeekSide, val total: Int)

data class SwipeHud(val brightness: Boolean, val frac: Float)

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
    bufferedMs: Long,
    controlsVisible: Boolean,
    flash: String?,
    swipeHud: SwipeHud? = null,
    seekMark: SeekMark?,
    seekInset: Dp = 0.dp,
    shiftPx: () -> Float = { 0f },
    videoShiftPx: () -> Float = shiftPx,
    sheetZoneTopPx: () -> Float = { Float.MAX_VALUE },
    onSheetDrag: (Float) -> Unit = {},
    onSheetDragEnd: (Float) -> Unit = {},
    titleBelow: Boolean = false,
    barsInset: Boolean = true,
    lockButton: Boolean = true,
    rotateButton: Boolean = false,
    locked: Boolean,
    subsOn: Boolean,
    canSubs: Boolean,
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
    onInteract: () -> Unit,
    onZoneTap: (ZoneTap) -> Unit,
    onToggleSubs: () -> Unit,
    onSwipe: (Boolean, Float) -> Unit,
    onSwipeEnd: () -> Unit,
    video: @Composable (Modifier) -> Unit,
) {
    val zone = rememberUpdatedState(sheetZoneTopPx)
    var headerH by remember { mutableFloatStateOf(0f) }
    val topInset = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
    val edgePx = with(LocalDensity.current) { (topInset + 12.dp).toPx() }
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
            },
    ) {
        video(Modifier.fillMaxSize().offset { IntOffset(0, -(videoShiftPx() / 2f).roundToInt()) })
        Row(Modifier.fillMaxSize()) {
            ZoneTap.entries.forEach { side ->
                Box(
                    Modifier.weight(1f).fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onZoneTap(side) },
                        ),
                )
            }
        }
        AnimatedVisibility(
            visible = controlsVisible && !locked,
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
                    .padding(horizontal = 16.dp).padding(bottom = 8.dp + seekInset),
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
                        Modifier.padding(10.dp).clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onToggleMute() },
                    ) {
                        Icon(
                            painterResource(if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume_on),
                            contentDescription = if (muted) "Unmute" else "Mute",
                            tint = Color.White, modifier = Modifier.size(20.dp),
                        )
                    }
                    Box(Modifier.padding(10.dp).clickable { onFullscreen() }) {
                        Icon(
                            painterResource(if (fullscreen) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen),
                            contentDescription = if (fullscreen) "Exit fullscreen" else "Enter fullscreen",
                            tint = Color.White, modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            }
        }
        AnimatedVisibility(
            visible = controlsVisible && !stalled && !locked,
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
                        .clickable { onTogglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isPlaying) {
                        Icon(painterResource(R.drawable.ic_pause), contentDescription = "Pause", tint = Color.White, modifier = Modifier.size(26.dp))
                    } else {
                        Icon(painterResource(R.drawable.ic_play), contentDescription = "Play", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = fullscreen && lockButton && controlsVisible,
            enter = fadeIn(tween(250, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(250, easing = FastOutSlowInEasing)),
            modifier = Modifier.align(Alignment.CenterStart)
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(start = 8.dp),
        ) {
            Box(
                Modifier.size(44.dp).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onToggleLock() },
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (locked) R.drawable.ic_lock else R.drawable.ic_unlock),
                    contentDescription = if (locked) "Locked" else "Unlocked",
                    tint = Color.White, modifier = Modifier.size(20.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = fullscreen && rotateButton && controlsVisible,
            enter = fadeIn(tween(250, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(250, easing = FastOutSlowInEasing)),
            modifier = Modifier.align(Alignment.CenterEnd)
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(end = 8.dp),
        ) {
            Box(
                Modifier.size(44.dp).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onRotate() },
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_rotate_screen),
                    contentDescription = "Rotate to landscape",
                    tint = Color.White, modifier = Modifier.size(20.dp),
                )
            }
        }
        // Headers first in child order would sit under the scrim Box below, so it is drawn last and
        // pulled above it with a zIndex instead.
        if (controlsVisible && !locked) {
            // Fullscreen shows the status bar again, so the header has to follow it down.
            Column(
                Modifier.fillMaxWidth()
                    .onGloballyPositioned { headerH = it.size.height.toFloat() }
                    .then(if (fullscreen && barsInset) Modifier.statusBarsPadding() else Modifier),
            ) {
            PlayerTopBar(
                title = title,
                fullscreen = fullscreen,
                titleBelow = titleBelow,
                barsInset = barsInset,
                large = fullscreen && seekBar,
                subsOn = subsOn,
                canSubs = canSubs,
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
                .padding(bottom = seekInset),
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
        if (swipeHud != null) {
            SwipeHudPill(
                swipeHud,
                Modifier.align(if (swipeHud.brightness) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 40.dp),
            )
        }
        if (seekMark != null) {
            Box(
                Modifier.fillMaxSize().padding(horizontal = 28.dp)
                    .offset { IntOffset(0, -(videoShiftPx() / 2f).roundToInt()) },
                contentAlignment = if (seekMark.side == SeekSide.Left) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Icon(
                    painterResource(if (seekMark.side == SeekSide.Left) R.drawable.ic_backward_10 else R.drawable.ic_forward_10),
                    contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp),
                )
            }
        }
        if (intro != null && durationMs > 0 && positionMs in intro && !Pip.inPip) {
            SkipIntroPill(
                Modifier.align(Alignment.BottomEnd)
                    .offset { IntOffset(0, -shiftPx().roundToInt()) }
                    .padding(end = 16.dp, bottom = seekInset + if (controlsVisible) 64.dp else 16.dp),
            ) { onSeek(intro.last.toFloat() / durationMs) }
        }
        if (stalled && showSpinner) {
            Box(
                Modifier.fillMaxSize().offset { IntOffset(0, -(videoShiftPx() / 2f).roundToInt()) },
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Cyan, modifier = Modifier.size(48.dp))
            }
        }
    }
}

@Composable
private fun PlayerTopBar(
    title: String,
    fullscreen: Boolean,
    titleBelow: Boolean,
    barsInset: Boolean,
    large: Boolean,
    subsOn: Boolean,
    canSubs: Boolean,
    onBack: () -> Unit,
    onFullscreen: () -> Unit,
    onToggleSubs: () -> Unit,
) {
    val subsActive = subsOn && canSubs
    Column(
        Modifier.fillMaxWidth()
            .padding(top = 8.dp).padding(end = 16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.padding(start = 4.dp).padding(10.dp)
                    .clickable { if (fullscreen) onFullscreen() else onBack() },
            ) {
                Icon(
                    painterResource(if (fullscreen) R.drawable.ic_chevron_down else R.drawable.ic_back),
                    contentDescription = if (fullscreen) "Exit fullscreen" else "Close player",
                    tint = Color.White, modifier = Modifier.size(if (large) 26.dp else 22.dp),
                )
            }
            if (fullscreen && !titleBelow) {
                Text(
                    title, color = Color.White,
                    fontSize = if (large) 18.sp else 15.sp, fontFamily = Rubik, fontWeight = FontWeight.Bold,
                    maxLines = 1, modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Box(
                Modifier.padding(10.dp).clickable(enabled = canSubs) { onToggleSubs() },
            ) {
                Icon(
                    painterResource(if (subsActive) R.drawable.ic_cc_on else R.drawable.ic_cc_off),
                    contentDescription = if (subsActive) "Turn subtitles off" else "Turn subtitles on",
                    tint = Color.White.copy(alpha = if (canSubs) 1f else 0.4f),
                    modifier = Modifier.size(if (large) 26.dp else 20.dp),
                )
            }
        }
        if (titleBelow) {
            Text(
                title, color = Color.White, fontSize = 16.sp, fontFamily = Rubik, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp),
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
private fun SkipIntroPill(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.7f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Skip intro", color = Color.White, fontSize = 13.sp, fontFamily = Rubik, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(4.dp))
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun SwipeHudPill(hud: SwipeHud, modifier: Modifier = Modifier) {
    Box(
        modifier.width(40.dp).height(150.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.fillMaxSize().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painterResource(
                    when {
                        hud.brightness -> R.drawable.ic_brightness
                        hud.frac <= 0f -> R.drawable.ic_volume_off
                        else -> R.drawable.ic_volume_on
                    },
                ),
                contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.weight(1f).width(3.dp).clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f)),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    Modifier.fillMaxWidth().fillMaxHeight(hud.frac.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(2.dp)).background(Color.White),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "${(hud.frac * 100).roundToInt()}",
                color = Color.White, fontSize = 12.sp, fontFamily = Rubik, fontWeight = FontWeight.Bold,
            )
        }
    }
}
