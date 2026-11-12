package dev.phantomwatch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.phantomwatch.R
import dev.phantomwatch.core.ioDispatcher
import dev.phantomwatch.data.Title
import dev.phantomwatch.data.Tmdb
import dev.phantomwatch.data.TmdbHost
import dev.phantomwatch.player.TrailerBridge
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class Clip(val title: Title, val key: String)

private enum class ClipStage { Idle, Prewarm, Active }

// survives the tab switch, which disposes this screen and would otherwise refetch every key
private val clipKeys = ConcurrentHashMap<String, String>()

internal suspend fun clipKeyOf(t: Title): String? {
    val id = "${t.kind}:${t.id}"
    clipKeys[id]?.let { return it }
    return Tmdb.clipKey(TmdbHost.key, t.kind, t.id)?.also { clipKeys[id] = it }
}

// one player at a time: a pager that composes a player per page churns the codec and stalls
@Composable
fun ClipsScreen(
    titles: List<Title>,
    onOpen: (Title) -> Unit,
    paused: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var clips by remember(titles) { mutableStateOf<List<Clip>>(emptyList()) }
    var loading by remember(titles) { mutableStateOf(true) }
    val density = LocalDensity.current
    val navBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    // the tab pill floats over the feed at navBottom + 8 + 64, so its chrome starts 16 above that
    val tabClear = navBottom + 76.dp

    LaunchedEffect(titles) {
        loading = true
        // boot the token WebView now so it is warm by the time a key lands
        TrailerBridge.warm()
        val slots = arrayOfNulls<Clip>(titles.size)
        coroutineScope {
            titles.forEachIndexed { i, t ->
                launch {
                    val k = withContext(ioDispatcher) { clipKeyOf(t) } ?: return@launch
                    slots[i] = Clip(t, k)
                    // only the ready prefix is published, so a late reply never shifts the open page
                    var n = 0
                    while (n < slots.size && slots[n] != null) n++
                    clips = slots.take(n).filterNotNull()
                    loading = false
                }
            }
        }
        loading = false
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        when {
            loading -> CircularProgressIndicator(color = Cyan, strokeWidth = 3.dp, modifier = Modifier.align(Alignment.Center).size(32.dp))
            clips.isEmpty() -> Text(
                stringResource(R.string.clips_empty), color = Slate400, fontSize = 14.sp,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
            else -> {
                val pager = rememberPagerState(pageCount = { clips.size })
                // resolving is the slow half of a swipe, and the bridge caches for 30 min
                val nextKey = clips.getOrNull(pager.currentPage + 1)?.key
                LaunchedEffect(nextKey) {
                    nextKey?.let { withContext(ioDispatcher) { TrailerBridge.resolve(it) } }
                }
                val warm = pager.currentPage + 1
                VerticalPager(state = pager, beyondViewportPageCount = 1, modifier = Modifier.fillMaxSize()) { page ->
                    ClipPage(
                        clip = clips[page],
                        stage = when (page) {
                            pager.currentPage -> ClipStage.Active
                            warm -> ClipStage.Prewarm
                            else -> ClipStage.Idle
                        },
                        paused = paused,
                        tabClear = tabClear,
                        onOpen = onOpen,
                    )
                }
            }
        }
    }
}

@Composable
private fun ClipPage(clip: Clip, stage: ClipStage, paused: Boolean, tabClear: Dp, onOpen: (Title) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val dir = LocalLayoutDirection.current
        // landscape puts the nav bar and a cutout on the side, not the bottom
        val side = with(density) {
            maxOf(WindowInsets.navigationBars.getRight(this, dir), WindowInsets.displayCutout.getRight(this, dir)).toDp()
        }
        // a short window has to keep room for the title, so the expanded text cannot take ten lines
        val cap = minOf(with(density) { 20.sp.toDp() * 10 }, (maxHeight - tabClear - 48.dp) * 0.55f)
        val frame = ClipFrame(tabClear, side, cap)
        // a tablet shows the same reel a phone would: one 9:16 column on black, nothing beside it
        val reel = minOf(640.dp, maxHeight * 0.85f)
        Box(Modifier.align(Alignment.Center).width(reel).fillMaxHeight().clipToBounds()) {
            ClipVideo(clip, stage, paused, frame)
            ClipOverlay(clip, frame, onOpen)
        }
    }
}

private data class ClipFrame(val tabClear: Dp, val side: Dp, val cap: Dp)

