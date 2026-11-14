package dev.phantomwatch.core

import android.content.Context
import android.os.Build
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

object UpdateChecker {
    data class Release(val tag: String, val code: Long, val url: String, val size: Long)

    private const val API = "https://api.github.com/repos/ejjays/phantom-watch/releases?per_page=10"
    private const val PREFS = "phantom_update"
    private const val EVERY_MS = 6 * 60 * 60 * 1000L
    private val DIGITS = Regex("""\d+""")

    fun installedCode(context: Context): Long = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }.getOrDefault(0L)

    fun cached(context: Context): Release? = runCatching {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val tag = prefs.getString("tag", null) ?: return null
        val url = prefs.getString("url", null) ?: return null
        Release(tag, prefs.getLong("code", 0), url, prefs.getLong("size", 0))
    }.getOrNull()

    fun available(context: Context): Release? =
        cached(context)?.takeIf { it.code > installedCode(context) }

    fun refresh(context: Context, force: Boolean = false): Release? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong("checked", 0) < EVERY_MS) return cached(context)
        return runCatching {
            val req = Request.Builder().url(API).header("Accept", "application/vnd.github+json").build()
            Http.client.newCall(req).execute().use { res ->
                if (res.code != 200) error("github ${res.code}")
                val release = parse(res.body?.string().orEmpty())
                val edit = prefs.edit().putLong("checked", now)
                if (release == null) {
                    edit.remove("tag").remove("url")
                } else {
                    edit.putString("tag", release.tag).putLong("code", release.code)
                        .putString("url", release.url).putLong("size", release.size)
                }
                edit.apply()
                AppLog.d("update check ${release?.tag ?: "none"} code=${release?.code ?: 0}")
                release
            }
        }.getOrElse {
            AppLog.d("update check failed ${it.javaClass.simpleName}")
            cached(context)
        }
    }

    // api order not reliable, highest build number wins not first entry
    internal fun parse(body: String): Release? {
        val releases = JSONArray(body)
        var best: Release? = null
        for (i in 0 until releases.length()) {
            val release = releases.optJSONObject(i)?.let(::releaseOf) ?: continue
            if (best == null || release.code > best.code) best = release
        }
        return best
    }

    private fun releaseOf(entry: JSONObject): Release? {
        if (entry.optBoolean("draft")) return null
        val assets = entry.optJSONArray("assets") ?: return null
        for (j in 0 until assets.length()) {
            val asset = assets.optJSONObject(j) ?: continue
            val url = asset.optString("browser_download_url")
            if (!url.startsWith("https://github.com/")) continue
            val name = asset.optString("name")
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            // build number sits in file name but bare app-release.apk is normal
            // to upload so tag is fallback before giving up on release
            val code = codeOf(name).takeIf { it > 0 } ?: codeOf(entry.optString("tag_name"))
            if (code <= 0) continue
            return Release(entry.optString("tag_name"), code, url, asset.optLong("size"))
        }
        return null
    }

    // largest number wins, 1.0.374.apk is build 374 not 1
    private fun codeOf(text: String): Long =
        DIGITS.findAll(text).maxOfOrNull { it.value.toLongOrNull() ?: 0L } ?: 0L
}
