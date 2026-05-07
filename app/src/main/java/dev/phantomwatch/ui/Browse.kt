package dev.phantomwatch.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.activity.compose.BackHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.phantomwatch.BuildConfig
import dev.phantomwatch.R
import dev.phantomwatch.Title
import dev.phantomwatch.VidLove
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class RailState(
    val key: String,
    val title: String,
    val path: String,
    val items: List<Title> = emptyList(),
    val page: Int = 1,
    val totalPages: Int = 1,
    val loadingMore: Boolean = false,
)

private val RailDefs = listOf(
    Triple("popular", "Popular Movies", "movie/popular"),
    Triple("top", "Top Rated", "movie/top_rated"),
    Triple("now", "Now Playing", "movie/now_playing"),
    Triple("tv-popular", "Popular TV Shows", "tv/popular"),
    Triple("tv-top", "Top Rated TV", "tv/top_rated"),
    Triple("airing", "Airing Today", "tv/airing_today"),
)

internal class BrowseState {
    var openTitle by mutableStateOf<Title?>(null)
    var backToEpisodes by mutableStateOf(false)
    var searchOpen by mutableStateOf(false)
    var query by mutableStateOf("")
    var results by mutableStateOf<List<Title>>(emptyList())
    var resultsFor by mutableStateOf("")
    var railKey by mutableStateOf<String?>(null)
    var trending by mutableStateOf<List<Title>>(emptyList())
    var rails by mutableStateOf<List<RailState>>(emptyList())
    var exploreItems by mutableStateOf<List<Title>>(emptyList())
    var explorePage by mutableStateOf(0)
    var exploreTotal by mutableStateOf(1)
    var loading by mutableStateOf(true)
    var failed by mutableStateOf(false)
    var loaded by mutableStateOf(false)
    val homeScroll = LazyListState()
}

