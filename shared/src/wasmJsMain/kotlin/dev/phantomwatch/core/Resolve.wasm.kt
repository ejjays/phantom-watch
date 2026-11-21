package dev.phantomwatch.core

import dev.phantomwatch.json.JSONObject
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class PlaySource(val url: String, val label: String, val h: Int, val hls: Boolean)

data class SubSource(val label: String, val url: String)

data class Resolved(val formats: List<PlaySource>, val subs: List<SubSource>)

private var lastCfg = -1
private var primaryDownUntil = 0.0

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => Date.now()")
private external fun jsNowMs(): Double

// POST through fetch and not the ktor client: a resolver that is down makes fetch reject and js
// engine parks the coroutine on that promise for good, which left player spinning with no way
// out. AbortController also gives the call a deadline that actually fires
@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """(url, key, body, ms, cb) => {
      const c = new AbortController();
      const t = setTimeout(() => c.abort(), ms);
      fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'x-pw-key': key },
        body: body,
        signal: c.signal,
      })
        .then(r => r.text().then(text => cb(JSON.stringify({ s: r.status, b: text }))))
        .catch(e => cb(JSON.stringify({ s: 0, b: '', e: String((e && e.message) || e) })))
        .finally(() => clearTimeout(t));
    }""",
)
private external fun postJson(url: String, key: String, body: String, ms: Int, cb: (String) -> Unit)

private suspend fun post(url: String, key: String, body: String): String =
    suspendCancellableCoroutine { cont ->
        postJson(url, key, body, 20_000) { res ->
            val o = JSONObject(res)
            val status = o.optInt("s")
            val text = o.optString("b")
            val err = o.optString("e")
            if (status == 200) {
                cont.resume(text)
            } else {
                cont.resumeWithException(RuntimeException(if (err.isNotEmpty()) err else "resolve http $status"))
            }
        }
    }

// web only: browser cannot send the Referer/UA provider cdns demand
suspend fun resolveSources(
    server: String, key: String,
    kind: String, id: String, season: String = "", episode: String = "",
    fallback: String = "",
): Resolved {
    val req = JSONObject()
        .put("kind", kind).put("id", id).put("season", season).put("episode", episode)
    val endpoints = if (fallback.isEmpty() || fallback == server) listOf(server) else listOf(server, fallback)
    var last: Exception? = null
    for (s in endpoints) {
        if (s == server && jsNowMs() < primaryDownUntil) continue
        try {
            val body = JSONObject(post("$s/resolve", key, req.toString()))

            val fa = body.optJSONArray("formats")
            val formats = (0 until (fa?.length() ?: 0)).mapNotNull { i ->
                val f = fa!!.optJSONObject(i) ?: return@mapNotNull null
                val url = f.optString("url")
                if (url.isEmpty()) null
                else PlaySource(url, f.optString("label"), f.optInt("h"), f.optString("hls") == "true")
            }
            val sa = body.optJSONArray("subs")
            val subs = (0 until (sa?.length() ?: 0)).mapNotNull { i ->
                val s2 = sa!!.optJSONObject(i) ?: return@mapNotNull null
                val url = s2.optString("url")
                if (url.isEmpty()) null
                else SubSource(s2.optString("label").ifEmpty { "Subtitles" }, url)
            }

            val legs = body.optJSONObject("legs")
            if (legs != null) {
                val it = legs.keys()
                while (it.hasNext()) {
                    val name = it.next()
                    val l = legs.optJSONObject(name) ?: continue
                    val state = when {
                        l.optBoolean("skip") -> "skip"
                        l.optBoolean("ok") -> "ok ${l.optInt("formats")}"
                        else -> "none"
                    }
                    val err = l.optString("err")
                    pwLog("leg", "$name $state ${l.optInt("ms")}ms" + if (err.isEmpty()) "" else " $err")
                }
            }
            val pick = body.optJSONObject("pick")
            val cfgVersion = body.optInt("cfg")
            if (cfgVersion != lastCfg) {
                lastCfg = cfgVersion
                pwLog("cfg", "v$cfgVersion")
            }
            pwLog(
                "resolve",
                "via $s $kind/$id -> ${formats.size} formats, ${subs.size} subs, ${body.optInt("ms")}ms, pick " +
                    (pick?.let { "${it.optString("src")} ${it.optInt("h")}p" } ?: "none"),
            )
            return Resolved(formats, subs)
        } catch (e: Exception) {
            last = e
            if (s == server) primaryDownUntil = jsNowMs() + 15 * 60_000
        }
    }
    throw last ?: RuntimeException("no resolver")
}
