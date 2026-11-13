package dev.phantomwatch.player

import android.content.Context
import android.net.Uri
import dev.phantomwatch.core.AppLog
import dev.phantomwatch.core.Http
import dev.phantomwatch.data.Sub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

// a provider vtt sits behind the same referer as its stream, and the player fetches a sideloaded
// sub without it, so the file 403s and no track ever appears: download it and hand over a local
// file, the same path the OpenSubtitles captions already render through
internal object SubCache {
    private val done = ConcurrentHashMap<String, File>()

    suspend fun local(ctx: Context, sub: Sub, headers: Map<String, String>): Sub {
        if (!sub.url.startsWith("http")) return sub
        val f = withContext(Dispatchers.IO) { fetch(ctx, sub.url, headers) } ?: return sub
        return sub.copy(url = Uri.fromFile(f).toString())
    }

    private fun fetch(ctx: Context, url: String, headers: Map<String, String>): File? {
        done[url]?.let { return it }
        val f = File(ctx.cacheDir, "sub-" + url.hashCode().toString() + ".vtt")
        if (f.length() > 0) return f.also { done[url] = it }
        return try {
            val b = Request.Builder().url(url)
            headers.forEach { (k, v) -> b.header(k, v) }
            val call = Http.client.newCall(b.build())
            call.timeout().deadline(3, TimeUnit.SECONDS)
            call.execute().use { r ->
                val body = if (r.isSuccessful) r.body?.bytes() else null
                if (body == null || body.isEmpty() || body.size > 4_000_000) {
                    AppLog.d("sub fetch ${r.code} ${url.substringAfter("://").substringBefore('/')}")
                    null
                } else {
                    f.writeBytes(body)
                    AppLog.d("sub cached ${body.size}b")
                    done[url] = f
                    f
                }
            }
        } catch (e: Exception) {
            AppLog.d("sub fetch fail ${e.javaClass.simpleName}")
            null
        }
    }
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
