package dev.phantomwatch.spike

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.phantomwatch.ui.BrowseState
import dev.phantomwatch.ui.DetailScreen
import dev.phantomwatch.ui.HomeScreen
import dev.phantomwatch.ui.WebPlayer
import dev.phantomwatch.ui.WebTrailer
import dev.phantomwatch.ui.WideDetail

// Placeholder until the phone's proxy endpoint can hand out resolved streams.
private const val DEMO_HLS = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"

@Composable
fun SpikeApp() {
    val browse = remember { BrowseState() }
    var playing by remember { mutableStateOf<String?>(null) }
    MaterialTheme(colorScheme = darkColorScheme()) {
        val url = playing
        if (url != null) {
            WebPlayer(url, onBack = { playing = null })
        } else {
            HomeScreen(
                browse = browse,
                onWatch = { _, _, _ -> playing = DEMO_HLS },
                detailPane = { item, onClose, onWatch, startEpisodes ->
                    if (item != null) DetailScreen(
                        item, onClose, onWatch, startEpisodes,
                        wideDetail = { a ->
                            val art = (a.item.backdrop ?: a.item.poster) ?: (a.d.backdrop ?: a.d.poster)
                            WideDetail(
                                a.item, a.d, a.pending, a.photosReady, a.checking,
                                a.onClose, a.onWatch, a.onWatchPress, a.onViewAll,
                                hero = { m -> WebTrailer(a.d.trailer, art, m) },
                            )
                        },
                    )
                },
            )
        }
    }
}
