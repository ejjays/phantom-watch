package dev.phantomwatch.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.phantomwatch.data.Stream
import dev.phantomwatch.data.Sub
import dev.phantomwatch.data.Title
import org.json.JSONArray
import org.json.JSONObject

internal object PhantomDownload {
    private const val PHANTOM = "com.phantom.app"
    private const val ACTION = "com.phantom.app.action.DOWNLOAD_JOB"
    private const val INSTALL_URL = "https://github.com/ejjays/phantom/releases/latest"
    private const val SAVE_SUBDIR = "PhantomWatch"
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Safari/537.36"

    fun isHttpUrl(url: String): Boolean =
        url.startsWith("http://") || url.startsWith("https://")

    fun englishSub(subs: List<Sub>): Sub? = subs.firstOrNull { sub ->
        val label = sub.label.lowercase()
        label.contains("english") || label == "en" || label.startsWith("en:") ||
            label.startsWith("en ") || label.startsWith("eng")
    }

    fun baseName(title: String, season: String, episode: String): String {
        val clean = title.replace(Regex("""[\\/:*?"<>|]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(120)
            .ifBlank { "download" }
        val s = season.toIntOrNull()
        val e = episode.toIntOrNull()
        return if (s != null && e != null) "$clean S%02dE%02d".format(s, e) else clean
    }

    fun build(
        media: Title,
        season: String,
        episode: String,
        stream: Stream,
        headers: Map<String, String>,
        height: Int,
        durationMs: Long,
    ): String? {
        if (!isHttpUrl(stream.url)) return null
        val job = JSONObject()
            .put("v", 1)
            .put("id", "${media.kind}-${media.id}-${System.currentTimeMillis()}")
            .put("url", stream.url)
            .put("title", media.title)
            .put("isHls", stream.isHls || stream.url.contains(".m3u8", ignoreCase = true))
            .put("saveSubdir", SAVE_SUBDIR)
        if (season.isNotBlank()) job.put("season", season)
        if (episode.isNotBlank()) job.put("episode", episode)
        if (height > 0) {
            job.put("height", height)
            job.put("quality", "${height}p")
        }
        if (durationMs > 0) job.put("durationSec", durationMs / 1000)
        media.poster?.takeIf(::isHttpUrl)?.let { job.put("poster", it) }

        val jsonHeaders = JSONObject()
        headers.forEach { (name, value) ->
            if (value.isNotBlank()) jsonHeaders.put(name, value)
        }
        if (headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
            jsonHeaders.put("User-Agent", UA)
        }
        job.put("headers", jsonHeaders)

        englishSub(stream.subs)?.takeIf { isHttpUrl(it.url) }?.let { sub ->
            job.put(
                "subtitles",
                JSONArray().put(
                    JSONObject()
                        .put("url", sub.url)
                        .put("lang", "en")
                        .put("label", sub.label)
                )
            )
        }
        return job.toString()
    }

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PHANTOM, 0)
        true
    } catch (_: Exception) {
        false
    }

    fun send(context: Context, job: String): Boolean = try {
        val intent = Intent(ACTION)
            .setPackage(PHANTOM)
            .putExtra("protocol", 1)
            .putExtra("job", job)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (context.packageManager.resolveActivity(intent, 0) == null) {
            false
        } else {
            context.startActivity(intent)
            true
        }
    } catch (_: Exception) {
        false
    }

    fun openInstall(context: Context) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(INSTALL_URL))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            // no browser; nothing else to do
        }
    }
}
