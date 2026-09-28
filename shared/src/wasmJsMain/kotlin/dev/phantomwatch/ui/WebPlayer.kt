package dev.phantomwatch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import kotlinx.browser.document
import org.w3c.dom.HTMLVideoElement
import org.w3c.dom.events.Event

/**
 * Web player. The phone runs media3 on a SurfaceView; a browser gets a <video> element parked
 * under the same composable rect. Chrome, gestures and keys live in spike/web/player.js because
 * the element paints above the Compose canvas.
 */
@Composable
fun WebPlayer(sources: List<PlaySource>, subs: List<SubSource>, title: String, onBack: () -> Unit) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val scale = LocalDensity.current.density.takeIf { it > 0f } ?: 1f
    val video = remember { video() }
    val back by rememberUpdatedState(onBack)

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
    LaunchedEffect(bounds, scale) {
        val s = video.style
        s.left = "${(bounds.left / scale).toInt()}px"
        s.top = "${(bounds.top / scale).toInt()}px"
        s.width = "${(bounds.width / scale).toInt()}px"
        s.height = "${(bounds.height / scale).toInt()}px"
        val ready = bounds.width > 0f
        s.visibility = if (ready) "visible" else "hidden"
        if (ready) attach(video, sources, subs)
    }
    Box(Modifier.fillMaxSize().background(Color.Black).onGloballyPositioned { bounds = it.boundsInWindow() })
}

// Provided by spike/web/player.js.
private external fun pwAttach(v: HTMLVideoElement, sourcesJson: String)

private external fun pwDetach(v: HTMLVideoElement)

private external fun pwTitle(v: HTMLVideoElement, title: String)

private var attached: String? = null

private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

// The list is the fallback chain: player.js moves to the next source on a fatal error.
// hls.js only ever sees HLS; a direct MP4 through it is a fatal error, not a fallback.
// One JSON document for both lists: player.js keeps the format list for the quality menu and
// fetches the subtitle track itself (the proxy already serves it with CORS).
private fun attach(v: HTMLVideoElement, sources: List<PlaySource>, subs: List<SubSource>) {
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
        append("]}")
    }
    if (attached == json) return
    attached = json
    pwAttach(v, json)
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
