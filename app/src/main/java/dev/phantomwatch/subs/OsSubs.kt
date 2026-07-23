package dev.phantomwatch.subs

import android.net.Uri
import dev.phantomwatch.core.Http
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object OsSubs {
    private const val API = "https://api.opensubtitles.com/api/v1"
    private const val UA = "PhantomWatch/1.0"
    private const val BLOCK = 65536

    fun oshash(head: ByteArray, tail: ByteArray, size: Long): String {
        var h = size.toULong()
        val buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        fun feed(b: ByteArray) {
            var i = 0
            while (i + 8 <= b.size) {
                buf.clear()
                buf.put(b, i, 8)
                buf.flip()
                h += buf.long.toULong()
                i += 8
            }
        }
        feed(head)
        feed(tail)
        return h.toString(16).padStart(16, '0')
    }

    private fun get(url: String, headers: Map<String, String>, range: String? = null): Pair<Int, ByteArray>? {
        return try {
            val b = Request.Builder().url(url).header("User-Agent", UA)
            headers.forEach { (k, v) -> b.header(k, v) }
            if (range != null) b.header("Range", range)
            Http.client.newCall(b.build()).execute().use { res ->
                val code = res.code
                val bytes = if (range != null && code != 206) ByteArray(0) else res.body?.bytes() ?: ByteArray(0)
                code to bytes
            }
        } catch (_: Exception) {
            null
        }
    }

    fun hashOf(url: String, headers: Map<String, String>): Pair<String, Long>? {
        return try {
            if (url.startsWith("file")) {
                val path = Uri.parse(url).path ?: return null
                val f = File(path)
                if (!f.exists() || f.length() < BLOCK * 2L) return null
                val head = ByteArray(BLOCK)
                val tail = ByteArray(BLOCK)
                f.inputStream().use {
                    var off = 0
                    while (off < BLOCK) {
                        val r = it.read(head, off, BLOCK - off)
                        if (r < 0) break
                        off += r
                    }
                }
                java.io.RandomAccessFile(f, "r").use {
                    it.seek(f.length() - BLOCK)
                    var off = 0
                    while (off < BLOCK) {
                        val r = it.read(tail, off, BLOCK - off)
                        if (r < 0) break
                        off += r
                    }
                }
                oshash(head, tail, f.length()) to f.length()
            } else {
                val b = Request.Builder().url(url).header("User-Agent", UA)
                headers.forEach { (k, v) -> b.header(k, v) }
                val size = Http.client.newCall(b.head().build()).execute().use { res ->
                    res.header("Content-Length")?.toLongOrNull() ?: -1L
                }
                if (size < BLOCK * 2L) return null
                val (c1, head) = get(url, headers, "bytes=0-${BLOCK - 1}") ?: return null
                val (c2, tail) = get(url, headers, "bytes=${size - BLOCK}-${size - 1}") ?: return null
                if (c1 != 206 || c2 != 206 || head.size < BLOCK || tail.size < BLOCK) return null
                oshash(head, tail, size) to size
            }
        } catch (_: Exception) {
            null
        }
    }

    fun cached(cacheDir: File, hash: String): File? {
        val f = File(cacheDir, "os-$hash.vtt")
        return if (f.exists() && f.length() > 0) f else null
    }

    fun search(apiKey: String, hash: String, size: Long): Long? {
        val url = "$API/subtitles?moviehash=$hash&moviebytesize=$size&languages=en"
        return searchPick(apiKey, url)
    }

    fun searchImdb(apiKey: String, imdbDigits: String, season: String, episode: String): Long? {
        val url = if (season.isEmpty()) "$API/subtitles?imdb_id=$imdbDigits&languages=en"
        else "$API/subtitles?imdb_id=$imdbDigits&season_number=$season&episode_number=$episode&languages=en"
        return searchPick(apiKey, url)
    }

    private fun searchPick(apiKey: String, url: String): Long? {
        return try {
            val b = Request.Builder().url(url)
                .header("Api-Key", apiKey)
                .header("User-Agent", UA)
                .header("Accept", "application/json")
            val body = Http.client.newCall(b.build()).execute().use { res ->
                if (res.code != 200) return null
                res.body?.string() ?: return null
            }
            pickFileId(body)
        } catch (_: Exception) {
            null
        }
    }

    internal fun pickFileId(body: String): Long? {
        val data = try {
            JSONObject(body).optJSONArray("data") ?: return null
        } catch (_: Exception) {
            return null
        }
        var best: Long? = null
        var bestDl = -1
        for (i in 0 until data.length()) {
            val a = data.optJSONObject(i)?.optJSONObject("attributes") ?: continue
            val files = a.optJSONArray("files") ?: continue
            if (files.length() == 0) continue
            val dl = a.optInt("download_count", 0)
            if (dl > bestDl) {
                bestDl = dl
                best = files.optJSONObject(0)?.optLong("file_id")?.takeIf { it > 0 }
            }
        }
        return best
    }

    internal fun linkOf(body: String): String? =
        try {
            JSONObject(body).optString("link").takeIf { it.startsWith("http") }
        } catch (_: Exception) {
            null
        }

    fun downloadText(apiKey: String, fileId: Long): String? {
        return try {
            val payload = JSONObject().put("file_id", fileId).toString()
                .toByteArray()
            val b = Request.Builder().url("$API/download")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .header("Api-Key", apiKey)
                .header("User-Agent", UA)
                .header("Accept", "application/json")
            val link = Http.client.newCall(b.build()).execute().use { res ->
                if (res.code != 200) return null
                linkOf(res.body?.string().orEmpty())
            } ?: return null
            Http.client.newCall(
                Request.Builder().url(link).header("User-Agent", UA).build(),
            ).execute().use { res ->
                if (res.code !in 200..299) return null
                res.body?.string()?.takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun toFile(text: String, cacheDir: File, hash: String): File? {
        return try {
            val cues = if ("Dialogue:" in text) Subtitles.parseAss(text) else Subtitles.parseCues(text)
            if (cues.isEmpty()) return null
            val f = File(cacheDir, "os-$hash.vtt")
            f.writeText(Subtitles.renderCues(cues))
            f
        } catch (_: Exception) {
            null
        }
    }
}
