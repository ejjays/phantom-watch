package dev.phantomwatch.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import dev.phantomwatch.json.JSONObject
import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.events.Event

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(code) => new Intl.DisplayNames(['en'], { type: 'region' }).of(code)")
private external fun regionName(code: String): String

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(url) => { try { const x = new XMLHttpRequest(); x.open('GET', url, false); x.send(); return x.status === 200 ? x.responseText : ''; } catch (e) { return ''; } }")
private external fun geoJson(url: String): String

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(url) => { try { const x = new XMLHttpRequest(); x.open('GET', url, false); x.send(); return x.status === 200 ? x.responseText : ''; } catch (e) { return ''; } }")
private external fun geoText(url: String): String

// async lookup before first composition, edge trace then browser ip apis, never proxy which reports proxy country, then timezone locale, result cached
@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("""(url) => {
  const done = (c) => {
    const code = String(c || '').toUpperCase();
    if (!/^[A-Z]{2}$/.test(code)) return false;
    window.__pwCountry = code;
    try { localStorage.setItem('pw_country', code); } catch (e) {}
    window.dispatchEvent(new Event('pwcountry'));
    return true;
  };
  const saved = (() => { try { return localStorage.getItem('pw_country'); } catch (e) { return ''; } })();
  if (saved && done(saved)) return;
  (async () => {
    try {
      const r = await fetch(url + '/cdn-cgi/trace', { cache: 'no-store' });
      if (r.ok) {
        const m = (await r.text()).match(/(?:^|\n)loc=([A-Z]{2})/);
        if (m && done(m[1])) return;
      }
    } catch (e) {}
    const apis = ['https://ipwho.is/', 'https://ipapi.co/json/', 'https://geojs.io/v1/ip/geo.json'];
    for (const api of apis) {
      let to = 0;
      try {
        const ctl = new AbortController();
        to = setTimeout(() => ctl.abort(), 2500);
        const r = await fetch(api, { signal: ctl.signal, cache: 'no-store' });
        clearTimeout(to);
        if (!r.ok) continue;
        const d = await r.json();
        const c = d.country_code || d.countryCode || (d.country && d.country.code) || '';
        if (done(c)) return;
      } catch (e) { if (to) clearTimeout(to); }
    }
    try {
      const tz = Intl.DateTimeFormat().resolvedOptions().timeZone || '';
      const map = {
        'Asia/Manila': 'PH', 'Asia/Singapore': 'SG', 'Asia/Kuala_Lumpur': 'MY',
        'Asia/Jakarta': 'ID', 'Asia/Tokyo': 'JP', 'Asia/Seoul': 'KR',
        'Asia/Kolkata': 'IN', 'Australia/Sydney': 'AU', 'America/Toronto': 'CA',
        'America/New_York': 'US', 'America/Los_Angeles': 'US', 'Europe/London': 'GB',
      };
      if (done(map[tz])) return;
    } catch (e) {}
    done((navigator.language || '').split('-').pop());
  })();
}""")
private external fun prefetchCountry(url: String)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => window.__pwCountry || ''")
private external fun countryNow(): String

fun startCountryLookup(origin: String) = prefetchCountry(origin)

private fun traceCountry(): String? =
    geoText(Geo.url.trimEnd('/') + "/cdn-cgi/trace")
        .lineSequence()
        .firstOrNull { it.startsWith("loc=") }
        ?.substringAfter("loc=")
        ?.trim()
        ?.uppercase()
        ?.takeIf { it.length == 2 }

private var resolved: Country? = null
private var geo: String? = null

@Composable
actual fun deviceCountry(): Country {
    DisposableEffect(Unit) {
        val onCountry: (Event) -> Unit = {
            val code = countryNow()
            if (code.length == 2) resolved = Country(code, nameOf(code))
        }
        document.addEventListener("pwcountry", onCountry)
        onDispose { document.removeEventListener("pwcountry", onCountry) }
    }
    Geo.overrideCode.takeIf { it.length == 2 }?.let { return Country(it, nameOf(it)) }
    resolved?.let { return it }
    // sync fallbacks so first composition still has best guess
    val ip = countryNow().takeIf { it.length == 2 }
        ?: traceCountry()
        ?: runCatching {
            JSONObject(geo ?: geoJson(Geo.url.trimEnd('/') + "/geo").also { geo = it }).optString("c")
        }.getOrNull()?.uppercase()?.takeIf { it.length == 2 }
    val code = ip ?: window.navigator.language.substringAfter('-', "").uppercase()
    val country = if (code.length != 2) Country("", "") else Country(code, nameOf(code))
    if (ip != null) resolved = country
    return country
}

private fun nameOf(code: String): String {
    val name = runCatching { regionName(code) }.getOrNull()?.takeIf { it.isNotBlank() } ?: code
    return countryName(code, name)
}
