package dev.phantomwatch.subs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
            SubtitleCue(1000, 500, "backwards"),
            SubtitleCue(1000, 2000, ""),
            SubtitleCue(1000, 3000, "fine"),
        )
        val out = MkvSubs.clamp(cues)
        assertEquals(1, out.size)
        assertEquals("fine", out[0].text)
    }
}
