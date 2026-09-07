package dev.phantomwatch.lan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanPlaylistTest {
    private val seen = LinkedHashMap<String, String>()
    private val link: (String) -> String = { url -> "/tok/s/" + seen.getOrPut(url) { "u" + seen.size } }

    private fun run(text: String, base: String = "https://cdn.example.com/hls/master.m3u8") =
        LanPlaylist.rewrite(text, base, link)

    @Test
    fun relativeSegmentResolvesAgainstManifestDir() {
        assertEquals("#EXTM3U\n/tok/s/u0\n", run("#EXTM3U\nseg1.ts\n"))
        assertEquals("https://cdn.example.com/hls/seg1.ts", seen.keys.first())
    }

    @Test
    fun rootRelativeKeepsHost() {
        run("/media/seg1.ts\n")
        assertTrue(seen.keys.contains("https://cdn.example.com/media/seg1.ts"))
    }

    @Test
    fun protocolRelativePicksManifestScheme() {
        run("//cdn2.example.com/a/seg1.ts\n")
        assertTrue(seen.keys.contains("https://cdn2.example.com/a/seg1.ts"))
    }

    @Test
    fun absoluteKeepsQuery() {
        run("https://x.example.com/s.ts?token=abc&n=1\n")
        assertTrue(seen.keys.contains("https://x.example.com/s.ts?token=abc&n=1"))
    }

    @Test
    fun keyUriRewrittenAndAttributesKept() {
        val out = run("#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\",IV=0x0123\n")
        assertEquals("#EXT-X-KEY:METHOD=AES-128,URI=\"/tok/s/u0\",IV=0x0123\n", out)
        assertEquals("https://cdn.example.com/hls/key.bin", seen.keys.first())
    }

    @Test
    fun sessionKeyRewritten() {
        val out = run("#EXT-X-SESSION-KEY:METHOD=AES-128,URI=\"k.bin\"\n")
        assertTrue(out.contains("URI=\"/tok/s/"))
    }

    @Test
    fun mapKeepsByteRangeAttribute() {
        val out = run("#EXT-X-MAP:URI=\"init.mp4\",BYTERANGE=\"720@0\"\n")
        assertEquals("#EXT-X-MAP:URI=\"/tok/s/u0\",BYTERANGE=\"720@0\"\n", out)
    }

    @Test
    fun byterangeLineUntouched() {
        val text = "#EXTINF:4.0,\n#EXT-X-BYTERANGE:1000@200\nseg.ts\n"
        val out = run(text)
        assertTrue(out.contains("#EXT-X-BYTERANGE:1000@200"))
    }

    @Test
    fun mediaAndIframeUrisRewritten() {
        val out = run(
            "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"a\",NAME=\"en\",URI=\"audio/en.m3u8\"\n" +
                "#EXT-X-I-FRAME-STREAM-INF:BANDWIDTH=100,URI=\"iframe.m3u8\"\n",
        )
        assertEquals(2, Regex("""URI="/tok/s/""").findAll(out).count())
    }

    @Test
    fun masterVariantOnItsOwnLine() {
        val out = run("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000\n720/index.m3u8\n")
        assertTrue(out.contains("/tok/s/u0\n"))
        assertEquals("https://cdn.example.com/hls/720/index.m3u8", seen.keys.first())
    }

    @Test
    fun crlfPreserved() {
        val out = run("#EXTM3U\r\nseg.ts\r\n")
        assertEquals("#EXTM3U\r\n/tok/s/u0\r\n", out)
    }

    @Test
    fun unparseableUriLeftVerbatim() {
        val out = run("seg with space.ts\n")
        assertEquals("seg with space.ts\n", out)
        assertTrue(seen.isEmpty())
    }

    @Test
    fun sameUrlTwiceSharesOneId() {
        val out = run("seg.ts\nseg.ts\n")
        assertEquals("/tok/s/u0\n/tok/s/u0\n", out)
        assertEquals(1, seen.size)
    }

    @Test
    fun emptyInput() {
        assertEquals("", run(""))
    }
}
