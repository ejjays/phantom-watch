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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import dev.phantomwatch.data.Resume

@Composable
internal fun HistoryScreen(
    recent: List<Resume.Recent>,
    gridW: Dp,
    onBack: () -> Unit,
    onPlay: (Resume.Recent) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("History", onBack)
        if (recent.isEmpty()) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("Nothing watched yet.", color = Cyan, fontSize = 14.sp, fontFamily = Mono, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text("Start an episode and it shows up here", color = Slate400, fontSize = 12.sp, fontFamily = Rubik)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(gridW),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(recent, key = { "history-${it.kind}-${it.id}" }) { r ->
                    ContinueCard(r, gridW, onPlay)
                }
            }
        }
    }
}

@Composable
private fun ContinueCard(r: Resume.Recent, width: Dp, onPlay: (Resume.Recent) -> Unit) {
    val frac = if (r.durationMs > 0) (r.positionMs.toFloat() / r.durationMs).coerceIn(0f, 1f) else 0f
    val radius = width * 0.24f
    Column(Modifier.width(width).pressClick { onPlay(r) }) {
        Box(Modifier.size(width, width * 1.5f).clip(RoundedCornerShape(radius)).background(SurfaceColor)) {
            if (r.poster != null) {
                AsyncImage(model = r.poster, contentDescription = r.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            if (r.season.isNotEmpty()) {
                Text(
                    "S${r.season} E${r.episode}", color = Color.White, fontSize = 10.sp, fontFamily = Mono,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                        .clip(RoundedCornerShape(6.dp)).background(Color(0xCC1E1E1E))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            // The bottom corners are round, so a full-bleed bar loses its ends to the clip.
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .padding(start = radius * 0.45f, end = radius * 0.45f, bottom = radius * 0.18f)
                    .height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)),
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(Cyan))
            }
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
