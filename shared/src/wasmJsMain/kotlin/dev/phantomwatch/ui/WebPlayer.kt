package dev.phantomwatch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import dev.phantomwatch.core.PlaySource
import dev.phantomwatch.core.SubSource
import dev.phantomwatch.core.pwLog
import kotlinx.browser.document
import kotlinx.coroutines.delay
import org.w3c.dom.HTMLVideoElement
import org.w3c.dom.events.Event

// phone runs media3 on SurfaceView, browser gets <video> element parked under same composable rect, chrome gestures keys in spike/web/player.js
@Composable
fun WebPlayer(
    sources: List<PlaySource>,
    subs: List<SubSource>,
    title: String,
    onBack: () -> Unit,
    fullscreen: Boolean = false,
    startMs: Long = 0L,
    scrollOffset: Float = 0f,
    onProgress: (Long, Long) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val scale = LocalDensity.current.density.takeIf { it > 0f } ?: 1f
    val video = remember { video() }
    val back by rememberUpdatedState(onBack)
    val progress by rememberUpdatedState(onProgress)

    // phone saves every ten seconds and on way out, browser same so library tab and continue rail agree with playhead
    LaunchedEffect(title) {
        while (true) {
            delay(5_000)
            report(video, progress)
        }
    }
    DisposableEffect(Unit) {
        onDispose { report(video, progress) }
    }

    DisposableEffect(Unit) {
        document.body?.appendChild(video)
        onDispose {
            detach(video)
            video.parentNode?.removeChild(video)
        }
    }
    DisposableEffect(Unit) {
        val onClick: (Event) -> Unit = { back() }
        document.addEventListener("pwback", onClick)
        onDispose { document.removeEventListener("pwback", onClick) }
    }
    LaunchedEffect(title) { pwTitle(video, title) }
    LaunchedEffect(fullscreen) { pwFullscreen(fullscreen) }
    LaunchedEffect(bounds, scale, sources, subs, title, scrollOffset) {
        val s = video.style
        s.left = "${(bounds.left / scale).toInt()}px"
        // video fixed to window, move it up by pane scroll so it travels with compose content instead of covering it
        s.top = "${(bounds.top / scale - scrollOffset).toInt()}px"
        s.width = "${(bounds.width / scale).toInt()}px"
        s.height = "${(bounds.height / scale).toInt()}px"
        val ready = bounds.width > 0f
        s.visibility = if (ready) "visible" else "hidden"
        if (!ready) return@LaunchedEffect
        // no sources is resolving screen, chrome goes up stalled, PlayerResolving draws and attach fills that stage instead of building second one
        if (sources.isEmpty()) {
            attached = null // retry hands back same list, attach() dedupes on it
            pwStall(video, title)
        } else {
            attach(video, sources, subs, startMs)
        }
        pwBounds(
            bounds.left / scale,
            bounds.top / scale - scrollOffset,
            bounds.width / scale,
            bounds.height / scale,
        )
    }
    Box(modifier.background(Color.Black).onGloballyPositioned { coords ->
        // boundsInWindow() clipped by pane shrinks video and letterboxes it, keep layout full size and real window position so video moves up intact
        val p = coords.boundsInWindow().topLeft
        val s = coords.size
        bounds = Rect(p.x, p.y, p.x + s.width, p.y + s.height)
    })
}

// provided by spike/web/player.js
private external fun pwStall(v: HTMLVideoElement, title: String)

private external fun pwAttach(v: HTMLVideoElement, sourcesJson: String)

private external fun pwDetach(v: HTMLVideoElement)

private external fun pwTitle(v: HTMLVideoElement, title: String)

private external fun pwBounds(left: Float, top: Float, width: Float, height: Float)

private external fun pwFullscreen(on: Boolean)

private var attached: String? = null

private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

// list is fallback chain, player.js moves to next source on fatal error, hls.js only sees HLS, one JSON document for both lists
private fun attach(v: HTMLVideoElement, sources: List<PlaySource>, subs: List<SubSource>, startMs: Long) {
    if (sources.isEmpty()) return
    val json = buildString {
        append("{\"src\":[")
        sources.forEachIndexed { i, s ->
            if (i > 0) append(',')
            append("{\"u\":\"").append(esc(s.url)).append("\",\"h\":").append(s.hls)
                .append(",\"l\":\"").append(esc(s.label)).append("\"}")
        }
        append("],\"subs\":[")
        subs.forEachIndexed { i, s ->
            if (i > 0) append(',')
            append("{\"l\":\"").append(esc(s.label)).append("\",\"u\":\"").append(esc(s.url)).append("\"}")
        }
        append("],\"start\":").append(startMs).append('}')
    }
    if (attached == json) return
    attached = json
    pwLog("player", "attach ${sources.size} sources, ${subs.size} subs at ${startMs / 1000}s")
    pwAttach(v, json)
}

// duration is NaN until first metadata, live stream never settles
private fun report(v: HTMLVideoElement, sink: (Long, Long) -> Unit) {
    val dur = v.duration
    if (!dur.isFinite() || dur <= 0.0) return
    sink((v.currentTime * 1000).toLong(), (dur * 1000).toLong())
}

private fun detach(v: HTMLVideoElement) {
    attached = null
    pwDetach(v)
}

private fun video(): HTMLVideoElement {
    val v = document.createElement("video") as HTMLVideoElement
    v.setAttribute("playsinline", "")
    v.setAttribute("autoplay", "")
    v.style.position = "fixed"
    v.style.background = "#000"
    v.style.objectFit = "contain"
    v.style.visibility = "hidden"
    return v
}
