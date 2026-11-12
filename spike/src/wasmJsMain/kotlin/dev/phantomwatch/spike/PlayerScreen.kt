package dev.phantomwatch.spike

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.phantomwatch.core.PlaySource
import dev.phantomwatch.core.SubSource
import dev.phantomwatch.core.ioDispatcher
import dev.phantomwatch.core.pwLog
import dev.phantomwatch.data.DetailCache
import dev.phantomwatch.data.Title
import dev.phantomwatch.data.TitleDetail
import dev.phantomwatch.data.Tmdb
import dev.phantomwatch.data.TmdbHost
import dev.phantomwatch.res.*
import dev.phantomwatch.ui.Bg
import dev.phantomwatch.ui.PosterCard
import dev.phantomwatch.ui.WorkSans
import dev.phantomwatch.ui.Slate300
import dev.phantomwatch.ui.Slate400
import dev.phantomwatch.ui.SurfaceColor
import dev.phantomwatch.ui.WebPlayer
import dev.phantomwatch.ui.fmt1
import dev.phantomwatch.ui.isExpanded
import kotlinx.browser.document
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource
import org.w3c.dom.events.Event
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val RailColor = Color(0xFF0A0A0B)

// Provided by spike/web/player.js: true while the browser is in fullscreen (or its in-app fallback).
private external fun pwFsState(): Boolean

/**
 * The player screen from the Android app, rebuilt for the web: video over the info on the left and
 * an up-next rail on the right once the window is tablet-wide, the same stack scrolling under the
 * video on phones. The <video> element sits in the stage box and the DOM chrome follows its rect.
 */
@Composable
fun PlayerScreen(
    media: Title,
    season: String,
    episode: String,
    sources: List<PlaySource>,
    subs: List<SubSource>,
    onBack: () -> Unit,
    onOpenTitle: (Title) -> Unit,
) {
    val key = TmdbHost.key
    var fs by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        // The chrome's fullscreen button drives the browser's fullscreen; this mirrors its state.
        val onAsk: (Event) -> Unit = { fs = pwFsState() }
        document.addEventListener("pwfs", onAsk)
        onDispose { document.removeEventListener("pwfs", onAsk) }
    }
    val back = { if (fs) fs = false else onBack() }
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
    // Moving between the three shells would dispose WebPlayer, and with it the <video>, which
    // restarts playback at 0:00. Movable content carries the element across instead.
    val stage = remember {
        movableContentOf<Boolean, Modifier, () -> Unit> { isFs, mod, onBack2 ->
            Stage(sources, subs, label, onBack2, isFs, mod)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Bg)) {
        val windowW = maxWidth
        if (fs) {
            stage(true, Modifier.fillMaxSize(), back)
        } else if (isExpanded(windowW)) {
            // Same split as PlayerWide: the rail keeps a third of the window, capped on both ends.
            val railW = (windowW * 0.3f).coerceIn(240.dp, 420.dp).coerceAtMost(windowW * 0.45f)
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(windowW - railW).fillMaxHeight()) {
                    stage(false, Modifier.fillMaxWidth().aspectRatio(16f / 9f), back)
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Info(media, detail, synopsis, Modifier.fillMaxWidth())
                    }
                }
                Box(Modifier.width(railW).fillMaxHeight().background(RailColor)) {
                    Box(Modifier.fillMaxHeight().width(1.dp).background(Color.White.copy(alpha = 0.07f)))
                    SimilarColumn(similar, onOpenTitle)
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                stage(false, Modifier.fillMaxWidth().aspectRatio(16f / 9f), back)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Info(media, detail, synopsis, Modifier.fillMaxWidth())
                    if (similar.isNotEmpty()) {
                        Spacer(Modifier.height(22.dp))
                        SectionTitle(stringResource(Res.string.player_more_like_this), Modifier.padding(horizontal = 20.dp))
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
            }
        }
    }
}

@Composable
private fun Stage(
    sources: List<PlaySource>,
    subs: List<SubSource>,
    label: String,
    onBack: () -> Unit,
    fs: Boolean,
    modifier: Modifier,
) {
    Box(modifier.background(Color.Black)) {
        WebPlayer(sources, subs, label, onBack, fullscreen = fs, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun Info(media: Title, d: TitleDetail?, synopsis: String?, modifier: Modifier) {
    Column(modifier.padding(bottom = 24.dp)) {
        Spacer(Modifier.height(16.dp))
        Text(
            media.title, color = Color.White, fontSize = 24.sp, fontFamily = WorkSans,
            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        if (synopsis != null) {
            Spacer(Modifier.height(16.dp))
            Column(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(SurfaceColor).padding(horizontal = 18.dp, vertical = 14.dp),
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
        }
        // Same four as the phone. The social backend has no web client yet, so they are inert.
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionIcon(Res.drawable.ic_like, stringResource(Res.string.player_like))
            ActionIcon(Res.drawable.ic_dislike, stringResource(Res.string.player_dislike))
            ActionIcon(Res.drawable.ic_comment, stringResource(Res.string.comments_title))
            Spacer(Modifier.weight(1f))
            ActionIcon(Res.drawable.ic_share, stringResource(Res.string.common_share))
        }
    }
}

@Composable
private fun ActionIcon(res: DrawableResource, label: String) {
    Box(Modifier.size(44.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
        Icon(
            painterResource(res), contentDescription = label, tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun SimilarColumn(items: List<Title>, onOpen: (Title) -> Unit) {
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
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, color = Color.White, fontSize = 18.sp, fontFamily = WorkSans,
        fontWeight = FontWeight.SemiBold, modifier = modifier,
    )
}
