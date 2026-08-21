package dev.phantomwatch.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.phantomwatch.data.Title
import dev.phantomwatch.ui.Bg
import dev.phantomwatch.ui.CommentIcon

@OptIn(ExperimentalMaterial3Api::class)
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
    // Tablets watch like YouTube: video over the info on the left, up-next rail on the right.
    if (tablet) {
        // Captions keyed to a fixed size: the pane's box is far taller than a phone stage. Lifted
        // clear of the bottom edge, which is the screen edge in fullscreen.
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
    var info by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        stage(inset, null)
        if (tablet) Box(
            Modifier.align(Alignment.CenterEnd).padding(end = 12.dp)
                .clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
                .clickable { info = true }
                .padding(13.dp),
        ) {
            CommentIcon(contentDescription = "Info", tint = Color.White, size = 22.dp)
        }
        if (info) {
            ModalBottomSheet(
                onDismissRequest = { info = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = Bg,
                contentColor = Color.White,
                contentWindowInsets = { WindowInsets(0) },
            ) {
                Box(Modifier.fillMaxHeight(0.85f)) {
                    PlayerPanel(
                        media = media,
                        season = season,
                        episode = episode,
                        overview = overview,
                        onEpisode = onEpisode,
                        onOpenTitle = onOpenTitle,
                    )
                }
            }
        }
    }
}
