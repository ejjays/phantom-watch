package dev.phantomwatch.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.phantomwatch.core.Log
import dev.phantomwatch.data.TmdbHost
import dev.phantomwatch.core.ioDispatcher
import dev.phantomwatch.core.nowMs
import dev.phantomwatch.res.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.phantomwatch.data.DetailCache
import dev.phantomwatch.data.Episode
import dev.phantomwatch.data.Person
import dev.phantomwatch.data.Search
import dev.phantomwatch.data.Title
import dev.phantomwatch.data.TitleDetail
import dev.phantomwatch.data.Tmdb
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun ShimmerBox(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f)),
    )
}

// Everything the tablet layout needs, so the screen itself can stay platform-free.
class WideDetailArgs(
    val item: Title,
    val d: TitleDetail,
    val pending: Boolean,
    val photosReady: Boolean,
    val checking: Boolean,
    val onClose: () -> Unit,
    val onWatch: (Title, String, String) -> Unit,
    val onWatchPress: () -> Unit,
    val onViewAll: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    item: Title,
    onClose: () -> Unit,
    onWatch: (Title, String, String) -> Unit,
    startEpisodes: Boolean = false,
    onEpisodesExit: (() -> Unit)? = null,
    prefetchStream: (Title) -> Unit = {},
    precacheImages: (List<String>) -> Unit = {},
    wideDetail: (@Composable (WideDetailArgs) -> Unit)? = null,
) {
    val key = TmdbHost.key
    val scope = rememberCoroutineScope()
    var detail by remember(item.id) { mutableStateOf(DetailCache.detail(item.kind, item.id)) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    var episodesOpen by remember(startEpisodes) { mutableStateOf(startEpisodes) }
    var checking by remember { mutableStateOf(false) }
    var photosReady by remember { mutableStateOf(detail != null) }
    PlatformBackHandler(enabled = true) { onClose() }

    val t0 = remember(item.id, item.kind) { nowMs() }
    fun ms() = nowMs() - t0
    LaunchedEffect(item.id, item.kind) {
        Log.d("detail open ${item.kind}/${item.id} ${item.title}")
    }
    LaunchedEffect(item.id, item.kind) {
        if (item.kind != "movie") return@LaunchedEffect
        delay(1200)
        prefetchStream(item)
    }
    LaunchedEffect(item.id, item.kind) {
        delay(600)
        photosReady = true
        Log.d("detail photos ready")
    }
    LaunchedEffect(item.id, item.kind, attempt) {
        if (detail != null) return@LaunchedEffect
        failed = false
        val start = nowMs()
        Log.d("detail fetch start ${item.kind}/${item.id}")
        var result: TitleDetail? = null
        try {
            result = withContext(ioDispatcher) { Tmdb.detail(key, item.kind, item.id) }
        } catch (_: Exception) {
        }
        val elapsed = nowMs() - start
        val dd = result
        Log.d("detail fetch end +${elapsed}ms null=${dd == null} genres=${dd?.genres?.size ?: 0} cast=${dd?.cast?.size ?: 0} ov=${dd?.overview?.length ?: 0} heroSame=${(dd?.backdrop ?: dd?.poster) == (item.backdrop ?: item.poster)}")
        val precache = ((dd?.cast.orEmpty()) + listOfNotNull(dd?.director)).mapNotNull { it.photo }.take(9)
        if (precache.isNotEmpty()) {
            precacheImages(precache)
            Log.d("detail precache ${precache.size}")
        }
        val wait = 400 - elapsed
        if (wait > 0) delay(wait)
        detail = result
        failed = result == null
        if (result != null) DetailCache.put(result)
        Log.d("detail applied +${ms()}ms")
    }

    val wide = isExpanded(screenWidthDp())
    Box(Modifier.fillMaxSize().background(Bg)) {
        val d = detail ?: TitleDetail(
            id = item.id, kind = item.kind, title = item.title, overview = "",
            poster = item.poster, backdrop = item.backdrop, year = item.year,
            runtime = 0, rating = item.rating ?: 0.0, votes = 0,
            genres = emptyList(), cert = null, director = null,
            cast = emptyList(), seasons = emptyList(),
        )
        val pending = detail == null
        // returning from the player lands on episodes, so skip the detail body entirely
        if (!(startEpisodes && episodesOpen)) when {
            failed && detail == null -> Column(
                Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(Res.string.detail_unavailable), color = Slate100, fontSize = 15.sp, fontFamily = Mono, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.detail_load_failed), color = Slate400, fontSize = 12.sp, fontFamily = Mono)
                Text(
                    stringResource(Res.string.common_retry), color = Cyan, fontSize = 13.sp, fontFamily = Rubik,
                    modifier = Modifier.padding(top = 10.dp).clickable { attempt++ },
                )
            }
            wide && wideDetail != null -> wideDetail(
                WideDetailArgs(
                    item = item, d = d, pending = pending, photosReady = photosReady, checking = checking,
                    onClose = onClose,
                    onWatch = onWatch,
                    onWatchPress = {
                        scope.launch {
                            checking = true
                            try {
                                if (d.kind == "movie") onWatch(item, "", "") else episodesOpen = true
                            } finally {
                                checking = false
                            }
                        }
                    },
                    onViewAll = { episodesOpen = true },
                ),
            )
            else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                val heroH = minOf(413.dp, maxHeight * 0.5f)
                // The sheet has to reach the bottom of the window: a short synopsis would otherwise
                // leave a band of bare background between the card and the floating watch bar.
                val minCardH = (maxHeight - heroH + 120.dp).coerceAtLeast(0.dp)
                Column(Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState()).align(Alignment.TopCenter)) {
                    Box(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(bottomStart = 55.dp, bottomEnd = 55.dp))
                            .background(Color.White.copy(alpha = 0.05f)),
                    ) {
                        val hero = (item.backdrop ?: item.poster) ?: (d.backdrop ?: d.poster)
                        if (hero != null) {
                            AsyncImage(model = hero, contentDescription = null, modifier = Modifier.fillMaxWidth().height(heroH), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, onSuccess = { Log.d("detail hero ok +${ms()}ms") }, onError = { Log.d("detail hero err +${ms()}ms") })
                        } else {
                            Spacer(Modifier.fillMaxWidth().height(120.dp))
                        }
                    }
                    DetailBody(
                        item = item,
                        d = d,
                        pending = pending,
                        photosReady = photosReady,
                        onEpisode = { s, e -> onWatch(item, s, e) },
                        onViewAll = { episodesOpen = true },
                        modifier = Modifier.padding(horizontal = 16.dp).offset(y = (-120).dp)
                            .clip(RoundedCornerShape(32.dp)).background(SurfaceColor)
                            .heightIn(min = minCardH).padding(20.dp),
                    )
                    Spacer(Modifier.height(24.dp))
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.statusBarsPadding().padding(start = 16.dp, top = 8.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)),
                ) {
                    Icon(painterResource(Res.drawable.ic_back), contentDescription = stringResource(Res.string.common_back), tint = Color.White, modifier = Modifier.size(22.dp))
                }
                WatchBar(
                    isTv = d.kind != "movie",
                    checking = checking,
                    onWatch = {
                        scope.launch {
                            checking = true
                            try {
                                if (d.kind == "movie") onWatch(item, "", "")
                                else episodesOpen = true
                            } finally {
                                checking = false
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = 720.dp),
                )
            }
        }
        EpisodesGate(isOpen = { episodesOpen }) {
            if (episodesOpen) {
                EpisodesScreen(
                    detail = detail,
                    // back from a post-player return skips the detail view
                    onClose = { if (startEpisodes && onEpisodesExit != null) onEpisodesExit() else episodesOpen = false },
                    onEpisode = { s, e ->
                        episodesOpen = false
                        onWatch(item, s, e)
                    },
                    precacheImages = precacheImages,
                )
            }
        }
    }
}