@Composable
internal fun MoviesHome(browse: BrowseState, onWatch: (Title, String, String) -> Unit) {
    val key = BuildConfig.TMDB_API_KEY
    val scope = rememberCoroutineScope()
    var searching by remember { mutableStateOf(false) }
    var searchSeq by remember { mutableStateOf(0) }
    var exploreMore by remember { mutableStateOf(false) }
    var selected by remember(browse.openTitle) { mutableStateOf(browse.openTitle) }
    val recents = remember { mutableStateListOf<Title>() }

    fun loadBrowse() {
        scope.launch {
            browse.loading = true
            try {
                val foundTrending = withContext(Dispatchers.IO) { VidLove.trending(key) }
                val foundRails = withContext(Dispatchers.IO) {
                    RailDefs.map { def ->
                        val r = VidLove.rail(key, def.third, 1)
                        RailState(def.first, def.second, def.third, r.items, 1, r.totalPages)
                    }
                }
                browse.trending = foundTrending.take(12)
                browse.rails = foundRails
                browse.exploreItems = emptyList()
                browse.explorePage = 0
                browse.exploreTotal = 1
                browse.failed = foundTrending.isEmpty() && foundRails.all { it.items.isEmpty() }
            } catch (_: Exception) {
                browse.failed = true
            } finally {
                browse.loading = false
                browse.loaded = true
            }
        }
    }

    fun loadMoreRail(railKey: String) {
        val rail = browse.rails.firstOrNull { it.key == railKey } ?: return
        if (rail.loadingMore || rail.page >= rail.totalPages) return
        scope.launch {
            browse.rails = browse.rails.map { if (it.key == railKey) it.copy(loadingMore = true) else it }
            try {
                val r = withContext(Dispatchers.IO) { VidLove.rail(key, rail.path, rail.page + 1) }
                val known = rail.items.map { "${it.kind}-${it.id}" }.toSet()
                browse.rails = browse.rails.map {
                    if (it.key != railKey) it
                    else it.copy(
                        items = it.items + r.items.filter { n -> "${n.kind}-${n.id}" !in known },
                        page = rail.page + 1,
                        totalPages = r.totalPages,
                        loadingMore = false,
                    )
                }
            } catch (_: Exception) {
                browse.rails = browse.rails.map { if (it.key == railKey) it.copy(loadingMore = false) else it }
            }
        }
    }

    fun loadMoreExplore() {
        if (exploreMore || browse.explorePage >= minOf(browse.exploreTotal, 10)) return
        scope.launch {
            exploreMore = true
            try {
                val seen = (browse.rails.flatMap { it.items } + browse.exploreItems).map { "${it.kind}-${it.id}" }.toMutableSet()
                var page = browse.explorePage
                var total = browse.exploreTotal
                var fresh = emptyList<Title>()
                while (page < minOf(total, 10)) {
                    val r = withContext(Dispatchers.IO) { VidLove.rail(key, "movie/popular", page + 1) }
                    page += 1
                    total = r.totalPages
                    fresh = r.items.filter { "${it.kind}-${it.id}" !in seen }
                    if (fresh.isNotEmpty()) break
                }
                if (fresh.isNotEmpty()) browse.exploreItems = browse.exploreItems + fresh
                browse.explorePage = page
                browse.exploreTotal = total
            } catch (_: Exception) {
            } finally {
                exploreMore = false
            }
        }
    }

    LaunchedEffect(Unit) { if (!browse.loaded) loadBrowse() }
    LaunchedEffect(browse.query) {
        val term = browse.query.trim()
        if (term.length < 2) {
            browse.results = emptyList()
            browse.resultsFor = ""
            searching = false
            return@LaunchedEffect
        }
        if (term == browse.resultsFor) return@LaunchedEffect
        searching = true
        delay(300)
        searchSeq += 1
        val mine = searchSeq
        try {
            val found = withContext(Dispatchers.IO) { VidLove.search(term) }
            if (mine == searchSeq) {
                browse.results = found
                browse.resultsFor = term
            }
        } catch (_: Exception) {
            if (mine == searchSeq) browse.results = emptyList()
        } finally {
            if (mine == searchSeq) searching = false
        }
    }

    fun openDetail(item: Title) {
        browse.openTitle = item
        selected = item
        recents.removeAll { it.kind == item.kind && it.id == item.id }
        recents.add(0, item)
        if (recents.size > 6) recents.removeLast()
    }

    val inSearch = browse.query.trim().length >= 2
    val openRail = browse.rails.firstOrNull { it.key == browse.railKey }
    val navOpen = browse.searchOpen || browse.railKey != null
    BackHandler(enabled = navOpen) {
        if (browse.searchOpen) {
            browse.searchOpen = false
            browse.query = ""
        } else {
            browse.railKey = null
        }
    }
    val screenW = LocalConfiguration.current.screenWidthDp.dp
    val gridW = (screenW - 56.dp) / 3

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize()) {
            if (openRail == null) {
                if (browse.searchOpen) {
                    SearchHeader(
                        query = browse.query,
                        onQuery = { browse.query = it },
                        onBack = { browse.searchOpen = false; browse.query = "" },
                    )
                } else {
                    BrowseHeader(
                        onSearch = { browse.searchOpen = true },
                        onClose = {},
                    )
                }
            }
            AnimatedContent(
                targetState = if (browse.searchOpen) 1 else if (openRail != null) 2 else 0,
                transitionSpec = {
                    fadeIn(tween(280)) togetherWith fadeOut(tween(280)) using SizeTransform(clip = false)
                },
                label = "browse-mode",
            ) { mode ->
                when (mode) {
                    1 -> SearchBody(
                        query = browse.query,
                        results = browse.results,
                        searching = searching,
                        recents = recents,
                        gridW = gridW,
                        
                        onOpen = ::openDetail,
                    )
                    2 -> openRail?.let {
                        RailGrid(
                            rail = it,
                            onBack = { browse.railKey = null },
                            onOpen = ::openDetail,
                            onEnd = { loadMoreRail(it.key) },
                        )
                    } ?: Box(Modifier.fillMaxSize())
                    else -> BrowseBody(
                        trending = browse.trending,
                        rails = browse.rails,
                        exploreItems = browse.exploreItems,
                        exploreMore = exploreMore,
                        exploreTotal = browse.exploreTotal,
                        loading = browse.loading,
                        failed = browse.failed,
                        detailOpen = selected != null,
                        listState = browse.homeScroll,
                        
                        onOpen = ::openDetail,
                        onSeeAll = { browse.railKey = it },
                        onRailEnd = ::loadMoreRail,
                        onGridEnd = ::loadMoreExplore,
                        onRetry = ::loadBrowse,
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = selected != null,
            enter = slideInVertically(tween(350), initialOffsetY = { it }) + fadeIn(tween(350)),
            exit = slideOutVertically(tween(350), targetOffsetY = { it }) + fadeOut(tween(350)),
        ) {
            selected?.let { item ->
                MovieDetail(
                    item = item,
                    onClose = { browse.openTitle = null; browse.backToEpisodes = false },
                    startEpisodes = browse.backToEpisodes,
                    onEpisodesExit = { browse.openTitle = null; browse.backToEpisodes = false },
                    onWatch = { t, s, e ->
                        selected = null
                        onWatch(t, s, e)
                    },
                    
                )
            }
        }
    }
}

@Composable
private fun BrowseHeader(onSearch: () -> Unit, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = R.drawable.pirate,
            contentDescription = null,
            modifier = Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.width(8.dp))
        Text("Watch", color = Color.White, fontSize = 24.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Surface).clickable(onClick = onSearch),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_search_normal), contentDescription = "Search", tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Surface).clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SearchHeader(query: String, onQuery: (String) -> Unit, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Surface).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Back", tint = Slate400, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier.weight(1f).height(48.dp).clip(CircleShape).background(Surface).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = Rubik),
                singleLine = true,
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Search movies or shows", color = Color(0xFF939392), fontSize = 12.sp, fontFamily = Rubik)
                    inner()
                },
            )
        }
    }
}

