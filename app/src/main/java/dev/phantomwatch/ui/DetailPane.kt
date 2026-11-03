package dev.phantomwatch.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import dev.phantomwatch.core.isWifi
import dev.phantomwatch.data.Resume
import dev.phantomwatch.data.Title
import dev.phantomwatch.provider.Resolve

@Composable
internal fun DetailPane(
    item: Title?,
    onClose: () -> Unit,
    onWatch: (Title, String, String) -> Unit,
    startEpisodes: Boolean,
    onEpisodesExit: (() -> Unit)?,
) {
    if (item == null) return
    val ctx = LocalContext.current
    DetailScreen(
        item = item,
        onClose = onClose,
        onWatch = onWatch,
        startEpisodes = startEpisodes,
        onEpisodesExit = onEpisodesExit,
        prefetchStream = { t ->
            if (isWifi(ctx)) {
                val (s, e) = if (t.kind == "movie") "" to ""
                else {
                    val r = Resume.recent(ctx).firstOrNull { it.kind == t.kind && it.id == t.id }
                    (r?.season?.ifBlank { "1" } ?: "1") to (r?.episode?.ifBlank { "1" } ?: "1")
                }
                Resolve.prefetch(t, s, e)
            }
        },
        precacheImages = { urls ->
            val loader = SingletonImageLoader.get(ctx)
            urls.forEach { loader.enqueue(ImageRequest.Builder(ctx).data(it).build()) }
        },
        wideDetail = { a -> TabletDetail(a) },
    )
}
