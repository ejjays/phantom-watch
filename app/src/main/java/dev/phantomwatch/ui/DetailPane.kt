package dev.phantomwatch.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import dev.phantomwatch.core.isWifi
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
        prefetchStream = { t -> if (isWifi(ctx)) Resolve.prefetch(t, "", "") },
        precacheImages = { urls ->
            val loader = SingletonImageLoader.get(ctx)
            urls.forEach { loader.enqueue(ImageRequest.Builder(ctx).data(it).build()) }
        },
        wideDetail = { a -> TabletDetail(a) },
    )
}
