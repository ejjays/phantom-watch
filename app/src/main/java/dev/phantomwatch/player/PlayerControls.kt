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

@Composable
internal fun PlayerTopBar(
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
    // 48dp boxes: 20dp glyph with padding around it only taps inside glyph itself
    val iconBox = Modifier.size(48.dp).clip(CircleShape)
    val cfg = LocalConfiguration.current
    val phone = cfg.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE &&
        cfg.smallestScreenWidthDp < 600
    val glyphSize = if (large || phone) 24.dp else 20.dp
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
                    indication = ripple(color = Color.White.copy(alpha = 0.22f)),
                ) { if (fullscreen) onFullscreen() else onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (fullscreen) R.drawable.ic_chevron_down else R.drawable.ic_back),
                    contentDescription = if (fullscreen) stringResource(R.string.player_exit_fullscreen) else stringResource(R.string.player_close),
                    tint = Color.White, modifier = Modifier.size(glyphSize),
                )
            }
            if (fullscreen && !titleBelow) {
                Text(
                    title, color = Color.White,
                    fontSize = if (large) 17.sp else 15.sp, fontFamily = Lilita, fontWeight = FontWeight.Normal,
                    maxLines = 1, modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Box(
                iconBox.focusRing().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = Color.White.copy(alpha = 0.22f)),
                ) { onToggleSubs() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (subsActive) R.drawable.ic_cc_on else R.drawable.ic_cc_off),
                    contentDescription = if (subsActive) stringResource(R.string.player_subs_off) else stringResource(R.string.player_subs_on),
                    tint = Color.White.copy(alpha = if (canSubs) 1f else 0.4f),
                    modifier = Modifier.size(glyphSize),
                )
            }
            Box(
                iconBox.focusRing().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = Color.White.copy(alpha = 0.22f)),
                    enabled = canSettings,
                ) { onSettings() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_settings),
                    contentDescription = stringResource(R.string.player_settings),
                    tint = if (settingsOn) Cyan else Color.White.copy(alpha = if (canSettings) 1f else 0.4f),
                    modifier = Modifier.size(glyphSize),
                )
            }
        }
        if (titleBelow) {
            Text(
                title, color = Color.White, fontSize = 15.sp, fontFamily = Lilita, fontWeight = FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp, top = 2.dp),
            )
        }
    }
}

@Composable
internal fun PlayerSeekBar(
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
                    color = Color.White, fontSize = 12.sp, fontFamily = WorkSans,
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
            if (!slim && thumb && trackW > 0) {
                val thumbOff = with(LocalDensity.current) {
                    val r = 7.dp.roundToPx()
                    (shown * trackW).roundToInt().coerceIn(r, (trackW - r).coerceAtLeast(r)) - r
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
    // rides bar width so thumbnail grows with stage, phone sized 176dp card reads as stamp
    // once bar spans landscape screen
    BoxWithConstraints(Modifier.fillMaxWidth().offset(y = 12.dp).padding(bottom = 2.dp)) {
        // never taller than half stage, 1.1 aspect card would climb past picture
        val budget = maxHeight * 0.5f
        val bar = with(LocalDensity.current) { trackW.toDp() }
        val cap = minOf(280.dp, budget * 16f / 9f, bar)
        val width = with(LocalDensity.current) { (trackW * 0.6f).toDp() }
            .coerceIn(minOf(176.dp, cap), cap)
        val widthPx = with(LocalDensity.current) { width.roundToPx() }
        val x = if (trackW > widthPx) {
            ((shown * trackW).roundToInt() - widthPx / 2).coerceIn(0, trackW - widthPx)
        } else {
            0
        }
        Column(
            Modifier.offset { IntOffset(x, 0) }.width(width),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.fillMaxWidth().heightIn(max = budget).clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Cyan, RoundedCornerShape(8.dp))
                    .background(Color.Black),
            ) {
                preview(Modifier.fillMaxWidth())
                Text(
                    playerClock(positionMs),
                    color = Color.White, fontSize = 11.sp, fontFamily = WorkSans, fontWeight = FontWeight.SemiBold,
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

// taps never move focus in compose, so ring only ever shows for keyboard or dpad
@Composable
internal fun Modifier.focusRing(shape: Shape = CircleShape): Modifier {
    var on by remember { mutableStateOf(false) }
    return onFocusChanged { on = it.isFocused }
        .then(if (on) Modifier.border(2.dp, Cyan, shape) else Modifier)
}
