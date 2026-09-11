package dev.phantomwatch.spike

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.phantomwatch.data.TmdbHost

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    TmdbHost.key = TMDB_KEY
    ComposeViewport(viewportContainerId = "app") {
        SpikeApp()
    }
}
