package dev.phantomwatch.lan

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.phantomwatch.data.Stream
import dev.phantomwatch.data.Sub
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// runs the real server against a fake provider CDN: the header rules and the playlist
// rewrite are the two things that silently break a laptop stream
class LanServerTest {
    private val pool = Executors.newFixedThreadPool(2)
    private lateinit var upstream: HttpServer
    private lateinit var lan: LanServer
    private var port = 0
    private val seen = mutableListOf<String>()

    private val http = OkHttpClient.Builder().build()

    @Before
    fun start() {
        upstream = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        upstream.executor = pool
        upstream.createContext("/") { ex -> serve(ex) }
        upstream.start()

        val base = "http://127.0.0.1:${upstream.address.port}"
        val stream = Stream(
            url = "$base/master.m3u8",
            isHls = true,
            subs = listOf(Sub("English", "$base/sub.srt")),
            referer = "https://provider.example/",
            sampleMp4 = null,
            headers = mapOf("User-Agent" to "okhttp/4.12.0"),
        )
        lan = LanServer("tok") { LanShare.Session(stream, stream.subs) }
        port = lan.start()
    }

    @After
    fun stop() {
        lan.stop()
        upstream.stop(0)
        pool.shutdownNow()
    }

    private fun serve(ex: HttpExchange) {
        seen += "${ex.requestHeaders.getFirst("User-Agent")}|${ex.requestHeaders.getFirst("Referer")}"
        val path = ex.requestURI.path
        val body = when (path) {
            "/master.m3u8" -> "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\n720.m3u8\n"
            "/720.m3u8" -> "#EXTM3U\n#EXTINF:4.0,\nseg.ts\n"
            "/seg.ts" -> "0123456789"
            "/sub.srt" -> "1\n00:00:01,000 --> 00:00:02,000\nHello\n"
            else -> ""
        }
        val range = ex.requestHeaders.getFirst("Range")
        if (path == "/seg.ts" && range != null) {
            val from = range.substringAfter('=').substringBefore('-').toInt()
            val part = body.substring(from).toByteArray()
            ex.responseHeaders.add("Content-Range", "bytes $from-${body.length - 1}/${body.length}")
            reply(ex, 206, "video/mp2t", part)
            return
        }
        val type = if (path.endsWith(".srt")) "text/plain" else "application/x-mpegURL"
        reply(ex, if (path == "/master.m3u8" || path == "/720.m3u8") 200 else 200, type, body.toByteArray())
    }

    private fun reply(ex: HttpExchange, code: Int, type: String, body: ByteArray) {
        ex.responseHeaders.add("Content-Type", type)
        ex.sendResponseHeaders(code, body.size.toLong())
        ex.responseBody.use { it.write(body) }
    }

    private fun get(path: String, range: String? = null): Pair<Int, String> {
        val req = Request.Builder().url("http://127.0.0.1:$port$path").apply {
            if (range != null) header("Range", range)
        }.build()
        http.newCall(req).execute().use { r -> return r.code to r.body?.string().orEmpty() }
    }

    private fun child(body: String): String =
        body.lineSequence().first { it.startsWith("/tok/s/") }

    @Test
    fun pageListsTracksAndTheStream() {
        val (code, body) = get("/tok/")
        assertEquals(200, code)
        assertTrue(body.contains("/tok/s/main"))
        assertTrue(body.contains("English"))
        assertTrue(body.contains("hls.js"))
        // sidecar subs exist here, so the manifest's own default track must be switched off
        assertTrue(body.contains("var sidecar = true;"))
        assertTrue(body.contains("MANIFEST_PARSED"))
        assertFalse(body.contains("{{"))
    }

    @Test
    fun masterPlaylistPointsBackAtTheProxy() {
        val (code, body) = get("/tok/s/main")
        assertEquals(200, code)
        assertTrue(body.contains("#EXT-X-STREAM-INF"))
        assertTrue(child(body).startsWith("/tok/s/"))
        assertFalse(body.contains("720.m3u8"))
    }

    @Test
    fun nestedPlaylistAndSegmentResolve() {
        val variant = child(get("/tok/s/main").second)
        val media = get(variant).second
        assertTrue(media.contains("#EXTINF"))
        val segment = child(media)
        assertEquals("0123456789", get(segment).second)
    }

    @Test
    fun rangeIsForwardedAnd206Mirrored() {
        val segment = child(get(child(get("/tok/s/main").second)).second)
        val (code, body) = get(segment, "bytes=5-")
        assertEquals(206, code)
        assertEquals("56789", body)
    }

    @Test
    fun srtIsConvertedToVtt() {
        val (code, body) = get("/tok/t/0.vtt")
        assertEquals(200, code)
        assertTrue(body.startsWith("WEBVTT"))
        assertTrue(body.contains("00:00:01.000 --> 00:00:02.000"))
        assertTrue(body.contains("Hello"))
    }

    @Test
    fun upstreamGetsTheFormatHeadersAndNothingElse() {
        get("/tok/s/main")
        assertEquals("okhttp/4.12.0|null", seen.first())
    }

    @Test
    fun wrongTokenIsRefused() {
        assertEquals(404, get("/nope/s/main").first)
        assertEquals(404, get("/tok/elsewhere").first)
    }
}
