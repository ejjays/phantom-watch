package dev.phantomwatch.spike

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.phantomwatch.data.Chart
import dev.phantomwatch.data.TmdbHost
import dev.phantomwatch.ui.Geo
import kotlinx.browser.window

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    TmdbHost.key = TMDB_KEY
    // tools/serve.py answers /top10 and /geo on this origin, anywhere else resolver does, because
    // a static host has no route for either and rows would fall back to guesses
    val host = window.location.hostname
    val backend = if (host == "localhost" || host == "127.0.0.1") window.location.origin else PW_URL
    Chart.url = backend
    Geo.url = backend
    ComposeViewport(viewportContainerId = "app") {
        SpikeApp()
    }
}
