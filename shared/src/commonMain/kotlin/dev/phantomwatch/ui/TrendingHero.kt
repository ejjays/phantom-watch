package dev.phantomwatch.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalDensity
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.phantomwatch.res.*
import dev.phantomwatch.core.ioDispatcher
import dev.phantomwatch.data.TmdbHost
import dev.phantomwatch.data.Title
import dev.phantomwatch.data.TitleDetail
import dev.phantomwatch.data.Tmdb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val HERO_MS = 9000

@Composable
fun TrendingHero(
    trending: List<Title>,
    paused: Boolean,
    onOpen: (Title) -> Unit,
    width: Dp,
    listState: LazyListState,
) {
    if (trending.isEmpty()) return
    val items = remember(trending) { trending.take(6) }
    if (isExpanded(width)) {
        TabletBillboard(items, paused, onOpen, width, listState)
    } else {
        PhoneHeroCarousel(items, paused, onOpen, listState)
    }
}

@Composable
private fun PhoneHeroCarousel(
    items: List<Title>,
    paused: Boolean,
    onOpen: (Title) -> Unit,
    listState: LazyListState,
) {
    val heroH = (screenHeightDp() * 0.58f).coerceIn(420.dp, 510.dp)
    val heroPx = with(LocalDensity.current) { heroH.toPx() }
    val hidden by remember(listState, heroPx) {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset >= heroPx.toInt() }
    }
    val idle = paused || hidden
    var index by remember(items) { mutableIntStateOf(0) }
    var details by remember(items) { mutableStateOf<Map<String, TitleDetail>>(emptyMap()) }
    val progress = remember { Animatable(0f) }
    val key = remember { TmdbHost.key }

    LaunchedEffect(index, items, key) {
        for (i in listOf(index, (index + 1) % items.size)) {
            val item = items[i]
            val id = "${item.kind}-${item.id}"
            if (details[id] != null) continue
            withContext(ioDispatcher) { Tmdb.detail(key, item.kind, item.id) }?.let { details = details + (id to it) }
        }
    }

    LaunchedEffect(index, idle, items) {
        progress.snapTo(0f)
        if (idle || items.size < 2) return@LaunchedEffect
        progress.animateTo(1f, tween(HERO_MS, easing = LinearEasing))
        index = (index + 1) % items.size
    }

    Box(Modifier.fillMaxWidth().height(heroH).clipToBounds()) {
        Crossfade(index, animationSpec = tween(1200), label = "hero-art") { page ->
            val art = items[page].backdrop ?: items[page].poster
            if (art != null) {
                AsyncImage(
                    model = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize().background(SurfaceColor))
            }
        }
        Box(
            Modifier.fillMaxWidth().height(160.dp).background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Bg.copy(alpha = 0.50f), 0.22f to Color.Transparent, 0.78f to Color.Transparent, 1f to Bg.copy(alpha = 0.50f)),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.38f to Color.Transparent,
                    0.68f to Bg.copy(alpha = 0.78f),
                    0.88f to Bg.copy(alpha = 0.96f),
                    1f to Bg,
                ),
            ),
        )

        Crossfade(index, animationSpec = tween(450), label = "hero-text") { page ->
            val item = items[page]
            val detail = details["${item.kind}-${item.id}"]
            Column(
                Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                HeroKind(item)
                Spacer(Modifier.height(5.dp))
                Text(
                    item.title,
                    color = Color.White,
                    fontFamily = Rubik,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    lineHeight = 30.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(7.dp))
                HeroMeta(item, detail)
                val desc = item.overview ?: detail?.overview
                if (!desc.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(desc, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    HeroActions(item, onOpen)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp),
                    ) {
                        repeat(items.size) { i ->
                            val on = i == index
                            Box(
                                Modifier
                                    .height(4.dp)
                                    .width(if (on) 18.dp else 5.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White.copy(alpha = 0.35f))
                                    .clickable { index = i },
                            ) {
                                if (on) {
                                    Box(
                                        Modifier.fillMaxSize()
                                            .graphicsLayer { scaleX = progress.value; transformOrigin = TransformOrigin(0f, 0.5f) }
                                            .background(Cyan),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroKind(item: Title, scale: Float = 1f) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(if (item.kind == "movie") Res.drawable.ic_kind_movie else Res.drawable.ic_kind_series),
            contentDescription = null,
            tint = Cyan,
            modifier = Modifier.size(15.dp * scale),
        )
        Spacer(Modifier.width(7.dp * scale))
        Text(
            if (item.kind == "movie") stringResource(Res.string.hero_kind_movie) else stringResource(Res.string.hero_kind_series),
            color = Cyan,
            fontSize = 11.sp * scale,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp * scale,
        )
    }
}

@Composable
private fun HeroMeta(item: Title, detail: TitleDetail?, scale: Float = 1f) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp * scale)) {
        item.rating?.takeIf { it > 0 }?.let { r ->
            Icon(painterResource(Res.drawable.ic_star), contentDescription = null, tint = StarYellow, modifier = Modifier.size(13.dp * scale))
            Text(fmt1(r), color = Slate200, fontSize = 12.sp * scale, fontFamily = Rubik, fontWeight = FontWeight.SemiBold)
        }
        item.year?.let { Text(it, color = Slate300, fontSize = 12.sp * scale, fontFamily = Rubik, fontWeight = FontWeight.Medium) }
        detail?.runtime?.takeIf { it > 0 }?.let {
            Text(
                if (it >= 60) stringResource(Res.string.hero_runtime_hm, it / 60, it % 60) else stringResource(Res.string.hero_runtime_m, it),
                color = Slate300, fontSize = 12.sp * scale, fontFamily = Rubik, fontWeight = FontWeight.Medium,
            )
        }
        detail?.genres?.take(2)?.takeIf { it.isNotEmpty() }?.let {
            Text(it.joinToString(" · "), color = Slate300, fontSize = 12.sp * scale, fontFamily = Rubik, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HeroActions(item: Title, onOpen: (Title) -> Unit, scale: Float = 1f) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp * scale), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier.height(42.dp * scale).clip(RoundedCornerShape(21.dp * scale)).background(Color.White)
                .pressClick { onOpen(item) }.padding(horizontal = 22.dp * scale),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(Res.drawable.ic_play_filled), contentDescription = null, tint = Color.Black, modifier = Modifier.size(15.dp * scale))
            Spacer(Modifier.width(7.dp * scale))
            Text(stringResource(Res.string.common_play), color = Color.Black, fontFamily = Rubik, fontWeight = FontWeight.Bold, fontSize = 14.sp * scale)
        }
        Row(
            modifier = Modifier.height(42.dp * scale).clip(RoundedCornerShape(21.dp * scale)).background(Color.White.copy(alpha = 0.16f))
                .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(21.dp * scale))
                .pressClick { onOpen(item) }.padding(horizontal = 18.dp * scale),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(Res.string.hero_details), color = Color.White, fontFamily = Rubik, fontWeight = FontWeight.SemiBold, fontSize = 14.sp * scale)
        }
    }
}

