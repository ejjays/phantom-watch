package dev.phantomwatch.data

import dev.phantomwatch.core.AppLog
import dev.phantomwatch.core.Http
import dev.phantomwatch.core.RemoteConfig
import java.net.URLEncoder

object Search {
    fun titles(term: String): List<Title> {
        val q = term.trim()
        if (q.length < 2) return emptyList()
        val url = "${RemoteConfig.tmdbBase()}/search/multi?api_key=${RemoteConfig.tmdbKey()}" +
            "&query=${URLEncoder.encode(q, "UTF-8")}"
        val data = Http.getJson(url, "https://www.themoviedb.org/") ?: return emptyList()
        val results = data.optJSONArray("results") ?: return emptyList()
        val out = mutableListOf<Title>()
        for (i in 0 until results.length()) {
            Tmdb.titleOf(results.optJSONObject(i) ?: continue)?.let { out.add(it) }
        }
        AppLog.d("search \"$q\" ${out.size}")
        return out
    }
}
