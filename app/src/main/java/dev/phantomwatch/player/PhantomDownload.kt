package dev.phantomwatch.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import dev.phantomwatch.R
import dev.phantomwatch.UpdateInstallActivity
import dev.phantomwatch.core.Http
import dev.phantomwatch.core.UpdateChecker
import dev.phantomwatch.data.Downloads
import dev.phantomwatch.data.Stream
import dev.phantomwatch.data.Sub
import dev.phantomwatch.data.Title
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

internal object PhantomDownload {
    private const val PHANTOM = "com.phantom.app"
    private const val ACTION = "com.phantom.app.action.DOWNLOAD_JOB"
    private const val INSTALL_URL = "https://github.com/ejjays/phantom-dl/releases/latest"
    private const val DOWNLOADER_API = "https://api.github.com/repos/ejjays/phantom-dl/releases/latest"
    private const val SAVE_SUBDIR = "PhantomWatch"
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Safari/537.36"
    private var lastJob: String? = null
    private var installedJob: String? = null
    private var installedAt = 0L

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
        formats: List<dev.phantomwatch.provider.VideoFormat> = emptyList(),
    ): String? {
        lastJob = null
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

        // all resolved qualities so phantom picker can offer every format, each with own Referer/Origin headers from same provider
        if (formats.isNotEmpty()) {
            val arr = JSONArray()
            val seen = HashSet<String>()
            for (f in formats) {
                // skip unlabelled streams without resolution, phantom renders those as Auto next to explicit heights
                if (f.label.isBlank() && f.height <= 0) continue
                if (!isHttpUrl(f.url) || !seen.add(f.url)) continue
                val fh = JSONObject()
                val merged = mediaHeaders(f.url, f.headers, f.referer)
                merged.forEach { (name, value) -> if (value.isNotBlank()) fh.put(name, value) }
                arr.put(
                    JSONObject()
                        .put("url", f.url)
                        .put("headers", fh)
                        .put("height", f.height)
                        .put("label", f.label.ifBlank { if (f.height > 0) "${f.height}p" else "Auto" })
                        .put("isHls", f.isHls || f.url.contains(".m3u8", ignoreCase = true))
                )
            }
            if (arr.length() > 0) job.put("formats", arr)
        }

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
        return job.toString().also { lastJob = it }
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
            Downloads.noteHandoff(context, job)
            true
        }
    } catch (_: Exception) {
        false
    }

    fun open(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(PHANTOM)
        if (launch == null) {
            openInstall(context)
        } else {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
        }
    }

    fun openInstall(context: Context) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(INSTALL_URL))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }

    fun install(context: Context, job: String? = lastJob) {
        Thread {
            val release = runCatching {
                val req = Request.Builder().url(DOWNLOADER_API).header("Accept", "application/vnd.github+json").build()
                Http.client.newCall(req).execute().use { res ->
                    if (res.code != 200) error("github ${res.code}")
                    UpdateChecker.releaseOf(JSONObject(res.body?.string().orEmpty()))
                }
            }.getOrNull()
            Handler(Looper.getMainLooper()).post {
                runCatching {
                    if (release == null) {
                        openInstall(context)
                    } else {
                        context.startActivity(
                            Intent(context, UpdateInstallActivity::class.java)
                                .putExtra("url", release.url)
                                .putExtra("size", release.size)
                                .putExtra("packageName", PHANTOM)
                                .putExtra("job", job)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }
        }.start()
    }

    fun installed(job: String?) {
        if (job.isNullOrBlank()) return
        installedJob = job
        installedAt = System.currentTimeMillis()
    }

    // bal blocks activity launch from install receiver, player resume is foreground
    fun resume(context: Context) {
        val job = installedJob ?: return
        if (System.currentTimeMillis() - installedAt > 600_000L) {
            installedJob = null
            return
        }
        if (send(context, job)) {
            installedJob = null
            Toast.makeText(context, R.string.phantom_installed, Toast.LENGTH_LONG).show()
        }
    }
}
