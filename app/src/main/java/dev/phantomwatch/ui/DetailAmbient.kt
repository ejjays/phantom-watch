package dev.phantomwatch.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.view.TextureView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import dev.phantomwatch.player.TrailerAudio
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

private const val STEPS = 96
private const val DEAD_ZONE = 0.015f
private const val MAX_STEP = 0.035f

private val SkyBlue = Color(0xFF38BDF8)
private val PearlWhite = Color(0xFFF0FDFA)

// waves traced around the card's rounded edge, tinted from the trailer
@Composable
internal fun DetailAmbient(
    surface: TextureView?,
    fallback: Color,
    audio: TrailerAudio,
    card: Rect,
    modifier: Modifier = Modifier,
) {
    var raw by remember { mutableStateOf(fallback) }
    LaunchedEffect(surface) {
        val view = surface ?: return@LaunchedEffect
        val bmp = Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888)
        var sample = raw
        while (isActive) {
            if (view.isAvailable) {
                runCatching {
                    view.getBitmap(bmp)
                    val next = ambientColor(bmp)
                    sample = slew(sample, next)
                    raw = sample
                }
            }
            delay(400)
        }
    }
    LaunchedEffect(fallback) {
        if (surface == null) raw = fallback
    }

    val hsv = remember { FloatArray(3) }
    AndroidColor.RGBToHSV(
        (raw.red * 255).toInt().coerceIn(0, 255),
        (raw.green * 255).toInt().coerceIn(0, 255),
        (raw.blue * 255).toInt().coerceIn(0, 255),
        hsv,
    )
    val satFactor = (hsv[1] / 0.20f).coerceIn(0f, 1f)
    val baseHue = hsv[0] * satFactor + 28f * (1f - satFactor)

    val targetPrimary = Color.hsv(baseHue, 0.88f, 0.98f)
    val targetWarm = Color.hsv((baseHue + 28f) % 360f, 0.82f, 1.0f)
    val targetHighlight = Color.hsv((baseHue + 14f) % 360f, 0.28f, 1.0f)

    val cPrimary by animateColorAsState(targetPrimary, tween(1000), label = "cPrimary")
    val cWarm by animateColorAsState(targetWarm, tween(1000), label = "cWarm")
    val cHighlight by animateColorAsState(targetHighlight, tween(1000), label = "cHighlight")

    val cViolet = remember(cPrimary) { lerp(cPrimary, Cyan, 0.50f) }
    val cPeach = remember(cWarm) { lerp(cWarm, PearlWhite, 0.40f) }

    val pal1 = remember(cPrimary, cViolet) { listOf(cPrimary, cViolet, Cyan, cPrimary) }
    val pal2 = remember(cWarm, cHighlight) { listOf(Cyan, SkyBlue, cPeach, cWarm, Cyan) }

    var t by remember { mutableFloatStateOf(0f) }
    var bassEnv by remember { mutableFloatStateOf(0f) }
    var midsEnv by remember { mutableFloatStateOf(0f) }
    var lvlEnv by remember { mutableFloatStateOf(0f) }

    val topStroke1 = remember { Path() }
    val topFill1 = remember { Path() }
    val topStroke2 = remember { Path() }
    val topFill2 = remember { Path() }

    val botStroke1 = remember { Path() }
    val botFill1 = remember { Path() }
    val botStroke2 = remember { Path() }
    val botFill2 = remember { Path() }

    val topBaseX = remember { FloatArray(STEPS + 1) }
    val topBaseY = remember { FloatArray(STEPS + 1) }
    val botBaseX = remember { FloatArray(STEPS + 1) }
    val botBaseY = remember { FloatArray(STEPS + 1) }

    LaunchedEffect(Unit) {
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (now - last >= 16_000_000L) {
                    val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceIn(0.008f, 0.050f)
                    last = now
                    t = (t + dt) % 1_000f
                    val b = audio.bands
                    var rawBass = 0f
                    var rawMids = 0f
                    if (b.isNotEmpty()) {
                        val n = b.size
                        for (i in 0 until minOf(5, n)) rawBass += b[i]
                        rawBass /= 5f
                        for (i in 5 until minOf(15, n)) rawMids += b[i]
                        rawMids /= 10f
                    }
                    val rawLvl = maxOf(audio.level, rawBass * 0.85f)
                    bassEnv += (rawBass - bassEnv) * 0.18f
                    midsEnv += (rawMids - midsEnv) * 0.18f
                    lvlEnv += (rawLvl - lvlEnv) * 0.16f
                }
            }
        }
    }

    Box(
        modifier.drawBehind {
            val w = size.width
            val h = size.height
            drawRect(Bg)

            if (card.height > 0f && card.width > 0f) {
                val center = card.center
                val cw = card.width
                val ch = card.height
                val cornerR = 24.dp.toPx()

                drawCircle(
                    brush = Brush.radialGradient(
                        0.0f to cViolet.copy(alpha = 0.20f + 0.12f * lvlEnv),
                        0.50f to cPrimary.copy(alpha = 0.12f + 0.08f * lvlEnv),
                        0.75f to Cyan.copy(alpha = 0.05f),
                        1.0f to Color.Transparent,
                        center = center,
                        radius = cw * 0.85f,
                    ),
                    radius = cw * 0.85f,
                    center = center,
                    blendMode = BlendMode.Screen,
                )
                val cyanOffset = Offset(center.x - cw * 0.20f, center.y - ch * 0.22f)
                drawCircle(
                    brush = Brush.radialGradient(
                        0.0f to Cyan.copy(alpha = 0.16f + 0.08f * lvlEnv),
                        0.60f to SkyBlue.copy(alpha = 0.05f),
                        1.0f to Color.Transparent,
                        center = cyanOffset,
                        radius = cw * 0.65f,
                    ),
                    radius = cw * 0.65f,
                    center = cyanOffset,
                    blendMode = BlendMode.Screen,
                )
                val warmOffset = Offset(center.x + cw * 0.20f, center.y + ch * 0.22f)
                drawCircle(
                    brush = Brush.radialGradient(
                        0.0f to cWarm.copy(alpha = 0.16f + 0.08f * lvlEnv),
                        0.60f to cHighlight.copy(alpha = 0.05f),
                        1.0f to Color.Transparent,
                        center = warmOffset,
                        radius = cw * 0.65f,
                    ),
                    radius = cw * 0.65f,
                    center = warmOffset,
                    blendMode = BlendMode.Screen,
                )

                val idle1 = 14.dp.toPx()
                val idle2 = 9.dp.toPx()
                val amp1 = idle1 + bassEnv * 28.dp.toPx() + lvlEnv * 12.dp.toPx()
                val amp2 = idle2 + midsEnv * 20.dp.toPx()

                topStroke1.reset(); topFill1.reset()
                topStroke2.reset(); topFill2.reset()
                botStroke1.reset(); botFill1.reset()
                botStroke2.reset(); botFill2.reset()

                val straightW = (cw - 2f * cornerR).coerceAtLeast(0f)
                val cornerArc = 0.5f * PI.toFloat() * cornerR
                val totalLen = (2f * cornerArc + straightW).coerceAtLeast(1f)
                val uCorner = cornerArc / totalLen
                val uStraight = 1f - uCorner

                val b = audio.bands
                val pi = PI.toFloat()
                val diveDepth = 24.dp.toPx()
                val tuckX = 4.dp.toPx()

                for (s in 0..STEPS) {
                    val u = s / STEPS.toFloat()

                    val (bx, by, nx, ny) = when {
                        u < uCorner -> {
                            val a = u / uCorner
                            val phi = pi - a * (0.5f * pi)
                            val cosP = cos(phi)
                            val sinP = sin(phi)
                            val x = card.left + cornerR + cornerR * cosP
                            val y = card.top + cornerR - cornerR * sinP
                            FloatArray(4).apply {
                                this[0] = x; this[1] = y; this[2] = cosP; this[3] = -sinP
                            }
                        }
                        u <= uStraight -> {
                            val a = (u - uCorner) / (uStraight - uCorner)
                            val x = card.left + cornerR + a * straightW
                            val y = card.top
                            FloatArray(4).apply {
                                this[0] = x; this[1] = y; this[2] = 0f; this[3] = -1f
                            }
                        }
                        else -> {
                            val a = (u - uStraight) / (1f - uStraight)
                            val phi = (1f - a) * (0.5f * pi)
                            val cosP = cos(phi)
                            val sinP = sin(phi)
                            val x = card.right - cornerR + cornerR * cosP
                            val y = card.top + cornerR - cornerR * sinP
                            FloatArray(4).apply {
                                this[0] = x; this[1] = y; this[2] = cosP; this[3] = -sinP
                            }
                        }
                    }
                    topBaseX[s] = bx
                    topBaseY[s] = by

                    val (bxBot, byBot, nxBot, nyBot) = when {
                        u < uCorner -> {
                            val a = u / uCorner
                            val phi = pi - a * (0.5f * pi)
                            val cosP = cos(phi)
                            val sinP = sin(phi)
                            val x = card.left + cornerR + cornerR * cosP
                            val y = card.bottom - cornerR + cornerR * sinP
                            FloatArray(4).apply {
                                this[0] = x; this[1] = y; this[2] = cosP; this[3] = sinP
                            }
                        }
                        u <= uStraight -> {
                            val a = (u - uCorner) / (uStraight - uCorner)
                            val x = card.left + cornerR + a * straightW
                            val y = card.bottom
                            FloatArray(4).apply {
                                this[0] = x; this[1] = y; this[2] = 0f; this[3] = 1f
                            }
                        }
                        else -> {
                            val a = (u - uStraight) / (1f - uStraight)
                            val phi = (1f - a) * (0.5f * pi)
                            val cosP = cos(phi)
                            val sinP = sin(phi)
                            val x = card.right - cornerR + cornerR * cosP
                            val y = card.bottom - cornerR + cornerR * sinP
                            FloatArray(4).apply {
                                this[0] = x; this[1] = y; this[2] = cosP; this[3] = sinP
                            }
                        }
                    }
                    botBaseX[s] = bxBot
                    botBaseY[s] = byBot

                    val taper = 0.25f + 0.75f * sin(pi * u).coerceIn(0f, 1f)

                    val spec1 = sample(b, u * 0.5f)
                    val spec2 = sample(b, 0.2f + u * 0.5f)

                    val p1 = u * 1.3f * pi + t * 0.7f
                    val idleMotion1 = 0.55f + 0.45f * sin(p1)
                    val audioBoost1 = 1.0f + 1.2f * spec1
                    val w1 = idleMotion1 * audioBoost1 * amp1 * taper
                    val w1Bot = (0.55f + 0.45f * sin(p1 + 1.4f)) * audioBoost1 * amp1 * taper

                    val p2 = u * 1.8f * pi - t * 0.9f + 1.2f
                    val idleMotion2 = 0.50f + 0.50f * sin(p2)
                    val audioBoost2 = 1.0f + 1.1f * spec2
                    val w2 = idleMotion2 * audioBoost2 * amp2 * taper
                    val w2Bot = (0.50f + 0.50f * sin(p2 - 1.2f)) * audioBoost2 * amp2 * taper

                    val wxTop1 = bx + nx * w1
                    val wyTop1 = by + ny * w1
                    val wxTop2 = bx + nx * w2
                    val wyTop2 = by + ny * w2

                    val wxBot1 = bxBot + nxBot * w1Bot
                    val wyBot1 = byBot + nyBot * w1Bot
                    val wxBot2 = bxBot + nxBot * w2Bot
                    val wyBot2 = byBot + nyBot * w2Bot

                    if (s == 0) {
                        val startTopX = card.left + tuckX
                        val startTopY = card.top + cornerR + diveDepth
                        topStroke1.moveTo(startTopX, startTopY)
                        topStroke2.moveTo(startTopX, startTopY)
                        topFill1.moveTo(startTopX, startTopY)
                        topFill2.moveTo(startTopX, startTopY)

                        topStroke1.lineTo(wxTop1, wyTop1)
                        topStroke2.lineTo(wxTop2, wyTop2)
                        topFill1.lineTo(wxTop1, wyTop1)
                        topFill2.lineTo(wxTop2, wyTop2)

                        val startBotX = card.left + tuckX
                        val startBotY = card.bottom - cornerR - diveDepth
                        botStroke1.moveTo(startBotX, startBotY)
                        botStroke2.moveTo(startBotX, startBotY)
                        botFill1.moveTo(startBotX, startBotY)
                        botFill2.moveTo(startBotX, startBotY)

                        botStroke1.lineTo(wxBot1, wyBot1)
                        botStroke2.lineTo(wxBot2, wyBot2)
                        botFill1.lineTo(wxBot1, wyBot1)
                        botFill2.lineTo(wxBot2, wyBot2)
                    } else {
                        topStroke1.lineTo(wxTop1, wyTop1)
                        topStroke2.lineTo(wxTop2, wyTop2)
                        topFill1.lineTo(wxTop1, wyTop1)
                        topFill2.lineTo(wxTop2, wyTop2)

                        botStroke1.lineTo(wxBot1, wyBot1)
                        botStroke2.lineTo(wxBot2, wyBot2)
                        botFill1.lineTo(wxBot1, wyBot1)
                        botFill2.lineTo(wxBot2, wyBot2)
                    }
                }

                val endTopX = card.right - tuckX
                val endTopY = card.top + cornerR + diveDepth
                topStroke1.lineTo(endTopX, endTopY)
                topStroke2.lineTo(endTopX, endTopY)
                topFill1.lineTo(endTopX, endTopY)
                topFill2.lineTo(endTopX, endTopY)

                val endBotX = card.right - tuckX
                val endBotY = card.bottom - cornerR - diveDepth
                botStroke1.lineTo(endBotX, endBotY)
                botStroke2.lineTo(endBotX, endBotY)
                botFill1.lineTo(endBotX, endBotY)
                botFill2.lineTo(endBotX, endBotY)

                for (s in STEPS downTo 0) {
                    val bx = topBaseX[s]
                    val by = topBaseY[s]
                    topFill1.lineTo(bx, by)
                    topFill2.lineTo(bx, by)

                    val bxB = botBaseX[s]
                    val byB = botBaseY[s]
                    botFill1.lineTo(bxB, byB)
                    botFill2.lineTo(bxB, byB)
                }
                topFill1.close(); topFill2.close()
                botFill1.close(); botFill2.close()

                val shift1 = (t * 20f) % cw
                val shift2 = (-t * 26f) % cw

                val brush1 = Brush.horizontalGradient(pal1, card.left - shift1, card.right + cw - shift1, TileMode.Repeated)
                val brush2 = Brush.horizontalGradient(pal2, card.left - shift2, card.right + cw - shift2, TileMode.Repeated)

                drawPath(
                    topFill1,
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.55f to cViolet.copy(alpha = 0.08f),
                        1.0f to cViolet.copy(alpha = 0.22f),
                        startY = card.top - amp1,
                        endY = card.top,
                    ),
                    blendMode = BlendMode.Screen,
                )
                drawPath(
                    botFill1,
                    Brush.verticalGradient(
                        0.0f to cViolet.copy(alpha = 0.22f),
                        0.45f to cViolet.copy(alpha = 0.08f),
                        1.0f to Color.Transparent,
                        startY = card.bottom,
                        endY = card.bottom + amp1,
                    ),
                    blendMode = BlendMode.Screen,
                )
                val glow1 = 8.dp.toPx()
                val core1 = 2.dp.toPx()
                drawPath(topStroke1, brush1, style = Stroke(glow1, cap = StrokeCap.Round, join = StrokeJoin.Round), alpha = 0.35f, blendMode = BlendMode.Screen)
                drawPath(topStroke1, brush1, style = Stroke(core1, cap = StrokeCap.Round, join = StrokeJoin.Round), alpha = 0.90f, blendMode = BlendMode.Screen)
                drawPath(botStroke1, brush1, style = Stroke(glow1, cap = StrokeCap.Round, join = StrokeJoin.Round), alpha = 0.35f, blendMode = BlendMode.Screen)
                drawPath(botStroke1, brush1, style = Stroke(core1, cap = StrokeCap.Round, join = StrokeJoin.Round), alpha = 0.90f, blendMode = BlendMode.Screen)

                drawPath(
                    topFill2,
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.55f to Cyan.copy(alpha = 0.07f),
                        1.0f to Cyan.copy(alpha = 0.18f),
                        startY = card.top - amp2,
                        endY = card.top,
                    ),
                    blendMode = BlendMode.Screen,
                )
                drawPath(
                    botFill2,
                    Brush.verticalGradient(
                        0.0f to Cyan.copy(alpha = 0.18f),
                        0.45f to Cyan.copy(alpha = 0.07f),
                        1.0f to Color.Transparent,
                        startY = card.bottom,
                        endY = card.bottom + amp2,
                    ),
                    blendMode = BlendMode.Screen,
                )
                val glow2 = 6.dp.toPx()
                val core2 = 1.5.dp.toPx()
                drawPath(topStroke2, brush2, style = Stroke(glow2, cap = StrokeCap.Round, join = StrokeJoin.Round), alpha = 0.40f, blendMode = BlendMode.Screen)
                drawPath(topStroke2, brush2, style = Stroke(core2, cap = StrokeCap.Round, join = StrokeJoin.Round), alpha = 0.92f, blendMode = BlendMode.Screen)
                drawPath(botStroke2, brush2, style = Stroke(glow2, cap = StrokeCap.Round, join = StrokeJoin.Round), alpha = 0.40f, blendMode = BlendMode.Screen)
                drawPath(botStroke2, brush2, style = Stroke(core2, cap = StrokeCap.Round, join = StrokeJoin.Round), alpha = 0.92f, blendMode = BlendMode.Screen)
            }

            drawRect(
                brush = Brush.radialGradient(
                    0.50f to Color.Transparent,
                    1.0f to Color.Black.copy(alpha = 0.45f),
                    center = Offset(w * 0.5f, h * 0.5f),
                    radius = maxOf(w, h) * 0.75f,
                ),
            )
        },
    )
}

private fun slew(from: Color, to: Color): Color {
    val d = maxOf(
        abs(to.red - from.red),
        abs(to.green - from.green),
        abs(to.blue - from.blue),
    )
    if (d < DEAD_ZONE) return from
    val t = if (d <= MAX_STEP) 1f else MAX_STEP / d
    return Color(
        from.red + (to.red - from.red) * t,
        from.green + (to.green - from.green) * t,
        from.blue + (to.blue - from.blue) * t,
    )
}

private fun sample(bands: FloatArray, u: Float): Float {
    if (bands.isEmpty()) return 0f
    val p = u.coerceIn(0f, 1f) * (bands.size - 1)
    val i = floor(p).toInt()
    val f = p - i
    val a = bands[i]
    val b = bands[(i + 1).coerceAtMost(bands.size - 1)]
    return a + (b - a) * (f * f * (3f - 2f * f))
}
