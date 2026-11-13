package dev.phantomwatch.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.phantomwatch.data.Settings
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
    val ctx = LocalContext.current
    val subScale = remember {
        Settings.sizeScale(Settings.choice(ctx, Settings.SUBS_SIZE, Settings.SIZE_MEDIUM))
    }
    if (tablet) {
        val subPx = with(LocalDensity.current) {
            val size = (if (fullscreen) 26.dp else 20.dp).toPx() * subScale
            size to (if (fullscreen) -40.dp else -28.dp).toPx()
        }
        PlayerPanel(
            media = media, season = season, episode = episode, overview = overview,
            onEpisode = onEpisode, onOpenTitle = onOpenTitle,
            video = { stage(0.dp, subPx) }, videoAspect = videoAspect,
            fullscreen = fullscreen,
        )
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val style = with(LocalDensity.current) {
            maxHeight.toPx() * Settings.SUBS_BASE_FRACTION * subScale to -14.dp.toPx()
        }
        stage(0.dp, style)
    }
}
