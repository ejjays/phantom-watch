package dev.phantomwatch.lan

import dev.phantomwatch.core.AppLog
import dev.phantomwatch.core.Http
import dev.phantomwatch.player.mediaHeaders
import dev.phantomwatch.subs.Subtitles
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

// re-serves the stream the phone is playing to a browser on the same Wi-Fi
// parity rule: upstream gets exactly mediaHeaders(stream), the browser gets only Range
internal class LanServer(
    private val token: String,
    private val session: () -> LanShare.Session?,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(16)
    private val clients = ConcurrentHashMap.newKeySet<Socket>()
    private val ids = ConcurrentHashMap<String, String>()
    private var socket: ServerSocket? = null

    // the shared client kills any call at 25s, which would cut a slow segment mid-body
    private val client: OkHttpClient = Http.client.newBuilder()
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun start(): Int {
        val s = bind(PORT) ?: bind(0) ?: error("no port for the lan server")
        socket = s
        scope.launch {
            while (isActive) {
                val c = runCatching { s.accept() }.getOrNull() ?: break
                clients.add(c)
                scope.launch {
                    gate.withPermit { runCatching { serve(c) } }
                    clients.remove(c)
                    runCatching { c.close() }
                }
            }
        }
        return s.localPort
    }

    fun stop() {
        runCatching { socket?.close() }
        socket = null
        clients.forEach { runCatching { it.close() } }
        clients.clear()
        ids.clear()
        scope.cancel()
    }

    private fun bind(port: Int): ServerSocket? = runCatching {
        ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(port), BACKLOG)
        }
    }.getOrNull()

    private fun serve(s: Socket) {
        s.soTimeout = 20_000
        val input = BufferedInputStream(s.getInputStream())
        val out = s.getOutputStream()
        val request = readHead(input) ?: return
        val method = request.substringBefore(' ')
        val target = request.substringAfter(' ').substringBefore(' ')
        val path = target.substringBefore('?')
        if (path != "/$token" && !path.startsWith("/$token/")) return notFound(out)
        val rest = path.removePrefix("/$token").trimStart('/')
        val range = header(request, "Range")
        when {
            rest.isEmpty() -> page(out)
            rest.startsWith("s/") -> proxy(out, rest.removePrefix("s/"), range, method)
            rest.startsWith("t/") -> subtitle(out, rest.removePrefix("t/").substringBefore('.'), method)
            else -> notFound(out)
        }
    }

    private fun page(out: OutputStream) {
        val sess = session() ?: return notFound(out)
        val tracks = sess.subs.mapIndexed { i, sub ->
            LanPage.Track(sub.label.ifBlank { "Sub ${i + 1}" }, "/$token/t/$i.vtt", i == 0)
        }
        val body = LanPage.html("/$token/s/main", sess.stream.isHls, tracks)
        send(out, 200, mapOf("Content-Type" to HTML, "Cache-Control" to "no-store"), body.toByteArray())
    }

    private fun proxy(out: OutputStream, id: String, range: String?, method: String) {
        val sess = session() ?: return send(out, 503, emptyMap(), EMPTY)
        val url = if (id == "main") sess.stream.url else ids[id] ?: return notFound(out)
        val req = Request.Builder().url(url).apply {
            mediaHeaders(sess.stream).forEach { (k, v) -> header(k, v) }
            header("Accept-Encoding", "identity")
            if (range != null) header("Range", range)
        }.build()
        client.newCall(req).execute().use { r ->
            if (r.code == 200 && range == null && isPlaylist(r)) return playlist(out, r)
            if (r.code !in 200..299 && r.code != 416) {
                AppLog.d("lan upstream ${r.code} ${url.substringBefore('?')}")
                return send(out, 502, emptyMap(), EMPTY)
            }
            media(out, r, method, url)
        }
    }

    // sniff rather than trust Content-Type: CDNs label segments and manifests alike, and a
    // misread segment would be rewritten as text
    private fun isPlaylist(r: Response): Boolean =
        runCatching { r.peekBody(SNIFF).string() }.getOrDefault("").startsWith("#EXTM3U")

    private fun playlist(out: OutputStream, r: Response) {
        val text = r.body?.string().orEmpty()
        val body = LanPlaylist.rewrite(text, r.request.url.toString(), ::link).toByteArray()
        send(out, 200, mapOf("Content-Type" to M3U8, "Cache-Control" to "no-store"), body)
    }

    private fun media(out: OutputStream, r: Response, method: String, url: String) {
        val h = LinkedHashMap<String, String>()
        h["Content-Type"] = r.header("Content-Type") ?: guessType(url)
        h["Accept-Ranges"] = r.header("Accept-Ranges") ?: "bytes"
        r.header("Content-Range")?.let { h["Content-Range"] = it }
        r.body?.contentLength()?.takeIf { it >= 0 }?.let { h["Content-Length"] = it.toString() }
        head(out, r.code, h)
        if (method == "HEAD") return
        runCatching { r.body?.byteStream()?.use { copy(it, out) } }
    }

    private fun subtitle(out: OutputStream, index: String, method: String) {
        val sess = session() ?: return notFound(out)
        val sub = sess.subs.getOrNull(index.toIntOrNull() ?: -1) ?: return notFound(out)
        val req = Request.Builder().url(sub.url).apply {
            mediaHeaders(sess.stream).forEach { (k, v) -> header(k, v) }
        }.build()
        client.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            val body = Subtitles.toVtt(text, sub.url)
            if (body.isEmpty()) return notFound(out)
            if (method == "HEAD") return head(out, 200, mapOf("Content-Type" to VTT))
            send(out, 200, mapOf("Content-Type" to VTT), body.toByteArray())
        }
    }

    private fun link(url: String): String {
        val id = idOf(url)
        ids[id] = url
        return "/$token/s/$id"
    }

    private fun idOf(url: String): String =
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).take(12)
            .joinToString("") { "%02x".format(it) }

    private fun copy(input: InputStream, out: OutputStream) {
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        out.flush()
    }

    private fun send(out: OutputStream, code: Int, h: Map<String, String>, body: ByteArray) {
        head(out, code, h + ("Content-Length" to body.size.toString()))
        out.write(body)
        out.flush()
    }

    private fun notFound(out: OutputStream) = send(out, 404, emptyMap(), EMPTY)

    private fun head(out: OutputStream, code: Int, h: Map<String, String>) {
        val sb = StringBuilder("HTTP/1.1 $code ${reason(code)}\r\n")
        sb.append("Connection: close\r\n")
        sb.append("Access-Control-Allow-Origin: *\r\n")
        sb.append("Access-Control-Allow-Headers: Range\r\n")
        sb.append("Access-Control-Expose-Headers: Content-Length, Content-Range, Accept-Ranges\r\n")
        for ((k, v) in h) sb.append(k).append(": ").append(v).append("\r\n")
        sb.append("\r\n")
        out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
        out.flush()
    }

    private fun reason(code: Int): String = when (code) {
        200 -> "OK"
        206 -> "Partial Content"
        416 -> "Range Not Satisfiable"
        502 -> "Bad Gateway"
        503 -> "Service Unavailable"
        else -> "Not Found"
    }

    private fun guessType(url: String): String {
        val path = url.substringBefore('?').lowercase()
        return when {
            path.endsWith(".ts") -> "video/mp2t"
            path.endsWith(".m4s") || path.endsWith(".mp4") || path.endsWith(".m4v") -> "video/mp4"
            path.endsWith(".webm") -> "video/webm"
            path.endsWith(".aac") -> "audio/aac"
            path.endsWith(".m4a") -> "audio/mp4"
            else -> "application/octet-stream"
        }
    }

    private fun header(head: String, name: String): String? = head.lineSequence().drop(1)
        .firstOrNull { it.substringBefore(':').trim().equals(name, ignoreCase = true) }
        ?.substringAfter(':')?.trim()

    private fun readHead(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        var state = 0
        while (buf.size() <= MAX_HEAD) {
            val b = input.read()
            if (b < 0) return null
            buf.write(b)
            state = when {
                b == 13 && (state == 0 || state == 2) -> state + 1
                b == 10 && (state == 1 || state == 3) -> state + 1
                else -> 0
            }
            if (state == 4) return buf.toString("ISO-8859-1")
        }
        return null
    }

    companion object {
        private const val PORT = 8765
        private const val BACKLOG = 50
        private const val MAX_HEAD = 8192
        private const val SNIFF = 256L
        private const val HTML = "text/html; charset=utf-8"
        private const val M3U8 = "application/vnd.apple.mpegurl"
        private const val VTT = "text/vtt; charset=utf-8"
        private val EMPTY = ByteArray(0)
    }
}
