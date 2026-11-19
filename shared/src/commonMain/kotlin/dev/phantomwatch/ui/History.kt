package dev.phantomwatch.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.phantomwatch.data.Recent
import dev.phantomwatch.res.*
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun HistoryScreen(
    recent: List<Recent>,
    cols: Int,
    gridW: Dp,
    onPlay: (Recent) -> Unit,
    onDelete: (Recent) -> Unit,
    onRestore: (Recent, Int) -> Unit,
    downloaded: List<Recent> = emptyList(),
    onPlayDownloaded: (Recent) -> Unit = {},
    onDeleteDownloaded: (Recent) -> Unit = {},
    onRestoreDownloaded: (Recent, Int) -> Unit = { _, _ -> },
) {
    val host = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val undoLabel = stringResource(Res.string.history_undo)
    // swipe settles against list now not one this card started with
    val latest = rememberUpdatedState(recent)
    val latestDownloads = rememberUpdatedState(downloaded)

    fun remove(
        gone: Recent,
        at: Int,
        msg: String,
        doRemove: (Recent) -> Unit,
        doRestore: (Recent, Int) -> Unit,
    ) {
        doRemove(gone)
        scope.launch {
            // newest swipe owns bar or old title outlives card
            host.currentSnackbarData?.dismiss()
            val done = host.showSnackbar(
                message = msg,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            if (done == SnackbarResult.ActionPerformed) doRestore(gone, at)
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 128.dp),
        ) {
            item {
                ScreenHeader(stringResource(Res.string.library_title), null, lilita = true)
            }
            if (recent.isNotEmpty()) {
                item {
                    LibraryRow(
                        title = stringResource(Res.string.history_title),
                        items = recent,
                        width = gridW,
                        onPlay = onPlay,
                        onSwipe = { gone, i, msg ->
                            val at = latest.value.indexOfFirst {
                                it.kind == gone.kind && it.id == gone.id &&
                                    it.season == gone.season && it.episode == gone.episode
                            }.takeIf { it >= 0 } ?: i
                            remove(gone, at, msg, onDelete, onRestore)
                        },
                    )
                }
            }
            if (downloaded.isNotEmpty()) {
                item {
                    LibraryRow(
                        title = stringResource(Res.string.library_downloaded),
                        items = downloaded,
                        width = gridW,
                        onPlay = onPlayDownloaded,
                        onSwipe = { gone, i, msg ->
                            val at = latestDownloads.value.indexOfFirst { it.id == gone.id }
                                .takeIf { it >= 0 } ?: i
                            remove(gone, at, msg, onDeleteDownloaded, onRestoreDownloaded)
                        },
                    )
                }
            }
        }
        if (recent.isEmpty() && downloaded.isEmpty()) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedPhantom(Modifier.size(132.dp))
                Spacer(Modifier.height(20.dp))
                Text(stringResource(Res.string.history_empty_title), color = Cyan, fontSize = 14.sp, fontFamily = WorkSans, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.history_empty_body), color = Slate400, fontSize = 12.sp, fontFamily = WorkSans)
            }
        }
        SnackbarHost(
            host,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.dp),
        ) {
            Snackbar(snackbarData = it, containerColor = SurfaceColor, contentColor = Slate100, actionColor = Cyan)
        }
    }
}

// home style horizontal row, cards delete on vertical drag so rail scrolling never fights dismiss gesture
@Composable
private fun LibraryRow(
    title: String,
    items: List<Recent>,
    width: Dp,
    onPlay: (Recent) -> Unit,
    onSwipe: (Recent, Int, String) -> Unit,
) {
    val rowState = remember { LazyListState() }
    val cell = libraryCell(width)
    Column {
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title, color = Slate200, fontSize = if (isExpanded(width)) 26.sp else 16.sp,
                fontFamily = WorkSans, fontWeight = FontWeight.Bold,
            )
        }
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(start = 16.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.mouseDragScroll(rowState),
        ) {
            itemsIndexed(items, key = { _, r -> "lib-${r.kind}-${r.id}-${r.season}-${r.episode}" }) { i, r ->
                val msg = stringResource(Res.string.history_removed, r.title)
                VerticalSwipeCard(onDelete = { onSwipe(r, i, msg) }) {
                    ContinuePoster(r, cell, onPlay, caption = false)
                }
            }
        }
    }
}

@Composable
private fun VerticalSwipeCard(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val drag = rememberDraggableState { dy ->
        scope.launch {
            // downward drag only, capped so long flick cant fly off screen
            offset.snapTo((offset.value + dy).coerceIn(0f, 220f))
        }
    }
    Box(
        modifier
            .graphicsLayer {
                translationY = offset.value
                alpha = 1f - (offset.value / 220f).coerceIn(0f, 1f) * 0.6f
            }
            .draggable(
                orientation = Orientation.Vertical,
                state = drag,
                onDragStopped = {
                    if (offset.value >= 110f) {
                        onDelete()
                        offset.snapTo(0f)
                    } else {
                        offset.animateTo(0f)
                    }
                },
            ),
    ) { content() }
}

// home rail reuses this without dismiss box, horizontal drag there belongs to row
@Composable
internal fun ContinuePoster(
    r: Recent,
    width: Dp,
    onPlay: (Recent) -> Unit,
    caption: Boolean = true,
) {
    val frac = if (r.durationMs > 0) (r.positionMs.toFloat() / r.durationMs).coerceIn(0f, 1f) else 0f
    val radius = width * 0.24f
    // corner arc reaches ~0.29 of radius from each edge so flat 10dp badge clips
    val badgeInset = (radius * 0.34f).coerceAtLeast(8.dp)
    Column(Modifier.width(width).pressClick { onPlay(r) }) {
        Box(Modifier.size(width, width * 1.5f).clip(RoundedCornerShape(radius)).background(SurfaceColor)) {
            r.poster?.let { poster ->
                AsyncImage(model = poster, contentDescription = r.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            if (r.season.isNotEmpty() && r.season != "0") {
                Text(
                    stringResource(Res.string.season_episode, r.season, r.episode), color = Color.White, fontSize = 10.sp, fontFamily = WorkSans,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.TopStart).padding(badgeInset)
                        .clip(RoundedCornerShape(6.dp)).background(Color(0xCC1E1E1E))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Box(
            Modifier.align(Alignment.CenterHorizontally).fillMaxWidth(0.6f).padding(top = 6.dp)
                .height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(Cyan))
        }
        var centered by remember { mutableStateOf(false) }
        if (caption) {
            Text(
                r.title.uppercase(), color = Slate100, fontSize = if (width >= 150.dp) 16.sp else 12.sp,
                fontFamily = WorkSans, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                onTextLayout = { centered = !it.isLineEllipsized(0) },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
}

@Composable
internal fun ContinueRow(recent: List<Recent>, width: Dp, onPlay: (Recent) -> Unit) {
    val rowState = remember { LazyListState() }
    val cell = featuredCell(width)
    Column {
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.rail_continue), color = Slate200, fontSize = if (isExpanded(width)) 26.sp else 16.sp,
                fontFamily = WorkSans, fontWeight = FontWeight.Bold,
            )
        }
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(start = 16.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.mouseDragScroll(rowState),
        ) {
            itemsIndexed(recent.take(10), key = { _, r -> "${r.kind}-${r.id}-${r.season}-${r.episode}" }) { _, r ->
                ContinuePoster(r, cell, onPlay, caption = false)
            }
        }
    }
}
