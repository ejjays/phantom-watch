package dev.phantomwatch.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// remembers where each title/episode was left off
object Resume {
    private const val PREFS = "phantom_resume"
    private const val MIN_MS = 10_000L
    private const val END_SLACK_MS = 30_000L
    private const val RECENT = "recent"
    private const val RECENT_MAX = 12

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(kind: String, id: String, season: String, episode: String) =
        "$kind|$id|$season|$episode"

    // a position at the very end would reopen the episode at STATE_ENDED
    internal fun shouldClear(positionMs: Long, durationMs: Long): Boolean =
        positionMs < MIN_MS || (durationMs > 0 && positionMs >= durationMs - END_SLACK_MS)

    internal fun startAt(positionMs: Long, durationMs: Long): Long =
        if (shouldClear(positionMs, durationMs)) 0L else positionMs

    internal fun mergeRecent(list: List<Recent>, entry: Recent): List<Recent> =
        (listOf(entry) + list.filterNot { it.kind == entry.kind && it.id == entry.id }).take(RECENT_MAX)

    internal fun dropRecent(
        list: List<Recent>,
        kind: String,
        id: String,
        season: String,
        episode: String,
    ): List<Recent> =
        list.filterNot { it.kind == kind && it.id == id && it.season == season && it.episode == episode }

    private fun encode(list: List<Recent>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("kind", it.kind)
                    .put("id", it.id)
                    .put("title", it.title)
                    .put("poster", it.poster.orEmpty())
                    .put("s", it.season)
                    .put("e", it.episode)
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
            Recent(
                kind = o.optString("kind"),
                id = o.optString("id"),
                title = o.optString("title"),
                poster = o.optString("poster").ifBlank { null },
                season = o.optString("s"),
                episode = o.optString("e"),
                positionMs = o.optLong("pos"),
                durationMs = o.optLong("dur"),
            )
        }
    }.getOrDefault(emptyList())

    internal fun recent(ctx: Context): List<Recent> =
        decode(prefs(ctx).getString(RECENT, null))

    fun position(ctx: Context, kind: String, id: String, season: String, episode: String): Long =
        prefs(ctx).getLong(key(kind, id, season, episode), 0L)

    @Synchronized
    fun clear(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }

    @Synchronized
    fun remove(ctx: Context, kind: String, id: String, season: String, episode: String) {
        val p = prefs(ctx)
        p.edit()
            .remove(key(kind, id, season, episode))
            .putString(RECENT, encode(dropRecent(decode(p.getString(RECENT, null)), kind, id, season, episode)))
            .apply()
    }

    @Synchronized
    internal fun restore(ctx: Context, list: List<Recent>) {
        val edit = prefs(ctx).edit()
        list.forEach { edit.putLong(key(it.kind, it.id, it.season, it.episode), it.positionMs) }
        edit.putString(RECENT, encode(list)).apply()
    }

    @Synchronized
    fun save(
        ctx: Context,
        media: Title,
        season: String,
        episode: String,
        positionMs: Long,
        durationMs: Long = 0L,
    ) {
        val p = prefs(ctx)
        val k = key(media.kind, media.id, season, episode)
        val old = decode(p.getString(RECENT, null))
        if (shouldClear(positionMs, durationMs)) {
            p.edit()
                .remove(k)
                .putString(RECENT, encode(dropRecent(old, media.kind, media.id, season, episode)))
                .apply()
            return
        }
        val entry = Recent(media.kind, media.id, media.title, media.poster, season, episode, positionMs, durationMs)
        p.edit()
            .putLong(k, positionMs)
            .putString(RECENT, encode(mergeRecent(old, entry)))
            .apply()
    }
}
