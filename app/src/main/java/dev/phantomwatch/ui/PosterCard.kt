package dev.phantomwatch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.phantomwatch.R
import dev.phantomwatch.data.Title

@Composable
fun PosterCard(
    item: Title,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    onOpen: (Title) -> Unit,
    caption: Boolean = true,
) {
    val radius = (width * 0.24f)
    val playSize = maxOf(13.dp, width * 0.13f)
    val titleSize = if (width >= 150.dp) 16.sp else 12.sp
    Column(Modifier.width(width).pressClick { onOpen(item) }) {
        Box(Modifier.clip(RoundedCornerShape(radius.value.dp)).background(Color.White.copy(alpha = 0.05f))) {
            if (item.poster != null) {
                AsyncImage(model = item.poster, contentDescription = item.title, modifier = Modifier.size(width, height), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.size(width, height), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_play_outline), contentDescription = null, tint = Slate500, modifier = Modifier.size(playSize))
                }
            }
        }
        // No poster means no art to name the card, so the rail keeps that one label.
        if (caption || item.poster == null) {
            var centered by remember { mutableStateOf(false) }
            Text(
                item.title.uppercase(), color = Slate100, fontSize = titleSize, fontFamily = Rubik,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                onTextLayout = { centered = !it.isLineEllipsized(0) },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
}

@Composable
internal fun TopTenCard(rank: Int, item: Title, width: Dp, height: Dp, onOpen: (Title) -> Unit) {
    val radius = (width * 0.24f)
    val density = LocalDensity.current
    // A glyph taller than this hides most of itself behind the poster; "10" needs a smaller
    // glyph and a wider lead-in or its second digit disappears under the poster.
    val wide = rank > 9
    val glyph = height * if (wide) 0.40f else 0.60f
    val lead = width * if (wide) 0.46f else 0.34f
    val face = with(density) {
        TextStyle(
            fontSize = glyph.toSp(),
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            lineHeight = glyph.toSp(),
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        )
    }
    val hairline = with(density) { Stroke(3.dp.toPx()) }
    val shape = RoundedCornerShape(radius.value.dp)
    Box(Modifier.width(width + lead).height(height).pressClick { onOpen(item) }) {
        // Line box keeps the descent, so the glyph lands ~0.2em above the slot's bottom.
        val slot = Modifier.align(Alignment.BottomStart).offset(y = glyph * 0.2f)
        // Filled glyph with an outline laid over it: a stroke alone reads as wireframe, not plate.
        Text("$rank", style = face.copy(color = SurfaceColor), maxLines = 1, modifier = slot)
        Text("$rank", style = face.copy(color = Color.White.copy(alpha = 0.62f), drawStyle = hairline), maxLines = 1, modifier = slot)
        Box(
            Modifier.align(Alignment.BottomEnd).size(width, height)
                .clip(shape).background(Color.White.copy(alpha = 0.05f)),
        ) {
            if (item.poster != null) {
                AsyncImage(model = item.poster, contentDescription = item.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_play_outline), contentDescription = null, tint = Slate500, modifier = Modifier.size(maxOf(13.dp, width * 0.13f)))
                }
            }
        }
    }
}
