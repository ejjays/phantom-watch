package dev.phantomwatch.ui

import dev.phantomwatch.data.Title
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavTest {
    private val title = Title("1", "movie", "T", null, null, null, null, null)

    @Test
    fun coldStartOnlyRestoresTabs() {
        assertEquals(Route.Search, Route.fromPath(Route.Search.path))
        assertEquals(Route.Home, Route.fromPath("/player/movie/1/1/2"))
        assertEquals(Route.Home, Route.fromPath(null))
    }

    @Test
    fun tabsReplaceTheStack() {
        val nav = NavStack()
        nav.push(Route.Search)
        nav.select(Route.History)
        assertEquals(Route.History, nav.top)
        assertTrue(nav.canPop)
        nav.select(Route.Home)
        assertEquals(Route.Home, nav.top)
        assertFalse(nav.canPop)
    }

    @Test
    fun playerRouteCarriesItsBackTarget() {
        val route = Route.Player("T", null, title, "1", "2", Route.Sources(title, "1", "2"))
        assertEquals("/player/movie/1/1/2", route.path)
        assertEquals("Player(back=Sources)", route.navLabel())
    }
}
