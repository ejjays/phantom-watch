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

@Composable
actual fun deviceCountry(): Country {
    resolved?.let { return it }
    // a browser has no sim, so the network's ip is the only place the country really comes from
    val ip = runCatching { JSONObject(geoJson()).optString("c") }.getOrNull()
        ?.uppercase()?.takeIf { it.length == 2 }
    val code = ip ?: window.navigator.language.substringAfter('-', "").uppercase()
    val country = if (code.length != 2) Country("", "") else Country(code, nameOf(code))
    resolved = country
    return country
}

private fun nameOf(code: String): String {
    val name = runCatching { regionName(code) }.getOrNull()?.takeIf { it.isNotBlank() } ?: code
    return countryName(code, name)
}