@Composable
private fun TabletBillboard(
    items: List<Title>,
    paused: Boolean,
    onOpen: (Title) -> Unit,
    width: Dp,
    listState: LazyListState,
) {
    val heroH = minOf(screenHeightDp() * 0.7f, width * 0.62f).coerceAtLeast(240.dp)
    val heroPx = with(LocalDensity.current) { heroH.toPx() }
    // off-screen the billboard must stop animating: its zoom repaints the whole hero every frame
    val hidden by remember(listState, heroPx) {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset >= heroPx.toInt() }
    }
    val idle = paused || hidden
    var index by remember(items) { mutableIntStateOf(0) }
    var details by remember(items) { mutableStateOf<Map<String, TitleDetail>>(emptyMap()) }
    val progress = remember { Animatable(0f) }
    val key = remember { TmdbHost.key }

    LaunchedEffect(index, items, key) {
        for (i in listOf(index, (index + 1) % items.size)) {
            val item = items[i]
            val id = "${item.kind}-${item.id}"
            if (details[id] != null) continue
            withContext(ioDispatcher) { Tmdb.detail(key, item.kind, item.id) }?.let { details = details + (id to it) }
        }
    }

    LaunchedEffect(index, idle, items) {
        progress.snapTo(0f)
        if (idle || items.size < 2) return@LaunchedEffect
        progress.animateTo(1f, tween(HERO_MS, easing = LinearEasing))
        index = (index + 1) % items.size
    }

    BoxWithConstraints(Modifier.fillMaxWidth().height(heroH).clipToBounds()) {
        val textW = minOf(680.dp, maxWidth * 0.62f)
        val s = (maxWidth / 1150.dp).coerceIn(1.2f, 1.5f)
        val zoom = remember { Animatable(1.06f) }
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
                    HeroKind(item, s)
                    Spacer(Modifier.height(6.dp * s))
                    Text(
                        item.title, color = Color.White, fontFamily = Rubik, fontWeight = FontWeight.Bold,
                        fontSize = 26.sp * s, lineHeight = 30.sp * s,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(9.dp * s))
                    HeroMeta(item, detail, s)
                    val desc = item.overview ?: detail?.overview
                    if (!desc.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp * s))
                        Text(desc, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp * s, lineHeight = 18.sp * s, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(14.dp * s))
                    HeroActions(item, onOpen, s)
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
