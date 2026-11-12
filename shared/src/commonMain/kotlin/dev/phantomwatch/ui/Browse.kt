package dev.phantomwatch.ui

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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phantomwatch.res.*
import org.jetbrains.compose.resources.StringResource
import dev.phantomwatch.core.ioDispatcher
import dev.phantomwatch.core.nowMs
import dev.phantomwatch.data.TmdbHost
import dev.phantomwatch.data.Aliases
import dev.phantomwatch.data.Chart
import dev.phantomwatch.data.Recent
import dev.phantomwatch.data.Search
import dev.phantomwatch.data.Title
import dev.phantomwatch.data.Tmdb
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior

data class RailState(
    val key: String,
    val title: StringResource,
    val path: String,
    val items: List<Title> = emptyList(),
    val page: Int = 1,
    val totalPages: Int = 1,
    val loadingMore: Boolean = false,
    val arg: String? = null,
    val ranked: Boolean = false,
    val landscape: Boolean = false,
)

private data class RailDef(
    val key: String,
    val title: StringResource,
    val path: String,
    val arg: String? = null,
    val ranked: Boolean = false,
    val chart: String? = null,
    val landscape: Boolean = false,
)

private fun railDefs(country: Country): List<RailDef> = listOfNotNull(
    localRail(country),
    RailDef("top", Res.string.rail_top, "movie/top_rated"),
    RailDef("popular", Res.string.rail_popular, "movie/popular", ranked = true),
    RailDef("tv-popular", Res.string.rail_tv_popular, "tv/popular"),
    RailDef("tv-top", Res.string.rail_tv_top, "tv/top_rated"),
    RailDef("anime", Res.string.rail_anime, "discover/tv?with_genres=16&with_original_language=ja&sort_by=popularity.desc"),
    RailDef("action", Res.string.rail_action, "discover/movie?with_genres=28&sort_by=popularity.desc"),
)

private fun localRail(country: Country): RailDef? {
    if (country.code.length != 2) return null
    return RailDef(
        "local",
        Res.string.rail_local,
        "discover/movie?with_origin_country=${country.code}&sort_by=popularity.desc",
        arg = country.name,
        ranked = true,
        chart = "movie",
        landscape = true,
    )
}

@Composable
private fun railTitle(title: StringResource, arg: String?): String =
    if (arg != null) stringResource(title, arg) else stringResource(title)

private val RuleEdge = Color.White.copy(alpha = 0.16f)

class BrowseState {
    val nav = NavStack(Route.fromPath(initialPath()))
    val historyOpen: Boolean get() = nav.top is Route.History
    val railKey: String? get() = (nav.top as? Route.Rail)?.key
    val tab: Int get() = nav.top.tabIndex()
    var openTitle by mutableStateOf<Title?>(null)
    var backToEpisodes by mutableStateOf(false)
    var query by mutableStateOf("")
    var results by mutableStateOf<List<Title>>(emptyList())
    var resultsFor by mutableStateOf("")
    var trending by mutableStateOf<List<Title>>(emptyList())
    var rails by mutableStateOf<List<RailState>>(emptyList())
    var recent by mutableStateOf<List<Recent>>(emptyList())
    val explore = ExploreFeed()
    var loading by mutableStateOf(true)
    var failed by mutableStateOf(false)
    var loaded by mutableStateOf(false)
    var loadedAt by mutableStateOf(0L)
    val homeScroll = LazyListState()

    fun selectTab(index: Int) {
        nav.select(tabRoute(index))
    }

    fun back(): Boolean {
        val leaving = nav.top
        if (!nav.pop()) return false
        if (leaving is Route.Search) query = ""
        return true
    }
}

