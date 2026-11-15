package dev.phantomwatch.spike

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.phantomwatch.data.Chart
import dev.phantomwatch.data.TmdbHost
import kotlinx.browser.window

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    TmdbHost.key = TMDB_KEY
    // tools/serve.py answers /top10 on this origin, so no CORS and no worker key here
    Chart.url = window.location.origin
    ComposeViewport(viewportContainerId = "app") {
        SpikeApp()
    }
}
