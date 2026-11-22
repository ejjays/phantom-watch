package dev.phantomwatch.web

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.phantomwatch.core.PlaySource
import dev.phantomwatch.core.SubSource
import dev.phantomwatch.core.ioDispatcher
import dev.phantomwatch.core.pwLog
import dev.phantomwatch.core.resolveSources
import dev.phantomwatch.data.DetailCache
import dev.phantomwatch.data.Episode
import dev.phantomwatch.data.Resume
import dev.phantomwatch.data.SettingsKeys
import dev.phantomwatch.data.SeasonInfo
import dev.phantomwatch.data.Title
import dev.phantomwatch.data.TitleDetail
import dev.phantomwatch.data.Tmdb
import dev.phantomwatch.data.TmdbHost
import dev.phantomwatch.res.*
import dev.phantomwatch.ui.Bg
import dev.phantomwatch.ui.Cyan
import dev.phantomwatch.ui.Lilita
import dev.phantomwatch.ui.PlatformBackHandler
import dev.phantomwatch.ui.PosterCard
import dev.phantomwatch.ui.WorkSans
import dev.phantomwatch.ui.Slate200
import dev.phantomwatch.ui.Slate300
import dev.phantomwatch.ui.Slate100
import dev.phantomwatch.ui.Slate500
import dev.phantomwatch.ui.Slate400
import dev.phantomwatch.ui.SurfaceColor
import dev.phantomwatch.ui.WebPlayer
import dev.phantomwatch.ui.fmt1
import dev.phantomwatch.ui.isExpanded
import kotlinx.browser.document
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.math.roundToInt
import kotlin.time.TimeSource
import org.w3c.dom.events.Event
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

internal enum class PlayerPanel { None, Episodes, Plot }

@Composable
internal fun ResolveError(err: String, onRetry: () -> Unit, onBack: () -> Unit) {
    val tint = ButtonDefaults.textButtonColors(contentColor = Cyan)
    Row(
        Modifier.fillMaxWidth().background(SurfaceColor).padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            err, color = Slate300, fontSize = 14.sp, fontFamily = WorkSans,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry, colors = tint) { Text(stringResource(Res.string.common_retry)) }
        TextButton(onClick = onBack, colors = tint) { Text(stringResource(Res.string.common_back)) }
    }
}

