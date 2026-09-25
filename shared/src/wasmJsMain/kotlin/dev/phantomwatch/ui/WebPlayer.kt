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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import kotlinx.browser.document
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLVideoElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event

/**
 * Web player. The phone runs media3 on a SurfaceView; a browser gets a <video> element parked
 * under the same composable rect, driven by hls.js when the browser cannot play HLS itself.
 *
 * Native controls stay on for now: the element paints above the canvas, so a Compose-drawn
 * scrubber would sit underneath it.
 */
@Composable
fun WebPlayer(urls: List<String>, onBack: () -> Unit) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val scale = LocalDensity.current.density.takeIf { it > 0f } ?: 1f
    val video = remember { video() }
    val back = remember { backButton() }

    DisposableEffect(Unit) {
        document.body?.appendChild(video)
        document.body?.appendChild(back)
        onDispose {
            detach(video)
            video.parentNode?.removeChild(video)
            back.parentNode?.removeChild(back)
        }
    }
    DisposableEffect(back) {
        val onClick: (Event) -> Unit = { onBack() }
        back.addEventListener("click", onClick)
        onDispose { back.removeEventListener("click", onClick) }
    }
    LaunchedEffect(bounds, scale) {
        val s = video.style
        s.left = "${(bounds.left / scale).toInt()}px"
        s.top = "${(bounds.top / scale).toInt()}px"
        s.width = "${(bounds.width / scale).toInt()}px"
        s.height = "${(bounds.height / scale).toInt()}px"
        val b = back.style
        b.left = "${(bounds.left / scale + 12).toInt()}px"
        b.top = "${(bounds.top / scale + 12).toInt()}px"
        val ready = bounds.width > 0f
        s.visibility = if (ready) "visible" else "hidden"
        b.visibility = if (ready) "visible" else "hidden"
        if (ready) attach(video, urls)
    }
    Box(Modifier.fillMaxSize().background(Color.Black).onGloballyPositioned { bounds = it.boundsInWindow() })
}

// Provided by index.html: hls.js when the browser cannot play HLS on its own.
private external fun pwAttach(v: HTMLVideoElement, urlsJson: String)

private external fun pwDetach(v: HTMLVideoElement)

private var attached: String? = null

// The list is the fallback chain: index.html moves to the next source on a fatal error.
private fun attach(v: HTMLVideoElement, urls: List<String>) {
    if (urls.isEmpty()) return
    val json = urls.joinToString(",", "[", "]") { "\"" + it.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" }
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
    v.setAttribute("controls", "")
    v.style.position = "fixed"
    v.style.background = "#000"
    v.style.objectFit = "contain"
    v.style.visibility = "hidden"
    v.style.zIndex = "1"
    return v
}

private fun backButton(): HTMLButtonElement {
    val b = document.createElement("button") as HTMLButtonElement
    b.type = "button"
    b.title = "Back"
    b.innerHTML =
        """<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M15 19l-7-7 7-7"/></svg>"""
    val s = (b as HTMLElement).style
    s.position = "fixed"
    s.width = "40px"
    s.height = "40px"
    s.border = "0"
    s.padding = "0"
    s.borderRadius = "50%"
    s.background = "rgba(0,0,0,0.55)"
    s.color = "#fff"
    s.display = "flex"
    s.alignItems = "center"
    s.justifyContent = "center"
    s.cursor = "pointer"
    s.visibility = "hidden"
    s.zIndex = "2"
    return b
}
