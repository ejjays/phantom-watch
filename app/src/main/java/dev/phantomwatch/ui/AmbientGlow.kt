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
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.min

private const val FADE = 0.75f
private const val MAX_LUMA = 56f
private const val MAX_STEP = 0.02f
private const val DEAD_ZONE = 0.012f

@Composable
fun AmbientGlow(
    surface: SurfaceView?,
    aspect: Float,
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
        val vh = min(size.height, size.width / aspect)
        val vw = vh * aspect
        val vTop = (size.height - vh) / 2f
        val vLeft = (size.width - vw) / 2f
        val vBottom = vTop + vh
        val vRight = vLeft + vw
        val vBand = min(vh * FADE, vTop)
        val hBand = min(vw * FADE, vLeft)
        if (vBand > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to glow.copy(alpha = 0f),
                    1f to glow,
                    startY = vTop - vBand,
                    endY = vTop,
                ),
                topLeft = Offset(0f, vTop - vBand),
                size = Size(size.width, vBand),
            )
            drawRect(
                brush = Brush.verticalGradient(
                    0f to glow,
                    1f to glow.copy(alpha = 0f),
                    startY = vBottom,
                    endY = vBottom + vBand,
                ),
                topLeft = Offset(0f, vBottom),
                size = Size(size.width, vBand),
            )
        }
        if (hBand > 0f) {
            drawRect(
                color = glow,
                topLeft = Offset(vLeft - hBand, 0f),
                size = Size(hBand, size.height),
            )
            drawRect(
                color = glow,
                topLeft = Offset(vRight, 0f),
                size = Size(hBand, size.height),
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

// per-title colours, so reopening a detail starts tinted instead of fading up from black
private val glowCache = object : LinkedHashMap<String, Color>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Color>?) = size > 64
}

// the player's own derivation, sampled from a 32px decode
@Composable
fun rememberImageGlow(url: String?): Color {
    val ctx = LocalContext.current
    var raw by remember(url) { mutableStateOf(url?.let { glowCache[it] } ?: Color.Black) }
    LaunchedEffect(url) {
        val src = url ?: return@LaunchedEffect
        if (glowCache.containsKey(src)) return@LaunchedEffect
        val bmp = withContext(Dispatchers.IO) {
            runCatching {
                val req = ImageRequest.Builder(ctx).data(src).size(32).allowHardware(false).build()
                SingletonImageLoader.get(ctx).execute(req).image?.toBitmap()
            }.getOrNull()
        } ?: return@LaunchedEffect
        val color = ambientColor(bmp)
        glowCache[src] = color
        raw = color
    }
    return animateColorAsState(raw, tween(400), label = "ambient-img").value
}

internal fun ambientColor(bmp: Bitmap): Color {
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
