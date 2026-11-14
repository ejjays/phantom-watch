package dev.phantomwatch.player

import android.content.Context
import android.net.Uri
import dev.phantomwatch.core.AppLog
import dev.phantomwatch.core.Http
import dev.phantomwatch.data.Sub
import dev.phantomwatch.subs.Subtitles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

// provider vtt sits behind same referer as stream, player fetches sideloaded
// sub without it so the file 403s and no track appears, download it and hand a local
// file, same path opensubtitles captions already use
internal object SubCache {
    private val done = ConcurrentHashMap<String, File>()

    suspend fun local(ctx: Context, sub: Sub, headers: Map<String, String>): Sub {
        if (!sub.url.startsWith("http")) return sub
        val f = withContext(Dispatchers.IO) { store(ctx.cacheDir, sub.url, headers) } ?: return sub
        return sub.copy(url = Uri.fromFile(f).toString())
    }

    internal fun cacheName(url: String): String = "sub-" + url.hashCode().toString() + ".vtt"

    // dir not context, write path stays reachable from plain jvm test
    internal fun store(dir: File, url: String, headers: Map<String, String>): File? {
        done[url]?.let { return it }
        val f = File(dir, cacheName(url))
        if (f.length() > 0) {
            // earlier build wrote provider srt bytes under this .vtt name and the
            // player parsed them as webvtt, track selected no cues nothing in log
            if (isVtt(f)) return f.also { done[url] = it }
            f.delete()
        }
        val body = download(url, headers) ?: return null
        val vtt = asVtt(body, url)
        f.writeBytes(vtt ?: body)
        AppLog.d("sub cached ${body.size}b" + if (vtt != null) " vtt" else " raw")
        done[url] = f
        return f
    }

    private fun download(url: String, headers: Map<String, String>): ByteArray? = try {
        val b = Request.Builder().url(url)
        headers.forEach { (k, v) -> b.header(k, v) }
        val call = Http.client.newCall(b.build())
        call.timeout().deadline(3, TimeUnit.SECONDS)
        call.execute().use { r ->
            val body = if (r.isSuccessful) r.body?.bytes() else null
            if (body == null || body.isEmpty() || body.size > 4_000_000) {
                AppLog.d("sub fetch ${r.code} ${url.substringAfter("://").substringBefore('/')}")
                null
            } else body
        }
    } catch (e: Exception) {
        AppLog.d("sub fetch fail ${e.javaClass.simpleName}")
        null
    }

    private fun isVtt(f: File): Boolean = runCatching {
        val head = ByteArray(16)
        val n = f.inputStream().use { it.read(head) }
        if (n <= 0) return@runCatching false
        String(head, 0, n, Charsets.UTF_8).removePrefix("\uFEFF").trimStart().startsWith("WEBVTT")
    }.getOrDefault(false)

    // file served as text/vtt whatever provider sent
    private fun asVtt(body: ByteArray, url: String): ByteArray? {
        val text = decode(body) ?: return null
        val vtt = Subtitles.toVtt(text, url)
        return if (vtt.isEmpty()) null else vtt.toByteArray()
    }

    private fun decode(b: ByteArray): String? = runCatching {
        val s = String(b, Charsets.UTF_8)
        // srt in the wild is often latin1, timings survive either way dialogue does not
        if (s.contains('\uFFFD')) String(b, Charsets.ISO_8859_1) else s
    }.getOrNull()
}

internal suspend fun List<Sub>.cached(
    ctx: Context,
    url: String,
    headers: Map<String, String>,
    referer: String,
): List<Sub> {
    if (isEmpty()) return this
    val h = mediaHeaders(url, headers, referer)
    return map { SubCache.local(ctx, it, h) }
}
