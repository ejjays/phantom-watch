package dev.phantomwatch.subs

import dev.phantomwatch.core.AppLog
import dev.phantomwatch.core.Http
import java.io.File
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okio.Buffer
import org.json.JSONObject

// one pass over the whole episode's HLS audio, ahead of the playhead: the live stream only
// covers the opening minute, this replaces it with exact timing for everything after
internal object PreRecorded {
    // a 2.5h film is ~160MB at 128kbps: the old cap tripped right at the end and never produced a transcript
    private const val MAX_BYTES = 256L * 1024 * 1024
    private const val MAX_TEXT = 1L * 1024 * 1024
    private const val MAX_CHARS = 42
    private const val MAX_WORDS = 12
    private const val GAP = 0.7
    private const val MIN_CUE_MS = 1200L
    private const val MAX_CUE_MS = 10_000L

    private val client: OkHttpClient by lazy {
        Http.client.newBuilder().readTimeout(180, TimeUnit.SECONDS).build()
    }

    // minutes of upload plus ~1.5x realtime of processing: the shared client's 25s call cap killed it
    private val uploader: OkHttpClient by lazy {
        Http.client.newBuilder()
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .build()
    }

    private class Part(val url: String, val seq: Long, val encrypted: Boolean)
    private class Playlist(val map: Part?, val parts: List<Part>, val keyUrl: String?, val iv: ByteArray?)

    suspend fun transcribe(
        url: String,
        headers: Map<String, String>,
        referer: String,
        language: String,
        key: String,
        dir: File,
    ): List<SubtitleCue>? = withContext(Dispatchers.IO) {
        val file = File(dir, "ai-audio.bin")
        try {
            val mime = download(url, headers, referer, language, file) ?: return@withContext null
            AppLog.d("ai subs batch lang=$language audio=${file.length() / 1024}kb $mime")
            post(file, mime, language, key)
        } catch (e: Exception) {
            AppLog.d("ai subs batch ${e.javaClass.simpleName}: ${e.message}")
            null
        } finally {
            file.delete()
        }
    }

    private suspend fun download(
        url: String,
        headers: Map<String, String>,
        referer: String,
        language: String,
        out: File,
    ): String? {
        var base = url
        var muxed = false
        var manifest = text(base, headers, referer) ?: return null
        if (manifest.contains("#EXT-X-STREAM-INF")) {
            val audio = manifest.lineSequence()
                .map { it.trim() }
                .filter { it.startsWith("#EXT-X-MEDIA:") && it.contains("TYPE=AUDIO") }
                .let { lines ->
                    lines.firstOrNull { DeepgramLive.languageOf(attr(it, "LANGUAGE")) == language }
                        ?: lines.firstOrNull { it.contains("DEFAULT=YES") }
                        ?: lines.firstOrNull()
                }
                ?.let { attr(it, "URI") }
                ?: smallest(manifest)?.also {
                    muxed = true
                    AppLog.d("ai subs batch no audio rendition, smallest variant")
                }
                ?: run {
                    AppLog.d("ai subs batch no playable variant")
                    return null
                }
            base = resolve(audio, base)
            manifest = text(base, headers, referer) ?: return null
        }
        val pl = parse(manifest, base)
        if (pl.parts.isEmpty()) return null
        val mime = if (pl.map != null && muxed) "video/mp4" else if (pl.map != null) "audio/mp4" else "video/mp2t"
        val aesKey = pl.keyUrl?.let { bytes(it, headers, referer) }?.takeIf { it.size == 16 }
        val parts = listOfNotNull(pl.map) + pl.parts
        val chunks = arrayOfNulls<ByteArray>(parts.size)
        val next = AtomicInteger(0)
        val window = Channel<Unit>(6)
        repeat(6) { window.trySend(Unit) }
        val lock = Any()
        var cursor = 0
        var written = 0L
        out.outputStream().buffered().use { sink ->
            coroutineScope {
                // the CDN throttles one connection to ~140 KB/s but serves six at ~2.8 MB/s
                repeat(6) {
                    launch {
                        while (isActive) {
                            val i = next.getAndIncrement()
                            if (i >= parts.size) return@launch
                            window.receive()
                            val part = parts[i]
                            val raw = bytes(part.url, headers, referer) ?: error("segment $i")
                            val data =
                                if (part.encrypted && aesKey != null) decrypt(raw, aesKey, pl.iv ?: seqIv(part.seq))
                                else raw
                            synchronized(lock) {
                                chunks[i] = data
                                while (cursor < parts.size && chunks[cursor] != null) {
                                    val d = chunks[cursor]!!
                                    sink.write(d)
                                    written += d.size
                                    chunks[cursor] = null
                                    cursor++
                                    window.trySend(Unit)
                                }
                                if (written > MAX_BYTES) error("audio too big ${written / 1048576}MB")
                            }
                        }
                    }
                }
            }
        }
        return if (out.length() > 0) mime else null
    }

    // a 1080p mux at 1.6 Mbps is 300MB for a 24-minute episode: mobile data spent on a maybe
    private const val MAX_VARIANT_BPS = 900_000

