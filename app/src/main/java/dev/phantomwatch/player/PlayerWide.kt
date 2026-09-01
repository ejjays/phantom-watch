package dev.phantomwatch.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.phantomwatch.data.Title

@Composable
internal fun PlayerWide(
    media: Title,
    season: String,
    episode: String,
    overview: String?,
    videoAspect: Float,
    fullscreen: Boolean,
    tablet: Boolean,
    onEpisode: (String, String) -> Unit,
    onOpenTitle: (Title) -> Unit,
    stage: @Composable (Dp, Pair<Float, Float>?) -> Unit,
) {
    if (tablet) {
        val subPx = with(LocalDensity.current) {
            (if (fullscreen) 26.dp else 20.dp).toPx() to (if (fullscreen) -40.dp else -28.dp).toPx()
        }
        PlayerPanel(
            media = media, season = season, episode = episode, overview = overview,
            onEpisode = onEpisode, onOpenTitle = onOpenTitle,
            video = { stage(0.dp, subPx) }, videoAspect = videoAspect,
            fullscreen = fullscreen,
        )
        return
    }
    val cfg = LocalConfiguration.current
    val fsW = maxOf(cfg.screenWidthDp, cfg.screenHeightDp).toFloat()
    val fsH = minOf(cfg.screenWidthDp, cfg.screenHeightDp).toFloat()
    val fsVideoH = if (videoAspect >= fsW / fsH) fsW / videoAspect else fsH
    val inset = ((fsH - fsVideoH) / 2).coerceAtLeast(0f).dp
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        stage(inset, null)
    }
}
