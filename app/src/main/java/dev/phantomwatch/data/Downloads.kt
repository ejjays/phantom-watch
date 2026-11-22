package dev.phantomwatch.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

// files phantom downloaded, reported by broadcast on save, separate from Resume (watch history) because entries carry local content:// uri for offline playback
object Downloads {
    private const val PREFS = "phantom_downloads"
    private const val ITEMS = "items"
    private const val HANDOFF = "handoff"
    private const val MAX = 200
    private const val PHANTOM_PREFIX = "Phantom Watch"

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

    internal fun phantomDownload(name: String): Triple<String, Int, Int>? {
        if (!name.startsWith(PHANTOM_PREFIX, ignoreCase = true)) return null
        val stem = name.substringBeforeLast('.')
        val ep = Regex("""\sS(\d+)E(\d+)$""").find(stem)
        val title = (ep?.let { stem.removeRange(it.range) } ?: stem)
            .removePrefix("$PHANTOM_PREFIX - ")
            .removePrefix(PHANTOM_PREFIX)
            .trim().ifBlank { return null }
        return Triple(
            title,
            ep?.groupValues?.get(1)?.toIntOrNull() ?: 0,
            ep?.groupValues?.get(2)?.toIntOrNull() ?: 0,
        )
    }

    private fun normalize(title: String): String =
        title.lowercase().replace(Regex("[^a-z0-9]"), "")

    @Synchronized
    fun noteHandoff(ctx: Context, job: String) {
        val o = runCatching { JSONObject(job) }.getOrNull() ?: return
        val poster = o.optString("poster").ifBlank { return }
        val entry = JSONObject()
            .put("t", o.optString("title"))
            .put("p", poster)
        val p = prefs(ctx)
        val arr = runCatching { JSONArray(p.getString(HANDOFF, "[]")) }.getOrDefault(JSONArray())
        arr.put(entry)
        while (arr.length() > 50) arr.remove(0)
        p.edit().putString(HANDOFF, arr.toString()).apply()
    }

    private fun posterMap(ctx: Context): Map<String, String> {
        val out = HashMap<String, String>()
        runCatching { JSONArray(prefs(ctx).getString(HANDOFF, "[]")) }.getOrNull()?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val title = o.optString("t")
                val poster = o.optString("p")
                if (title.isNotBlank() && poster.isNotBlank()) out[normalize(title)] = poster
            }
        }
        Resume.recent(ctx).forEach { r ->
            val poster = r.poster ?: return@forEach
            out.putIfAbsent(normalize(r.title), poster)
        }
        return out
    }

    private fun thumbnail(ctx: Context, videoId: Long): String? {
        if (Build.VERSION.SDK_INT < 29) return null
        val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videoId)
        val bitmap = runCatching {
            ctx.contentResolver.loadThumbnail(uri, Size(512, 512), null)
        }.getOrNull() ?: return null
        val dir = File(ctx.filesDir, "thumbs").apply { mkdirs() }
        val file = File(dir, "$videoId.jpg")
        runCatching {
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        }.onFailure { return null }
        bitmap.recycle()
        return Uri.fromFile(file).toString()
    }

    @Synchronized
    private fun setPoster(ctx: Context, id: String, poster: String) {
        val p = prefs(ctx)
        val list = decode(p.getString(ITEMS, null)).toMutableList()
        val at = list.indexOfFirst { same(it, id) }
        if (at < 0 || list[at].poster == poster) return
        list[at] = list[at].copy(poster = poster)
        p.edit().putString(ITEMS, encode(list)).apply()
        rev++
    }

    fun importPhantomDownloads(ctx: Context) {
        val cursor = runCatching {
            ctx.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME),
                "${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?",
                arrayOf("$PHANTOM_PREFIX%"),
                "${MediaStore.Video.Media.DATE_ADDED} DESC",
            )
        }.getOrNull() ?: return
        val existing = decode(prefs(ctx).getString(ITEMS, null)).associateBy { it.localUri.orEmpty() }
        val posters = posterMap(ctx)
        cursor.use { c ->
            while (c.moveToNext()) importRow(ctx, c, existing, posters)
        }
    }

    private fun importRow(
        ctx: Context,
        c: android.database.Cursor,
        existing: Map<String, Recent>,
        posters: Map<String, String>,
    ) {
        val videoId = c.getLong(c.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
        val uri = ContentUris.withAppendedId(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videoId,
        ).toString()
        val name = c.getString(c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)).orEmpty()
        val parsed = phantomDownload(name) ?: return
        val known = posters[normalize(parsed.first)]
        val prior = existing[uri]
        if (prior == null) {
            add(ctx, "phantom:$uri", parsed.first, known ?: thumbnail(ctx, videoId), parsed.second, parsed.third, uri)
            return
        }
        val priorPoster = prior.poster
        val better = known ?: if (priorPoster == null || priorPoster.startsWith("file:")) {
            thumbnail(ctx, videoId)
        } else null
        if (better != null) setPoster(ctx, prior.id, better)
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
        val at = list.indexOfFirst { same(it, id) || it.localUri == uri }
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
