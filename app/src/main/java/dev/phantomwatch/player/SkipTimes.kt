package dev.phantomwatch.player

import dev.phantomwatch.core.AppLog
import dev.phantomwatch.core.Http
import dev.phantomwatch.data.Stream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.phantomwatch.json.JSONObject

// AniSkip keys on MAL episode + runtime, so it fits any provider's cut of the same episode
object SkipTimes {
    internal fun windowOf(json: JSONObject, types: List<String>): LongRange? {
        val results = json.optJSONArray("results") ?: return null
        for (i in 0 until results.length()) {
            val r = results.optJSONObject(i) ?: continue
            if (r.optString("skipType") !in types) continue
            val iv = r.optJSONObject("interval") ?: continue
            val start = (iv.optDouble("startTime") * 1000).toLong()
            val end = (iv.optDouble("endTime") * 1000).toLong()
            if (end > start) return start..end
        }
        return null
    }

    // fills what the provider did not send: opening from AniSkip, ending from the indexes
    suspend fun fill(s: Stream, tmdbId: String, season: String, episode: String, durationMs: Long): Stream {
        if (durationMs < 60_000) return s
        var out = s
        if (out.intro == null) fallback(tmdbId, episode.ifBlank { "1" }, durationMs)?.let { out = out.copy(intro = it) }
        if (out.outro == null) {
            outro(tmdbId, out.imdb, season.ifBlank { "1" }, episode.ifBlank { "1" }, durationMs)?.let { out = out.copy(outro = it) }
        }
        out.intro?.let { AppLog.d("intro ${it.first / 1000}-${it.last / 1000}s") }
        out.outro?.let { AppLog.d("outro ${it.first / 1000}-${it.last / 1000}s dur=${durationMs / 1000}s") }
        return out
    }

    suspend fun fallback(tmdbId: String, episode: String, durationMs: Long): LongRange? = withContext(Dispatchers.IO) {
        aniSkip(tmdbId, episode, durationMs, "types[]=op&types[]=mixed-op", listOf("op", "mixed-op"))
    }

    // each index is timed on one release, so a window that starts past the end describes another cut
    suspend fun outro(tmdbId: String, imdb: String?, season: String, episode: String, durationMs: Long): LongRange? =
        withContext(Dispatchers.IO) {
            val s = season.toIntOrNull()
            val e = episode.toIntOrNull()
            fun fits(w: LongRange?) = w?.takeIf { it.first in 1 until durationMs }
            val tv = if (s != null && e != null) introDb(imdb, s, e) else null
            val anime = if (fits(tv) == null) aniSkip(tmdbId, episode, durationMs, "types[]=ed", listOf("ed")) else null
            fits(tv) ?: fits(anime) ?: fits(if (s != null && e != null) theIntroDb(imdb, s, e, durationMs) else null)
        }

    // TVDB-mapped credits, and end_ms is usually null, i.e. "runs to the end": the one shape that
    // still lands on the credits when the provider serves a shorter cut
    private suspend fun theIntroDb(imdb: String?, season: Int, episode: Int, durationMs: Long): LongRange? {
        val id = imdb?.removePrefix("tt")?.takeIf { it.isNotEmpty() } ?: return null
        val json = runCatching {
            Http.getJson(
                "https://api.theintrodb.org/v4/media?imdb_id=tt$id&season=$season&episode=$episode",
                "https://theintrodb.org/",
            )
        }.getOrNull() ?: return null
        return creditsWindow(json, durationMs)?.also {
            AppLog.d("theintrodb tt$id s${season}e$episode ${it.first / 1000}-${it.last / 1000}s")
        }
    }

    internal fun creditsWindow(json: JSONObject, durationMs: Long): LongRange? {
        val c = json.optJSONArray("credits")?.optJSONObject(0) ?: return null
        val start = if (c.has("start_ms")) c.optLong("start_ms") else (c.optDouble("start_sec") * 1000).toLong()
        if (start <= 0) return null
        val end = if (c.has("end_ms")) c.optLong("end_ms") else 0L
        val last = if (end > start) minOf(end, durationMs) else durationMs
        return if (start < last) start..last else null
    }

    private suspend fun introDb(imdb: String?, season: Int, episode: Int): LongRange? {
        val id = imdb?.removePrefix("tt")?.takeIf { it.isNotEmpty() } ?: return null
        val json = runCatching {
            Http.getJson(
                "https://api.introdb.app/segments?imdb_id=tt$id&season=$season&episode=$episode",
                "https://introdb.app/",
            )
        }.getOrNull() ?: return null
        val o = json.optJSONObject("outro") ?: return null
        val start = if (o.has("start_ms")) o.optLong("start_ms") else (o.optDouble("start_sec") * 1000).toLong()
        val end = if (o.has("end_ms")) o.optLong("end_ms") else (o.optDouble("end_sec") * 1000).toLong()
        if (end <= start) return null
        AppLog.d("introdb tt$id s${season}e$episode ${start / 1000}-${end / 1000}s")
        return start..end
    }

    private suspend fun aniSkip(
        tmdbId: String,
        episode: String,
        durationMs: Long,
        query: String,
        types: List<String>,
    ): LongRange? {
        if (durationMs < 60_000) return null
        val mal = runCatching {
            Http.getJson("https://api.ani.zip/mappings?themoviedb_id=$tmdbId", "https://api.ani.zip/")
                ?.optJSONObject("mappings")?.optInt("mal_id") ?: 0
        }.getOrDefault(0)
        if (mal <= 0) return null
        val window = runCatching {
            Http.getJson(
                "https://api.aniskip.com/v2/skip-times/$mal/$episode?episodeLength=${durationMs / 1000}&$query",
                "https://api.aniskip.com/",
            )?.let { windowOf(it, types) }
        }.getOrNull()
        if (window != null) AppLog.d("aniskip mal=$mal ep=$episode ${window.first / 1000}-${window.last / 1000}s")
        return window
    }
}
