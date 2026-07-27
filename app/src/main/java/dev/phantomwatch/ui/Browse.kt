package dev.phantomwatch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import dev.phantomwatch.R
import dev.phantomwatch.core.Recomp
import dev.phantomwatch.core.RemoteConfig
import dev.phantomwatch.core.TrackScrollJank
import dev.phantomwatch.data.Resume
import dev.phantomwatch.data.Search
import dev.phantomwatch.data.Title
import dev.phantomwatch.data.Tmdb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
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
    var historyOpen by mutableStateOf(false)
    var query by mutableStateOf("")
    var results by mutableStateOf<List<Title>>(emptyList())
    var resultsFor by mutableStateOf("")
    var railKey by mutableStateOf<String?>(null)
    var trending by mutableStateOf<List<Title>>(emptyList())
    var rails by mutableStateOf<List<RailState>>(emptyList())
    var recent by mutableStateOf<List<Resume.Recent>>(emptyList())
    var exploreItems by mutableStateOf<List<Title>>(emptyList())
    var explorePage by mutableStateOf(0)
    var exploreTotal by mutableStateOf(1)
    var loading by mutableStateOf(true)
    var failed by mutableStateOf(false)
    var loaded by mutableStateOf(false)
    val homeScroll = LazyListState()
}

@Composable
internal fun HomeScreen(browse: BrowseState, onWatch: (Title, String, String) -> Unit) {
    Recomp.tick("home")
    val key = RemoteConfig.tmdbKey()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var searching by remember { mutableStateOf(false) }
    var searchSeq by remember { mutableStateOf(0) }
    var exploreMore by remember { mutableStateOf(false) }
    var selected by remember(browse.openTitle) { mutableStateOf(browse.openTitle) }
    val recents = remember { mutableStateListOf<Title>() }

    fun loadBrowse() {
        scope.launch {
            browse.loading = true
            try {
                val foundTrending = withContext(Dispatchers.IO) { Tmdb.trending(key) }
                val foundRails = withContext(Dispatchers.IO) {
                    RailDefs.map { def ->
                        val r = Tmdb.rail(key, def.third, 1)
                            ?: return@map RailState(def.first, def.second, def.third, emptyList(), 1, 1)
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
                val r = withContext(Dispatchers.IO) { Tmdb.rail(key, rail.path, rail.page + 1) }
                // Keep page/totalPages on a failed page so the next scroll retries it.
                if (r == null) {
                    browse.rails = browse.rails.map { if (it.key == railKey) it.copy(loadingMore = false) else it }
                    return@launch
                }
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
        if (exploreMore || browse.explorePage >= browse.exploreTotal) return
        exploreMore = true
        scope.launch {
            try {
                // Dedupe set and page walk stay off the main thread: both grow with every page.
                val (page, total, fresh) = withContext(Dispatchers.IO) {
                    val seen = (browse.rails.flatMap { it.items } + browse.exploreItems)
                        .mapTo(HashSet()) { "${it.kind}-${it.id}" }
                    var page = browse.explorePage
                    var total = browse.exploreTotal
                    var fresh = emptyList<Title>()
                    var skipped = 0
                    while (page < total && skipped < 3) {
                        val r = Tmdb.rail(key, "movie/popular", page + 1) ?: break
                        page += 1
                        skipped += 1
                        total = r.totalPages
                        fresh = r.items.filter { "${it.kind}-${it.id}" !in seen }
                        if (fresh.isNotEmpty()) break
                    }
                    Triple(page, total, fresh)
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

    LaunchedEffect(Unit) { browse.recent = withContext(Dispatchers.IO) { Resume.recent(ctx) } }
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
            val found = withContext(Dispatchers.IO) { Search.titles(term) }
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
        keyboard?.hide()
        focusManager.clearFocus()
        browse.openTitle = item
        selected = item
        recents.removeAll { it.kind == item.kind && it.id == item.id }
        recents.add(0, item)
        if (recents.size > 6) recents.removeLast()
    }

    val inSearch = browse.query.trim().length >= 2
    val openRail = browse.rails.firstOrNull { it.key == browse.railKey }
    val navOpen = browse.searchOpen || browse.railKey != null || browse.historyOpen
    BackHandler(enabled = navOpen) {
        when {
            browse.searchOpen -> {
                browse.searchOpen = false
                browse.query = ""
            }
            browse.railKey != null -> browse.railKey = null
            else -> browse.historyOpen = false
        }
    }
    val screenW = LocalConfiguration.current.screenWidthDp.dp
    val expanded = isExpanded(screenW)
    val split = expanded && selected != null
    val detailW = if (split) detailPaneWidth(screenW) else 0.dp
    val contentW = screenW - detailW
    val gridW = gridCell(contentW, gridColumns(contentW))
    val closeDetail = {
        browse.openTitle = null
        browse.backToEpisodes = false
    }
    val watchFromDetail = { t: Title, s: String, e: String ->
        selected = null
        onWatch(t, s, e)
    }
    val openItem = stableCallback<Title> { openDetail(it) }
    val seeAll = stableCallback<String> { browse.railKey = it }
    val railEnd = stableCallback<String> { loadMoreRail(it) }
    val gridEnd = stableCallback0 { loadMoreExplore() }
    val retryBrowse = stableCallback0 { loadBrowse() }

    val home = !browse.searchOpen && openRail == null && !browse.historyOpen
    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize().padding(end = detailW)) {
            if (!home) {
                if (browse.searchOpen) {
                    SearchHeader(
                        query = browse.query,
                        onQuery = { browse.query = it },
                        onBack = { browse.searchOpen = false; browse.query = "" },
                    )
                } else {
                    BrowseHeader(
                        onSearch = { browse.searchOpen = true },
                        onHistory = { browse.historyOpen = true },
                    )
                }
            }
            AnimatedContent(
                targetState = when {
                    browse.searchOpen -> 1
                    openRail != null -> 2
                    browse.historyOpen -> 3
                    else -> 0
                },
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
                        
                        onOpen = openItem,
                    )
                    2 -> openRail?.let {
                        RailGrid(
                            rail = it,
                            width = contentW,
                            onBack = { browse.railKey = null },
                            onOpen = openItem,
                            onEnd = { loadMoreRail(it.key) },
                        )
                    } ?: Box(Modifier.fillMaxSize())
                    3 -> HistoryScreen(
                        recent = browse.recent,
                        gridW = gridW,
                        onBack = { browse.historyOpen = false },
                        onPlay = { r ->
                            onWatch(
                                Title(id = r.id, kind = r.kind, title = r.title, poster = r.poster),
                                r.season,
                                r.episode,
                            )
                        },
                    )
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
                        width = contentW,
                        onOpen = openItem,
                        onSeeAll = seeAll,
                        onRailEnd = railEnd,
                        onGridEnd = gridEnd,
                        onRetry = retryBrowse,
                    )
                }
            }
        }
        if (home) {
            val scrolled by remember {
                derivedStateOf { browse.homeScroll.firstVisibleItemIndex > 0 || browse.homeScroll.firstVisibleItemScrollOffset > 8 }
            }
            Box(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(end = detailW)) {
                BrowseHeader(
                    onSearch = { browse.searchOpen = true },
                    onHistory = { browse.historyOpen = true },
                    floating = true,
                    scrolled = scrolled,
                )
            }
        }
        if (split) {
            Box(Modifier.align(Alignment.CenterEnd).width(detailW).fillMaxSize()) {
                DetailPane(selected, closeDetail, watchFromDetail, browse.backToEpisodes, closeDetail)
            }
        }
        if (!expanded) {
            AnimatedVisibility(
                visible = selected != null,
                enter = slideInVertically(tween(350), initialOffsetY = { it }) + fadeIn(tween(350)),
                exit = slideOutVertically(tween(350), targetOffsetY = { it }) + fadeOut(tween(350)),
            ) {
                DetailPane(selected, closeDetail, watchFromDetail, browse.backToEpisodes, closeDetail)
            }
        }
    }
}

@Composable
private fun BrowseHeader(onSearch: () -> Unit, onHistory: () -> Unit, floating: Boolean = false, scrolled: Boolean = false) {
    Recomp.tick("header")
    val solid by animateFloatAsState(if (scrolled) 1f else 0f, tween(320), label = "header-solid")
    Box(
        Modifier.fillMaxWidth().then(
            if (!floating) Modifier
            else Modifier
                .background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.72f), Color.Black.copy(alpha = 0.26f), Color.Transparent)),
                )
                .drawBehind {
                    if (solid > 0f) {
                        drawRect(
                            Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.4f * solid), Color.Transparent)),
                            topLeft = Offset(0f, size.height),
                            size = Size(size.width, 12.dp.toPx()),
                        )
                    }
                },
        ),
    ) {
        if (floating) Box(Modifier.matchParentSize().background(Bg.copy(alpha = 0.97f * solid)))
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
            Text("Phantom", color = Color.White, fontSize = 24.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(SurfaceColor).clickable(onClick = onSearch),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_search), contentDescription = "Search", tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(SurfaceColor).clickable(onClick = onHistory),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_history), contentDescription = "History", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
        if (floating) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f * solid)))
        }
    }
}

