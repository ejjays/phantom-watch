package dev.phantomwatch.core

import dev.phantomwatch.json.JSONObject
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType

data class PlaySource(val url: String, val label: String, val h: Int, val hls: Boolean)

// Web only: a browser cannot send the Referer/UA the provider CDNs demand, so a server
// resolves and relays. Android keeps its native resolver.
suspend fun resolveSources(
    server: String, key: String,
    kind: String, id: String, season: String = "", episode: String = "",
): List<PlaySource> {
    val req = JSONObject()
        .put("kind", kind).put("id", id).put("season", season).put("episode", episode)
    val res = client.post("$server/resolve") {
        header("x-pw-key", key)
        contentType(ContentType.Application.Json)
        setBody(req.toString())
    }
    if (res.status.value != 200) throw RuntimeException("resolve http ${res.status.value}")
    val body = JSONObject(res.bodyAsText())
    val arr = body.optJSONArray("formats") ?: return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        val f = arr.optJSONObject(i) ?: return@mapNotNull null
        val url = f.optString("url")
        if (url.isEmpty()) null
        else PlaySource(url, f.optString("label"), f.optInt("h"), f.optString("hls") == "true")
    }
}
