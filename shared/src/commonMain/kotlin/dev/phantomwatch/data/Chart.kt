package dev.phantomwatch.data

import dev.phantomwatch.core.httpJson

// the netflix chart is resolved by the worker; url stays blank on a build without one
object Chart {
    var url: String = ""
    var key: String = ""

    suspend fun top(apiKey: String, country: String, kind: String): List<Ranked>? {
        val base = url.trimEnd('/')
        if (base.isEmpty() || country.length != 2) return null
        return try {
            val data = httpJson("$base/top10?k=$key&c=$country&kind=$kind&t=$apiKey", "https://www.netflix.com/") ?: return null
            val arr = data.optJSONArray("results") ?: return null
            val out = mutableListOf<Ranked>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                // a title missing from tmdb leaves a hole; fall back to its place in the list
                Tmdb.titleOf(o)?.let { out.add(Ranked(it, o.optInt("rank", i + 1))) }
            }
            out.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }
}

data class Ranked(val title: Title, val rank: Int)