    private fun smallest(master: String): String? {
        var best: Pair<Int, String>? = null
        var pending = -1
        for (line in master.lineSequence().map { it.trim() }) {
            when {
                line.startsWith("#EXT-X-STREAM-INF:") ->
                    pending = Regex("BANDWIDTH=(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: -1
                line.isEmpty() || line.startsWith("#") -> Unit
                pending >= 0 -> {
                    if (best == null || pending < best.first) best = pending to line
                    pending = -1
                }
            }
        }
        val (bps, uri) = best ?: return null
        if (bps > MAX_VARIANT_BPS) {
            AppLog.d("ai subs batch variant ${bps / 1000}k too big")
            return null
        }
        return uri
    }

    private fun parse(manifest: String, base: String): Playlist {
        var map: Part? = null
        var keyUrl: String? = null
        var iv: ByteArray? = null
        var seq = 0L
        val parts = mutableListOf<Part>()
        for (line in manifest.lineSequence().map { it.trim() }) {
            when {
                line.startsWith("#EXT-X-MEDIA-SEQUENCE:") ->
                    seq = line.substringAfter(':').trim().toLongOrNull() ?: 0L
                line.startsWith("#EXT-X-KEY:") -> {
                    val method = attr(line, "METHOD")
                    keyUrl = if (method == "AES-128") attr(line, "URI")?.let { resolve(it, base) } else null
                    iv = attr(line, "IV")?.removePrefix("0x")?.hex()
                }
                line.startsWith("#EXT-X-MAP:") ->
                    map = attr(line, "URI")?.let { Part(resolve(it, base), seq, keyUrl != null) }
                line.isEmpty() || line.startsWith("#") -> Unit
                else -> {
                    parts += Part(resolve(line, base), seq, keyUrl != null)
                    seq++
                }
            }
        }
        return Playlist(map, parts, keyUrl, iv)
    }

    private fun decrypt(raw: ByteArray, key: ByteArray, iv: ByteArray): ByteArray =
        Cipher.getInstance("AES/CBC/PKCS5Padding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            doFinal(raw)
        }

    private fun seqIv(seq: Long): ByteArray {
        val iv = ByteArray(16)
        for (i in 0 until 8) iv[15 - i] = ((seq shr (8 * i)) and 0xFF).toByte()
        return iv
    }

    private fun post(file: File, mime: String, language: String, key: String): List<SubtitleCue> {
        val url = "https://api.deepgram.com/v1/listen" +
            "?model=nova-3&language=$language&smart_format=true&punctuate=true"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Token $key")
            .post(file.asRequestBody(mime.toMediaType()))
            .build()
        uploader.newCall(request).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                AppLog.d("ai subs batch http=${r.code} ${text.take(80)}")
                return emptyList()
            }
            return cues(JSONObject(text))
        }
    }

    private fun cues(body: JSONObject): List<SubtitleCue> {
        val words = body.optJSONObject("results")
            ?.optJSONArray("channels")
            ?.optJSONObject(0)
            ?.optJSONArray("alternatives")
            ?.optJSONObject(0)
            ?.optJSONArray("words")
            ?: return emptyList()
        val out = mutableListOf<SubtitleCue>()
        val line = StringBuilder()
        var start = 0L
        var count = 0
        for (i in 0 until words.length()) {
            val w = words.getJSONObject(i)
            val text = w.optString("punctuated_word").ifEmpty { w.optString("word") }
            val end = (w.optDouble("end") * 1000).toLong()
            if (count == 0) start = (w.optDouble("start") * 1000).toLong()
            if (line.isNotEmpty()) line.append(' ')
            line.append(text)
            count++
            val next = if (i + 1 < words.length()) words.getJSONObject(i + 1) else null
            val closes = text.trimEnd('"', '\'', ')', ',').lastOrNull()?.let { it in ".?!" } == true
            val gap = next != null && next.optDouble("start") - w.optDouble("end") > GAP
            if (next == null || closes || gap || line.length >= MAX_CHARS || count >= MAX_WORDS) {
                out += SubtitleCue(start, end, line.toString())
                line.setLength(0)
                count = 0
            }
        }
        return hold(out)
    }

    // a one-word line flashing for 300ms reads as a glitch, so every line gets a floor
    private fun hold(cues: List<SubtitleCue>): List<SubtitleCue> {
        for (i in cues.indices) {
            val c = cues[i]
            val next = cues.getOrNull(i + 1)
            if (c.end - c.start > MAX_CUE_MS) c.end = c.start + MAX_CUE_MS
            if (c.end - c.start < MIN_CUE_MS) {
                val want = c.start + MIN_CUE_MS
                c.end = if (next != null) minOf(want, next.start - 100) else want
                if (c.end <= c.start) c.end = c.start + 400
            }
        }
        return cues
    }

    private fun req(url: String, headers: Map<String, String>, referer: String): Request {
        val b = Request.Builder().url(url)
        headers.forEach { (k, v) -> b.header(k, v) }
        if (referer.isNotEmpty() && headers.keys.none { it.equals("Referer", true) }) {
            b.header("Referer", referer)
            runCatching { b.header("Origin", "https://${URL(referer).host}") }
        }
        return b.build()
    }

    private fun bytes(url: String, headers: Map<String, String>, referer: String): ByteArray? =
        runCatching {
            client.newCall(req(url, headers, referer)).execute().use {
                if (it.isSuccessful) it.body?.bytes() else null
            }
        }.getOrNull()

    // a manifest is a few hundred KB: past the cap this is a direct MP4 that would fill the heap
    private fun text(url: String, headers: Map<String, String>, referer: String): String? =
        runCatching {
            client.newCall(req(url, headers, referer)).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val src = r.body?.source() ?: return@use null
                val head = Buffer()
                while (head.size < MAX_TEXT) {
                    if (src.read(head, MAX_TEXT - head.size) == -1L) break
                }
                if (head.size >= MAX_TEXT) return@use null
                head.readUtf8()
            }
        }.getOrNull()

    private fun attr(line: String, name: String): String? =
        Regex("""(?:^|,)$name=("[^"]*"|[^,]*)""")
            .find(line.substringAfter(':'))
            ?.groupValues?.get(1)?.trim('"')

    private fun String.hex(): ByteArray? = runCatching {
        ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }.getOrNull()

    private fun resolve(path: String, base: String): String =
        runCatching { URL(URL(base), path).toString() }.getOrDefault(path)
}
