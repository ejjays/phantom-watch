package dev.phantomwatch.ui

import android.graphics.Bitmap
import android.view.TextureView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Full-screen ambient for the detail wall: the trailer's colour, realtime, no card geometry. */
@Composable
internal fun DetailAmbient(surface: TextureView?, fallback: Color, modifier: Modifier = Modifier) {
    var raw by remember { mutableStateOf(fallback) }
    LaunchedEffect(surface) {
        val view = surface ?: return@LaunchedEffect
        val bmp = Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888)
        while (isActive) {
            if (view.isAvailable) {
                runCatching {
                    view.getBitmap(bmp)
                    raw = ambientColor(bmp)
                }
            }
            delay(200)
        }
    }
    LaunchedEffect(fallback) {
        if (surface == null) raw = fallback
    }
    val glow by animateColorAsState(raw, tween(300), label = "detailAmbient")
    val cool = lerp(glow, Cyan, 0.35f)

    Box(
        modifier.drawBehind {
            drawRect(Bg)
            drawRect(
                Brush.verticalGradient(
                    0f to glow.copy(alpha = 0.40f),
                    0.5f to glow.copy(alpha = 0.15f),
                    1f to Bg,
                ),
            )
            drawRect(
                Brush.radialGradient(
                    0f to glow.copy(alpha = 0.45f),
                    0.55f to glow.copy(alpha = 0.16f),
                    1f to Color.Transparent,
                    center = Offset(size.width * 0.27f, size.height * 0.5f),
                    radius = size.width * 0.80f,
                ),
            )
            drawRect(
                Brush.radialGradient(
                    0f to cool.copy(alpha = 0.16f),
                    1f to Color.Transparent,
                    center = Offset(size.width * 0.12f, size.height * 0.90f),
                    radius = size.width * 0.55f,
                ),
            )
            drawRect(
                Brush.radialGradient(
                    0.55f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.45f),
                    center = Offset(size.width * 0.35f, size.height * 0.5f),
                    radius = size.width * 0.95f,
                ),
            )
        },
    )
}
