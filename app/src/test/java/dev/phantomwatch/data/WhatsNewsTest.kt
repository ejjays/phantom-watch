package dev.phantomwatch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewsTest {
    @Test
    fun unseenBuildShowsTheDialog() {
        assertTrue(WhatsNews.shouldShow(0L, 471L, true))
    }

    @Test
    fun olderBuildShowsTheDialog() {
        assertTrue(WhatsNews.shouldShow(465L, 471L, true))
    }

    @Test
    fun currentBuildDoesNotShowTheDialog() {
        assertFalse(WhatsNews.shouldShow(471L, 471L, true))
    }

    @Test
    fun entryWithoutHighlightNeverShowsTheDialog() {
        assertFalse(WhatsNews.shouldShow(465L, 471L, false))
    }

    @Test
    fun parseReadsBodyAndBullets() {
        val json = """{"entries":[{"version":"v","title":"t","highlight":true,"body":"A prose intro.","bullets":["one"]}]}"""
        val entry = WhatsNews.parse(json).single()
        assertEquals("A prose intro.", entry.body)
        assertEquals(listOf("one"), entry.bullets)
    }

    @Test
    fun parseWithoutBodyLeavesItEmpty() {
        val entry = WhatsNews.parse("""{"entries":[{"version":"v","title":"t","highlight":true}]}""").single()
        assertEquals("", entry.body)
        assertTrue(entry.bullets.isEmpty())
    }
}
