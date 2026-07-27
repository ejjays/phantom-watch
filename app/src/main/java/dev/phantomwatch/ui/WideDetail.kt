package dev.phantomwatch.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.phantomwatch.R
import dev.phantomwatch.data.Title
import dev.phantomwatch.data.TitleDetail

/**
 * Tablet detail: fullscreen art on the left, scrolling sheet with the watch button on the right.
 * The art fades and drifts in from the left edge while the sheet rises from below.
 */
@Composable
internal fun WideDetail(
    item: Title,
    d: TitleDetail,
    pending: Boolean,
    photosReady: Boolean,
    checking: Boolean,
    onClose: () -> Unit,
    onWatch: (Title, String, String) -> Unit,
    onWatchPress: () -> Unit,
    onViewAll: () -> Unit,
) {
    val enter = remember(item.id) { Animatable(0f) }
    LaunchedEffect(item.id) { enter.animateTo(1f, tween(460, easing = FastOutSlowInEasing)) }
    val art = (item.backdrop ?: item.poster) ?: (d.backdrop ?: d.poster)
    // The sheet floats on the title's own colour instead of flat black, like the player's glow.
    val glow = rememberImageGlow(art)
    Box(Modifier.fillMaxSize()) {
        // Behind the whole row, not just the sheet: the art slides off its own layout box and the
        // exposed strip has to be tinted too, otherwise a black band rides along with the animation.
        Box(
            Modifier.matchParentSize().background(
                Brush.horizontalGradient(0f to glow, 0.52f to glow, 1f to Color.Transparent),
            ),
        )
        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier.weight(1.1f).fillMaxHeight().graphicsLayer {
                    alpha = enter.value
                    translationX = -140.dp.toPx() * (1f - enter.value)
                },
            ) {
                if (art != null) {
                    AsyncImage(
                        model = art, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(SurfaceColor))
                }
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0.6f to Color.Transparent, 1f to glow)))
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.statusBarsPadding().padding(start = 16.dp, top = 8.dp)
                        .clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)),
                ) {
                    Icon(painterResource(R.drawable.ic_back), contentDescription = "Back", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Box(
                    Modifier.fillMaxSize()
                        .statusBarsPadding().navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        Modifier.clip(RoundedCornerShape(32.dp)).background(SurfaceColor)
                            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(32.dp))
                            .graphicsLayer { translationY = 96.dp.toPx() * (1f - enter.value) },
                    ) {
                        // fill = false: the card hugs a short synopsis and only takes the full height when it must scroll.
                        Column(
                            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
                        ) {
                            DetailBody(
                                item = item, d = d, pending = pending, photosReady = photosReady,
                                onEpisode = { s, e -> onWatch(item, s, e) },
                                onViewAll = onViewAll,
                            )
                        }
                        WatchBar(
                            isTv = d.kind != "movie", checking = checking, onWatch = onWatchPress,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
