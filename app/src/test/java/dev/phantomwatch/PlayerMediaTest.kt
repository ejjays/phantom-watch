package dev.phantomwatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerMediaTest {
    private fun cue(text: String) = VidLove.Cue(0, 1000, text)

    @Test
    fun detectsJapanese() {
        assertEquals("ja", detectLang(listOf(cue("こんにちは"), cue("ありがとう"), cue("你好"))))
    }

    @Test
    fun detectsEnglish() {
        assertEquals("en", detectLang(listOf(cue("hello there"), cue("general kenobi"))))
    }

    @Test
    fun ignoresEmptyCues() {
        assertEquals(null, detectLang(emptyList()))
        assertEquals(null, detectLang(listOf(cue("   "))))
    }

    @Test
    fun explicitHeadersWinOverReferer() {
        val s = Stream("https://a/x.m3u8", true, emptyList(), "https://ref/", null, headers = mapOf("User-Agent" to "x"))
        val h = mediaHeaders(s)
        assertEquals("x", h["User-Agent"])
        assertFalse(h.containsKey("Referer"))
    }

    @Test
    fun refererBecomesRefererAndOrigin() {
        val s = Stream("https://a/x.m3u8", true, emptyList(), "https://host/path", null)
        val h = mediaHeaders(s)
        assertEquals("https://host/path", h["Referer"])
        assertEquals("https://host", h["Origin"])
    }

    @Test
    fun authBecomesAuthorizationHeader() {
        val s = Stream("https://a/x.m3u8", true, emptyList(), "", null, auth = "Bearer t")
        assertEquals("Bearer t", mediaHeaders(s)["Authorization"])
    }

    @Test
    fun fileStreamsGetNoReferer() {
        val s = Stream("file:///data/x.mp4", false, emptyList(), "https://host/", null)
        assertFalse(mediaHeaders(s).containsKey("Referer"))
    }
}

class MkvSubsTest {
    @Test
    fun srtCuesDropMarkupAndClamp() {
        val raws = listOf(
            MkvSubs.Raw(1000, 2000, "{\\i1}hello".toByteArray()),
            MkvSubs.Raw(2000, 0, "world".toByteArray()),
        )
        val cues = MkvSubs.srtCues(raws)
        assertEquals(2, cues.size)
        assertEquals("hello", cues[0].text)
        assertEquals(2000, cues[0].end)
        assertEquals(2000, cues[1].start)
    }

    @Test
    fun assCuesBecomeTimedCues() {
        val raws = listOf(
            MkvSubs.Raw(1500, 1500, "0,0,0,0,,0,0,0,First".toByteArray()),
        )
        val cues = MkvSubs.assCues(raws)
        assertEquals(1, cues.size)
        assertEquals(1500, cues[0].start)
        assertEquals(3000, cues[0].end)
        assertTrue(cues[0].text.contains("First"))
    }

    @Test
    fun clampDropsEmptyAndInvertedCues() {
        val cues = listOf(
            VidLove.Cue(1000, 500, "backwards"),
            VidLove.Cue(1000, 2000, ""),
            VidLove.Cue(1000, 3000, "fine"),
        )
        val out = MkvSubs.clamp(cues)
        assertEquals(1, out.size)
        assertEquals("fine", out[0].text)
    }
}