private val SearchTextStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = Rubik)

@Composable
private fun SearchHeader(query: String, onQuery: (String) -> Unit, onBack: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(SurfaceColor).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_back), contentDescription = "Back", tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier.weight(1f).height(48.dp).clip(CircleShape).background(SurfaceColor).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                textStyle = SearchTextStyle,
                singleLine = true,
                cursorBrush = SolidColor(Cyan),
                decorationBox = { inner ->
                    if (query.isEmpty()) BasicText("Search movies or shows", style = SearchTextStyle.copy(color = Color(0xFF939392)))
                    inner()
                },
            )
        }
    }
}

/** One remembered instance so a parent recomposition does not invalidate every poster card. */
@Composable
private fun <T> stableCallback(block: (T) -> Unit): (T) -> Unit {
    val latest = rememberUpdatedState(block)
    return remember { { t: T -> latest.value(t) } }
}

@Composable
private fun stableCallback0(block: () -> Unit): () -> Unit {
    val latest = rememberUpdatedState(block)
    return remember { { latest.value() } }
}

@Composable
internal fun Modifier.pressClick(onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "press")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.clickable(interactionSource = src, indication = null, onClick = onClick)
}

@Composable
fun PosterCard(
    item: Title,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    onOpen: (Title) -> Unit,
) {
    Recomp.tick("card")
    val radius = (width * 0.24f)
    val playSize = maxOf(13.dp, width * 0.13f)
    val titleSize = if (width >= 150.dp) 16.sp else 12.sp
    Column(Modifier.width(width).pressClick { onOpen(item) }) {
        Box(Modifier.clip(RoundedCornerShape(radius.value.dp)).background(Color.White.copy(alpha = 0.05f))) {
            if (item.poster != null) {
                AsyncImage(model = item.poster, contentDescription = item.title, modifier = Modifier.size(width, height), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.size(width, height), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_play_outline), contentDescription = null, tint = Slate500, modifier = Modifier.size(playSize))
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
    width: Dp,
    onOpen: (Title) -> Unit,
    onSeeAll: (String) -> Unit,
    onRailEnd: (String) -> Unit,
    onGridEnd: () -> Unit,
    onRetry: () -> Unit,
) {
    Recomp.tick("browse")
    val cols = exploreColumns(width)
    val gridW = gridCell(width, cols)
    val sectionSize = if (isExpanded(width)) 22.sp else 16.sp
    TrackScrollJank("home") { listState.isScrollInProgress }
    val exploreRows = remember(exploreItems, cols) { exploreItems.chunked(cols) }
    // Outside composition on purpose: a state read here would re-run this whole body (and every
    // poster card in the content lambda) each time a page lands or the spinner item appears.
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= 0 && last >= info.totalItemsCount - 2
        }.collect { if (it) onGridEnd() }
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
        Recomp.tick("dsl")
        // One item per rail, not one item for the whole feed: an explore page arriving then only
        // recomposes the grid rows, and rails that scroll off screen stop holding their images.
        item(key = "hero", contentType = "hero") {
            TrendingHero(trending = trending, paused = detailOpen, onOpen = onOpen, width = width, listState = listState)
        }
        rails.forEach { rail ->
            if (rail.items.isNotEmpty()) {
                item(key = "rail-${rail.key}", contentType = "rail") {
                    RailSection(rail, width, onOpen, onSeeAll, onRailEnd)
                }
            }
        }
        if (exploreItems.isNotEmpty() || exploreMore) {
            item(key = "explore", contentType = "header") {
                Text(
                    "Explore more", color = Slate200, fontSize = sectionSize, fontFamily = Rubik,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                )
            }
        }
        items(
            exploreRows,
            key = { row -> "row-" + row.first().let { "${it.kind}-${it.id}" } },
            contentType = { "row" },
        ) { row ->
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
            item(key = "more", contentType = "more") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Cyan, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun RailSection(
    rail: RailState,
    width: Dp,
    onOpen: (Title) -> Unit,
    onSeeAll: (String) -> Unit,
    onEnd: (String) -> Unit,
) {
    val rowState = remember(rail.key) { LazyListState() }
    val railW = featuredCell(width)
    TrackScrollJank("rail ${rail.title}") { rowState.isScrollInProgress }
    LaunchedEffect(rowState) {
        snapshotFlow {
            val info = rowState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= 0 && last >= info.totalItemsCount - 3
        }.distinctUntilChanged().collect { if (it) onEnd(rail.key) }
    }
    Column {
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                rail.title, color = Slate200, fontSize = if (isExpanded(width)) 22.sp else 16.sp,
                fontFamily = Rubik, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
            )
            Row(Modifier.clickable { onSeeAll(rail.key) }, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "See all", color = Cyan, fontSize = if (isExpanded(width)) 15.sp else 13.sp,
                    fontFamily = Rubik, fontWeight = FontWeight.SemiBold,
                )
                ChevronRightIcon(contentDescription = null, tint = Cyan, size = if (isExpanded(width)) 18.dp else 15.dp)
            }
        }
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(start = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(rail.items, key = { "${it.kind}-${it.id}" }, contentType = { "poster" }) { item ->
                PosterCard(item = item, width = railW, height = railW * 4f / 3f, onOpen = onOpen)
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
                    columns = GridCells.Adaptive(gridW),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize().imePadding(),
                ) {
                    items(results, key = { "${it.kind}-${it.id}" }) { item ->
                        PosterCard(item = item, width = gridW, height = gridW * 1.5f, onOpen = onOpen)
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
        BoxWithConstraints(Modifier.fillMaxSize().navigationBarsPadding().imePadding(), contentAlignment = Alignment.Center) {
            val ghost = (maxHeight * 0.26f).coerceIn(84.dp, 132.dp)
            val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val comp by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.lottie_search))
                LottieAnimation(
                    composition = comp,
                    iterations = 1,
                    isPlaying = imeVisible,
                    restartOnPlay = true,
                    modifier = Modifier.size(ghost),
                )
                Spacer(Modifier.height(20.dp))
                Text("Let's Grab a Movie", color = Color.White, fontSize = 16.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun RailGrid(rail: RailState, width: Dp, onBack: () -> Unit, onOpen: (Title) -> Unit, onEnd: () -> Unit) {
    Recomp.tick("grid")
    val cols = exploreColumns(width)
    val gridW = gridCell(width, cols)
    val railW = featuredCell(width)
    val state = rememberLazyGridState()
    TrackScrollJank("grid ${rail.title}") { state.isScrollInProgress }
    LaunchedEffect(state) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { idx -> if (idx != null && idx >= rail.items.size - 3) onEnd() }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = "Back", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Text(rail.title, color = Color.White, fontSize = 18.sp, fontFamily = Rubik, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            state = state,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(rail.items, key = { "${it.kind}-${it.id}" }) { item ->
                PosterCard(item = item, width = gridW, height = gridW * 1.5f, onOpen = onOpen)
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
