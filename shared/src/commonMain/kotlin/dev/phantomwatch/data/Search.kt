package dev.phantomwatch.data

import dev.phantomwatch.core.Log
import dev.phantomwatch.core.httpJson
import dev.phantomwatch.core.urlEncode

object Search {
    suspend fun titles(term: String): List<Title> {
        val q = term.trim()
        if (q.length < 2) return emptyList()
        val url = "${TmdbHost.base}/search/multi?api_key=${TmdbHost.key}" +
            "&query=${urlEncode(q)}"
        val data = httpJson(url, "https://www.themoviedb.org/") ?: return emptyList()
        val results = data.optJSONArray("results") ?: return emptyList()
        val out = mutableListOf<Title>()
        for (i in 0 until results.length()) {
            Tmdb.titleOf(results.optJSONObject(i) ?: continue)?.let { out.add(it) }
        }
        Log.d("search \"$q\" ${out.size}")
        return out.distinctBy { it.kind to it.id }
    }
}
