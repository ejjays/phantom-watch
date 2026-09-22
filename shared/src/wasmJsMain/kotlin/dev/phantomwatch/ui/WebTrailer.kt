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
import org.w3c.dom.HTMLIFrameElement
import org.w3c.dom.HTMLElement

/**
 * Autoplaying YouTube trailer.
 *
 * The phone plays a resolved stream through ExoPlayer; a browser cannot, because googlevideo
 * refuses cross-origin playback. So this drops a YouTube iframe on top of the canvas instead and
 * keeps it in sync with the composable's window rect.
 */
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

@Composable
private fun TrailerFrame(videoId: String, modifier: Modifier) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    // Compose lays out in device pixels; the iframe lives in CSS pixels.
    val scale = LocalDensity.current.density.takeIf { it > 0f } ?: 1f
    val frame = remember(videoId) { iframe() }

    DisposableEffect(frame) {
        document.body?.appendChild(frame)
        onDispose { frame.parentNode?.removeChild(frame) }
    }
    LaunchedEffect(bounds, scale) {
        val s = frame.style
        s.left = "${(bounds.left / scale).toInt()}px"
        s.top = "${(bounds.top / scale).toInt()}px"
        s.width = "${(bounds.width / scale).toInt()}px"
        s.height = "${(bounds.height / scale).toInt()}px"
        // Load only once it has a size: an iframe that starts hidden never begins autoplaying.
        if (bounds.width > 0f) {
            s.visibility = "visible"
            if (frame.getAttribute("src").isNullOrBlank()) frame.src = embedUrl(videoId)
        } else {
            s.visibility = "hidden"
        }
    }
    Box(modifier.onGloballyPositioned { bounds = it.boundsInWindow() })
}

private fun embedUrl(videoId: String) = "https://www.youtube.com/embed/$videoId" +
    "?autoplay=1&mute=1&controls=0&loop=1&playlist=$videoId" +
    "&playsinline=1&modestbranding=1&rel=0&iv_load_policy=3"

private fun iframe(): HTMLIFrameElement {
    val el = document.createElement("iframe") as HTMLIFrameElement
    el.setAttribute("allow", "autoplay; encrypted-media; picture-in-picture")
    el.setAttribute("frameborder", "0")
    val s = (el as HTMLElement).style
    s.position = "fixed"
    s.border = "0"
    s.background = "#000"
    // matches the 24dp card radius in WideDetail
    s.borderRadius = "24px"
    s.visibility = "hidden"
    s.zIndex = "1"
    return el
}
