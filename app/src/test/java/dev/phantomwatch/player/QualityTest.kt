package dev.phantomwatch.player

import org.junit.Assert.assertEquals
import org.junit.Test

class QualityTest {
    @Test
    fun labelsRungsByTheirLongSide() {
        assertEquals("1080p", qualityLabel(1920, 800))
        assertEquals("1080p", qualityLabel(1920, 1080))
        assertEquals("1080p", qualityLabel(1080, 1920))
        assertEquals("720p", qualityLabel(1280, 720))
        assertEquals("480p", qualityLabel(854, 480))
        assertEquals("360p", qualityLabel(640, 360))
        assertEquals("1440p", qualityLabel(2560, 1440))
        assertEquals("2160p", qualityLabel(3840, 1600))
        assertEquals("200p", qualityLabel(300, 200))
    }

    @Test
    fun rungsDropDuplicateLabelsAndSortTallestFirst() {
        val r = rungsOf(listOf(1920 to 800, 1920 to 1080, 1280 to 720))
        assertEquals(listOf("1080p", "720p"), r.map { it.label })
        assertEquals(listOf(1080, 720), r.map { it.height })
    }

    @Test
    fun rungsSkipTracksWithoutAHeight() {
        assertEquals(listOf(720), rungsOf(listOf(0 to 0, 1280 to 720)).map { it.height })
    }
}