@Composable
internal fun Info(
    media: Title,
    d: TitleDetail?,
    synopsis: String?,
    isTv: Boolean,
    episodes: List<Episode>,
    loadingEpisodes: Boolean,
    season: String,
    episode: String,
    onEpisode: (String, String) -> Unit,
    onOpenPlot: () -> Unit,
    onAllEpisodes: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier) {
        Text(
            media.title, color = Color.White, fontSize = 24.sp, fontFamily = Lilita,
            fontWeight = FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 20.dp)
                .clickable(enabled = synopsis != null) { onOpenPlot() },
        )
        if (synopsis != null) {
            Spacer(Modifier.height(16.dp))
            Column(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(SurfaceColor).clickable { onOpenPlot() }
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                Text(
                    stringResource(Res.string.player_story_plot), color = Color.White, fontSize = 16.sp,
                    fontFamily = WorkSans, fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    synopsis, color = Slate300, fontSize = 15.sp, fontFamily = WorkSans,
                    lineHeight = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            // no web client for social backend yet, thumbs are inert
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionIcon(Res.drawable.ic_like, stringResource(Res.string.player_like))
                ActionIcon(Res.drawable.ic_dislike, stringResource(Res.string.player_dislike))
                ActionIcon(Res.drawable.ic_comment, stringResource(Res.string.comments_title))
                Spacer(Modifier.weight(1f))
                ActionIcon(Res.drawable.ic_share, stringResource(Res.string.common_share))
            }
        }
        if (isTv) {
            Spacer(Modifier.height(22.dp))
            EpisodeStrip(
                episodes = episodes, loading = loadingEpisodes, season = season, episode = episode,
                onEpisode = onEpisode, onAll = onAllEpisodes,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
internal fun EpisodeStrip(
    episodes: List<Episode>,
    loading: Boolean,
    season: String,
    episode: String,
    onEpisode: (String, String) -> Unit,
    onAll: () -> Unit,
) {
    val list = rememberLazyListState()
    var fresh by remember { mutableStateOf(true) }
    LaunchedEffect(episode) {
        fresh = true
        delay(3000)
        fresh = false
    }
    LaunchedEffect(episode, episodes.size) {
        val at = episodes.indexOfFirst { it.n.toString() == episode }
        if (at >= 0) list.scrollToItem((at - 1).coerceAtLeast(0))
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stripW = minOf(
            maxWidth,
            EpisodePad * 2 + (EpisodeChip + EpisodeGap) * EpisodesVisible + EpisodePeek,
            if (episodes.isEmpty()) maxWidth
            else EpisodePad * 2 + (EpisodeChip + EpisodeGap) * episodes.size - EpisodeGap,
        )
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                Modifier.width(stripW).padding(horizontal = EpisodePad),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle(stringResource(Res.string.episodes_title))
                Spacer(Modifier.weight(1f))
                Row(Modifier.clickable { onAll() }, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(Res.string.player_all_episodes), color = Slate200, fontSize = 13.sp,
                        fontFamily = WorkSans, fontWeight = FontWeight.Medium,
                    )
                    Icon(
                        painterResource(Res.drawable.ic_chevron_right), contentDescription = null,
                        tint = Slate200, modifier = Modifier.size(15.dp),
                    )
                }
            }
            when {
                loading && episodes.isEmpty() -> Box(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Cyan, modifier = Modifier.size(22.dp))
                }
                episodes.isEmpty() -> Text(
                    stringResource(Res.string.player_no_episodes), color = Slate500, fontSize = 13.sp,
                    fontFamily = WorkSans, modifier = Modifier.padding(horizontal = 20.dp).padding(vertical = 12.dp),
                )
                else -> {
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.width(stripW).clip(RoundedCornerShape(12.dp))) {
                        LazyRow(
                            state = list,
                            contentPadding = PaddingValues(horizontal = EpisodePad),
                            horizontalArrangement = Arrangement.spacedBy(EpisodeGap),
                        ) {
                            items(episodes, key = { it.n }) { ep ->
                                val active = ep.n.toString() == episode
                                val bg by animateColorAsState(
                                    when {
                                        !active -> Color.White.copy(alpha = 0.08f)
                                        fresh -> Cyan
                                        else -> Slate300
                                    },
                                    tween(700),
                                    label = "ep-chip",
                                )
                                val fg by animateColorAsState(
                                    if (active) Bg else Slate200, tween(700), label = "ep-chip-text",
                                )
                                Box(
                                    Modifier.size(EpisodeChip, 40.dp).clip(RoundedCornerShape(10.dp))
                                        .background(bg)
                                        .clickable { onEpisode(season, ep.n.toString()) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        ep.n.toString(), color = fg,
                                        fontSize = 15.sp, fontFamily = WorkSans, fontWeight = FontWeight.Medium,
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
private fun ActionIcon(res: DrawableResource, label: String) {
    Box(Modifier.size(40.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
        Icon(
            painterResource(res), contentDescription = label, tint = Slate300,
            modifier = Modifier.size(24.dp),
        )
    }
}

// PlayerPanel.kt railSheets timing: 300 in, 280 out on FastOutSlowIn
@Composable
internal fun Sheet(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it },
        exit = slideOutVertically(tween(280, easing = FastOutSlowInEasing)) { it },
        modifier = modifier,
    ) { content() }
}

@Composable
internal fun PanelBody(
    panel: PlayerPanel,
    media: Title,
    d: TitleDetail?,
    synopsis: String?,
    sel: String,
    playing: String,
    episode: String,
    episodes: List<Episode>,
    loading: Boolean,
    onSeason: (String) -> Unit,
    onEpisode: (String, String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    // panel sliding away keeps content until it left screen
    var shown by remember { mutableStateOf(panel) }
    if (panel != PlayerPanel.None) shown = panel
    PlatformBackHandler(enabled = true) { onClose() }
    Column(modifier.background(Bg)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (shown == PlayerPanel.Plot) {
                    stringResource(Res.string.player_story_plot)
                } else {
                    stringResource(Res.string.episodes_title)
                },
                color = Color.White, fontSize = 18.sp, fontFamily = WorkSans,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).clickable { onClose() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(Res.drawable.ic_close),
                    contentDescription = stringResource(Res.string.common_close),
                    tint = Slate400, modifier = Modifier.size(18.dp),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
        when (shown) {
            PlayerPanel.Plot -> PlotBody(media, d, synopsis.orEmpty())
            PlayerPanel.Episodes -> EpisodesBody(d, sel, playing, episode, episodes, loading, onSeason, onEpisode)
            PlayerPanel.None -> Unit
        }
    }
}

@Composable
private fun EpisodesBody(
    d: TitleDetail?,
    sel: String,
    playing: String,
    episode: String,
    episodes: List<Episode>,
    loading: Boolean,
    onSeason: (String) -> Unit,
    onEpisode: (String, String) -> Unit,
) {
    val seasons = d?.seasons.orEmpty()
    if (seasons.size > 1) SeasonPicker(seasons, sel, onSeason)
    val list = rememberLazyListState()
    LaunchedEffect(episodes, episode, sel) {
        if (sel != playing) return@LaunchedEffect
        val at = episodes.indexOfFirst { it.n.toString() == episode }
        if (at >= 0) list.scrollToItem(at)
    }
    when {
        loading && episodes.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Cyan, modifier = Modifier.size(22.dp))
        }
        episodes.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(Res.string.player_no_episodes), color = Slate500,
                fontSize = 14.sp, fontFamily = WorkSans,
            )
        }
        else -> LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
            items(episodes, key = { it.n }) { e ->
                EpisodeRow(e, sel == playing && e.n.toString() == episode) {
                    onEpisode(sel, e.n.toString())
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.05f)))
            }
        }
    }
}

@Composable
private fun EpisodeRow(e: Episode, current: Boolean, onClick: () -> Unit) {
    val density = LocalDensity.current
    val windowW = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    // web dp is one css px, phone dp is a fraction of a much narrower screen. lift the rows on
    // wide windows so the list reads at the same relative size as the Android sidebar
    val wide = isExpanded(windowW)
    val padH = if (wide) 18.dp else 14.dp
    val padV = if (wide) 14.dp else 10.dp
    val thumbW = if (wide) 96.dp else 72.dp
    val gap = if (wide) 13.dp else 10.dp
    val nameSize = if (wide) 17.sp else 14.sp
    val lineH = if (wide) 22.sp else 18.sp
    val epSize = if (wide) 13.sp else 11.sp
    Row(
        Modifier.fillMaxWidth()
            .background(if (current) Color.White.copy(alpha = 0.06f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = padH, vertical = padV),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(thumbW).aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            val still = e.still
            if (still != null) {
                AsyncImage(
                    model = still, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize(),
                )
            } else {
                Icon(
                    painterResource(Res.drawable.ic_play_outline), contentDescription = null,
                    tint = Slate400, modifier = Modifier.size(if (wide) 20.dp else 16.dp),
                )
            }
        }
        Spacer(Modifier.width(gap))
        Column(Modifier.weight(1f)) {
            Text(
                e.name, color = if (current) Cyan else Slate100, fontSize = nameSize,
                lineHeight = lineH, fontFamily = WorkSans,
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(if (wide) 3.dp else 2.dp))
            Text(
                stringResource(Res.string.player_episode_no, e.n),
                color = if (current) Cyan.copy(alpha = 0.75f) else Slate500,
                fontSize = epSize, fontFamily = WorkSans,
            )
        }
    }
}

@Composable
private fun SeasonPicker(seasons: List<SeasonInfo>, season: String, onSeason: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = seasons.firstOrNull { it.number.toString() == season } ?: seasons.firstOrNull()
    Box {
        Row(
            Modifier.fillMaxWidth().clickable { open = true }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                current?.name ?: stringResource(Res.string.player_season, season.toIntOrNull() ?: 1),
                color = Cyan, fontSize = 14.sp, fontFamily = WorkSans, fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                painterResource(Res.drawable.ic_chevron_right), contentDescription = null,
                tint = Cyan, modifier = Modifier.size(16.dp).rotate(90f),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = SurfaceColor) {
            seasons.forEach { s ->
                DropdownMenuItem(
                    text = {
                        Text(
                            s.name,
                            color = if (s.number.toString() == season) Cyan else Slate100,
                            fontSize = 14.sp, fontFamily = WorkSans,
                        )
                    },
                    onClick = {
                        onSeason(s.number.toString())
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PlotBody(media: Title, d: TitleDetail?, synopsis: String) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text(media.title, color = Color.White, fontSize = 20.sp, fontFamily = Lilita, lineHeight = 28.sp)
        Spacer(Modifier.height(14.dp))
        Text(synopsis, color = Slate300, fontSize = 15.sp, fontFamily = WorkSans, lineHeight = 24.sp)
        val year = d?.year ?: media.year
        val cards = buildList {
            d?.rating?.takeIf { it > 0 }?.let { add(fmt1(it) to stringResource(Res.string.stat_rating)) }
            d?.votes?.takeIf { it > 0 }?.let { add(compact(it) to stringResource(Res.string.stat_votes)) }
            year?.let { add(it to stringResource(Res.string.stat_released)) }
        }
        if (cards.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                cards.forEach { (value, label) ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(value, color = Color.White, fontSize = 18.sp, fontFamily = WorkSans, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(2.dp))
                        Text(label, color = Slate400, fontSize = 11.sp, fontFamily = WorkSans)
                    }
                }
            }
        }
        val genres = d?.genres.orEmpty()
        if (genres.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                genres.take(3).forEach { g ->
                    Box(
                        Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(g, color = Slate200, fontSize = 12.sp, fontFamily = WorkSans, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun compact(n: Int): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}.${(n % 1_000_000) / 100_000}M"
    n >= 1_000 -> "${n / 1_000}.${(n % 1_000) / 100}K"
    else -> n.toString()
}

@Composable
internal fun SimilarColumn(items: List<Title>, onOpen: (Title) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(key = "header") { SectionTitle(stringResource(Res.string.player_more_like_this)) }
        items(items, key = { it.kind + "-" + it.id }) { t ->
            Column(Modifier.fillMaxWidth().clickable { onOpen(t) }) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(14.dp)).background(SurfaceColor),
                ) {
                    val art = t.backdrop ?: t.poster
                    if (art != null) {
                        AsyncImage(
                            model = art, contentDescription = t.title,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    t.title, color = Color.White, fontSize = 14.sp, fontFamily = WorkSans,
                    fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                val meta = listOfNotNull(
                    t.year,
                    t.rating?.takeIf { it > 0 }?.let { fmt1(it) },
                ).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(meta, color = Slate400, fontSize = 12.sp, fontFamily = WorkSans, maxLines = 1)
                }
            }
        }
    }
}

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, color = Color.White, fontSize = 16.sp, fontFamily = WorkSans,
        fontWeight = FontWeight.SemiBold, modifier = modifier,
    )
}
