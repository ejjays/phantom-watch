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
import coil3.compose.AsyncImage
import kotlinx.browser.document
import org.w3c.dom.HTMLIFrameElement
import org.w3c.dom.HTMLElement

/**
 * Autoplaying YouTube trailer over the poster.
 *
 * The phone plays a resolved stream through ExoPlayer; a browser cannot, because googlevideo
 * refuses cross-origin playback. So this drops a YouTube iframe on top of the canvas instead and
 * keeps it in sync with the composable's window rect.
 */
@Composable
fun WebTrailer(videoId: String?, poster: String?, modifier: Modifier = Modifier) {
    Box(modifier) {
        if (poster != null) {
            AsyncImage(model = poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (videoId != null) TrailerFrame(videoId, Modifier.fillMaxSize())
    }
}

@Composable
private fun TrailerFrame(videoId: String, modifier: Modifier) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val frame = remember(videoId) { iframe(videoId) }

    DisposableEffect(frame) {
        document.body?.appendChild(frame)
        onDispose { frame.parentNode?.removeChild(frame) }
    }
    LaunchedEffect(bounds) {
        val s = frame.style
        s.left = "${bounds.left.toInt()}px"
        s.top = "${bounds.top.toInt()}px"
        s.width = "${bounds.width.toInt()}px"
        s.height = "${bounds.height.toInt()}px"
        s.visibility = if (bounds.width > 0f) "visible" else "hidden"
    }
    Box(modifier.onGloballyPositioned { bounds = it.boundsInWindow() })
}

private fun iframe(videoId: String): HTMLIFrameElement {
    val el = document.createElement("iframe") as HTMLIFrameElement
    el.src = "https://www.youtube.com/embed/$videoId" +
        "?autoplay=1&mute=1&controls=0&loop=1&playlist=$videoId" +
        "&playsinline=1&modestbranding=1&rel=0&iv_load_policy=3"
    el.setAttribute("allow", "autoplay; encrypted-media; picture-in-picture")
    el.setAttribute("frameborder", "0")
    val s = (el as HTMLElement).style
    s.position = "fixed"
    s.border = "0"
    s.background = "#000"
    // matches the 24dp card radius in WideDetail
    s.borderRadius = "24px"
    s.setProperty("pointer-events", "none")
    s.visibility = "hidden"
    s.zIndex = "1"
    return el
}
