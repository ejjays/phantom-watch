package dev.phantomwatch.player

import dev.phantomwatch.subs.Subtitles
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

// player parses the cache file so these assert on bytes on disk
class SubCacheTest {
    private lateinit var server: MockWebServer
    private lateinit var dir: File

    private fun url(path: String) = "http://127.0.0.1:${server.port}$path"

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        dir = Files.createTempDirectory("phantom-subcache").toFile()
    }

    @After
    fun tearDown() {
        server.shutdown()
        dir.deleteRecursively()
    }

    @Test
    fun srtFromProviderIsStoredAsWebVtt() {
        server.enqueue(MockResponse().setBody(SRT))
        val f = SubCache.store(dir, url("/en.srt"), emptyMap())
        assertNotNull(f)
        val text = f!!.readText()
        assertTrue(text.startsWith("WEBVTT"))
        assertEquals(2, Subtitles.parseCues(text).size)
    }

    @Test
    fun webVttIsStoredUnchanged() {
        server.enqueue(MockResponse().setBody(VTT))
        val text = SubCache.store(dir, url("/en.vtt"), emptyMap())!!.readText()
        assertEquals(VTT, text)
    }

    @Test
    fun staleSrtCacheIsReplaced() {
        val stale = File(dir, SubCache.cacheName(url("/old.srt")))
        stale.writeText(SRT)
        server.enqueue(MockResponse().setBody(SRT))
        val f = SubCache.store(dir, url("/old.srt"), emptyMap())!!
        assertTrue(f.readText().startsWith("WEBVTT"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun failedFetchStoresNothing() {
        server.enqueue(MockResponse().setResponseCode(403))
        assertNull(SubCache.store(dir, url("/blocked.srt"), emptyMap()))
        assertTrue(dir.listFiles().isNullOrEmpty())
    }

    private companion object {
        const val SRT = "1\n00:00:01,000  -->  00:00:02,000\nHello\n\n" +
            "2\n00:00:03,500 --> 00:00:04,000\nWorld\n"
        const val VTT = "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nHello\n"
    }
}
