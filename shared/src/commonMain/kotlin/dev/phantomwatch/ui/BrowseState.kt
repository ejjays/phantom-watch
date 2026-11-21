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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import dev.phantomwatch.core.SplashGate
import dev.phantomwatch.data.TmdbHost
import dev.phantomwatch.data.Aliases
import dev.phantomwatch.data.Chart
import dev.phantomwatch.data.Recent
import dev.phantomwatch.data.Search
import dev.phantomwatch.data.Title
import dev.phantomwatch.data.Tmdb
import dev.phantomwatch.json.JSONArray
import dev.phantomwatch.json.JSONObject
import dev.phantomwatch.core.Log
import kotlinx.coroutines.coroutineScope
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
    val ranks: List<Int> = emptyList(),
    val landscape: Boolean = false,
)

internal data class RailDef(
    val key: String,
    val title: StringResource,
    val path: String,
    val arg: String? = null,
    val ranked: Boolean = false,
    val chart: String? = null,
    val landscape: Boolean = false,
)

internal fun railDefs(country: Country): List<RailDef> = listOfNotNull(
    localRail(country),
    RailDef("top", Res.string.rail_top, "movie/top_rated"),
    RailDef("tv-popular", Res.string.rail_tv_popular, "tv/popular"),
    RailDef("tv-top", Res.string.rail_tv_top, "tv/top_rated"),
    RailDef("popular", Res.string.rail_popular, "movie/popular", ranked = true),
    RailDef("anime", Res.string.rail_anime, "discover/tv?with_genres=16&with_original_language=ja&sort_by=popularity.desc"),
    RailDef("action", Res.string.rail_action, "discover/movie?with_genres=28&sort_by=popularity.desc"),
)

private fun localRail(country: Country): RailDef? {
    if (country.code.length != 2) return null
    return RailDef(
        "local",
        Res.string.rail_local,
        "discover/tv?with_origin_country=${country.code}&sort_by=popularity.desc",
        arg = country.name,
        ranked = true,
        chart = "tv",
        landscape = true,
    )
}

internal suspend fun railOf(def: RailDef, key: String, country: Country): RailState {
    val chart = def.chart?.let { Chart.top(key, country.code, it) }
    if (!chart.isNullOrEmpty()) {
        return RailState(
            def.key, Res.string.rail_chart, def.path, chart.map { it.title }, 1, 1,
            arg = country.name, ranked = true, ranks = chart.map { it.rank },
            landscape = def.landscape,
        )
    }
    val r = Tmdb.rail(key, def.path, 1)
        ?: return RailState(def.key, def.title, def.path, arg = def.arg, ranked = def.ranked, landscape = def.landscape)
    return RailState(def.key, def.title, def.path, r.items, 1, r.totalPages, arg = def.arg, ranked = def.ranked, landscape = def.landscape)
}
private const val FEED_CACHE_VERSION = 4

private fun feedTitle(e: JSONObject): Title? {
    val id = e.optString("id")
    val kind = e.optString("kind")
    val name = e.optString("title")
    if (id.isEmpty() || name.isEmpty() || (kind != "movie" && kind != "tv")) return null
    return Title(
        id, kind, name,
        e.optString("poster").ifEmpty { null },
        e.optString("backdrop").ifEmpty { null },
        e.optDouble("rating").takeIf { !it.isNaN() },
        e.optString("year").ifEmpty { null },
        e.optString("overview").ifEmpty { null },
    )
}

private fun feedTitles(arr: JSONArray?): List<Title> =
    arr?.let { a -> (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let(::feedTitle) } }.orEmpty()

private fun titleJson(t: Title): JSONObject = JSONObject()
    .put("id", t.id).put("kind", t.kind).put("title", t.title)
    .put("poster", t.poster).put("backdrop", t.backdrop)
    .put("rating", t.rating).put("year", t.year).put("overview", t.overview)

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
    var downloaded by mutableStateOf<List<Recent>>(emptyList())
    val explore = ExploreFeed()
    var loading by mutableStateOf(true)
    var failed by mutableStateOf(false)
    var loaded by mutableStateOf(false)
    var loadedAt by mutableStateOf(0L)
    var hydrated by mutableStateOf(false)
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

    // last feed painted before network answers, cold start is not a spinner
    fun restoreFeed(raw: String, country: Country) {
        if (rails.isNotEmpty() || trending.isNotEmpty()) return
        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return
        if (o.optInt("v") != FEED_CACHE_VERSION) return
        val arr = o.optJSONArray("rails") ?: return
        val defs = railDefs(country).associateBy { it.key }
        val restored = (0 until arr.length()).mapNotNull { i ->
            val e = arr.optJSONObject(i) ?: return@mapNotNull null
            val def = defs[e.optString("key")] ?: return@mapNotNull null
            val items = feedTitles(e.optJSONArray("items"))
            if (items.isEmpty()) return@mapNotNull null
            // chart row swapped rail title and ranks for live chart
            val ranks = e.optJSONArray("ranks")?.let { a -> (0 until a.length()).map { a.optInt(it) } }.orEmpty()
            val chart = ranks.isNotEmpty()
            RailState(
                def.key, if (chart) Res.string.rail_chart else def.title, def.path, items,
                e.optInt("page", 1), e.optInt("pages", 1),
                arg = if (chart) country.name else def.arg, ranked = def.ranked,
                ranks = ranks, landscape = def.landscape,
            )
        }
        val trend = feedTitles(o.optJSONArray("trending")).take(12)
        if (restored.isEmpty() && trend.isEmpty()) return
        trending = trend
        rails = restored
        failed = false
        val at = o.optLong("at", 0L)
        if (at > 0) {
            loaded = true
            loadedAt = at
        }
        Log.d("feed restore ${restored.size} rails")
    }

    fun feedJson(country: Country): String? {
        val defs = railDefs(country).map { it.key }.toSet()
        val keep = rails.filter { it.items.isNotEmpty() && it.key in defs }
        if (trending.isEmpty() && keep.isEmpty()) return null
        val o = JSONObject().put("v", FEED_CACHE_VERSION)
        o.put("at", nowMs())
        o.put("trending", JSONArray().also { a -> trending.forEach { a.put(titleJson(it)) } })
        o.put(
            "rails",
            JSONArray().also { a ->
                keep.forEach { r ->
                    a.put(
                        JSONObject().put("key", r.key).put("page", r.page).put("pages", r.totalPages)
                            .put("ranks", JSONArray().also { ra -> r.ranks.forEach { ra.put(it) } })
                            .put("items", JSONArray().also { ia -> r.items.forEach { ia.put(titleJson(it)) } }),
                    )
                }
            },
        )
        return o.toString()
    }
}
