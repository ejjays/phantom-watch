package dev.phantomwatch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.phantomwatch.R
import dev.phantomwatch.data.Resume
import kotlinx.coroutines.launch

@Composable
internal fun HistoryScreen(
    recent: List<Resume.Recent>,
    cols: Int,
    gridW: Dp,
    onBack: () -> Unit,
    onPlay: (Resume.Recent) -> Unit,
    onDelete: (Resume.Recent) -> Unit,
    onRestore: (List<Resume.Recent>) -> Unit,
) {
    val host = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(stringResource(R.string.history_title), onBack)
            if (recent.isEmpty()) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(stringResource(R.string.history_empty_title), color = Cyan, fontSize = 14.sp, fontFamily = Mono, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.history_empty_body), color = Slate400, fontSize = 12.sp, fontFamily = Rubik)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(cols),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(recent, key = { "history-${it.kind}-${it.id}" }) { r ->
                        ContinueCard(r, gridW, onPlay) { gone ->
                            val before = recent
                            onDelete(gone)
                            scope.launch {
                                val done = host.showSnackbar(
                                    message = ctx.getString(R.string.history_removed, gone.title),
                                    actionLabel = ctx.getString(R.string.history_undo),
                                    duration = SnackbarDuration.Short,
                                )
                                if (done == SnackbarResult.ActionPerformed) onRestore(before)
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(host, Modifier.align(Alignment.BottomCenter)) {
            Snackbar(snackbarData = it, containerColor = SurfaceColor, contentColor = Slate100, actionColor = Cyan)
        }
    }
}

@Composable
private fun ContinueCard(
    r: Resume.Recent,
    width: Dp,
    onPlay: (Resume.Recent) -> Unit,
    onDelete: (Resume.Recent) -> Unit,
) {
    val frac = if (r.durationMs > 0) (r.positionMs.toFloat() / r.durationMs).coerceIn(0f, 1f) else 0f
    val radius = width * 0.24f
    // the corner arc reaches ~0.29 of the radius in from each edge, so a badge at a flat 10dp gets clipped
    val badgeInset = (radius * 0.34f).coerceAtLeast(8.dp)
    val dismiss = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) onDelete(r)
            true
        },
    )
    // the state is saveable, so an undone card comes back still swiped off-screen over its neighbour
    LaunchedEffect(Unit) { dismiss.reset() }
    SwipeToDismissBox(
        state = dismiss,
        enableDismissFromStartToEnd = false,
        backgroundContent = {},
    ) {
        Column(Modifier.width(width).pressClick { onPlay(r) }) {
            Box(Modifier.size(width, width * 1.5f).clip(RoundedCornerShape(radius)).background(SurfaceColor)) {
                r.poster?.let { poster ->
                    AsyncImage(model = poster, contentDescription = r.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                if (r.season.isNotEmpty()) {
                    Text(
                        stringResource(R.string.season_episode, r.season, r.episode), color = Color.White, fontSize = 10.sp, fontFamily = Mono,
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
            Text(
                r.title.uppercase(), color = Slate100, fontSize = if (width >= 150.dp) 16.sp else 12.sp,
                fontFamily = Rubik, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                onTextLayout = { centered = !it.isLineEllipsized(0) },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
}
