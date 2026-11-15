package dev.phantomwatch.ui

import androidx.compose.runtime.Composable
import dev.phantomwatch.json.JSONObject
import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.browser.window

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(code) => new Intl.DisplayNames(['en'], { type: 'region' }).of(code)")
private external fun regionName(code: String): String

// sync because the feed is built on the first composition; serve.py answers from its own cache
@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => { try { const x = new XMLHttpRequest(); x.open('GET', '/geo', false); x.send(); return x.status === 200 ? x.responseText : ''; } catch (e) { return ''; } }")
private external fun geoJson(): String

private var resolved: Country? = null
private var geo: String? = null

@Composable
actual fun deviceCountry(): Country {
    resolved?.let { return it }
    // a browser has no sim, so the network's ip is the only place the country really comes from
    val ip = runCatching { JSONObject(geo ?: geoJson().also { geo = it }).optString("c") }
        .getOrNull()?.uppercase()?.takeIf { it.length == 2 }
    val code = ip ?: window.navigator.language.substringAfter('-', "").uppercase()
    val country = if (code.length != 2) Country("", "") else Country(code, nameOf(code))
    // only a real answer is worth pinning: a dead proxy must not freeze the locale guess
    if (ip != null) resolved = country
    return country
}

private fun nameOf(code: String): String {
    val name = runCatching { regionName(code) }.getOrNull()?.takeIf { it.isNotBlank() } ?: code
    return countryName(code, name)
}