@Composable
fun HomeScreen(
    browse: BrowseState,
    onWatch: (Title, String, String) -> Unit,
    loadRecent: () -> List<Recent> = { emptyList() },
    onRemoveRecent: (Recent) -> Unit = {},
    onRestoreRecent: (List<Recent>) -> Unit = {},
    onExitApp: (() -> Unit)? = null,
    searchGhost: @Composable (Dp, Int) -> Unit = { _, _ -> },
    detailPane: @Composable (Title?, () -> Unit, (Title, String, String) -> Unit, Boolean) -> Unit = { _, _, _, _ -> },
    youScreen: @Composable () -> Unit = {},
    clipsScreen: @Composable (onOpen: (Title) -> Unit) -> Unit = {},
) {
    val key = TmdbHost.key
    val country = deviceCountry()
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var searching by remember { mutableStateOf(false) }
    var searchSeq by remember { mutableStateOf(0) }
    var selected by remember(browse.openTitle) { mutableStateOf(browse.openTitle) }
    val recents = remember { mutableStateListOf<Title>() }

    fun loadBrowse() {
        scope.launch {
            // a failed refresh must not replace a feed the user is already looking at
            val hadContent = browse.rails.any { it.items.isNotEmpty() }
            browse.loading = true
            try {
                val foundTrending = withContext(ioDispatcher) { Tmdb.trending(key) }
                val foundRails = withContext(ioDispatcher) {
                    railDefs(country).map { def ->
                        val chart = def.chart?.let { Chart.top(key, country.code, it) }
                        if (!chart.isNullOrEmpty()) {
                            return@map RailState(
                                def.key, Res.string.rail_chart, def.path, chart, 1, 1,
                                arg = country.name, ranked = true, landscape = def.landscape,
                            )
                        }
                        val r = Tmdb.rail(key, def.path, 1)
                            ?: return@map RailState(def.key, def.title, def.path, arg = def.arg, ranked = def.ranked, landscape = def.landscape)
                        RailState(def.key, def.title, def.path, r.items, 1, r.totalPages, arg = def.arg, ranked = def.ranked, landscape = def.landscape)
                    }
                }
                browse.trending = foundTrending.take(12)
                browse.rails = foundRails
                browse.explore.reset()
                browse.failed = foundTrending.isEmpty() && foundRails.all { it.items.isEmpty() }
            } catch (_: Exception) {
                browse.failed = !hadContent
            } finally {
                browse.loading = false
                browse.loaded = true
                browse.loadedAt = nowMs()
            }
        }
    }

    fun loadMoreRail(railKey: String) {
        val rail = browse.rails.firstOrNull { it.key == railKey } ?: return
        if (rail.loadingMore || rail.page >= rail.totalPages) return
        scope.launch {
            browse.rails = browse.rails.map { if (it.key == railKey) it.copy(loadingMore = true) else it }
            try {
                val r = withContext(ioDispatcher) { Tmdb.rail(key, rail.path, rail.page + 1) }
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
        browse.explore.loadMore(key, browse.rails, scope)
    }

    LaunchedEffect(Unit) { browse.recent = withContext(ioDispatcher) { loadRecent() } }
    LaunchedEffect(browse.nav.top) { syncRoute(browse.nav.top.path) }
    // TMDB's popular list drifts daily, so a process that lives for days would keep day-one rows
    OnResumeEffect {
        val onHomeTop = browse.nav.top is Route.Home && browse.homeScroll.firstVisibleItemIndex == 0
        if (onHomeTop && (!browse.loaded || nowMs() - browse.loadedAt > 6 * 60 * 60 * 1000L)) loadBrowse()
    }
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
            val found = withContext(ioDispatcher) { Search.titles(term) }
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
        val t = Aliases.resolve(item)
        browse.openTitle = t
        browse.backToEpisodes = false
        selected = t
        recents.removeAll { it.kind == t.kind && it.id == t.id }
        recents.add(0, t)
        // removeLast() resolves to the Java 21 List member, which does not exist before API 35
        if (recents.size > 6) recents.removeAt(recents.lastIndex)
    }

    val inSearch = browse.query.trim().length >= 2
    val openRail = browse.rails.firstOrNull { it.key == browse.railKey }
    var exitAsk by remember { mutableStateOf(false) }
    // registered first so the more specific handler below wins on Android: last callback wins
    if (onExitApp != null) PlatformBackHandler(enabled = true) { exitAsk = true }
    // only while an in-screen view is up, so back at home falls through to the host
    PlatformBackHandler(enabled = browse.nav.canPop) { browse.back() }
    val screenW = screenWidthDp()
    val expanded = isExpanded(screenW)
    val wideDetail = expanded && selected != null
    val contentW = screenW
    val cols = exploreColumns(contentW)
    val gridW = gridCell(contentW, cols)
    val closeDetail = {
        browse.openTitle = null
        browse.backToEpisodes = false
    }
    val watchFromDetail = { t: Title, s: String, e: String ->
        selected = null
        onWatch(t, s, e)
    }
    val openItem = stableCallback<Title> { openDetail(it) }
    val seeAll = stableCallback<String> { browse.nav.push(Route.Rail(it)) }
    val railEnd = stableCallback<String> { loadMoreRail(it) }
    val gridEnd = stableCallback0 { loadMoreExplore() }
    val retryBrowse = stableCallback0 { loadBrowse() }

    val scrolled by remember {
        derivedStateOf { browse.homeScroll.firstVisibleItemIndex > 0 || browse.homeScroll.firstVisibleItemScrollOffset > 8 }
    }
    Box(Modifier.fillMaxSize().background(Bg)) {
        if (!wideDetail) {
            Column(Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = when (browse.nav.top) {
                        is Route.Clips -> 5
                        is Route.Search -> 1
                        is Route.Rail -> 2
                        is Route.History -> 3
                        is Route.You -> 4
                        else -> 0
                    },
                    transitionSpec = {
                        fadeIn(tween(280)) togetherWith fadeOut(tween(280)) using SizeTransform(clip = false)
                    },
                    label = "browse-mode",
                ) { mode ->
                    when (mode) {
                        1 -> Column(Modifier.fillMaxSize()) {
                            SearchHeader(
                                query = browse.query,
                                onQuery = { browse.query = it },
                            )
                            SearchBody(
                                query = browse.query,
                                results = browse.results,
                                searching = searching,
                                recents = recents,
                                cols = cols,
                                gridW = gridW,
                                onOpen = openItem,
                                searchGhost = searchGhost,
                            )
                        }
                        2 -> openRail?.let {
                            RailGrid(
                                rail = it,
                                width = contentW,
                                onBack = { browse.back() },
                                onOpen = openItem,
                                onEnd = { loadMoreRail(it.key) },
                            )
                        } ?: Box(Modifier.fillMaxSize())
                        3 -> HistoryScreen(
                            recent = browse.recent,
                            cols = cols,
                            gridW = gridW,
                            onPlay = { r ->
                                onWatch(
                                    Title(id = r.id, kind = r.kind, title = r.title, poster = r.poster),
                                    r.season,
                                    r.episode,
                                )
                            },
                            onDelete = { r ->
                                browse.recent = browse.recent.filterNot {
                                    it.kind == r.kind && it.id == r.id && it.season == r.season && it.episode == r.episode
                                }
                                scope.launch(ioDispatcher) { onRemoveRecent(r) }
                            },
                            onRestore = { r, at ->
                                val list = browse.recent.toMutableList()
                                list.add(at.coerceIn(0, list.size), r)
                                browse.recent = list
                                scope.launch(ioDispatcher) { onRestoreRecent(list) }
                            },
                        )
                        4 -> youScreen()
                        5 -> clipsScreen(openItem)
                        else -> Box(Modifier.fillMaxSize()) {
                            BrowseBody(
                                trending = browse.trending,
                                rails = browse.rails,
                                recent = browse.recent,
                                explore = browse.explore,
                                onGenre = { browse.explore.select(it, key, scope) },
                                loading = browse.loading,
                                failed = browse.failed,
                                detailOpen = selected != null,
                                listState = browse.homeScroll,
                                width = contentW,
                                onOpen = openItem,
                                onPlayRecent = { r ->
                                    onWatch(Title(id = r.id, kind = r.kind, title = r.title, poster = r.poster), r.season, r.episode)
                                },
                                onSeeAll = seeAll,
                                onRailEnd = railEnd,
                                onGridEnd = gridEnd,
                                onRetry = retryBrowse,
                            )
                            BrowseHeader(scrolled = scrolled, onSearch = { browse.nav.push(Route.Search) })
                        }
                    }
                }
            }
        }
        if (wideDetail) {
            detailPane(selected, closeDetail, watchFromDetail, browse.backToEpisodes)
        }
        if (!expanded) {
            AnimatedVisibility(
                visible = selected != null,
                enter = slideInVertically(tween(350), initialOffsetY = { it }) + fadeIn(tween(350)),
                exit = slideOutVertically(tween(350), targetOffsetY = { it }) + fadeOut(tween(350)),
            ) {
                detailPane(selected, closeDetail, watchFromDetail, browse.backToEpisodes)
            }
        }
        if (exitAsk) {
            ExitDialog(
                onCancel = { exitAsk = false },
                onExit = {
                    exitAsk = false
                    onExitApp?.invoke()
                },
            )
        }
    }
}

