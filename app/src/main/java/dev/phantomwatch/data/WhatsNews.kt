package dev.phantomwatch.data

import android.content.Context
import dev.phantomwatch.BuildConfig
import org.json.JSONObject

data class WhatsNewEntry(
    val version: String,
    val title: String,
    val highlight: Boolean,
    val body: String = "",
    val bullets: List<String> = emptyList(),
)

object WhatsNews {
    private const val KEY_SEEN = "whatsnew_seen"

    fun load(ctx: Context): List<WhatsNewEntry> = runCatching {
        parse(ctx.assets.open("whatsnew.json").bufferedReader().use { it.readText() })
    }.getOrDefault(emptyList())

    internal fun parse(text: String): List<WhatsNewEntry> = runCatching {
        val arr = JSONObject(text).optJSONArray("entries") ?: return@runCatching emptyList()
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val bullets = o.optJSONArray("bullets")
            WhatsNewEntry(
                version = o.optString("version"),
                title = o.optString("title"),
                highlight = o.optBoolean("highlight"),
                body = o.optString("body"),
                bullets = if (bullets == null) emptyList() else (0 until bullets.length()).map { bullets.optString(it) },
            )
        }
    }.getOrDefault(emptyList())

    fun shouldShow(ctx: Context, entries: List<WhatsNewEntry>): Boolean {
        val seen = Settings.prefs(ctx).getLong(KEY_SEEN, 0L)
        return shouldShow(seen, BuildConfig.VERSION_CODE.toLong(), entries.firstOrNull()?.highlight == true)
    }

    internal fun shouldShow(seen: Long, code: Long, highlight: Boolean): Boolean = seen != code && highlight

    fun markSeen(ctx: Context) {
        Settings.prefs(ctx).edit().putLong(KEY_SEEN, BuildConfig.VERSION_CODE.toLong()).apply()
    }
}
