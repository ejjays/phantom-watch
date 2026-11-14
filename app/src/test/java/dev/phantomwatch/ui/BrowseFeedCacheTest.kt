package dev.phantomwatch.ui

import dev.phantomwatch.data.Title
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseFeedCacheTest {
    private val country = Country("PH", "Philippines")
    private val dune = Title("438631", "movie", "Dune", "https://img/p.jpg", "https://img/b.jpg", 8.1, "2021", "spice")

    @Test
    fun roundTripsTheFeed() {
        val state = BrowseState()
        state.trending = listOf(dune)

        val raw = state.feedJson(country)!!
        val restored = BrowseState()
        restored.restoreFeed(raw, country)

        assertEquals(listOf(dune), restored.trending)
    }

    @Test
    fun writesNothingForAnEmptyFeed() {
        assertNull(BrowseState().feedJson(country))
    }

    @Test
    fun keepsTheFetchTimeSoAFreshColdStartDoesNotRefetch() {
        val state = BrowseState()
        state.trending = listOf(dune)
        val restored = BrowseState()
        restored.restoreFeed(state.feedJson(country)!!, country)
        assertTrue(restored.loaded)
        assertTrue(restored.loadedAt > 0)
    }

    @Test
    fun keepsTheLocalChartRailWithItsRanks() {
        val raw = """{"v":4,"at":1,"rails":[{"key":"local","page":1,"pages":1,"ranks":[7],""" +
            """"items":[{"id":"438631","kind":"movie","title":"Dune"}]}]}"""
        val state = BrowseState()
        state.restoreFeed(raw, country)
        assertEquals("local", state.rails.single().key)
        assertEquals(listOf(7), state.rails.single().ranks)

        val again = state.feedJson(country)!!
        assertTrue(again.contains("\"key\":\"local\""))
        assertTrue(again.contains("\"ranks\":[7]"))
    }

    @Test
    fun ignoresACacheFromAnotherVersion() {
        val restored = BrowseState()
        restored.restoreFeed("""{"v":99,"trending":[{"id":"1","kind":"movie","title":"x"}]}""", country)
        assertTrue(restored.trending.isEmpty())
    }

    @Test
    fun neverOverwritesAFeedThatIsAlreadyShowing() {
        val state = BrowseState()
        state.trending = listOf(dune)
        state.restoreFeed("""{"v":4,"trending":[{"id":"2","kind":"tv","title":"other"}]}""", country)
        assertEquals(listOf(dune), state.trending)
    }
}