@Composable
private fun BrowseHeader(scrolled: Boolean, onSearch: () -> Unit) {
    val solid by animateFloatAsState(if (scrolled) 1f else 0f, tween(320), label = "header-solid")
    Box(
        Modifier.fillMaxWidth()
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
    ) {
        Box(Modifier.matchParentSize().background(Bg.copy(alpha = 0.97f * solid)))
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val screenW = screenWidthDp()
            Image(
                painter = painterResource(Res.drawable.phantom_logo),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(if (isExpanded(screenW)) 24.dp else 22.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.browse_brand), color = Color.White, fontSize = if (isExpanded(screenW)) 24.sp else 20.sp, fontFamily = Lilita, fontWeight = FontWeight.Normal, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onSearch),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(Res.drawable.ic_search), contentDescription = stringResource(Res.string.tab_search), tint = Slate200, modifier = Modifier.size(21.dp))
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f * solid)))
    }
}

private val SearchTextStyle: TextStyle
    @Composable get() = TextStyle(
        color = Color.White,
        fontSize = if (isExpanded(screenWidthDp())) 15.sp else 13.sp,
        fontFamily = Rubik,
    )

@Composable
private fun SearchHeader(query: String, onQuery: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.widthIn(max = 480.dp).fillMaxWidth()
                .height(if (isExpanded(screenWidthDp())) 52.dp else 48.dp)
                .clip(CircleShape).background(SurfaceColor).padding(horizontal = 16.dp),
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
                    if (query.isEmpty()) BasicText(stringResource(Res.string.browse_search_hint), style = SearchTextStyle.copy(color = Color(0xFF939392)))
                    inner()
                },
            )
        }
    }
}