@Composable
private fun ClipVideo(clip: Clip, stage: ClipStage, paused: Boolean, frame: ClipFrame) {
    val art = clip.title.backdrop ?: clip.title.poster
    if (stage != ClipStage.Idle) {
        CompositionLocalProvider(
            LocalTrailerChrome provides TrailerChrome(
                bottomInset = frame.tabClear,
                muteBottom = frame.tabClear + 132.dp,
                muteEnd = 42.dp + frame.side,
                prewarm = stage == ClipStage.Prewarm,
                paused = paused,
            ),
        ) {
            TrailerHero(videoId = clip.key, poster = art, modifier = Modifier.fillMaxSize())
        }
    } else if (art != null) {
        AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.42f),
                0.22f to Color.Transparent,
                0.5f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.94f),
            ),
        ),
    )
}

@Composable
private fun BoxScope.ClipOverlay(clip: Clip, frame: ClipFrame, onOpen: (Title) -> Unit) {
    Column(
        Modifier.align(Alignment.BottomStart)
            .padding(start = 16.dp, end = 108.dp + frame.side, bottom = frame.tabClear + 48.dp)
            .fillMaxWidth(),
    ) {
        Column(Modifier.clickable { onOpen(clip.title) }) {
            Text(
                clip.title.title, color = Color.White, fontSize = 23.sp, lineHeight = 27.sp,
                fontFamily = Lilita, fontWeight = FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            ClipTags(clip.title, Modifier.padding(top = 7.dp))
        }
        clip.title.overview?.takeIf { it.isNotBlank() }?.let { ClipOverview(it, frame.cap) }
    }
    ClipPoster(clip.title, frame.side, frame.tabClear, onOpen)
}

@Composable
private fun BoxScope.ClipPoster(t: Title, side: Dp, tabClear: Dp, onOpen: (Title) -> Unit) {
    val poster = t.poster ?: return
    AsyncImage(
        model = poster, contentDescription = t.title, contentScale = ContentScale.Crop,
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 15.dp + side, bottom = tabClear + 34.dp)
            .size(88.dp).clip(CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
            .clickable { onOpen(t) },
    )
}

@Composable
private fun ClipTags(t: Title, modifier: Modifier = Modifier) {
    val rating = t.rating?.takeIf { it > 0.0 }?.let { "%.1f".format(it) }
    val rest = listOfNotNull(
        t.year?.takeIf { it.isNotBlank() },
        stringResource(if (t.kind == "tv") R.string.detail_kind_series else R.string.detail_kind_movie),
    ).joinToString("  •  ")
    val tint = Color.White.copy(alpha = 0.86f)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (rating != null) {
            Icon(painterResource(R.drawable.ic_star), contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            Text(rating, color = tint, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp, end = 10.dp))
        }
        Text(
            if (rating != null) "•  $rest" else rest, color = tint, fontSize = 13.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ClipOverview(overview: String, cap: Dp) {
    var open by remember(overview) { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val touch = remember { MutableInteractionSource() }
    // only a text that actually overflows needs its drag kept away from the pager
    val scrolls = open && scroll.maxValue > 0
    // collapsed always shows the top, so the first line is never a leftover scrolled position
    LaunchedEffect(open) { if (!open) scroll.scrollTo(0) }
    Text(
        text = clipBody(overview, open), color = Color.White.copy(alpha = 0.94f), fontSize = 14.sp, lineHeight = 20.sp,
        maxLines = if (open) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 10.dp)
            .animateContentSize()
            .heightIn(max = cap)
            .then(if (scrolls) Modifier.nestedScroll(KeepPageScroll) else Modifier)
            .then(if (open) Modifier.verticalScroll(scroll) else Modifier)
            .clickable(interactionSource = touch, indication = null) { open = !open },
    )
}

// the text sits inside the pager, so leftover drag at its ends would otherwise flip the clip
private object KeepPageScroll : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) = Offset(0f, available.y)
    override suspend fun onPostFling(consumed: Velocity, available: Velocity) = Velocity(0f, available.y)
}

// the tail is cut by hand so the bold "more" lands on the last line instead of after an ellipsis
private fun clipBody(overview: String, open: Boolean) = buildAnnotatedString {
    val flat = overview.replace('\n', ' ').trim()
    if (open) {
        append(flat)
        append("  ")
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("less") }
        return@buildAnnotatedString
    }
    if (flat.length <= 44) return@buildAnnotatedString append(flat)
    append(flat.take(44).substringBeforeLast(' '))
    append(" … ")
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("more") }
}
