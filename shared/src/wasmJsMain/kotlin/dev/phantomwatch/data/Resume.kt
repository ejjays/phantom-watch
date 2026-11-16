package dev.phantomwatch.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import dev.phantomwatch.json.JSONArray
import dev.phantomwatch.json.JSONObject
import kotlinx.browser.localStorage

// phone keeps this in SharedPreferences. browser keeps same document in localStorage, so library
// tab, continue rail and a reload all read one list
object Resume {
    private const val STORE = "phantom_resume"
    private const val MIN_MS = 10_000L
    private const val END_SLACK_MS = 30_000L
    private const val RECENT_MAX = 12

    // the player saves after home has read the list, so the list follows the store not a load once
    var rev by mutableIntStateOf(0)
        private set

    private fun key(kind: String, id: String, season: String, episode: String) =
        "$kind|$id|$season|$episode"

    // a position at the very end reopens the episode from the start
    private fun shouldClear(positionMs: Long, durationMs: Long): Boolean =
        positionMs < MIN_MS || (durationMs > 0 && positionMs >= durationMs - END_SLACK_MS)

    fun startAt(positionMs: Long, durationMs: Long): Long =
        if (shouldClear(positionMs, durationMs)) 0L else positionMs

    private fun read(): JSONObject =
        runCatching { JSONObject(localStorage.getItem(STORE) ?: "{}") }.getOrDefault(JSONObject())

    private fun write(doc: JSONObject) {
        runCatching { localStorage.setItem(STORE, doc.toString()) }
        rev++
    }

    private fun entries(doc: JSONObject): List<Recent> {
        val arr = doc.optJSONArray("recent") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
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
    }

    private fun encode(list: List<Recent>): JSONArray {
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
        return arr
    }

    private fun positions(doc: JSONObject): JSONObject = doc.optJSONObject("pos") ?: JSONObject()

    fun recent(): List<Recent> = entries(read())

    fun position(kind: String, id: String, season: String, episode: String): Long =
        positions(read()).optLong(key(kind, id, season, episode))

    fun save(media: Title, season: String, episode: String, positionMs: Long, durationMs: Long) {
        val doc = read()
        val list = entries(doc)
        val pos = positions(doc)
        val k = key(media.kind, media.id, season, episode)
        // one entry per title, the way the phone merges: the newest episode wins
        val kept = list.filterNot { it.kind == media.kind && it.id == media.id }
        if (shouldClear(positionMs, durationMs)) {
            pos.remove(k)
            doc.put("recent", encode(kept))
            doc.put("pos", pos)
            write(doc)
            return
        }
        val entry = Recent(media.kind, media.id, media.title, media.poster, season, episode, positionMs, durationMs)
        pos.put(k, positionMs)
        doc.put("recent", encode((listOf(entry) + kept).take(RECENT_MAX)))
        doc.put("pos", pos)
        write(doc)
    }

    fun remove(kind: String, id: String, season: String, episode: String) {
        val doc = read()
        val pos = positions(doc)
        pos.remove(key(kind, id, season, episode))
        doc.put("pos", pos)
        doc.put("recent", encode(entries(doc).filterNot {
            it.kind == kind && it.id == id && it.season == season && it.episode == episode
        }))
        write(doc)
    }

    // undo puts the card back where it was, with the position it was showing
    fun restore(list: List<Recent>) {
        val doc = read()
        val pos = positions(doc)
        list.forEach { pos.put(key(it.kind, it.id, it.season, it.episode), it.positionMs) }
        doc.put("pos", pos)
        doc.put("recent", encode(list))
        write(doc)
    }

    fun clear() {
        write(JSONObject())
    }
}