// one remembered instance, so a parent recomposition doesn't invalidate every poster card
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrowseBody(
    trending: List<Title>,
    rails: List<RailState>,
    recent: List<Recent>,
    explore: ExploreFeed,
    loading: Boolean,
    failed: Boolean,
    detailOpen: Boolean,
    listState: LazyListState,
    width: Dp,
    onOpen: (Title) -> Unit,
    onGenre: (ExploreGenre) -> Unit,
    onPlayRecent: (Recent) -> Unit,
    onSeeAll: (String) -> Unit,
    onRailEnd: (String) -> Unit,
    onGridEnd: () -> Unit,
    onRetry: () -> Unit,
) {
    val cols = exploreColumns(width)
    val gridW = gridCell(width, cols)
    val exploreRows = remember(explore.items, cols) { explore.items.chunked(cols) }
    // outside composition: a state read here would re-run every poster card in the content lambda
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            // the index, not a boolean: snapshotFlow drops repeats, so a latched boolean could never retry
            info.visibleItemsInfo.lastOrNull()?.index?.let { it to info.totalItemsCount } ?: (-1 to 0)
        }.collect { (last, total) -> if (last >= total - 2) onGridEnd() }
    }
    if (loading && trending.isEmpty() && rails.all { it.items.isEmpty() }) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Cyan)
        }
        return
    }
    if (failed) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(stringResource(Res.string.browse_tmdb_failed), color = Cyan, fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(Res.string.browse_tap_retry), color = Slate400, fontFamily = Mono, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.TextButton(onClick = onRetry) {
                Text(stringResource(Res.string.common_retry), color = Cyan, fontFamily = Rubik, fontWeight = FontWeight.SemiBold)
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
        // one item per rail, so an explore page only recomposes the grid rows
        item(key = "hero", contentType = "hero") {
            TrendingHero(trending = trending, paused = detailOpen, onOpen = onOpen, width = width, listState = listState)
        }
        if (recent.isNotEmpty()) {
            item(key = "continue", contentType = "continue") {
                ContinueRow(recent, width, onPlayRecent)
            }
        }
        rails.forEach { rail ->
            if (rail.items.isNotEmpty()) {
                item(key = "rail-${rail.key}", contentType = "rail") {
                    RailSection(rail, width, onOpen, onSeeAll, onRailEnd)
                }
            }
        }
        if (explore.items.isNotEmpty() || explore.loading) {
            item(key = "explore", contentType = "header") {
                Column(
                    Modifier.fillMaxWidth().padding(top = 44.dp, bottom = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, RuleEdge))))
                        Text(
                            stringResource(Res.string.browse_explore_more), color = Slate200, fontSize = if (isExpanded(width)) 28.sp else 20.sp,
                            fontFamily = Rubik, fontWeight = FontWeight.Bold,
                        )
                        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(RuleEdge, Color.Transparent))))
                    }
                    Spacer(Modifier.height(14.dp))
                    ExploreGenrePicker(explore.genre, explore.loading, onGenre)
                }
            }
        }
        items(
            exploreRows,
            key = { row -> "row-" + row.first().let { "${it.kind}-${it.id}" } },
            contentType = { "row" },
        ) { row ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).animateItem(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { item ->
                    PosterCard(item = item, width = gridW, height = gridW * 1.5f, onOpen = onOpen)
                }
            }
        }
        if (explore.loading) {
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
    val ranked = rail.ranked
    val landscape = rail.landscape
    val railW = if (landscape) landscapeCell(width) else featuredCell(width)
    val railH = if (landscape) railW * 9f / 16f else railW * 4f / 3f
    val snap = rememberSnapFlingBehavior(rowState)
    // the numeral lives in the left gutter, so the padding is uneven to centre the poster itself
    val lead = railH * 0.26f
    val gutter = ((width - railW) / 2).coerceAtLeast(16.dp)
    val side = if (landscape) (gutter - lead).coerceAtLeast(8.dp) else 16.dp
    val tail = if (landscape) gutter else 16.dp
    LaunchedEffect(rowState, ranked) {
        if (ranked) return@LaunchedEffect
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
                railTitle(rail.title, rail.arg), color = Slate200, fontSize = if (isExpanded(width)) 22.sp else 16.sp,
                fontFamily = Rubik, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
            )
            if (!ranked) {
                Row(Modifier.clickable { onSeeAll(rail.key) }, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(Res.string.browse_see_all), color = Cyan, fontSize = if (isExpanded(width)) 15.sp else 13.sp,
                        fontFamily = Rubik, fontWeight = FontWeight.SemiBold,
                    )
                    ChevronRightIcon(contentDescription = null, tint = Cyan, size = if (isExpanded(width)) 18.dp else 15.dp)
                }
            }
        }
        val cells: LazyListScope.() -> Unit = {
            val shown = if (ranked) rail.items.take(10) else rail.items
            itemsIndexed(shown, key = { _, it -> "${it.kind}-${it.id}" }, contentType = { _, _ -> "poster" }) { i, item ->
                if (ranked) {
                    TopTenCard(i + 1, item, railW, railH, onOpen)
                } else {
                    PosterCard(item = item, width = railW, height = railH, onOpen = onOpen, caption = false)
                }
            }
        }
        if (landscape) {
            LazyRow(
                state = rowState,
                contentPadding = PaddingValues(start = side, end = tail),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                flingBehavior = snap,
                content = cells,
            )
        } else {
            LazyRow(
                state = rowState,
                contentPadding = PaddingValues(start = 16.dp, end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                content = cells,
            )
        }
    }
}

