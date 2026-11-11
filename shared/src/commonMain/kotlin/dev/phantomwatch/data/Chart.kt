package dev.phantomwatch.data

import dev.phantomwatch.core.httpJson

// the netflix chart is resolved by the worker; url stays blank on a build without one
object Chart {
    var url: String = ""
    var key: String = ""

    suspend fun top(apiKey: String, country: String, kind: String): List<Title>? {
        val base = url.trimEnd('/')
        if (base.isEmpty() || country.length != 2) return null
        return try {
            val data = httpJson("$base/top10?k=$key&c=$country&kind=$kind&t=$apiKey", "https://www.netflix.com/") ?: return null
            val arr = data.optJSONArray("results") ?: return null
            val out = mutableListOf<Title>()
            for (i in 0 until arr.length()) {
                Tmdb.titleOf(arr.optJSONObject(i) ?: continue)?.let { out.add(it) }
            }
            out.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }
}
