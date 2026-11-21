package dev.phantomwatch.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.phantomwatch.data.Chart
import dev.phantomwatch.data.TmdbHost
import dev.phantomwatch.ui.Geo
import dev.phantomwatch.ui.startCountryLookup
import kotlinx.browser.window

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    TmdbHost.key = TMDB_KEY
    // tools/serve.py answers /top10 and /geo on this origin, anywhere else resolver does, because
    // static host has no route for either, rows would fall back to guesses
    val host = window.location.hostname
    val backend = if (host == "localhost" || host == "127.0.0.1") window.location.origin else PW_URL
    Chart.url = backend
    // country from visitor edge, Cloudflare Pages exposes /cdn-cgi/trace, resolver /geo sees proxy ip and reports datacenter country so never ask it
    Geo.url = window.location.origin
    startCountryLookup(window.location.origin)
    ComposeViewport(viewportContainerId = "app") {
        WebApp()
    }
}
