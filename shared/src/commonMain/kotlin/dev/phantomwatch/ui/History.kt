package dev.phantomwatch.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
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

@Composable
fun HistoryScreen(
    recent: List<Recent>,
    cols: Int,
    gridW: Dp,
    onPlay: (Recent) -> Unit,
    onDelete: (Recent) -> Unit,
    onRestore: (Recent, Int) -> Unit,
) {
    val host = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val undoLabel = stringResource(Res.string.history_undo)
    // swipe settles against the list now not the one this card started with
    val latest = rememberUpdatedState(recent)
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(stringResource(Res.string.history_title), null, lilita = true)
            if (recent.isEmpty()) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(stringResource(Res.string.history_empty_title), color = Cyan, fontSize = 14.sp, fontFamily = WorkSans, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(Res.string.history_empty_body), color = Slate400, fontSize = 12.sp, fontFamily = WorkSans)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(cols),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 128.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(recent, key = { _, r -> "history-${r.kind}-${r.id}" }) { i, r ->
                        val removedMsg = stringResource(Res.string.history_removed, r.title)
                        ContinueCard(r, gridW, onPlay, Modifier.animateItem()) { gone ->
                            // this one card back where it was, a snapshot brings back anything
                            // deleted since card was composed
                            val at = latest.value.indexOfFirst {
                                it.kind == gone.kind && it.id == gone.id &&
                                    it.season == gone.season && it.episode == gone.episode
                            }.takeIf { it >= 0 } ?: i
                            onDelete(gone)
                            scope.launch {
                                // newest swipe owns the bar or old title outlives the card
                                host.currentSnackbarData?.dismiss()
                                val done = host.showSnackbar(
                                    message = removedMsg,
                                    actionLabel = undoLabel,
                                    duration = SnackbarDuration.Short,
                                )
                                if (done == SnackbarResult.ActionPerformed) onRestore(gone, at)
                            }
                        }
                    }
                }
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

@Composable
private fun ContinueCard(
    r: Recent,
    width: Dp,
    onPlay: (Recent) -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (Recent) -> Unit,
) {
    val dismiss = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it != SwipeToDismissBoxValue.Settled) onDelete(r)
            true
        },
    )
    LaunchedEffect(Unit) { dismiss.reset() }
    SwipeToDismissBox(
        state = dismiss,
        modifier = modifier,
        backgroundContent = {},
    ) {
        ContinuePoster(r, width, onPlay)
    }
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
    // corner arc reaches ~0.29 of the radius from each edge so a flat 10dp badge clips
    val badgeInset = (radius * 0.34f).coerceAtLeast(8.dp)
    Column(Modifier.width(width).pressClick { onPlay(r) }) {
        Box(Modifier.size(width, width * 1.5f).clip(RoundedCornerShape(radius)).background(SurfaceColor)) {
            r.poster?.let { poster ->
                AsyncImage(model = poster, contentDescription = r.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            if (r.season.isNotEmpty()) {
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
        ) {
            items(recent.take(10), key = { "${it.kind}-${it.id}-${it.season}-${it.episode}" }) { r ->
                ContinuePoster(r, cell, onPlay, caption = false)
            }
        }
    }
}
