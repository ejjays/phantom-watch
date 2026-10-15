package dev.phantomwatch.player

import dev.phantomwatch.data.Recent
import dev.phantomwatch.data.Resume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NextUpTest {
    private fun entry(id: String, season: String = "1", episode: String = "1", pos: Long = 60_000) =
        Recent("tv", id, "Show $id", "https://img/$id.jpg", season, episode, pos, 600_000)

    @Test
    fun newestFirstAndOneCardPerTitle() {
        var list = emptyList<Recent>()
        list = Resume.mergeRecent(list, entry("1", episode = "1"))
        list = Resume.mergeRecent(list, entry("2"))
        list = Resume.mergeRecent(list, entry("1", episode = "2"))
        assertEquals(listOf("1", "2"), list.map { it.id })
        assertEquals("2", list.first().episode)
    }

    @Test
    fun keepsAtMostTwelve() {
        var list = emptyList<Recent>()
        repeat(20) { list = Resume.mergeRecent(list, entry("$it")) }
        assertEquals(12, list.size)
        assertEquals("19", list.first().id)
        assertTrue(list.none { it.id == "7" })
    }

    @Test
    fun finishingAnEpisodeDropsOnlyThatEpisode() {
        val list = listOf(entry("1", episode = "2"), entry("2"))
        assertEquals(listOf("2"), Resume.dropRecent(list, "tv", "1", "1", "2").map { it.id })
        assertEquals(2, Resume.dropRecent(list, "tv", "1", "1", "1").size)
    }

    @Test
    fun decodeReadsStoredShapeAndRejectsGarbage() {
        val raw = """[{"kind":"movie","id":"9","title":"Heat","poster":"","s":"","e":"","pos":120000,"dur":0}]"""
        val one = Resume.decode(raw)
        assertEquals(1, one.size)
        assertEquals("Heat", one[0].title)
        assertNull(one[0].poster)
        assertTrue(Resume.decode("not json").isEmpty())
        assertTrue(Resume.decode(null).isEmpty())
    }

    @Test
    fun nextEpisodePicksTheFollowingNumber() {
        assertEquals(4, nextInSeason(listOf(1, 2, 3, 4), 3))
        assertEquals(2, nextInSeason(listOf(1, 3, 2), 1))
        assertNull(nextInSeason(listOf(1, 2, 3), 3))
    }

    @Test
    fun outroWindowNeverOpensLaterThanThreeMinutesFromTheEnd() {
        // credits running to the end of the file: capped, a late index must not hide the pill
        assertEquals(2_270_000L..2_450_000L, outroWindow(2_387_000L..2_450_000L, 2_450_000L, true))
        // an ED that stops before the next-episode preview is trusted, minus the lead-in
        assertEquals(1_302_000L..1_376_000L, outroWindow(1_317_000L..1_376_000L, 1_407_000L, true))
        // no index at all still offers the last three minutes
        assertEquals(2_270_000L..2_450_000L, outroWindow(null, 2_450_000L, true))
        assertNull(outroWindow(null, 2_450_000L, false))
    }
}
