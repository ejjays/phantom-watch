package dev.phantomwatch.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.phantomwatch.data.Title
import dev.phantomwatch.data.TitleDetail
import dev.phantomwatch.res.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun WideDetail(
    item: Title,
    d: TitleDetail,
    pending: Boolean,
    photosReady: Boolean,
    checking: Boolean,
    onClose: () -> Unit,
    onWatch: (Title, String, String) -> Unit,
    onWatchPress: () -> Unit,
    onViewAll: () -> Unit,
    onArtBounds: (Rect) -> Unit = {},
    hero: @Composable (Modifier) -> Unit = { m ->
        val art = (item.backdrop ?: item.poster) ?: (d.backdrop ?: d.poster)
        if (art != null) AsyncImage(art, contentDescription = null, modifier = m, contentScale = ContentScale.Crop)
    },
) {
    val enter = remember(item.id) { Animatable(0f) }
    LaunchedEffect(item.id) { enter.animateTo(1f, tween(460, easing = FastOutSlowInEasing)) }
    Row(Modifier.fillMaxSize()) {
        Box(
            Modifier.weight(1.1f).fillMaxHeight().graphicsLayer {
                alpha = enter.value
                translationX = -140.dp.toPx() * (1f - enter.value)
            },
        ) {
            Box(
                Modifier.align(Alignment.Center).padding(horizontal = 24.dp).fillMaxWidth()
                    .aspectRatio(16f / 9f).onGloballyPositioned { onArtBounds(it.boundsInRoot()) },
            ) {
                Box(
                    Modifier.matchParentSize().shadow(20.dp, RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp)).background(SurfaceColor)
                        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(24.dp)),
                ) {
                    hero(Modifier.matchParentSize())
                }
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 16.dp, top = 8.dp)
                    .clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)),
            ) {
                Icon(painterResource(Res.drawable.ic_back), contentDescription = stringResource(Res.string.common_back), tint = Color.White, modifier = Modifier.size(22.dp))
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
