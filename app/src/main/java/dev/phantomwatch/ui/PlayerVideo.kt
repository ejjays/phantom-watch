package dev.phantomwatch.ui

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.google.android.gms.media.effect.enhancement.EnhancementSession
import dev.phantomwatch.PipCtl

@Composable
fun PlayerVideo(
    exo: ExoPlayer,
    enhSession: EnhancementSession?,
    videoAspect: Float,
    maxVideoH: Dp?,
    subCues: String,
    subStyle: Pair<Float, Float>?,
    subTopPx: Float?,
    onEnhSurface: (Surface?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var videoSurface by remember { mutableStateOf<SurfaceView?>(null) }
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val vh = minOf(maxVideoH?.coerceAtMost(maxHeight) ?: maxHeight, maxWidth / videoAspect)
        val vw = vh * videoAspect
        AmbientGlow(videoSurface, videoAspect, Modifier.fillMaxSize())
        if (enhSession != null) {
            AndroidView(
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(h: SurfaceHolder) {
                                onEnhSurface(h.surface)
                            }

                            override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {}

                            override fun surfaceDestroyed(h: SurfaceHolder) {
                                onEnhSurface(null)
                            }
                        })
                    }
                },
                update = { videoSurface = it },
                modifier = Modifier.size(vw, vh),
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = exo
                        useController = false
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
                update = {
                    it.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    videoSurface = it.videoSurfaceView as? SurfaceView
                    it.subtitleView?.visibility = View.GONE
                },
                modifier = Modifier.size(vw, vh),
            )
        }
        SubtitleLayer(subCues, subStyle, subTopPx)
    }
}

@Composable
private fun SubtitleLayer(cues: String, style: Pair<Float, Float>?, topPx: Float?) {
    if (cues.isEmpty()) return
    val d = LocalDensity.current
    val top = topPx?.let { with(d) { it.toDp() } }
    BoxWithConstraints(
        Modifier.fillMaxSize().then(if (top != null) Modifier.padding(top = top) else Modifier),
        contentAlignment = if (top != null) Alignment.TopCenter else Alignment.BottomCenter,
    ) {
        val textPx = style?.first ?: with(d) { maxHeight.toPx() } * 0.0533f
        val shift = style?.second ?: if (PipCtl.inPip) 0f else -with(d) { 14.dp.toPx() }
        val shape = RoundedCornerShape(with(d) { (textPx * 0.52f).toDp() })
        Text(
            text = cues,
            color = Color.White,
            textAlign = TextAlign.Center,
            style = TextStyle(
                fontSize = with(d) { textPx.toSp() },
                lineHeight = with(d) { (textPx * 1.2f).toSp() },
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
            modifier = Modifier
                .graphicsLayer { translationY = if (top == null) shift else 0f }
                .padding(horizontal = 8.dp)
                .background(Color(0xFF080C10).copy(alpha = 0.80f), shape)
                .padding(
                    horizontal = with(d) { (textPx * 0.59f).toDp() },
                    vertical = with(d) { (textPx * 0.33f).toDp() },
                ),
        )
    }
}
