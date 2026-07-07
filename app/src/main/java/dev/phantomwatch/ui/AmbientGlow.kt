package dev.phantomwatch.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.min

private const val FADE = 0.75f
private const val MAX_LUMA = 28f
private const val MAX_STEP = 0.02f
private const val DEAD_ZONE = 0.012f

@Composable
fun AmbientGlow(
    surface: SurfaceView?,
    aspect: Float,
    matchH: Boolean,
    modifier: Modifier = Modifier,
) {
    var raw by remember { mutableStateOf(Color.Black) }
    LaunchedEffect(surface) {
        val view = surface ?: return@LaunchedEffect
        val handler = Handler(Looper.getMainLooper())
        val sample = Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888)
        while (isActive) {
            val result = try {
                suspendCancellableCoroutine<Int> { cont ->
                    PixelCopy.request(view, sample, { r -> if (cont.isActive) cont.resume(r) }, handler)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                PixelCopy.ERROR_UNKNOWN
            }
            if (result == PixelCopy.SUCCESS) raw = slew(raw, ambientColor(sample))
            delay(500)
        }
    }
    val glow by animateColorAsState(raw, tween(900), label = "ambient")
    Canvas(
        modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val vh = if (matchH) size.height else min(size.width / aspect, size.height)
        val vTop = (size.height - vh) / 2f
        val vBottom = vTop + vh
        val band = vh * FADE
        val top = (vTop - band).coerceAtLeast(0f)
        val bottom = (vBottom + band).coerceAtMost(size.height)
        if (bottom - top <= 0f) return@Canvas
        if (top < vTop) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to glow.copy(alpha = 0f),
                    1f to glow,
                    startY = top,
                    endY = vTop,
                ),
                topLeft = Offset(0f, top),
                size = Size(size.width, vTop - top),
            )
        }
        if (bottom > vBottom) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to glow,
                    1f to glow.copy(alpha = 0f),
                    startY = vBottom,
                    endY = bottom,
                ),
                topLeft = Offset(0f, vBottom),
                size = Size(size.width, bottom - vBottom),
            )
        }
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Black.copy(alpha = 0.68f),
                0.125f to Color.Black.copy(alpha = 0.833f),
                0.25f to Color.Black.copy(alpha = 0.917f),
                0.375f to Color.Black,
                0.625f to Color.Black,
                0.75f to Color.Black.copy(alpha = 0.917f),
                0.875f to Color.Black.copy(alpha = 0.833f),
                1f to Color.Black.copy(alpha = 0.68f),
            ),
            topLeft = Offset(0f, top),
            size = Size(size.width, bottom - top),
            blendMode = BlendMode.DstIn,
        )
    }
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

private fun ambientColor(bmp: Bitmap): Color {
    val px = IntArray(bmp.width * bmp.height)
    bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    var r = 0f
    var g = 0f
    var b = 0f
    for (p in px) {
        r += (p shr 16) and 0xFF
        g += (p shr 8) and 0xFF
        b += p and 0xFF
    }
    val n = px.size
    r /= n
    g /= n
    b /= n
    val m = 0.2126f * r + 0.7152f * g + 0.0722f * b
    var cr = (m + 1.8f * (r - m)) * 0.55f
    var cg = (m + 1.8f * (g - m)) * 0.55f
    var cb = (m + 1.8f * (b - m)) * 0.55f
    val lum = 0.2126f * cr + 0.7152f * cg + 0.0722f * cb
    if (lum > MAX_LUMA) {
        val k = MAX_LUMA / lum
        cr *= k
        cg *= k
        cb *= k
    }
    return Color(
        cr.toInt().coerceIn(0, 255),
        cg.toInt().coerceIn(0, 255),
        cb.toInt().coerceIn(0, 255),
    )
}
