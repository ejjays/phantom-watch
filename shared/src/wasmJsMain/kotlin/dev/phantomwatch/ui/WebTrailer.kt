package dev.phantomwatch.ui

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import coil3.compose.AsyncImage
import kotlinx.browser.document
import kotlinx.coroutines.delay
import kotlin.js.toJsString
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLIFrameElement
import org.w3c.dom.HTMLElement

private const val BUTTON_PX = 32
private const val BUTTON_INSET = 12

@Composable
fun WebTrailer(videoId: String?, poster: String?, modifier: Modifier = Modifier) {
    Box(modifier) {
        if (videoId != null) {
            TrailerFrame(videoId, Modifier.fillMaxSize())
        } else if (poster != null) {
            AsyncImage(model = poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@Composable
private fun TrailerFrame(videoId: String, modifier: Modifier) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    // sound on by default; the URL still says mute=1 so autoplay is never refused outright
    var muted by remember(videoId) { mutableStateOf(false) }
    var loaded by remember(videoId) { mutableStateOf(false) }
    // Compose lays out in device pixels; the DOM lives in CSS pixels
    val scale = LocalDensity.current.density.takeIf { it > 0f } ?: 1f
    val frame = remember(videoId) { iframe() }
    val button = remember(videoId) { muteButton() }

    DisposableEffect(frame, button) {
        document.body?.appendChild(frame)
        document.body?.appendChild(button)
        onDispose {
            frame.parentNode?.removeChild(frame)
            button.parentNode?.removeChild(button)
        }
    }
    DisposableEffect(button) {
        val onClick: (org.w3c.dom.events.Event) -> Unit = { muted = !muted }
        button.addEventListener("click", onClick)
        onDispose { button.removeEventListener("click", onClick) }
    }
    DisposableEffect(frame) {
        val onLoad: (org.w3c.dom.events.Event) -> Unit = { loaded = true }
        frame.addEventListener("load", onLoad)
        onDispose { frame.removeEventListener("load", onLoad) }
    }
    LaunchedEffect(bounds, scale) {
        val w = bounds.width / scale
        val left = bounds.left / scale
        val top = bounds.top / scale
        val s = frame.style
        s.left = "${left.toInt()}px"
        s.top = "${top.toInt()}px"
        s.width = "${w.toInt()}px"
        s.height = "${(bounds.height / scale).toInt()}px"
        val b = button.style
        b.left = "${(left + w - BUTTON_INSET - BUTTON_PX).toInt()}px"
        b.top = "${(top + BUTTON_INSET).toInt()}px"
        // load only once it has a size: an iframe that starts hidden never begins autoplaying
        if (w > 0f) {
            s.visibility = "visible"
            b.visibility = "visible"
            if (frame.getAttribute("src").isNullOrBlank()) frame.src = embedUrl(videoId)
        } else {
            s.visibility = "hidden"
            b.visibility = "hidden"
        }
    }
    LaunchedEffect(muted) {
        button.innerHTML = if (muted) ICON_MUTED else ICON_SOUND
        button.title = if (muted) "Unmute" else "Mute"
    }
    // no ready callback without the full iframe API, so the same command is repeated after load
    LaunchedEffect(muted, loaded) {
        if (!loaded) return@LaunchedEffect
        val cmd = command(if (muted) "mute" else "unMute").toJsString()
        repeat(3) {
            frame.contentWindow?.postMessage(cmd, "*")
            delay(800)
        }
    }
    Box(modifier.onGloballyPositioned { bounds = it.boundsInWindow() })
}

private fun embedUrl(videoId: String) = "https://www.youtube.com/embed/$videoId" +
    "?autoplay=1&mute=1&controls=0&loop=1&playlist=$videoId" +
    "&playsinline=1&modestbranding=1&rel=0&iv_load_policy=3&enablejsapi=1"

private fun command(name: String) = """{"event":"command","func":"$name","args":""}"""

private fun iframe(): HTMLIFrameElement {
    val el = document.createElement("iframe") as HTMLIFrameElement
    el.setAttribute("allow", "autoplay; encrypted-media; picture-in-picture")
    el.setAttribute("frameborder", "0")
    val s = (el as HTMLElement).style
    s.position = "fixed"
    s.border = "0"
    s.background = "#000"
    s.borderRadius = "24px"
    s.visibility = "hidden"
    s.zIndex = "1"
    return el
}

private fun muteButton(): HTMLButtonElement {
    val el = document.createElement("button") as HTMLButtonElement
    el.type = "button"
    val s = (el as HTMLElement).style
    s.position = "fixed"
    s.width = "${BUTTON_PX}px"
    s.height = "${BUTTON_PX}px"
    s.border = "0"
    s.padding = "0"
    s.borderRadius = "50%"
    s.background = "rgba(0,0,0,0.45)"
    s.color = "#fff"
    s.display = "flex"
    s.alignItems = "center"
    s.justifyContent = "center"
    s.cursor = "pointer"
    s.visibility = "hidden"
    s.zIndex = "2"
    return el
}

private const val ICON_SOUND =
    """<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M4 9.5h3L12 5.5v13L7 14.5H4z" fill="currentColor" stroke="none"/><path d="M16 8.6a4.6 4.6 0 010 6.8"/><path d="M18.6 6a8 8 0 010 12"/></svg>"""

private const val ICON_MUTED =
    """<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M4 9.5h3L12 5.5v13L7 14.5H4z" fill="currentColor" stroke="none"/><path d="M16.5 9.5l5 5"/><path d="M21.5 9.5l-5 5"/></svg>"""
