package dev.phantomwatch.data

import android.net.Uri
import dev.phantomwatch.core.AppLog
import dev.phantomwatch.core.Http
import java.util.concurrent.TimeUnit
import okhttp3.Request
import org.json.JSONObject

object Search {
    private val ORIGINS = listOf(
        "https://watchluna.gd",
        "https://watchluna.com",
        "https://watchluna.to",
        "https://watchluna.io",
    )

    fun titles(term: String): List<Title> {
        val q = term.trim()
        if (q.length < 2) return emptyList()
        for (origin in ORIGINS) {
            try {
                val req = Request.Builder()
                    .url("$origin/api/search?q=${Uri.encode(q)}")
                    .header("User-Agent", Http.UA)
                    .build()
                val call = Http.client.newCall(req)
                call.timeout().deadline(10, TimeUnit.SECONDS)
                val body = call.execute().use { res ->
                    if (res.code != 200) return@use null
                    res.body?.string()
                } ?: continue
                val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
                val out = mutableListOf<Title>()
                for (i in 0 until results.length()) {
                    Tmdb.titleOf(results.optJSONObject(i) ?: continue)?.let { out.add(it) }
                }
                AppLog.d("search \"$q\" ${out.size} via $origin")
                return out
            } catch (_: Exception) {
                continue
            }
        }
        AppLog.d("search \"$q\" all origins failed")
        return emptyList()
    }
}
