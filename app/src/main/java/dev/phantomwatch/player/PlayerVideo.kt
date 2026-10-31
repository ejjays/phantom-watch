package dev.phantomwatch.player

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.google.android.gms.media.effect.enhancement.EnhancementSession
import dev.phantomwatch.core.AppLog
import dev.phantomwatch.ui.AmbientGlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

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
    // a 16:9 encode often pads a wider picture with real black rows; measure them once and crop
    // so the picture fills the stage instead of sitting inside its own bars. the view exists long
    // before the first frame does: the resolve alone can take 15s
    var padTarget by remember { mutableStateOf(0f) }
    val pad by animateFloatAsState(padTarget, tween(320), label = "pad")
    LaunchedEffect(videoSurface) {
        val sv = videoSurface ?: return@LaunchedEffect
        repeat(60) {
            delay(500)
            val f = frameBars(sv) ?: return@repeat
            padTarget = f
            AppLog.d("video pad ${(f * 100).toInt()}%")
            return@LaunchedEffect
        }
        AppLog.d("video pad none")
    }
    // A trip to the background kills the view's surface; without a rebind the picture stays black
    // while audio and subtitles keep running.
    val ctx = LocalContext.current
    var resumeTick by remember { mutableStateOf(0) }
    val bound = remember { intArrayOf(0) }
    DisposableEffect(ctx) {
        val lc = (ctx as? ComponentActivity)?.lifecycle ?: return@DisposableEffect onDispose { }
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) resumeTick++ }
        lc.addObserver(obs)
        onDispose { lc.removeObserver(obs) }
    }
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // a raw surface scales the buffer to its bounds, so only the PlayerView path may crop
        val aspect = if (pad > 0f && enhSession == null) videoAspect / (1f - pad) else videoAspect
        val vh = minOf(maxVideoH?.coerceAtMost(maxHeight) ?: maxHeight, maxWidth / aspect)
        val vw = vh * aspect
        // cropped picture leaves a band exactly where the bars were; glowing it reads as a stray bar
        if (pad <= 0f) AmbientGlow(videoSurface, aspect, Modifier.fillMaxSize())
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
                    it.resizeMode = if (pad > 0f) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                    videoSurface = it.videoSurfaceView as? SurfaceView
                    it.subtitleView?.visibility = View.GONE
                    if (resumeTick != bound[0]) {
                        bound[0] = resumeTick
                        if (resumeTick > 0) {
                            (it.videoSurfaceView as? SurfaceView)?.let { sv -> exo.setVideoSurfaceView(sv) }
                        }
                    }
                },
                onRelease = { it.player = null },
                modifier = Modifier.size(vw, vh),
            )
        }
        // a real track always wins; AI only fills in when the provider ships none, unless the
        // user forced it
        val ai = subCues.ifEmpty { AiSubtitles.text }
        SubtitleLayer(ai, subStyle, subTopPx)
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
        val shift = style?.second ?: if (Pip.inPip) 0f else -with(d) { 14.dp.toPx() }
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

// rows that are black across the whole frame are padding, not picture. a 2.39 film in a 16:9
// encode carries about 13% of it top and bottom, and the two sides have to match
internal fun barFraction(px: IntArray, w: Int, h: Int): Float? {
    var top = -1
    var bottom = -1
    for (y in 0 until h) {
        var lit = false
        for (x in 0 until w) {
            val p = px[y * w + x]
            if (((p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF)) / 3 > 12) {
                lit = true
                break
            }
        }
        if (lit) {
            if (top < 0) top = y
            bottom = y
        }
    }
    if (top < 0) return null
    val bars = (top + (h - 1 - bottom)).toFloat() / h
    val skew = kotlin.math.abs(top - (h - 1 - bottom)).toFloat() / h
    return if (bars >= 0.04f && bars <= 0.4f && skew <= 0.02f) bars else 0f
}

private suspend fun frameBars(sv: SurfaceView): Float? {
    if (!sv.holder.surface.isValid) return null
    return try {
        suspendCancellableCoroutine { c ->
            val w = 48
            val h = 48
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            PixelCopy.request(
                sv,
                bmp,
                { r ->
                    if (c.isActive) {
                        if (r != PixelCopy.SUCCESS) {
                            c.resume(null)
                        } else {
                            val px = IntArray(w * h)
                            bmp.getPixels(px, 0, w, 0, 0, w, h)
                            c.resume(barFraction(px, w, h))
                        }
                    }
                },
                Handler(Looper.getMainLooper()),
            )
        }
    } catch (_: Exception) {
        null
    }
}
