package dev.phantomwatch.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// one place that knows where the user is: screens push and pop, back is just pop()
sealed interface Route {
    val path: String

    data object Home : Route {
        override val path = "/"
    }

    data object Search : Route {
        override val path = "/search"
    }

    data object Clips : Route {
        override val path = "/clips"
    }

    data class Rail(val key: String) : Route {
        override val path get() = "/rail/$key"
    }

    data object History : Route {
        override val path = "/history"
    }

    data object You : Route {
        override val path = "/you"
    }

    companion object {
        // only routes whose data is already on hand survive a cold start: a rail or title needs
        // a fetch first, so those land back on home
        fun fromPath(path: String?): Route = when (path) {
            Search.path -> Search
            History.path -> History
            You.path -> You
            else -> Home
        }
    }
}

internal fun tabRoute(index: Int): Route = when (index) {
    1 -> Route.Clips
    2 -> Route.History
    3 -> Route.You
    else -> Route.Home
}

internal fun Route.tabIndex(): Int = when (this) {
    Route.Clips -> 1
    Route.History -> 2
    Route.You -> 3
    else -> 0
}

class NavStack(start: Route = Route.Home) {
    var routes by mutableStateOf(listOf(start))
        private set

    val top: Route get() = routes.last()
    val canPop: Boolean get() = routes.size > 1

    fun push(route: Route) {
        if (routes.last() != route) routes = routes + route
    }

    fun pop(): Boolean {
        if (!canPop) return false
        routes = routes.dropLast(1)
        return true
    }

    // tabs are siblings, not a stack: switching one discards whatever was pushed on top
    fun select(route: Route) {
        routes = if (route == Route.Home) listOf(route) else listOf(Route.Home, route)
    }
}
