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
import dev.phantomwatch.json.JSONObject
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

private val RailColor = Color(0xFF0A0A0B)

// PlayerPanel.kt strip, these numbers have to match it
internal val EpisodeChip = 46.dp
internal val EpisodeGap = 8.dp
internal val EpisodePad = 20.dp
internal val EpisodePeek = 12.dp
internal const val EpisodesVisible = 10

// words built during composition, string read before bundle loads came back blank
private enum class Fail { Generic, NoStreams, Playback }

private external fun pwFsState(): Boolean

// player.js holds flag, stage can be built after this call
private external fun pwEpisodes(on: Boolean)

private external fun pwAspect(): Float

private external fun pwSubsTop(on: Boolean)

private external fun pwNext(json: String)

// keys phone reads, browser stored in localStorage but never used
private fun playerConfig(tmdb: String, episode: String, language: String?): String = JSONObject()
    .put("tmdb", tmdb)
    .put("episode", episode)
    .put("language", language.orEmpty())
    .put("subsOn", WebSettings.on(SettingsKeys.SUBS_ON))
    .put("subsSize", WebSettings.on(SettingsKeys.SUBS_SIZE, 1))
    .put("subsBg", WebSettings.on(SettingsKeys.SUBS_BG, 1))
    .put("subsStyle", WebSettings.on(SettingsKeys.SUBS_STYLE, SettingsKeys.STYLE_OUTLINED))
    .put("seekStep", WebSettings.on(SettingsKeys.SEEK_STEP, 1))
    .put("skipIntro", WebSettings.on(SettingsKeys.SKIP_INTRO))
    .put("autoplay", WebSettings.on(SettingsKeys.AUTOPLAY))
    .put("nextCount", WebSettings.on(SettingsKeys.NEXT_COUNT, 1))
    .toString()