@Composable
fun PosterCard(
    item: Title,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    onOpen: (Title) -> Unit,
) {
    val radius = (width * 0.24f)
    val playSize = maxOf(13.dp, width * 0.13f)
    val badgePad = maxOf(4.dp, width * 0.04f)
    val titleSize = if (width >= 150.dp) 16.sp else 12.sp
    Column(Modifier.width(width).clickable { onOpen(item) }) {
        Box(Modifier.clip(RoundedCornerShape(radius.value.dp)).background(Color.White.copy(alpha = 0.05f))) {
            if (item.poster != null) {
                AsyncImage(model = item.poster, contentDescription = item.title, modifier = Modifier.size(width, height), contentScale = ContentScale.Crop)
                Box(
                    Modifier.align(Alignment.BottomEnd)
                        .padding(end = width * 0.12f, bottom = height * 0.08f)
                        .clip(CircleShape).background(Color(0xCC1E1E1E)).padding(badgePad),
                ) {
                    Icon(painterResource(R.drawable.ic_play3), contentDescription = null, tint = Color.White, modifier = Modifier.size(playSize))
                }
            } else {
                Box(Modifier.size(width, height), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_play3), contentDescription = null, tint = Slate500, modifier = Modifier.size(playSize))
                }
            }
        }
        var centered by remember { mutableStateOf(false) }
        Text(
            item.title.uppercase(), color = Slate100, fontSize = titleSize, fontFamily = Rubik,
            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            onTextLayout = { centered = !it.isLineEllipsized(0) },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrowseBody(
    trending: List<Title>,
    rails: List<RailState>,
    exploreItems: List<Title>,
    exploreMore: Boolean,
    exploreTotal: Int,
    loading: Boolean,
    failed: Boolean,
    detailOpen: Boolean,
    listState: LazyListState,
    onOpen: (Title) -> Unit,
    onSeeAll: (String) -> Unit,
    onRailEnd: (String) -> Unit,
    onGridEnd: () -> Unit,
    onRetry: () -> Unit,
) {
    val screenW = LocalConfiguration.current.screenWidthDp.dp
    val gridW = (screenW - 56.dp) / 3
    val exploreRows = remember(exploreItems) { exploreItems.chunked(3) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { idx ->
                if (idx != null && idx >= exploreRows.size) onGridEnd()
            }
    }
    if (loading && trending.isEmpty() && rails.all { it.items.isEmpty() }) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Cyan)
        }
        return
    }
    if (failed) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Could not reach TMDB.", color = Cyan, fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Text("Tap retry below", color = Slate400, fontFamily = Mono, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.TextButton(onClick = onRetry) {
                Text("Retry", color = Cyan, fontFamily = Rubik, fontWeight = FontWeight.SemiBold)
            }
        }
        return
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 128.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text("Trending this week", color = Slate200, fontSize = 16.sp, fontFamily = Rubik, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 8.dp))
                TrendingCarousel(trending = trending, paused = detailOpen, onOpen = onOpen)
                rails.forEach { rail ->
                    if (rail.items.isNotEmpty()) {
                        Spacer(Modifier.height(28.dp))
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(rail.title, color = Slate200, fontSize = 16.sp, fontFamily = Rubik, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Row(Modifier.clickable { onSeeAll(rail.key) }, verticalAlignment = Alignment.CenterVertically) {
                                Text("See all", color = Cyan, fontSize = 13.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold)
                                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Cyan, modifier = Modifier.size(15.dp))
                            }
                        }
                        val rowState = rememberLazyListState()
                        LaunchedEffect(rowState, rail.items.size) {
                            snapshotFlow { rowState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                                .collect { idx ->
                                    if (idx != null && idx >= rail.items.size - 3) onRailEnd(rail.key)
                                }
                        }
                        LazyRow(
                            state = rowState,
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(rail.items, key = { "${it.kind}-${it.id}" }) { item ->
                                PosterCard(item = item, width = 162.dp, height = 216.dp, onOpen = onOpen)
                            }
                        }
                    }
                }
                if (exploreItems.isNotEmpty() || exploreMore) {
                    Spacer(Modifier.height(24.dp))
                    Text("Explore more", color = Slate200, fontSize = 16.sp, fontFamily = Rubik, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp))
                }
            }
        }
        items(exploreRows, key = { row -> row.first().let { "${it.kind}-${it.id}" } }) { row ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { item ->
                    PosterCard(item = item, width = gridW, height = gridW * 1.5f, onOpen = onOpen)
                }
            }
        }
        if (exploreMore) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Cyan, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrendingCarousel(trending: List<Title>, paused: Boolean, onOpen: (Title) -> Unit) {
    if (trending.isEmpty()) return
    val screenW = LocalConfiguration.current.screenWidthDp.dp
    val pageW = minOf(screenW - 64.dp, 560.dp)
    val bannerH = (pageW - 32.dp) * 310f / 353f
    val pages = remember(trending) {
        if (trending.isEmpty()) emptyList() else List(trending.size * 3) { trending[it % trending.size] }
    }
    val state = rememberPagerState(initialPage = trending.size, pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    LaunchedEffect(paused, trending) {
        if (paused || trending.size < 2) return@LaunchedEffect
        while (true) {
            delay(4500)
            val n = trending.size
            if (state.currentPage >= 3 * n - 1) {
                state.scrollToPage(state.currentPage - n)
            }
            state.animateScrollToPage(state.currentPage + 1)
        }
    }
    LaunchedEffect(state, trending) {
        snapshotFlow { state.isScrollInProgress to state.currentPage }
            .collect { (scrolling, page) ->
                val n = trending.size
                if (!scrolling && n > 1) {
                    when {
                        page < n -> state.scrollToPage(page + n)
                        page >= 2 * n -> state.scrollToPage(page - n)
                    }
                }
            }
    }
    Column {
        HorizontalPager(state = state, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = (screenW - pageW) / 2), pageSpacing = 0.dp) { page ->
            val item = pages[page]
            Column(Modifier.width(pageW).padding(horizontal = 16.dp)) {
                Box(Modifier.fillMaxWidth().height(bannerH + 32.dp)) {
                    Box(
                        Modifier.fillMaxWidth().height(bannerH)
                            .clip(RoundedCornerShape(48.dp)).background(Surface).clickable { onOpen(item) },
                    ) {
                        if (item.poster != null) {
                            AsyncImage(model = item.poster, contentDescription = item.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Box(Modifier.fillMaxSize().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
                                Text(item.title, color = Color.White, fontSize = 18.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    Row(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp)
                            .clip(RoundedCornerShape(32.dp)).background(Surface)
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.title.uppercase(), color = Color.White, fontSize = 16.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (item.rating != null && item.rating > 0) {
                                    Icon(painterResource(R.drawable.ic_star2), contentDescription = null, tint = StarYellow, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(
                                    if (item.rating != null && item.rating > 0) "${String.format("%.1f", item.rating)}  •  ${item.year ?: ""}"
                                    else if (item.kind == "movie") "MOVIE" else "TV SHOW",
                                    color = Slate300, fontSize = 12.sp, fontFamily = Rubik, fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(50)).background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF333333), Color(0xFF767676), Color(0xFF363535)),
                                ),
                            ).clickable { onOpen(item) }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                        ) {
                            Text("Details", color = Color.White, fontSize = 14.sp, fontFamily = Rubik, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
            val total = trending.size
            val pos = ((state.currentPage + state.currentPageOffsetFraction) % total + total) % total
            trending.forEachIndexed { i, dot ->
                val raw = kotlin.math.abs(pos - i)
                val dist = minOf(raw, total - raw)
                val t = (1f - minOf(dist, 1f)).coerceIn(0f, 1f)
                val dotW = 8 + t * 14
                val dotColor = lerp(Color.White.copy(alpha = 0.2f), Cyan, t)
                Box(
                    Modifier.size(dotW.dp, 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(dotColor)
                        .clickable {
                            scope.launch { state.animateScrollToPage(total + i) }
                        },
                )
            }
        }
    }
}

@Composable
private fun SearchBody(
    query: String,
    results: List<Title>,
    searching: Boolean,
    recents: List<Title>,
    gridW: androidx.compose.ui.unit.Dp,
    onOpen: (Title) -> Unit,
) {
    if (query.trim().length >= 2) {
        Column(Modifier.fillMaxSize()) {
            Text(
                if (searching) "Searching for “$query”…"
                else if (results.isEmpty()) "No results"
                else "${results.size} results for “$query”",
                color = Slate400, fontSize = 12.sp, fontFamily = Mono,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (results.isEmpty() && !searching) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("Nothing found out there.", color = Cyan, fontSize = 14.sp, fontFamily = Mono, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text("Try another title", color = Slate400, fontSize = 12.sp, fontFamily = Rubik)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(results, key = { "${it.kind}-${it.id}" }) { item ->
                        PosterCard(item = item, width = gridW - 12.dp, height = (gridW - 12.dp) * 1.5f, onOpen = onOpen)
                    }
                }
            }
        }
    } else if (recents.isNotEmpty()) {
        Column(Modifier.fillMaxSize()) {
            Text("Recent search", color = Color.White, fontSize = 16.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(recents, key = { "${it.kind}-${it.id}" }) { item ->
                    PosterCard(item = item, width = 120.dp, height = 160.dp, onOpen = onOpen)
                }
            }
        }
    } else {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Search Movie", color = Color.White, fontSize = 16.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("Type a title to look it up", color = Slate400, fontSize = 12.sp, fontFamily = Mono)
        }
    }
}

@Composable
private fun RailGrid(rail: RailState, onBack: () -> Unit, onOpen: (Title) -> Unit, onEnd: () -> Unit) {
    val screenW = LocalConfiguration.current.screenWidthDp.dp
    val gridW = (screenW - 56.dp) / 3
    val state = rememberLazyGridState()
    LaunchedEffect(state) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { idx -> if (idx != null && idx >= rail.items.size - 3) onEnd() }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Back", tint = Slate400, modifier = Modifier.size(18.dp))
            }
            Text(rail.title, color = Color.White, fontSize = 18.sp, fontFamily = Rubik, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${rail.items.size} titles", color = Slate500, fontSize = 11.sp, fontFamily = Mono)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = state,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(rail.items, key = { "${it.kind}-${it.id}" }) { item ->
                PosterCard(item = item, width = gridW - 12.dp, height = (gridW - 12.dp) * 1.5f, onOpen = onOpen)
            }
            if (rail.loadingMore) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Cyan, modifier = Modifier.size(24.dp))
                    }
                }
            } else if (rail.page >= rail.totalPages) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "That is everything — ${rail.totalPages} pages",
                        color = Slate600, fontSize = 11.sp, fontFamily = Mono, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }
        }
    }
}
