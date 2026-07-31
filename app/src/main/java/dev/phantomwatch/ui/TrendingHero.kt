package dev.phantomwatch.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.phantomwatch.R
import dev.phantomwatch.core.RemoteConfig
import dev.phantomwatch.data.Title
import dev.phantomwatch.data.TitleDetail
import dev.phantomwatch.data.Tmdb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val HERO_MS = 9000

/** Full-bleed billboard: backdrop, left-aligned copy, progress dots. Slower than it looks. */
@Composable
internal fun TrendingHero(trending: List<Title>, paused: Boolean, onOpen: (Title) -> Unit, width: Dp, listState: LazyListState) {
    if (trending.isEmpty()) return
    val items = remember(trending) { trending.take(6) }
    val heroH = minOf(LocalConfiguration.current.screenHeightDp.dp * 0.7f, width * 0.62f).coerceAtLeast(240.dp)
    val heroPx = with(LocalDensity.current) { heroH.toPx() }
    // Off-screen the billboard must stop animating: its zoom repaints the whole hero every frame.
    val hidden by remember(listState, heroPx) {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset >= heroPx.toInt() }
    }
    val idle = paused || hidden
    var index by remember(items) { mutableIntStateOf(0) }
    var details by remember(items) { mutableStateOf<Map<String, TitleDetail>>(emptyMap()) }
    val progress = remember { Animatable(0f) }
    val key = remember { RemoteConfig.tmdbKey() }

    LaunchedEffect(index, items, key) {
        for (i in listOf(index, (index + 1) % items.size)) {
            val item = items[i]
            val id = "${item.kind}-${item.id}"
            if (details[id] != null) continue
            withContext(Dispatchers.IO) { Tmdb.detail(key, item.kind, item.id) }?.let { details = details + (id to it) }
        }
    }

    LaunchedEffect(index, idle, items) {
        progress.snapTo(0f)
        if (idle || items.size < 2) return@LaunchedEffect
        progress.animateTo(1f, tween(HERO_MS, easing = LinearEasing))
        index = (index + 1) % items.size
    }

    BoxWithConstraints(Modifier.fillMaxWidth().height(heroH).clipToBounds()) {
        val textW = minOf(520.dp, maxWidth * 0.62f)
        val compact = heroH < 320.dp
        val zoom = remember { Animatable(1.06f) }
        // A scaled full-bleed bitmap is re-composited every frame, so never pay for it during a scroll.
        val scrolling = listState.isScrollInProgress
        LaunchedEffect(hidden, scrolling) {
            if (hidden || scrolling) zoom.stop()
            else zoom.animateTo(1.15f, infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse))
        }

        Crossfade(index, animationSpec = tween(1200), label = "hero-art") { i ->
            val art = items[i].backdrop ?: items[i].poster
            if (art != null) {
                AsyncImage(
                    model = art, contentDescription = null, contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom.value; scaleY = zoom.value },
                )
            } else {
                Box(Modifier.fillMaxSize().background(SurfaceColor))
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Bg.copy(alpha = 0.94f), 0.3f to Bg.copy(alpha = 0.55f), 0.65f to Color.Transparent),
            ),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Bg)))

        Crossfade(index, animationSpec = tween(450), label = "hero-text") { i ->
            val item = items[i]
            val detail = details["${item.kind}-${item.id}"]
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 26.dp).width(textW)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("PHANTOM", color = Cyan, fontSize = 11.sp, fontFamily = Rubik, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(if (item.kind == "movie") "FILM" else "SERIES", color = Slate400, fontSize = 11.sp, fontFamily = Rubik, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        item.title, color = Color.White, fontFamily = Rubik, fontWeight = FontWeight.Bold,
                        fontSize = if (compact) 28.sp else 40.sp, lineHeight = if (compact) 30.sp else 42.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(9.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        if (item.rating != null && item.rating > 0) {
                            Icon(painterResource(R.drawable.ic_star), contentDescription = null, tint = StarYellow, modifier = Modifier.size(13.dp))
                            Text(String.format("%.1f", item.rating), color = Slate200, fontSize = 13.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold)
                        }
                        item.year?.let { Text(it, color = Slate300, fontSize = 13.sp, fontFamily = Rubik, fontWeight = FontWeight.Medium) }
                        detail?.runtime?.takeIf { it > 0 }?.let {
                            Text(if (it >= 60) "${it / 60}h ${it % 60}m" else "${it}m", color = Slate300, fontSize = 13.sp, fontFamily = Rubik, fontWeight = FontWeight.Medium)
                        }
                        Text(
                            if (item.kind == "movie") "Film" else "Series", color = Slate200, fontSize = 11.sp,
                            fontFamily = Rubik, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.16f)).padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                        detail?.genres?.take(3)?.takeIf { it.isNotEmpty() }?.let {
                            Text(it.joinToString(" · "), color = Slate300, fontSize = 13.sp, fontFamily = Rubik, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    val desc = item.overview ?: detail?.overview
                    if (!compact && !desc.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(desc, color = Color.White.copy(alpha = 0.88f), fontSize = 14.sp, lineHeight = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(16.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(Color.White)
                            .pressClick { onOpen(item) }.padding(horizontal = 26.dp, vertical = 12.dp),
                    ) {
                        Text("Details", color = Color.Black, fontSize = 14.sp, fontFamily = Rubik, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Row(
            Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 29.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(items.size) { i ->
                val on = i == index
                Box(
                    Modifier.height(3.dp).width(if (on) 40.dp else 20.dp)
                        .clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.25f))
                        .clickable { index = i },
                ) {
                    if (on) {
                        Box(
                            Modifier.fillMaxSize()
                                .graphicsLayer { scaleX = progress.value; transformOrigin = TransformOrigin(0f, 0.5f) }
                                .background(Color.White),
                        )
                    }
                }
            }
        }
    }
}
