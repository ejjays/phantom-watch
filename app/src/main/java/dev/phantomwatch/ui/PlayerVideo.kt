package dev.phantomwatch.ui

import android.util.TypedValue
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import dev.phantomwatch.PipCtl
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.google.android.gms.media.effect.enhancement.EnhancementSession

@Composable
fun PlayerVideo(
    exo: ExoPlayer,
    enhSession: EnhancementSession?,
    videoAspect: Float,
    matchH: Boolean,
    subStyle: Pair<Float, Float>?,
    onEnhSurface: (Surface?) -> Unit,
    onSubView: (SubtitleView?) -> Unit,
    onSubViewReleased: (SubtitleView) -> Unit,
    modifier: Modifier = Modifier,
) {
    var videoSurface by remember { mutableStateOf<SurfaceView?>(null) }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                modifier = Modifier.aspectRatio(videoAspect, matchH),
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
                    val sub = subStyle
                    it.subtitleView?.let { sv ->
                        if (sub == null) {
                            sv.visibility = View.VISIBLE
                            sv.translationY = if (PipCtl.inPip) 0f else -(14 * it.resources.displayMetrics.density)
                        } else {
                            sv.visibility = View.GONE
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (subStyle != null || enhSession != null) {
            AndroidView(
                factory = { ctx -> SubtitleView(ctx) },
                onRelease = onSubViewReleased,
                update = {
                    onSubView(it)
                    val sub = subStyle
                    if (sub != null) {
                        it.setFixedTextSize(TypedValue.COMPLEX_UNIT_PX, sub.first)
                        it.setBottomPaddingFraction(0f)
                        it.translationY = sub.second
                    } else {
                        it.translationY = -(14 * it.resources.displayMetrics.density)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
