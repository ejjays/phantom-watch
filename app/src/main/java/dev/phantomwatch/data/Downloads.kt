package dev.phantomwatch.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

// files phantom downloaded, reported by broadcast on save, separate from Resume (watch history) because entries carry local content:// uri for offline playback
object Downloads {
    private const val PREFS = "phantom_downloads"
    private const val ITEMS = "items"
    private const val MAX = 200

    var rev by mutableIntStateOf(0)
        private set

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun encode(list: List<Recent>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("kind", it.kind)
                    .put("title", it.title)
                    .put("poster", it.poster.orEmpty())
                    .put("s", it.season)
                    .put("e", it.episode)
                    .put("uri", it.localUri.orEmpty())
                    .put("pos", it.positionMs)
                    .put("dur", it.durationMs),
            )
        }
        return arr.toString()
    }

    internal fun decode(raw: String?): List<Recent> = runCatching {
        val arr = JSONArray(raw ?: return emptyList())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val uri = o.optString("uri")
            if (uri.isBlank()) return@mapNotNull null
            val s = o.optString("s")
            val e = o.optString("e")
            Recent(
                kind = o.optString("kind").ifBlank { if (s.isNotBlank()) "tv" else "movie" },
                id = o.optString("id"),
                title = o.optString("title"),
                poster = o.optString("poster").ifBlank { null },
                season = s,
                episode = e,
                positionMs = o.optLong("pos"),
                durationMs = o.optLong("dur"),
                localUri = uri,
            )
        }
    }.getOrDefault(emptyList())

    fun all(ctx: Context): List<Recent> {
        val downloads = decode(prefs(ctx).getString(ITEMS, null))
        // downloads saved before progress tracking have 0/0, player records local playback under same id in Resume so mirror it and let ContinuePoster draw bar
        val watched = Resume.recent(ctx).associateBy { "${it.kind}|${it.id}|${it.season}|${it.episode}" }
        return downloads.map { d ->
            if (d.durationMs > 0) d else {
                val w = watched["${d.kind}|${d.id}|${d.season}|${d.episode}"]
                if (w != null && w.durationMs > 0) {
                    d.copy(positionMs = w.positionMs, durationMs = w.durationMs)
                } else d
            }
        }
    }

    private fun same(a: Recent, id: String) = a.id == id

    @Synchronized
    fun updateProgress(
        ctx: Context,
        id: String,
        season: String,
        episode: String,
        positionMs: Long,
        durationMs: Long,
    ) {
        val p = prefs(ctx)
        val list = decode(p.getString(ITEMS, null)).toMutableList()
        val at = list.indexOfFirst { same(it, id) }
        if (at < 0) return
        val entry = list[at]
        if (entry.season != season || entry.episode != episode) return
        list[at] = entry.copy(positionMs = positionMs, durationMs = durationMs)
        p.edit().putString(ITEMS, encode(list)).apply()
        rev++
    }

    @Synchronized
    fun add(
        ctx: Context,
        id: String,
        title: String,
        poster: String?,
        season: Int,
        episode: Int,
        uri: String,
    ) {
        if (id.isBlank() || uri.isBlank()) return
        val p = prefs(ctx)
        val list = decode(p.getString(ITEMS, null)).toMutableList()
        val at = list.indexOfFirst { same(it, id) }
        val hasSeason = season > 0 && episode > 0
        val entry = Recent(
            kind = if (hasSeason) "tv" else "movie",
            id = id,
            title = title,
            poster = poster,
            season = if (hasSeason) season.toString() else "",
            episode = if (hasSeason) episode.toString() else "",
            positionMs = 0L,
            durationMs = 0L,
            localUri = uri,
        )
        if (at >= 0) list[at] = entry else list.add(0, entry)
        p.edit().putString(ITEMS, encode(list.take(MAX))).apply()
        rev++
    }

    @Synchronized
    fun remove(ctx: Context, id: String) {
        val p = prefs(ctx)
        p.edit()
            .putString(ITEMS, encode(decode(p.getString(ITEMS, null)).filterNot { same(it, id) }))
            .apply()
        rev++
    }

    @Synchronized
    internal fun restoreAt(ctx: Context, entry: Recent, at: Int) {
        val p = prefs(ctx)
        val list = decode(p.getString(ITEMS, null)).toMutableList()
        list.add(at.coerceIn(0, list.size), entry)
        p.edit().putString(ITEMS, encode(list.take(MAX))).apply()
        rev++
    }
}