@Composable
private fun EpisodesGate(isOpen: () -> Boolean, content: @Composable AnimatedVisibilityScope.() -> Unit) {
    AnimatedVisibility(
        visible = isOpen(),
        enter = slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it },
        exit = slideOutVertically(tween(280, easing = FastOutSlowInEasing)) { it },
    ) {
        content()
    }
}

@Composable
private fun MetaCell(label: String, value: String) {
    Column {
        Text(label, color = Slate500, fontSize = 11.sp, fontFamily = Mono)
        Text(value, color = Slate100, fontSize = 15.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Avatar(person: Person, size: androidx.compose.ui.unit.Dp, loadPhoto: Boolean = true) {
    if (person.photo != null && loadPhoto) {
        AsyncImage(model = person.photo, contentDescription = person.name, modifier = Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop, onSuccess = { Log.d("detail img ok ${person.name}") }, onError = { Log.d("detail img err ${person.name}") })
    } else {
        val initials = person.name.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString("")
        Box(Modifier.size(size).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Text(initials, color = Slate200, fontSize = 14.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SeasonPreview(detail: TitleDetail, start: Boolean, onEpisode: (String, String) -> Unit, onViewAll: () -> Unit) {
    val key = TmdbHost.key
    val seasons = detail.seasons
    var seasonIdx by remember(detail.id) { mutableStateOf(seasons.indexOfFirst { it.number > 0 }.takeIf { it >= 0 } ?: 0) }
    var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    LaunchedEffect(detail.id, seasonIdx, start) {
        if (!start) return@LaunchedEffect
        Log.d("detail season start")
        val n = seasons.getOrNull(seasonIdx)?.number ?: 1
        try {
            val all = withContext(ioDispatcher) { Tmdb.season(key, detail.id, n) }
            if (all.isNotEmpty()) {
                DetailCache.putSeason(detail.kind, detail.id, n)
                DetailCache.putEpisodes(detail.kind, detail.id, n, all)
            }
            episodes = all.take(3)
        } catch (_: Exception) {
        }
        Log.d("detail season end ${episodes.size}")
    }
    if (seasons.size > 1) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(seasons.size) { i ->
                val active = i == seasonIdx
                Box(
                    Modifier.clip(CircleShape)
                        .background(if (active) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
                        .clickable { seasonIdx = i }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(seasons[i].name, color = if (active) Color.White else Slate400, fontSize = 13.sp, fontFamily = Rubik)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    episodes.forEach { ep ->
        EpisodeRow(ep = ep, fallbackStill = detail.backdrop ?: detail.poster, onClick = {
            onEpisode((seasons.getOrNull(seasonIdx)?.number ?: 1).toString(), ep.n.toString())
        })
    }
    val total = seasons.sumOf { it.count }
    Row(
        Modifier.fillMaxWidth().clip(CircleShape).background(Color.White.copy(alpha = 0.05f))
            .clickable(onClick = onViewAll).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(Res.string.detail_view_all_episodes, total), color = Cyan, fontSize = 13.sp, fontFamily = Rubik)
        ChevronRightIcon(contentDescription = null, tint = Cyan, size = 15.dp)
    }
}

// Lenient on purpose: bad input falls back to the raw ISO string. Fixed en-US shape because
// java.time's localized formatter has no multiplatform twin.
private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

internal fun airLabel(iso: String): String {
    val p = iso.split('-')
    val y = p.getOrNull(0)?.toIntOrNull() ?: return iso
    val m = p.getOrNull(1)?.toIntOrNull() ?: return iso
    val d = p.getOrNull(2)?.toIntOrNull() ?: return iso
    if (m !in 1..12) return iso
    return "${MONTHS[m - 1]} $d, $y"
}

@Composable
internal fun EpisodeRow(ep: Episode, fallbackStill: String?, playing: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val thumb = ep.still ?: fallbackStill
        if (thumb != null) {
            AsyncImage(model = thumb, contentDescription = null, modifier = Modifier.size(120.dp, 68.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.size(120.dp, 68.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.05f)), contentAlignment = Alignment.Center) {
                Icon(painterResource(Res.drawable.ic_play_outline), contentDescription = null, tint = Slate200, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                ep.air?.let { stringResource(Res.string.detail_episode_air, ep.n, airLabel(it)) }
                    ?: stringResource(Res.string.detail_episode, ep.n),
                color = if (playing) Cyan else Slate500, fontSize = 11.sp, fontFamily = Mono,
            )
            Text(ep.name, color = Slate100, fontSize = 14.sp, fontFamily = Rubik, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (playing) {
            Box(
                Modifier.clip(CircleShape).background(Cyan.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(stringResource(Res.string.detail_playing), color = Cyan, fontSize = 9.sp, fontFamily = Mono, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Icon(painterResource(Res.drawable.ic_play_outline), contentDescription = null, tint = Slate200, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun WatchBar(isTv: Boolean, checking: Boolean, onWatch: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(CircleShape)
                .background(SurfaceColor).border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).height(56.dp).clip(CircleShape).background(WatchRed)
                    .clickable(onClick = onWatch).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (checking) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                } else {
                    Icon(painterResource(Res.drawable.ic_play_filled), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isTv) stringResource(Res.string.episodes_title) else stringResource(Res.string.detail_watch_now), color = Color.White, fontSize = 15.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun EpisodesScreen(
    detail: TitleDetail?,
    topInset: Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
    onClose: () -> Unit,
    onEpisode: (String, String) -> Unit,
    precacheImages: (List<String>) -> Unit = {},
) {
    val key = TmdbHost.key
    val seasons = detail?.seasons.orEmpty()
    var seasonIdx by remember(detail?.id) {
        val def = seasons.indexOfFirst { it.number > 0 }.takeIf { it >= 0 } ?: 0
        val last = detail?.let { DetailCache.season(it.kind, it.id) } ?: -1
        mutableStateOf(seasons.indexOfFirst { it.number == last }.takeIf { it >= 0 } ?: def)
    }
    val initial = detail?.let { DetailCache.episodes(it.kind, it.id, seasons.getOrNull(seasonIdx)?.number ?: 1) }
    var episodes by remember { mutableStateOf(initial.orEmpty()) }
    var loading by remember { mutableStateOf(initial == null) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    PlatformBackHandler(enabled = true) { onClose() }

    val t0 = remember(detail?.id) { nowMs() }
    fun ms() = nowMs() - t0
    LaunchedEffect(detail?.id) {
        detail?.let { Log.d("ep open ${it.kind}/${it.id} ${it.title}") }
    }
    LaunchedEffect(detail?.id, seasonIdx) {
        val d = detail ?: return@LaunchedEffect
        val n = seasons.getOrNull(seasonIdx)?.number ?: 1
        DetailCache.putSeason(d.kind, d.id, n)
        val cached = DetailCache.episodes(d.kind, d.id, n)
        if (cached != null) {
            episodes = cached
            loading = false
            return@LaunchedEffect
        }
        loading = true
        val start = nowMs()
        val first = episodes.isEmpty()
        Log.d("ep fetch start ${d.id} s$seasonIdx")
        var result = emptyList<Episode>()
        try {
            result = withContext(ioDispatcher) {
                Tmdb.season(key, d.id, n)
            }
        } catch (_: Exception) {
            result = emptyList()
        }
        val elapsed = nowMs() - start
        Log.d("ep fetch end +${elapsed}ms n=${result.size}")
        val stills = result.mapNotNull { it.still }.take(12)
        if (stills.isNotEmpty()) {
            precacheImages(stills)
            Log.d("ep precache ${stills.size}")
        }
        if (first) {
            val wait = 400 - elapsed
            if (wait > 0) delay(wait)
        }
        episodes = result
        loading = false
        if (result.isNotEmpty()) DetailCache.putEpisodes(d.kind, d.id, n, result)
        Log.d("ep applied +${ms()}ms n=${result.size}")
    }

    val shown = remember(query, episodes) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) episodes
        else episodes.filter {
            it.name.lowercase().contains(q) || ("e${it.n}" == q) || (it.n.toString() == q)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Bg)) {
        val thumbW = (maxWidth * 0.46f).coerceAtMost(200.dp)
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(top = topInset).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(38.dp)) {
                    Icon(painterResource(Res.drawable.ic_back), contentDescription = stringResource(Res.string.common_back), tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(6.dp))
                if (searching) {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(8.dp)).background(SurfaceColor).padding(horizontal = 12.dp),
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp, fontFamily = Rubik),
                        singleLine = true,
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (query.isEmpty()) Text(stringResource(Res.string.detail_search_hint), color = Slate500, fontSize = 15.sp, fontFamily = Rubik)
                                inner()
                            }
                        },
                    )
                    IconButton(onClick = { searching = false; query = "" }) {
                        CloseIcon(contentDescription = stringResource(Res.string.common_close), tint = Slate200, size = 20.dp)
                    }
                } else {
                    Text(stringResource(Res.string.episodes_title), color = Color.White, fontSize = 18.sp, fontFamily = Rubik, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { searching = true }) {
                        Icon(painterResource(Res.drawable.ic_search), contentDescription = stringResource(Res.string.common_search), tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }
            if (seasons.size > 1 && !searching) {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(seasons.size) { i ->
                        val active = i == seasonIdx
                        Box(
                            Modifier.height(36.dp).clip(CircleShape)
                                .background(if (active) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
                                .clickable { seasonIdx = i }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(seasons[i].name, color = if (active) Color.White else Slate400, fontSize = 13.sp, fontFamily = Rubik, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            when {
                loading -> LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)) {
                    items(6) {                                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.Top) {
                            ShimmerBox(Modifier.width(thumbW).aspectRatio(16f / 9f))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                ShimmerBox(Modifier.fillMaxWidth(0.95f).height(16.dp))
                                Spacer(Modifier.height(6.dp))
                                ShimmerBox(Modifier.fillMaxWidth(0.7f).height(16.dp))
                                Spacer(Modifier.height(8.dp))
                                ShimmerBox(Modifier.fillMaxWidth(0.4f).height(12.dp))
                            }
                        }
                    }
                }
                shown.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (query.isNotEmpty()) stringResource(Res.string.detail_no_matches, query) else stringResource(Res.string.detail_no_episodes), color = Slate200, fontSize = 15.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold)
                }
                else -> {
                    if (query.isNotEmpty()) {
                        Text(stringResource(Res.string.detail_matches, shown.size), color = Slate500, fontSize = 11.sp, fontFamily = Mono, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                    }
                    LazyColumn(modifier = Modifier.imePadding(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)) {
                        items(shown, key = { it.n }) { ep ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                    onEpisode((seasons.getOrNull(seasonIdx)?.number ?: 1).toString(), ep.n.toString())
                                }.padding(vertical = 14.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                val thumb = ep.still ?: (detail?.backdrop ?: detail?.poster)
                                Box(Modifier.width(thumbW).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.05f))) {
                                    if (thumb != null) {
                                        AsyncImage(model = thumb, contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop, onSuccess = { Log.d("ep still ok E${ep.n}") }, onError = { Log.d("ep still err E${ep.n}") })
                                    } else {
                                        Icon(painterResource(Res.drawable.ic_play_outline), contentDescription = null, tint = Slate200, modifier = Modifier.align(Alignment.Center).size(18.dp))
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(ep.name, color = Color.White, fontSize = 16.sp, lineHeight = 21.sp, fontFamily = Rubik, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        ep.air?.let { stringResource(Res.string.detail_episode_air, ep.n, airLabel(it)) }
                                            ?: stringResource(Res.string.detail_episode, ep.n),
                                        color = Slate400, fontSize = 13.sp, fontFamily = Mono,
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

/** Title, meta, plot, episodes, director and cast: the sheet shared by the phone and tablet layouts. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailBody(
    item: Title,
    d: TitleDetail,
    pending: Boolean,
    photosReady: Boolean,
    onEpisode: (String, String) -> Unit,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            if ((item.poster ?: d.poster) != null) {
                AsyncImage(model = item.poster ?: d.poster, contentDescription = null, modifier = Modifier.size(120.dp, 176.dp).clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop, onSuccess = { Log.d("detail poster ok") }, onError = { Log.d("detail poster err") })
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(d.title, color = Color.White, fontSize = 20.sp, lineHeight = 28.sp, fontFamily = Rubik, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    d.year?.let { Text(it, color = Slate400, fontSize = 12.sp, fontFamily = Mono) }
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.1f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(if (d.kind == "movie") Res.drawable.ic_kind_movie else Res.drawable.ic_kind_series),
                                contentDescription = null,
                                tint = Slate300,
                                modifier = Modifier.size(11.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (d.kind == "movie") stringResource(Res.string.detail_kind_movie) else stringResource(Res.string.detail_kind_series), color = Slate300, fontSize = 10.sp, fontFamily = Mono)
                        }
                    }
                    d.cert?.let { cert ->
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.clip(RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text(cert, color = Slate300, fontSize = 10.sp, fontFamily = Mono)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(Res.drawable.ic_star), contentDescription = null, tint = StarYellow, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    if (d.rating > 0) {
                        Text(fmt1(d.rating), color = Color.White, fontSize = 13.sp, fontFamily = Mono, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        if (d.votes > 0) stringResource(Res.string.detail_votes, d.votes) else stringResource(Res.string.detail_votes_none),
                        color = Slate500, fontSize = 11.sp, fontFamily = Mono,
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Column {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetaCell(stringResource(Res.string.stat_rating), if (d.rating > 0) fmt1(d.rating) else stringResource(Res.string.detail_value_none))
            MetaCell(stringResource(Res.string.stat_runtime), if (d.runtime > 0) stringResource(Res.string.detail_runtime_min, d.runtime) else stringResource(Res.string.detail_value_none))
            MetaCell(stringResource(Res.string.stat_released), d.year ?: stringResource(Res.string.detail_value_none))
        }
        if (d.genres.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                d.genres.take(6).forEach { g ->
                    Box(Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text(g, color = Slate100, fontSize = 12.sp, fontFamily = Rubik, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        } else if (pending) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShimmerBox(Modifier.size(72.dp, 30.dp))
                ShimmerBox(Modifier.size(90.dp, 30.dp))
                ShimmerBox(Modifier.size(64.dp, 30.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(Res.string.detail_story_plot), color = Color.White, fontSize = 16.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        if (d.overview.isNotEmpty()) {
            Text(d.overview, color = Slate300, fontSize = 14.sp, lineHeight = 24.sp, fontFamily = Rubik)
        } else if (pending) {
            ShimmerBox(Modifier.fillMaxWidth().height(14.dp))
            Spacer(Modifier.height(8.dp))
            ShimmerBox(Modifier.fillMaxWidth(0.9f).height(14.dp))
            Spacer(Modifier.height(8.dp))
            ShimmerBox(Modifier.fillMaxWidth(0.8f).height(14.dp))
            Spacer(Modifier.height(8.dp))
            ShimmerBox(Modifier.fillMaxWidth(0.7f).height(14.dp))
            Spacer(Modifier.height(8.dp))
            ShimmerBox(Modifier.fillMaxWidth(0.5f).height(14.dp))
        }
        if (d.kind != "movie") {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(Res.string.episodes_title), color = Color.White, fontSize = 16.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (d.seasons.isNotEmpty()) {
                SeasonPreview(
                    detail = d,
                    start = photosReady,
                    onEpisode = onEpisode,
                    onViewAll = onViewAll,
                )
            } else if (pending) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShimmerBox(Modifier.size(90.dp, 36.dp))
                    ShimmerBox(Modifier.size(90.dp, 36.dp))
                }
                Spacer(Modifier.height(8.dp))
                repeat(3) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ShimmerBox(Modifier.size(120.dp, 68.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            ShimmerBox(Modifier.fillMaxWidth(0.3f).height(12.dp))
                            Spacer(Modifier.height(6.dp))
                            ShimmerBox(Modifier.fillMaxWidth(0.8f).height(14.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        val dir = d.director
        if (dir != null) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(Res.string.detail_director), color = Color.White, fontSize = 16.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(person = dir, size = 44.dp, loadPhoto = photosReady)
                Spacer(Modifier.width(10.dp))
                Text(dir.name, color = Slate300, fontSize = 14.sp, fontFamily = Rubik)
            }
        } else if (pending) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(Res.string.detail_director), color = Color.White, fontSize = 16.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShimmerBox(Modifier.size(44.dp))
                Spacer(Modifier.width(10.dp))
                ShimmerBox(Modifier.fillMaxWidth(0.4f).height(14.dp))
            }
        }
        if (photosReady && d.cast.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(Res.string.detail_cast), color = Color.White, fontSize = 16.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(d.cast.take(8), key = { it.name }) { p ->
                    Column(Modifier.width(80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Avatar(person = p, size = 60.dp, loadPhoto = photosReady)
                        Spacer(Modifier.height(6.dp))
                        Text(p.name, color = Slate200, fontSize = 11.sp, lineHeight = 14.sp, fontFamily = Rubik, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.heightIn(min = 28.dp))
                    }
                }
            }
        } else if (pending || !photosReady) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(Res.string.detail_cast), color = Color.White, fontSize = 16.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(8) {
                    Column(Modifier.width(80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        ShimmerBox(Modifier.size(60.dp))
                        Spacer(Modifier.height(6.dp))
                        ShimmerBox(Modifier.fillMaxWidth(0.8f).height(10.dp))
                    }
                }
            }
        }
        }
        Spacer(Modifier.height(16.dp))
    }
}
