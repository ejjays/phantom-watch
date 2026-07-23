package dev.phantomwatch.ui

import androidx.compose.runtime.Composable
import dev.phantomwatch.data.Title

@Composable
internal fun DetailPane(
    item: Title?,
    onClose: () -> Unit,
    onWatch: (Title, String, String) -> Unit,
    startEpisodes: Boolean,
    onEpisodesExit: (() -> Unit)?,
) {
    if (item == null) return
    DetailScreen(
        item = item,
        onClose = onClose,
        onWatch = onWatch,
        startEpisodes = startEpisodes,
        onEpisodesExit = onEpisodesExit,
    )
}
