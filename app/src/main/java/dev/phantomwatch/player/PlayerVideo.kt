package dev.phantomwatch.player

import android.graphics.Bitmap
import android.os.Build
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
    subBgAlpha: Float = 0.80f,
    glow: Boolean = true,
    onEnhSurface: (Surface?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var videoSurface by remember { mutableStateOf<SurfaceView?>(null) }
    // a 16:9 encode often pads a wider picture with real black rows: measure them and crop so the
    // picture fills the stage. the view exists long before the first frame does
    var padTarget by remember { mutableStateOf(0f) }
    val pad by animateFloatAsState(padTarget, tween(320), label = "pad")
    LaunchedEffect(videoSurface) {
        val sv = videoSurface ?: return@LaunchedEffect
        // a second pass runs with the zoom on, reads the cropped view as bar-free and undoes itself
        if (padTarget > 0f) return@LaunchedEffect
        // one frame lies: a fade or a dark shot reads as bars, so a few frames decide together
        val seen = ArrayList<Float>(5)
        repeat(100) {
            delay(400)
            seen += frameBars(sv) ?: return@repeat
            padTarget = seen.sorted()[seen.size / 2]
            if (seen.size < 5) return@repeat
            AppLog.d("video pad ${(padTarget * 100).toInt()}%")
            return@LaunchedEffect
        }
        AppLog.d("video pad none")
    }
    // a trip to the background kills the view's surface: without a rebind the picture stays black
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
        val aspect = if (pad > 0f && enhSession == null) videoAspect / (1f - pad) else videoAspect
        val vh = minOf(maxVideoH?.coerceAtMost(maxHeight) ?: maxHeight, maxWidth / aspect)
        val vw = vh * aspect
        if (glow && pad <= 0f) AmbientGlow(videoSurface, aspect, Modifier.fillMaxSize())
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
                        // before the player: setPlayer auto-shows the controller and hide() animates
                        useController = false
                        this.player = exo
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
                    (it.videoSurfaceView as? SurfaceView)?.let { sv ->
                        if (Build.VERSION.SDK_INT >= 35 && sv.display?.hdrCapabilities?.supportedHdrTypes?.isEmpty() == true) {
                            sv.setDesiredHdrHeadroom(1f)
                        }
                    }
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
        val ai = subCues.ifEmpty { AiSubtitles.text }
        SubtitleLayer(ai, subStyle, subTopPx, subBgAlpha)
    }
}

@Composable
private fun SubtitleLayer(cues: String, style: Pair<Float, Float>?, topPx: Float?, bgAlpha: Float) {
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
                .background(if (bgAlpha > 0f) Color(0xFF080C10).copy(alpha = bgAlpha) else Color.Transparent, shape)
                .padding(
                    horizontal = with(d) { (textPx * 0.59f).toDp() },
                    vertical = with(d) { (textPx * 0.33f).toDp() },
                ),
        )
    }
}

// rows that are black across the whole frame are padding, not picture: a 2.39 film in a 16:9
// encode carries about 13% top and bottom, and the two sides have to match
internal fun barFraction(px: IntArray, w: Int, h: Int): Float? {
    var top = -1
    var bottom = -1
    for (y in 0 until h) {
        var lit = 0
        for (x in 0 until w) {
            val p = px[y * w + x]
            if (((p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF)) / 3 > 12) lit++
        }
        if (lit * 3 >= w) {
            if (top < 0) top = y
            bottom = y
        }
    }
    if (top < 0) return null
    val bars = (top + (h - 1 - bottom)).toFloat() / h
    val skew = kotlin.math.abs(top - (h - 1 - bottom)).toFloat() / h
    // 5%, not 2%: one row of the 48 is already 2.08%, so a single row of asymmetry used to veto
    return if (bars >= 0.04f && bars <= 0.4f && skew <= 0.05f) bars else 0f
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
