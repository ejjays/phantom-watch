package dev.phantomwatch.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import dev.phantomwatch.R
import dev.phantomwatch.core.RemoteConfig
import coil3.compose.AsyncImage
import dev.phantomwatch.data.DetailCache
import dev.phantomwatch.data.Episode
import dev.phantomwatch.data.Title
import dev.phantomwatch.data.TitleDetail
import dev.phantomwatch.data.Tmdb
import dev.phantomwatch.social.Social
import dev.phantomwatch.ui.Bg
import dev.phantomwatch.ui.ChevronRightIcon
import dev.phantomwatch.ui.CloseIcon
import dev.phantomwatch.ui.CommentIcon
import dev.phantomwatch.ui.CommentsSheet
import dev.phantomwatch.ui.Cyan
import dev.phantomwatch.ui.EpisodesScreen
import dev.phantomwatch.ui.PosterCard
import dev.phantomwatch.ui.Rubik
import dev.phantomwatch.ui.ShareIcon
import dev.phantomwatch.ui.Slate200
import dev.phantomwatch.ui.Slate300
import dev.phantomwatch.ui.Slate400
import dev.phantomwatch.ui.Slate500
import dev.phantomwatch.ui.SurfaceColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Up-next rail: one step darker than the page, so the two tablet panes read as separate. */
private val RailColor = Color(0xFF0A0A0B)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlayerPanel(
    media: Title,
    season: String,
    episode: String,
    overview: String? = null,
    onEpisode: ((String, String) -> Unit)? = null,
    onOpenTitle: ((Title) -> Unit)? = null,
    onHeaderBottom: ((Float) -> Unit)? = null,
    onSheetLock: ((Boolean) -> Unit)? = null,
    video: (@Composable (Modifier) -> Unit)? = null,
    videoAspect: Float = 16f / 9f,
    fullscreen: Boolean = false,
) {
    val key = RemoteConfig.tmdbKey()
    var detail by remember(media.id) { mutableStateOf(DetailCache.detail(media.kind, media.id)) }
    LaunchedEffect(media.id) {
        if (detail != null) return@LaunchedEffect
        val d = withContext(Dispatchers.IO) {
            runCatching { Tmdb.detail(key, media.kind, media.id) }.getOrNull()
        }
        if (d != null) {
            DetailCache.put(d)
            detail = d
        }
    }
    val d: TitleDetail? = detail
    val synopsis = overview?.takeIf { it.isNotBlank() } ?: d?.overview?.takeIf { it.isNotBlank() }
    var showSheet by remember { mutableStateOf(false) }
    var showEpisodes by remember { mutableStateOf(false) }
    val tmdbId = media.id.toIntOrNull() ?: 0
    var showComments by remember { mutableStateOf(false) }
    LaunchedEffect(showComments) { onSheetLock?.invoke(showComments) }
    var stats by remember(media.kind, media.id) {
        mutableStateOf(Social.cachedStats(media.kind, tmdbId) ?: Social.Stats())
    }
    var voted by remember(media.kind, media.id) { mutableStateOf(false) }
    val scopeUi = rememberCoroutineScope()
    LaunchedEffect(media.kind, media.id) {
        if (tmdbId > 0) Social.stats(media.kind, tmdbId)?.let { stats = it }
    }

    val isTv = media.kind != "movie"
    val playingSeason = season.toIntOrNull() ?: 1
    var episodes by remember(media.id) { mutableStateOf(DetailCache.episodes(media.kind, media.id, playingSeason).orEmpty()) }
    var loadingEpisodes by remember(media.id) { mutableStateOf(false) }
    LaunchedEffect(media.id, playingSeason) {
        if (!isTv) return@LaunchedEffect
        DetailCache.episodes(media.kind, media.id, playingSeason)?.let {
            episodes = it
            return@LaunchedEffect
        }
        loadingEpisodes = true
        val list = withContext(Dispatchers.IO) {
            runCatching { Tmdb.season(key, media.id, playingSeason) }.getOrDefault(emptyList())
        }
        if (list.isNotEmpty()) DetailCache.putEpisodes(media.kind, media.id, playingSeason, list)
        episodes = list
        loadingEpisodes = false
    }

    var similar by remember(media.id) { mutableStateOf(DetailCache.similar(media.kind, media.id).orEmpty()) }
    LaunchedEffect(media.id) {
        if (onOpenTitle == null || DetailCache.similar(media.kind, media.id) != null) return@LaunchedEffect
        val list = withContext(Dispatchers.IO) {
            runCatching { Tmdb.similar(key, media.kind, media.id) }.getOrDefault(emptyList())
        }
        if (list.isNotEmpty()) DetailCache.putSimilar(media.kind, media.id, list)
        similar = list
    }

    val cast: (Int) -> Unit = { v ->
        voted = true
        val prev = stats
        if (Social.enabled && tmdbId > 0) {
            stats = if (prev.mine == v) {
                prev.copy(
                    mine = 0,
                    likes = (prev.likes - if (v == Social.LIKE) 1 else 0).coerceAtLeast(0),
                    dislikes = (prev.dislikes - if (v == Social.DISLIKE) 1 else 0).coerceAtLeast(0),
                )
            } else {
                prev.copy(
                    mine = v,
                    likes = (prev.likes + (if (v == Social.LIKE) 1 else 0) -
                        (if (prev.mine == Social.LIKE) 1 else 0)).coerceAtLeast(0),
                    dislikes = (prev.dislikes + (if (v == Social.DISLIKE) 1 else 0) -
                        (if (prev.mine == Social.DISLIKE) 1 else 0)).coerceAtLeast(0),
                )
            }
            scopeUi.launch {
                Social.vote(media.kind, tmdbId, v)?.let { stats = it } ?: run { stats = prev }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Bg)) {
        val panelH = maxHeight
        val railW = (maxWidth * 0.3f).coerceIn(320.dp, 420.dp)
        val slot = video
        if (slot != null) {
            // Tablets: video over the info on the left, a vertical up-next rail on the right.
            // Fullscreen grows the video column and pushes the rail off the right edge.
            val fs by animateFloatAsState(
                if (fullscreen) 1f else 0f, tween(320, easing = FastOutSlowInEasing), label = "fs",
            )
            val colW = maxWidth - railW * (1f - fs)
            // Nothing tucks under the status bar until the video is actually fullscreen.
            val topInset = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
            val paneTop = topInset * (1f - fs)
            val paneH = maxHeight - paneTop
            Row(Modifier.fillMaxSize().clipToBounds()) {
                Column(Modifier.width(colW).fillMaxHeight().padding(top = paneTop)) {
                    // Grows past the video's own aspect so fullscreen letterboxes on the ambient glow.
                    Box(
                        Modifier.fillMaxWidth()
                            .height(lerp(colW / videoAspect.coerceIn(0.4f, 2.4f), paneH, fs))
                            .background(Color.Black),
                    ) { slot(Modifier) }
                    if (fs < 1f) {
                        PlayerInfo(
                            media = media, synopsis = synopsis, stats = stats, voted = voted,
                            isTv = isTv, episodes = episodes, loadingEpisodes = loadingEpisodes,
                            season = playingSeason.toString(), episode = episode,
                            onVote = cast, onComments = { showComments = true },
                            onOpenPlot = { showSheet = true }, onAllEpisodes = { showEpisodes = true },
                            onEpisode = onEpisode,
                            modifier = Modifier.weight(1f).padding(top = 16.dp)
                                .graphicsLayer { alpha = 1f - fs }
                                .verticalScroll(rememberScrollState(), overscrollEffect = null)
                                .navigationBarsPadding(),
                        )
                    }
                }
                Box(
                    Modifier.width(railW).fillMaxHeight().background(RailColor)
                        .graphicsLayer { alpha = 1f - fs },
                ) {
                    Box(Modifier.fillMaxHeight().width(1.dp).background(Color.White.copy(alpha = 0.07f)))
                    if (onOpenTitle != null) {
                        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                            SimilarList(similar, onOpenTitle)
                        }
                    }
                }
            }
        } else {
            Column(
                Modifier.align(Alignment.TopCenter).widthIn(max = 720.dp).fillMaxHeight()
                    .verticalScroll(rememberScrollState(), overscrollEffect = null)
                    .padding(top = 18.dp)
                    .navigationBarsPadding(),
            ) {
                PlayerInfo(
                    media = media, synopsis = synopsis, stats = stats, voted = voted,
                    isTv = isTv, episodes = episodes, loadingEpisodes = loadingEpisodes,
                    season = playingSeason.toString(), episode = episode,
                    onVote = cast, onComments = { showComments = true },
                    onOpenPlot = { showSheet = true }, onAllEpisodes = { showEpisodes = true },
                    onEpisode = onEpisode, onHeaderBottom = onHeaderBottom,
                )
                if (similar.isNotEmpty() && onOpenTitle != null) {
                    Spacer(Modifier.height(22.dp))
                    Box(Modifier.padding(horizontal = 20.dp)) { SectionTitle("More Like This") }
                    Spacer(Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(similar.take(12), key = { it.kind + "-" + it.id }) { t ->
                            PosterCard(item = t, width = 104.dp, height = 156.dp, onOpen = onOpenTitle)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        AnimatedVisibility(
            visible = showEpisodes,
            enter = slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it },
            exit = slideOutVertically(tween(280, easing = FastOutSlowInEasing)) { it },
        ) {
            EpisodesScreen(
                detail = d,
                topInset = 0.dp,
                onClose = { showEpisodes = false },
                onEpisode = { s, e -> onEpisode?.invoke(s, e) },
            )
        }
        if (showSheet && synopsis != null) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val scope = rememberCoroutineScope()
            ModalBottomSheet(
                onDismissRequest = { showSheet = false },
                sheetState = sheetState,
                containerColor = Bg,
                contentColor = Color.White,
                tonalElevation = 0.dp,
                scrimColor = Color.Transparent,
                dragHandle = null,
                contentWindowInsets = { WindowInsets(0) },
            ) {
                Column(Modifier.height(panelH)) {
                    Box(
                        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(40.dp, 4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)))
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Story plot", color = Color.White, fontSize = 22.sp, fontFamily = Rubik,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                        )
                        Box(
                            Modifier.clickable {
                                scope.launch { sheetState.hide() }.invokeOnCompletion {
                                    if (!sheetState.isVisible) showSheet = false
                                }
                            }.padding(6.dp),
                        ) {
                            CloseIcon(contentDescription = "Close", tint = Slate200, size = 22.dp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(20.dp).navigationBarsPadding(),
                    ) {
                        Text(
                            media.title, color = Color.White, fontSize = 20.sp, fontFamily = Rubik,
                            fontWeight = FontWeight.Bold, lineHeight = 28.sp,
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            synopsis.orEmpty(), color = Slate300, fontSize = 15.sp, fontFamily = Rubik,
                            lineHeight = 24.sp,
                        )
                        val year = d?.year ?: media.year
                        val cards = buildList {
                            d?.rating?.takeIf { it > 0 }?.let { add(String.format("%.1f", it) to "Rating") }
                            d?.votes?.takeIf { it > 0 }?.let { add(compactCount(it) to "Votes") }
                            year?.let { add(it to "Released") }
                        }
                        if (cards.isNotEmpty()) {
                            Spacer(Modifier.height(22.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                cards.forEach { (value, label) ->
                                    StatCard(value, label, Modifier.weight(1f))
                                }
                            }
                        }
                        val genres = d?.genres.orEmpty()
                        if (genres.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                genres.take(6).forEach { g ->
                                    Box(
                                        Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.08f))
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                    ) {
                                        Text(g, color = Slate200, fontSize = 12.sp, fontFamily = Rubik, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
        if (showComments) CommentsSheet(media) { showComments = false }
    }
    BackHandler(enabled = showSheet) { showSheet = false }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.06f))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = Color.White, fontSize = 18.sp, fontFamily = Rubik, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(label, color = Slate400, fontSize = 11.sp, fontFamily = Rubik)
    }
}

internal fun compactCount(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fK".format(n / 1_000.0)
    else -> n.toString()
}

@Composable
private fun ThumbButton(
    up: Boolean, stats: Social.Stats, size: Dp, countSize: Int, animate: Boolean,
    onVote: (Int) -> Unit,
) {
    val active = stats.mine == if (up) Social.LIKE else Social.DISLIKE
    val count = if (up) stats.likes else stats.dislikes
    val fillSpec = if (animate) tween(180) else snap<Float>()
    val tintSpec = if (animate) tween(180) else snap<Color>()
    val fill by animateFloatAsState(if (active) 1f else 0f, fillSpec, label = "thumb")
    val tint by animateColorAsState(if (active) Color.White else Slate300, tintSpec, label = "tint")
    val slot = 40.dp
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (count > 0) compactCount(count) else "", color = tint, fontSize = countSize.sp,
            fontFamily = Rubik, fontWeight = FontWeight.SemiBold, lineHeight = (countSize + 2).sp,
        )
        Box(
            Modifier.size(slot).clip(CircleShape)
                .clickable { onVote(if (up) Social.LIKE else Social.DISLIKE) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(if (up) R.drawable.ic_like else R.drawable.ic_dislike),
                contentDescription = if (up) "Like" else "Dislike",
                tint = tint,
                modifier = Modifier.size(size).graphicsLayer {
                    alpha = 1f - fill
                    if (!up) scaleX = -1f
                },
            )
            Icon(
                painter = painterResource(
                    if (up) R.drawable.ic_like_filled else R.drawable.ic_dislike_filled,
                ),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(size).graphicsLayer {
                    alpha = fill
                    val s = 0.72f + 0.28f * fill
                    scaleX = if (up) s else -s
                    scaleY = s
                },
            )
        }
    }
}

@Composable
private fun ActionIcon(onClick: () -> Unit = {}, content: @Composable () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Color.White, fontSize = 16.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold)
}

/** Title, plot, votes, actions and the episode strip: shared by the sheet and the tablet column. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerInfo(
    media: Title,
    synopsis: String?,
    stats: Social.Stats,
    voted: Boolean,
    isTv: Boolean,
    episodes: List<Episode>,
    loadingEpisodes: Boolean,
    season: String,
    episode: String,
    onVote: (Int) -> Unit,
    onComments: () -> Unit,
    onOpenPlot: () -> Unit,
    onAllEpisodes: () -> Unit,
    onEpisode: ((String, String) -> Unit)?,
    onHeaderBottom: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
                Text(
                    media.title, color = Color.White, fontSize = 24.sp, fontFamily = Rubik,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
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
                            "Story plot", color = Color.White, fontSize = 16.sp, fontFamily = Rubik,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            synopsis, color = Slate300, fontSize = 15.sp, fontFamily = Rubik,
                            lineHeight = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                    ThumbButton(true, stats, 24.dp, 14, voted) { onVote(Social.LIKE) }
                    ThumbButton(false, stats, 24.dp, 14, voted) { onVote(Social.DISLIKE) }
                        ActionIcon(onClick = { onComments() }) {
                            CommentIcon(contentDescription = "Comments", tint = Slate300, size = 24.dp)
                        }
                        Spacer(Modifier.weight(1f))
                        ActionIcon { ShareIcon(contentDescription = "Share", tint = Slate300, size = 24.dp) }
                    }
                }
                if (isTv) {
                    Spacer(Modifier.height(22.dp))
                    Row(
                        Modifier.padding(horizontal = 20.dp)
                            .onGloballyPositioned { onHeaderBottom?.invoke(it.positionInWindow().y) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionTitle("Episodes")
                        Spacer(Modifier.weight(1f))
                        if (onEpisode != null) {
                            Row(
                                Modifier.clickable { onAllEpisodes() },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Full", color = Slate200, fontSize = 13.sp, fontFamily = Rubik,
                                    fontWeight = FontWeight.Medium,
                                )
                                ChevronRightIcon(contentDescription = "All episodes", tint = Slate200, size = 15.dp)
                            }
                        }
                    }
                    when {
                        loadingEpisodes -> Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Cyan, modifier = Modifier.size(22.dp))
                        }
                        episodes.isEmpty() -> Text(
                            "No episodes available", color = Slate500, fontSize = 13.sp, fontFamily = Rubik,
                            modifier = Modifier.padding(horizontal = 20.dp).padding(vertical = 12.dp),
                        )
                        else -> {
                            val listState = rememberLazyListState()
                            LaunchedEffect(episodes.size) {
                                val at = episodes.indexOfFirst { it.n.toString() == episode }
                                if (at > 0) listState.scrollToItem(at)
                            }
                            Spacer(Modifier.height(12.dp))
                            LazyRow(
                                state = listState,
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(episodes, key = { it.n }) { ep ->
                                    val active = ep.n.toString() == episode
                                    Box(
                                        Modifier.size(52.dp, 44.dp).clip(RoundedCornerShape(10.dp))
                                            .background(if (active) Cyan else Color.White.copy(alpha = 0.08f))
                                            .clickable { onEpisode?.invoke(season, ep.n.toString()) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            ep.n.toString(), color = if (active) Bg else Slate200,
                                            fontSize = 15.sp, fontFamily = Rubik, fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
    }
}

/** Tablet up-next rail: YouTube-shaped cards, thumbnail over the title. */
@Composable
private fun SimilarList(items: List<Title>, onOpen: (Title) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(key = "header") { SectionTitle("More Like This") }
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
                    t.title, color = Color.White, fontSize = 14.sp, fontFamily = Rubik,
                    fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                val meta = listOfNotNull(
                    t.year,
                    t.rating?.takeIf { it > 0 }?.let { String.format("%.1f", it) },
                ).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(meta, color = Slate400, fontSize = 12.sp, fontFamily = Rubik, maxLines = 1)
                }
            }
        }
    }
}