@Composable
private fun SearchBody(
    query: String,
    results: List<Title>,
    searching: Boolean,
    recents: List<Title>,
    cols: Int,
    gridW: androidx.compose.ui.unit.Dp,
    onOpen: (Title) -> Unit,
    searchGhost: @Composable (Dp, Int) -> Unit,
) {
    if (query.trim().length >= 2) {
        Column(Modifier.fillMaxSize()) {
            Text(
                if (searching) stringResource(Res.string.browse_searching, query)
                else if (results.isEmpty()) stringResource(Res.string.browse_no_results)
                else stringResource(Res.string.browse_results_for, results.size, query),
                color = Slate400, fontSize = 12.sp, fontFamily = Mono,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (results.isEmpty() && !searching) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(stringResource(Res.string.browse_nothing_found), color = Cyan, fontSize = 14.sp, fontFamily = Mono, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(Res.string.browse_try_another), color = Slate400, fontSize = 12.sp, fontFamily = Rubik)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(cols),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
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
            Text(stringResource(Res.string.browse_recent_search), color = Color.White, fontSize = 16.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
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
            var run by remember { mutableStateOf(0) }
            LaunchedEffect(imeVisible) { if (imeVisible) run++ }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                searchGhost(ghost, run)
                Spacer(Modifier.height(20.dp))
                Text(stringResource(Res.string.browse_search_prompt), color = Color.White, fontSize = 16.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun RailGrid(rail: RailState, width: Dp, onBack: () -> Unit, onOpen: (Title) -> Unit, onEnd: () -> Unit) {
    val cols = exploreColumns(width)
    val gridW = gridCell(width, cols)
    val railW = featuredCell(width)
    val state = rememberLazyGridState()
    LaunchedEffect(state) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { idx -> if (idx != null && idx >= rail.items.size - 3) onEnd() }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(railTitle(rail.title, rail.arg), onBack)
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            state = state,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
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
                        stringResource(Res.string.browse_all_pages, rail.totalPages),
                        color = Slate600, fontSize = 11.sp, fontFamily = Mono, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }
        }
    }
}
