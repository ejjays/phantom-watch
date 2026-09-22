package dev.phantomwatch.ui

import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import dev.phantomwatch.player.TrailerAudio

/** Tablet detail on the phone: the shared layout, with the trailer and its ambient glow behind. */
@Composable
internal fun TabletDetail(a: WideDetailArgs) {
    val art = (a.item.backdrop ?: a.item.poster) ?: (a.d.backdrop ?: a.d.poster)
    val glow = rememberImageGlow(art)
    var surface by remember(a.item.id) { mutableStateOf<TextureView?>(null) }
    var card by remember(a.item.id) { mutableStateOf(Rect.Zero) }
    val audio = remember(a.item.id) { TrailerAudio() }
    Box(Modifier.fillMaxSize()) {
        DetailAmbient(surface, glow, audio, card, Modifier.matchParentSize())
        WideDetail(
            item = a.item, d = a.d, pending = a.pending, photosReady = a.photosReady, checking = a.checking,
            onClose = a.onClose, onWatch = a.onWatch, onWatchPress = a.onWatchPress, onViewAll = a.onViewAll,
            onArtBounds = { card = it },
            hero = { m ->
                val trailer = a.d.trailer
                if (trailer != null) {
                    TrailerHero(trailer, art, m, { surface = it }, audio)
                } else if (art != null) {
                    AsyncImage(art, contentDescription = null, modifier = m, contentScale = ContentScale.Crop)
                }
            },
        )
    }
}
