package dev.phantomwatch.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.min

private const val ViewW = 240f
private const val ViewH = 271f

private val NeutralMouth = floatArrayOf(
    187f, 198f, 195.67f, 202.67f, 204.33f, 202.67f, 213f, 198f,
    204.33f, 198.67f, 195.67f, 198.67f, 187f, 198f,
)
private val SmileMouth = floatArrayOf(
    186f, 193f, 195.33f, 209f, 204.67f, 209f, 214f, 193f,
    204.67f, 189f, 195.33f, 189f, 186f, 193f,
)

@Composable
fun PhantomHero(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "phantom")
    val float by t.animateFloat(
        0f, -15f,
        infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "float",
    )
    val blink by t.animateFloat(
        1f, 1f,
        infiniteRepeatable(
            keyframes {
                durationMillis = 4200
                1f at 0
                1f at 3864
                0.1f at 4032
                1f at 4200
            },
        ),
        label = "blink",
    )
    val smile by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(
            keyframes {
                durationMillis = 12000
                0f at 0
                0f at 2000
                1f at 2300
                1f at 11000
                0f at 11300
            },
        ),
        label = "smile",
    )
    val body = remember {
        Path().apply {
            moveTo(80f, 170f)
            cubicTo(80f, 103.7f, 133.7f, 50f, 200f, 50f)
            cubicTo(266.3f, 50f, 320f, 103.7f, 320f, 170f)
            lineTo(320f, 295f)
            quadraticBezierTo(290f, 317f, 260f, 295f)
            quadraticBezierTo(230f, 317f, 200f, 295f)
            quadraticBezierTo(170f, 317f, 140f, 295f)
            quadraticBezierTo(110f, 317f, 80f, 295f)
            close()
        }
    }
    val fold = remember {
        Path().apply {
            moveTo(285f, 105f)
            cubicTo(305f, 140f, 320f, 220f, 320f, 295f)
            quadraticBezierTo(290f, 317f, 260f, 295f)
            quadraticBezierTo(250f, 287f, 245f, 275f)
            cubicTo(270f, 270f, 280f, 180f, 285f, 105f)
            close()
        }
    }
    val mouth = FloatArray(14)

    Canvas(modifier) {
        val s = min(size.width / ViewW, size.height / ViewH)
        val ox = (size.width - ViewW * s) / 2f
        val oy = (size.height - ViewH * s) / 2f
        withTransform({
            translate(ox, oy)
            scale(s, s, Offset.Zero)
            translate(-80f, -35f)
        }) {
            withTransform({ translate(0f, float) }) {
                drawPath(
                    body,
                    Brush.linearGradient(
                        0f to Color(0xFFE0F7FA),
                        0.6f to Color(0xFF67E8F9),
                        1f to Color(0xFF06B6D4),
                        start = Offset(0f, 0f),
                        end = Offset(400f, 430f),
                    ),
                )
                drawPath(fold, Color(0xFF0891B2), alpha = 0.4f)
                withTransform({ scale(1f, blink, pivot = Offset(200f, 160f)) }) {
                    drawOval(Color(0xFF083344), topLeft = Offset(152f, 142f), size = Size(26f, 36f))
                    drawOval(Color(0xFF083344), topLeft = Offset(222f, 142f), size = Size(26f, 36f))
                    drawOval(Color.White, topLeft = Offset(160f, 153f), size = Size(10f, 14f))
                    drawOval(Color.White, topLeft = Offset(230f, 153f), size = Size(10f, 14f))
                }
                drawOval(Color(0xFF0891B2), topLeft = Offset(139f, 172f), size = Size(18f, 10f), alpha = 0.6f)
                drawOval(Color(0xFF0891B2), topLeft = Offset(243f, 172f), size = Size(18f, 10f), alpha = 0.6f)
                for (i in mouth.indices) {
                    mouth[i] = NeutralMouth[i] + (SmileMouth[i] - NeutralMouth[i]) * smile
                }
                drawPath(
                    Path().apply {
                        moveTo(mouth[0], mouth[1])
                        cubicTo(mouth[2], mouth[3], mouth[4], mouth[5], mouth[6], mouth[7])
                        cubicTo(mouth[8], mouth[9], mouth[10], mouth[11], mouth[12], mouth[13])
                        close()
                    },
                    Color(0xFF083344),
                )
            }
        }
    }
}
