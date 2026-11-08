package dev.phantomwatch.player

import dev.phantomwatch.data.Stream
import dev.phantomwatch.data.Title

sealed interface Screen {
    data object Home : Screen
    data class Player(
        val title: String,
        val stream: Stream?,
        val media: Title,
        val season: String,
        val episode: String,
        val back: Screen,
    ) : Screen

    data class Sources(val title: Title, val season: String, val episode: String) : Screen
}

internal fun Screen.navLabel(): String = when (this) {
    is Screen.Player -> "Player(back=${back.navLabel()})"
    else -> this::class.simpleName ?: "?"
}
