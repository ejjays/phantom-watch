package dev.phantomwatch.player

import dev.phantomwatch.core.AppLog
import dev.phantomwatch.core.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

// AniSkip keys on MAL episode + runtime, so it fits any provider's cut of the same episode
object SkipTimes {
    internal fun windowOf(json: JSONObject): LongRange? {
        val results = json.optJSONArray("results") ?: return null
        for (i in 0 until results.length()) {
            val r = results.optJSONObject(i) ?: continue
            val type = r.optString("skipType")
            if (type != "op" && type != "mixed-op") continue
            val iv = r.optJSONObject("interval") ?: continue
            val start = (iv.optDouble("startTime") * 1000).toLong()
            val end = (iv.optDouble("endTime") * 1000).toLong()
            if (end > start) return start..end
        }
        return null
    }

    suspend fun fallback(tmdbId: String, episode: String, durationMs: Long): LongRange? = withContext(Dispatchers.IO) {
        if (durationMs < 60_000) return@withContext null
        val mal = runCatching {
            Http.getJson("https://api.ani.zip/mappings?themoviedb_id=$tmdbId", "https://api.ani.zip/")
                ?.optJSONObject("mappings")?.optInt("mal_id") ?: 0
        }.getOrDefault(0)
        if (mal <= 0) return@withContext null
        val window = runCatching {
            Http.getJson(
                "https://api.aniskip.com/v2/skip-times/$mal/$episode?episodeLength=${durationMs / 1000}&types[]=op&types[]=mixed-op",
                "https://api.aniskip.com/",
            )?.let(::windowOf)
        }.getOrNull()
        if (window != null) AppLog.d("aniskip mal=$mal ep=$episode ${window.first / 1000}-${window.last / 1000}s")
        window
    }
}
