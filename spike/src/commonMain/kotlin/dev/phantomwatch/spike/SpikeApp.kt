package dev.phantomwatch.spike

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.phantomwatch.ui.BrowseState
import dev.phantomwatch.ui.DetailScreen
import dev.phantomwatch.ui.HomeScreen
import dev.phantomwatch.ui.WideDetail

@Composable
fun SpikeApp() {
    val browse = remember { BrowseState() }
    MaterialTheme(colorScheme = darkColorScheme()) {
        HomeScreen(
            browse = browse,
            onWatch = { _, _, _ -> },
            detailPane = { item, onClose, onWatch, startEpisodes ->
                if (item != null) DetailScreen(
                    item, onClose, onWatch, startEpisodes,
                    wideDetail = { a ->
                        WideDetail(
                            a.item, a.d, a.pending, a.photosReady, a.checking,
                            a.onClose, a.onWatch, a.onWatchPress, a.onViewAll,
                        )
                    },
                )
            },
        )
    }
}
