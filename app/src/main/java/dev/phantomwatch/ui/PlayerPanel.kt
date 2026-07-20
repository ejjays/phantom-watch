package dev.phantomwatch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phantomwatch.Social
import dev.phantomwatch.RemoteConfig
import dev.phantomwatch.Title
import dev.phantomwatch.TitleDetail
import dev.phantomwatch.Episode
import dev.phantomwatch.VidLove
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
) {
    val key = RemoteConfig.tmdbKey()
    var detail by remember(media.id) { mutableStateOf(DetailCache.detail(media.id)) }
    LaunchedEffect(media.id) {
        if (detail != null) return@LaunchedEffect
        val d = withContext(Dispatchers.IO) {
            runCatching { VidLove.detail(key, media.kind, media.id) }.getOrNull()
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
    var stats by remember(media.kind, media.id) { mutableStateOf(Social.Stats()) }
    val scopeUi = rememberCoroutineScope()
    LaunchedEffect(media.kind, media.id) {
        if (tmdbId > 0) Social.stats(media.kind, tmdbId)?.let { stats = it }
    }

    val isTv = media.kind != "movie"
    val playingSeason = season.toIntOrNull() ?: 1
    var episodes by remember(media.id) { mutableStateOf(DetailCache.episodes(media.id, playingSeason).orEmpty()) }
    var loadingEpisodes by remember(media.id) { mutableStateOf(false) }
    LaunchedEffect(media.id, playingSeason) {
        if (!isTv) return@LaunchedEffect
        DetailCache.episodes(media.id, playingSeason)?.let {
            episodes = it
            return@LaunchedEffect
        }
        loadingEpisodes = true
        val list = withContext(Dispatchers.IO) {
            runCatching { VidLove.season(key, media.id, playingSeason) }.getOrDefault(emptyList())
        }
        if (list.isNotEmpty()) DetailCache.putEpisodes(media.id, playingSeason, list)
        episodes = list
        loadingEpisodes = false
    }

    var similar by remember(media.id) { mutableStateOf(DetailCache.similar(media.id).orEmpty()) }
    LaunchedEffect(media.id) {
        if (onOpenTitle == null || DetailCache.similar(media.id) != null) return@LaunchedEffect
        val list = withContext(Dispatchers.IO) {
            runCatching { VidLove.similar(key, media.kind, media.id) }.getOrDefault(emptyList())
        }
        if (list.isNotEmpty()) DetailCache.putSimilar(media.id, list)
        similar = list
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Bg)) {
        val panelH = maxHeight
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState(), overscrollEffect = null)
                .padding(top = 18.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                media.title, color = Color.White, fontSize = 24.sp, fontFamily = Rubik,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp)
                    .clickable(enabled = synopsis != null) { showSheet = true },
            )
            if (synopsis != null) {
                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background(Surface).clickable { showSheet = true }
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
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val cast: (Int) -> Unit = { v ->
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
                    ActionIcon(onClick = { cast(Social.LIKE) }) {
                        LikeIcon(
                            contentDescription = "Like",
                            tint = if (stats.mine == Social.LIKE) Cyan else Slate300,
                            size = 24.dp,
                        )
                        if (stats.likes > 0) {
                            Text(
                                compactCount(stats.likes), color = Slate200, fontSize = 13.sp,
                                fontFamily = Rubik, modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                    ActionIcon(onClick = { cast(Social.DISLIKE) }) {
                        DislikeIcon(
                            contentDescription = "Dislike",
                            tint = if (stats.mine == Social.DISLIKE) Cyan else Slate300,
                            size = 24.dp,
                        )
                        if (stats.dislikes > 0) {
                            Text(
                                compactCount(stats.dislikes), color = Slate200, fontSize = 13.sp,
                                fontFamily = Rubik, modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                    ActionIcon(onClick = { showComments = true }) {
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
                            Modifier.clickable { showEpisodes = true },
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
                                        .clickable { onEpisode?.invoke(playingSeason.toString(), ep.n.toString()) },
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
            if (similar.isNotEmpty() && onOpenTitle != null) {
                Spacer(Modifier.height(22.dp))
                Box(Modifier.padding(horizontal = 20.dp)) { SectionTitle("More Like This") }
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(similar.take(12), key = { "${it.kind}-${it.id}" }) { t ->
                        PosterCard(item = t, width = 104.dp, height = 156.dp, onOpen = onOpenTitle)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
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
        AnimatedVisibility(
            visible = showComments && tmdbId > 0,
            enter = slideInVertically(tween(240, easing = FastOutSlowInEasing)) { it },
            exit = slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { it },
        ) {
            CommentsPanel(
                kind = media.kind,
                id = tmdbId,
                onDismiss = { showComments = false },
            )
        }
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

private fun compactCount(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fK".format(n / 1_000.0)
    else -> n.toString()
}

@Composable
private fun ActionIcon(onClick: () -> Unit = {}, content: @Composable () -> Unit) {
    Row(
        Modifier.height(48.dp).clip(CircleShape).clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Color.White, fontSize = 16.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun CommentsPanel(kind: String, id: Int, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf<Social.Page?>(null) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(kind, id) { page = Social.comments(kind, id) }

    val submit = {
        val body = input.trim()
        if (body.isNotEmpty() && !sending) {
            sending = true
            error = null
            scope.launch {
                val err = Social.post(kind, id, body)
                sending = false
                if (err == null) {
                    input = ""
                    page = Social.comments(kind, id)
                } else {
                    error = err
                }
            }
        }
        Unit
    }

    Column(
        Modifier.fillMaxSize().background(Bg).navigationBarsPadding().imePadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Comments", color = Color.White, fontSize = 16.sp, fontFamily = Rubik,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp,
                )
                page?.let {
                    Text(
                        if (it.total == 1) "1 comment" else "${compactCount(it.total)} comments",
                        color = Slate400, fontSize = 12.sp, fontFamily = Rubik,
                    )
                }
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                CloseIcon(contentDescription = "Close comments", tint = Slate300, size = 20.dp)
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val p = page
            when {
                p == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Cyan, modifier = Modifier.size(20.dp))
                }
                p.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No comments yet", color = Slate500, fontSize = 13.sp, fontFamily = Rubik)
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    items(p.items) { c -> CommentRow(c) }
                }
            }
        }
        error?.let {
            Text(
                it, color = Color(0xFFE06C75), fontSize = 12.sp, fontFamily = Rubik,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Avatar(Social.me().orEmpty(), 32.dp)
            Spacer(Modifier.size(12.dp))
            Row(
                Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = { input = it.take(500) },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontFamily = Rubik),
                    cursorBrush = SolidColor(Cyan),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                    decorationBox = { field ->
                        Box {
                            if (input.isEmpty()) {
                                Text(
                                    "Add a comment…", color = Slate500, fontSize = 14.sp,
                                    fontFamily = Rubik,
                                )
                            }
                            field()
                        }
                    },
                )
                Box(
                    Modifier.size(30.dp).clip(CircleShape).clickable(enabled = input.isNotBlank() && !sending) {
                        submit()
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    SendIcon(
                        contentDescription = "Post comment",
                        tint = if (input.isBlank() || sending) Slate500 else Cyan,
                        size = 20.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentRow(c: Social.Comment) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Avatar(c.author, 40.dp)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "@${c.author}", color = Slate300, fontSize = 12.sp, fontFamily = Rubik,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text("  ·  ", color = Slate500, fontSize = 12.sp, fontFamily = Rubik)
                Text(Social.ago(c.at), color = Slate500, fontSize = 12.sp, fontFamily = Rubik)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                c.body, color = Color(0xFFE8EAED), fontSize = 14.sp, fontFamily = Rubik,
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun Avatar(handle: String, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            handle.trimStart('@').firstOrNull()?.uppercase() ?: "?",
            color = Slate300, fontSize = (size.value * 0.36f).sp, fontFamily = Rubik,
            fontWeight = FontWeight.Medium,
        )
    }
}
