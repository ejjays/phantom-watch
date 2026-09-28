package dev.phantomwatch.core

import dev.phantomwatch.json.JSONObject
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType

data class PlaySource(val url: String, val label: String, val h: Int, val hls: Boolean)

data class SubSource(val label: String, val url: String)

data class Resolved(val formats: List<PlaySource>, val subs: List<SubSource>)

// Web only: a browser cannot send the Referer/UA the provider CDNs demand, so a server
// resolves and relays. Android keeps its native resolver.
suspend fun resolveSources(
    server: String, key: String,
    kind: String, id: String, season: String = "", episode: String = "",
): Resolved {
    val req = JSONObject()
        .put("kind", kind).put("id", id).put("season", season).put("episode", episode)
    val res = client.post("$server/resolve") {
        header("x-pw-key", key)
        contentType(ContentType.Application.Json)
        setBody(req.toString())
    }
    if (res.status.value != 200) throw RuntimeException("resolve http ${res.status.value}")
    val body = JSONObject(res.bodyAsText())

    val fa = body.optJSONArray("formats")
    val formats = (0 until (fa?.length() ?: 0)).mapNotNull { i ->
        val f = fa!!.optJSONObject(i) ?: return@mapNotNull null
        val url = f.optString("url")
        if (url.isEmpty()) null
        else PlaySource(url, f.optString("label"), f.optInt("h"), f.optString("hls") == "true")
    }
    val sa = body.optJSONArray("subs")
    val subs = (0 until (sa?.length() ?: 0)).mapNotNull { i ->
        val s = sa!!.optJSONObject(i) ?: return@mapNotNull null
        val url = s.optString("url")
        if (url.isEmpty()) null
        else SubSource(s.optString("label").ifEmpty { "Subtitles" }, url)
    }
    return Resolved(formats, subs)
}