@Composable
fun PlayerScreen(
    media: Title,
    season: String,
    episode: String,
    onBack: () -> Unit,
    onOpenTitle: (Title) -> Unit,
    onEpisode: (String, String) -> Unit,
) {
    val key = TmdbHost.key
    val isTv = media.kind != "movie"
    var fs by remember { mutableStateOf(false) }
    var sources by remember(media.id, season, episode) { mutableStateOf<List<PlaySource>>(emptyList()) }
    var subs by remember(media.id, season, episode) { mutableStateOf<List<SubSource>>(emptyList()) }
    var fail by remember(media.id, season, episode) { mutableStateOf<Fail?>(null) }
    var errText by remember(media.id, season, episode) { mutableStateOf("") }
    var retry by remember(media.id, season, episode) { mutableStateOf(0) }
    // player draws own stall, resolving here never leaves scrim over feed
    LaunchedEffect(media.kind, media.id, season, episode, retry) {
        if (sources.isNotEmpty()) return@LaunchedEffect
        fail = null
        pwLog("direct", "${media.kind}/${media.id} s=$season e=$episode \"${media.title}\"")
        val mark = TimeSource.Monotonic.markNow()
        try {
            val r = withTimeout(45_000) {
                resolveSources(PW_URL, PW_KEY, media.kind, media.id, season, episode, fallback = PW_FALLBACK)
            }
            pwLog(
                "resolve",
                "+${mark.elapsedNow().inWholeMilliseconds}ms formats=${r.formats.size} subs=${r.subs.size}",
            )
            if (r.formats.isEmpty()) {
                fail = Fail.NoStreams
            } else {
                sources = r.formats
                subs = r.subs
            }
        } catch (e: Exception) {
            pwLog("resolve", "failed: ${e::class.simpleName}: ${e.message}")
            // status code worth showing, browser failed to fetch is not
            val msg = e.message.orEmpty()
            errText = if (msg.startsWith("resolve http ")) msg else ""
            fail = Fail.Generic
        }
    }
    DisposableEffect(media.id, season, episode) {
        val onAsk: (Event) -> Unit = { fs = pwFsState() }
        val onFail: (Event) -> Unit = { fail = Fail.Playback }
        document.addEventListener("pwfs", onAsk)
        document.addEventListener("pwfail", onFail)
        onDispose {
            document.removeEventListener("pwfs", onAsk)
            document.removeEventListener("pwfail", onFail)
        }
    }
    val back = { if (fs) fs = false else onBack() }
    PlatformBackHandler(enabled = true) { back() }
    // video is fixed dom layer, needs scroll offset to move with content
    val leftScroll = rememberScrollState()
    var aspect by remember(media.id) { mutableFloatStateOf(0f) }
    LaunchedEffect(media.id, season, episode) {
        var waited = 0
        while (waited < 6000) {
            val a = pwAspect()
            if (a > 0f) {
                aspect = a
                return@LaunchedEffect
            }
            delay(120)
            waited += 120
        }
    }

    var seasonSel by remember(media.id) { mutableStateOf(season) }
    LaunchedEffect(season) { if (season.isNotEmpty()) seasonSel = season }
    var episodes by remember(media.id, seasonSel) { mutableStateOf<List<Episode>>(emptyList()) }
    var loadingEpisodes by remember(media.id, seasonSel) { mutableStateOf(isTv) }
    LaunchedEffect(media.kind, media.id, seasonSel) {
        if (!isTv) return@LaunchedEffect
        val n = seasonSel.toIntOrNull() ?: return@LaunchedEffect
        DetailCache.episodes(media.kind, media.id, n)?.let {
            episodes = it
            loadingEpisodes = false
            return@LaunchedEffect
        }
        loadingEpisodes = true
        val list = withContext(ioDispatcher) {
            runCatching { Tmdb.season(key, media.id, n) }.getOrDefault(emptyList())
        }
        if (list.isNotEmpty()) DetailCache.putEpisodes(media.kind, media.id, n, list)
        episodes = list
        loadingEpisodes = false
    }
    val nextEp by remember(episodes, season, episode) {
        mutableStateOf(episodes.firstOrNull { it.n == (episode.toIntOrNull() ?: -1) + 1 })
    }
    LaunchedEffect(nextEp, season) {
        val up = nextEp
        pwNext(if (up != null && season.isNotEmpty()) JSONObject()
            .put("se", "S$season E${up.n}").put("name", up.name).toString() else "null")
    }
    DisposableEffect(nextEp, season) {
        val onNext: (Event) -> Unit = {
            val up = nextEp
            if (up != null && season.isNotEmpty()) onEpisode(season, up.n.toString())
        }
        document.addEventListener("pwnext", onNext)
        onDispose { document.removeEventListener("pwnext", onNext) }
    }
    var panel by remember(media.id) { mutableStateOf(PlayerPanel.None) }
    DisposableEffect(Unit) {
        val onList: (Event) -> Unit = {
            panel = if (panel == PlayerPanel.Episodes) PlayerPanel.None else PlayerPanel.Episodes
        }
        document.addEventListener("pweplist", onList)
        onDispose { document.removeEventListener("pweplist", onList) }
    }
    var detail by remember(media.id) { mutableStateOf(DetailCache.detail(media.kind, media.id)) }
    var similar by remember(media.id) { mutableStateOf(DetailCache.similar(media.kind, media.id).orEmpty()) }
    LaunchedEffect(media.kind, media.id) {
        if (detail == null) {
            val mark = TimeSource.Monotonic.markNow()
            pwLog("detail", "fetch start ${media.kind}/${media.id}")
            val d = withContext(ioDispatcher) {
                runCatching { Tmdb.detail(key, media.kind, media.id) }.getOrNull()
            }
            pwLog(
                "detail",
                "fetch end +${mark.elapsedNow().inWholeMilliseconds}ms null=${d == null}" +
                    (d?.let { " genres=${it.genres.size} cast=${it.cast.size} ov=${it.overview?.length ?: 0}" } ?: ""),
            )
            if (d != null) {
                DetailCache.put(d)
                detail = d
            }
        }
        if (similar.isEmpty()) {
            val list = withContext(ioDispatcher) {
                runCatching { Tmdb.similar(key, media.kind, media.id) }.getOrDefault(emptyList())
            }
            if (list.isNotEmpty()) DetailCache.putSimilar(media.kind, media.id, list)
            similar = list
        }
    }
    val synopsis = detail?.overview?.takeIf { it.isNotBlank() } ?: media.overview?.takeIf { it.isNotBlank() }
    val label = if (season.isNotEmpty() && episode.isNotEmpty()) {
        "${media.title} · S$season E$episode"
    } else {
        media.title
    }
    val again: () -> Unit = {
        sources = emptyList()
        subs = emptyList()
        fail = null
        retry++
    }
    // shell changes dispose WebPlayer, move stage to keep playback
    val feed by rememberUpdatedState(StageFeed(sources, subs, label, media, season, episode, detail?.language))
    val stage = remember(media.id) {
        movableContentOf<Boolean, Modifier, Float, () -> Unit> { isFs, mod, scrollOffset, onBack2 ->
            val f = feed
            Stage(f.sources, f.subs, f.label, onBack2, isFs, mod, f.media, f.season, f.episode, scrollOffset, f.language)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Bg)) {
        val windowW = maxWidth
        // short edge under 600dp is phone on its side
        val landscape = maxWidth > maxHeight
        val phone = minOf(maxWidth, maxHeight) < 600.dp
        val sidePanel = landscape && phone
        // phone sidebar caps at 320dp, tablet rail uses wider formula
        val panelW = if (isExpanded(windowW)) {
            (windowW * 0.3f).coerceIn(240.dp, 420.dp).coerceAtMost(windowW * 0.45f)
        } else {
            minOf(320.dp, windowW * 0.36f)
        }
        // picture is dom layer above canvas, drawing in its rect is hidden
        val generic = stringResource(Res.string.player_resolve_failed)
        val failed = when (fail) {
            null -> null
            Fail.Generic -> errText.ifEmpty { generic }
            Fail.NoStreams -> stringResource(Res.string.player_no_streams)
            Fail.Playback -> stringResource(Res.string.player_playback_failed)
        }
        val open = panel
        val closePanel = { panel = PlayerPanel.None }
        val railOpen by animateDpAsState(
            if (open != PlayerPanel.None) panelW else 0.dp, tween(220), label = "rail",
        )
        val rail = @Composable { mod: Modifier ->
            Box(mod.fillMaxHeight().background(Bg)) {
                Box(Modifier.fillMaxHeight().width(1.dp).background(Color.White.copy(alpha = 0.07f)))
                PanelBody(
                    open, media, detail, synopsis, seasonSel, season, episode,
                    episodes, loadingEpisodes, onSeason = { seasonSel = it },
                    onEpisode = onEpisode, onClose = closePanel,
                )
            }
        }
        val sheets = @Composable { mod: Modifier ->
            Sheet(open != PlayerPanel.None, mod) {
                PanelBody(
                    open, media, detail, synopsis, seasonSel, season, episode,
                    episodes, loadingEpisodes, onSeason = { seasonSel = it },
                    onEpisode = onEpisode, onClose = closePanel,
                )
            }
        }
        // episodes control only in fullscreen
        LaunchedEffect(isTv, fs) { pwEpisodes(isTv && fs) }
        if (fs) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight()) { stage(true, Modifier.fillMaxSize(), 0f, back) }
                if (railOpen > 0.dp) rail(Modifier.width(railOpen))
            }
        } else if (sidePanel) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) { stage(false, Modifier.fillMaxSize(), 0f, back) }
                    if (failed != null) ResolveError(failed, again, back)
                }
                if (railOpen > 0.dp) rail(Modifier.width(railOpen))
            }
        } else if (isExpanded(windowW)) {
            val railW = (windowW * 0.3f).coerceIn(240.dp, 420.dp).coerceAtMost(windowW * 0.45f)
            val leftW = windowW - railW
            // video follows real aspect, 16:9 until metadata
            val stageH = if (aspect > 0f) leftW / aspect else leftW * 9f / 16f
            Row(Modifier.fillMaxSize()) {
                // one scroll container for left pane, tall video used to cover content
                Column(
                    Modifier.width(leftW).fillMaxHeight()
                        .verticalScroll(leftScroll),
                ) {
                    stage(false, Modifier.fillMaxWidth().height(stageH), leftScroll.value.toFloat(), back)
                    if (failed != null) ResolveError(failed, again, back)
                    Spacer(Modifier.height(16.dp))
                    Info(
                        media, detail, synopsis, isTv, episodes, loadingEpisodes, seasonSel, episode,
                        onEpisode = onEpisode, onOpenPlot = { panel = PlayerPanel.Plot },
                        onAllEpisodes = { panel = PlayerPanel.Episodes },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(24.dp))
                }
                Box(Modifier.width(railW).fillMaxHeight().background(RailColor)) {
                    Box(Modifier.fillMaxHeight().width(1.dp).background(Color.White.copy(alpha = 0.07f)))
                    SimilarColumn(similar, onOpenTitle)
                    sheets(Modifier.fillMaxSize())
                }
            }
        } else {
            val ratio = (if (aspect > 0f) aspect else 16f / 9f).coerceIn(0.4f, 16f / 9f)
            val fullH = maxHeight
            val stageH = (maxWidth / ratio).coerceAtMost(fullH * 0.6f)
            val panelH = (fullH - stageH).coerceAtLeast(0.dp)
            val density = LocalDensity.current
            val restPx = with(density) { stageH.toPx() }
            val panelPx = with(density) { panelH.toPx() }
            val flingPx = with(density) { 125.dp.toPx() }
            var sheet by remember(media.id) { mutableFloatStateOf(0f) }
            val progress = if (panelPx > 0f) (sheet / panelPx).coerceIn(0f, 1f) else 0f
            // theater puts captions under picture when sheet fully raised
            val theater = progress > 0.5f
            LaunchedEffect(theater) { pwSubsTop(theater) }
            DisposableEffect(Unit) { onDispose { pwSubsTop(false) } }
            val scroller = rememberScrollState()
            val scope = rememberCoroutineScope()
            val glide: (Float) -> Unit = { to -> scope.launch { animate(sheet, to) { v, _ -> sheet = v } } }
            val sheetNested = remember(panelPx, scroller) {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        if (available.y >= 0f || sheet <= 0f) return Offset.Zero
                        val take = available.y.coerceAtLeast(-sheet)
                        sheet += take
                        return Offset(0f, take)
                    }

                    override fun onPostScroll(
                        consumed: Offset,
                        available: Offset,
                        source: NestedScrollSource,
                    ): Offset {
                        if (available.y <= 0f || scroller.value > 0) return Offset.Zero
                        val take = available.y.coerceAtMost(panelPx - sheet)
                        if (take <= 0f) return Offset.Zero
                        sheet += take
                        return Offset(0f, take)
                    }

                    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                        if (sheet <= 0f) return Velocity.Zero
                        glide(if (available.y > flingPx || sheet > panelPx / 2f) panelPx else 0f)
                        return Velocity.Zero
                    }
                }
            }
            PlatformBackHandler(enabled = progress > 0.5f) { glide(0f) }
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().height(stageH + (fullH - stageH) * progress)) {
                    stage(false, Modifier.fillMaxSize(), 0f, back)
                }
                Box(
                    Modifier.offset { IntOffset(0, (restPx + sheet).roundToInt()) }
                        .fillMaxWidth().height(panelH),
                ) {
                    Column(Modifier.fillMaxSize()) {
                        if (failed != null) ResolveError(failed, again, back)
                        Box(Modifier.weight(1f)) {
                            Column(
                                Modifier.fillMaxSize().nestedScroll(sheetNested).verticalScroll(scroller),
                            ) {
                                Spacer(Modifier.height(18.dp))
                                Info(
                                    media, detail, synopsis, isTv, episodes, loadingEpisodes, seasonSel, episode,
                                    onEpisode = onEpisode, onOpenPlot = { panel = PlayerPanel.Plot },
                                    onAllEpisodes = { panel = PlayerPanel.Episodes },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                if (similar.isNotEmpty()) {
                                    Spacer(Modifier.height(22.dp))
                                    SectionTitle(
                                        stringResource(Res.string.player_more_like_this),
                                        Modifier.padding(horizontal = 20.dp),
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 20.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        items(similar.take(12), key = { it.kind + "-" + it.id }) { t ->
                                            PosterCard(item = t, width = 104.dp, height = 156.dp, onOpen = onOpenTitle)
                                        }
                                    }
                                    Spacer(Modifier.height(24.dp))
                                }
                            }
                            sheets(Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

private class StageFeed(
    val sources: List<PlaySource>,
    val subs: List<SubSource>,
    val label: String,
    val media: Title,
    val season: String,
    val episode: String,
    val language: String? = null,
)

@Composable
private fun Stage(
    sources: List<PlaySource>,
    subs: List<SubSource>,
    label: String,
    onBack: () -> Unit,
    fs: Boolean,
    modifier: Modifier,
    media: Title,
    season: String,
    episode: String,
    scrollOffset: Float = 0f,
    language: String? = null,
) {
    val start = remember(media.kind, media.id, season, episode) {
        Resume.startAt(Resume.position(media.kind, media.id, season, episode), 0L)
    }
    val keep = WebSettings.on(SettingsKeys.SAVE_HISTORY) != 0
    Box(modifier.background(Color.Black)) {
        WebPlayer(
            sources, subs, label, onBack, fullscreen = fs,
            startMs = start,
            scrollOffset = scrollOffset,
            onProgress = { pos, dur -> if (keep) Resume.save(media, season, episode, pos, dur) },
            config = playerConfig(media.id, episode, language),
            modifier = Modifier.fillMaxSize(),
        )
    }
}
