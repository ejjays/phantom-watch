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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.phantomwatch.core.Log
import dev.phantomwatch.data.Title
import dev.phantomwatch.res.*
import org.jetbrains.compose.resources.painterResource

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
            val poster = item.poster
            var failed by remember(item.id) { mutableStateOf(false) }
            if (poster != null && !failed) {
                AsyncImage(
                    model = poster, contentDescription = item.title, modifier = Modifier.size(width, height),
                    contentScale = ContentScale.Crop,
                    onError = { st ->
                        failed = true
                        Log.d("poster err ${item.id} ${st.result.throwable.message}")
                    },
                )
            } else {
                Box(Modifier.size(width, height), contentAlignment = Alignment.Center) {
                    Icon(painterResource(Res.drawable.ic_play_outline), contentDescription = null, tint = Slate500, modifier = Modifier.size(playSize))
                }
            }
        }
        if (caption || item.poster == null) {
            var centered by remember { mutableStateOf(false) }
            Text(
                item.title.uppercase(), color = Slate100, fontSize = titleSize, fontFamily = WorkSans,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                onTextLayout = { centered = !it.isLineEllipsized(0) },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
}

@Composable
fun TopTenCard(rank: Int, item: Title, width: Dp, height: Dp, onOpen: (Title) -> Unit) {
    if (width > height) TopTenWide(rank, item, width, height, onOpen) else TopTenTall(rank, item, width, height, onOpen)
}

// outline numeral in the gutter, bottom-aligned, with the poster sliding over its right side
@Composable
private fun RankNumeral(rank: Int, size: Dp, color: Color, stroke: Dp, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val em = with(density) { size.toSp() }
    Text(
        "$rank",
        style = TextStyle(
            fontSize = em,
            fontFamily = WorkSans,
            fontWeight = FontWeight.Bold,
            lineHeight = em,
            color = color,
            drawStyle = Stroke(with(density) { stroke.toPx() }),
        ),
        maxLines = 1,
        modifier = modifier,
    )
}

@Composable
private fun TopTenWide(rank: Int, item: Title, width: Dp, height: Dp, onOpen: (Title) -> Unit) {
    val twoDigit = rank > 9
    val glyph = height * if (twoDigit) 0.44f else 0.61f
    // the numeral is not condensed here, so the gutter has to hold its full width
    val lead = height * if (twoDigit) 0.385f else 0.26f
    Box(Modifier.width(width + lead).height(height).pressClick { onOpen(item) }) {
        RankNumeral(
            rank, glyph, Color.White, 3.dp,
            Modifier.align(Alignment.BottomStart).offset(y = height * 0.092f),
        )
        Box(
            Modifier.align(Alignment.BottomEnd).size(width, height)
                .clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = 0.05f)),
        ) {
            val art = item.backdrop ?: item.poster
            if (art != null) {
                AsyncImage(model = art, contentDescription = item.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                // the still carries no lettering, unlike the portrait poster, so the name has to be drawn
                Box(
                    Modifier.matchParentSize()
                        .background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.9f))),
                )
                // 0.118 of the card height, so the name keeps the same share on a tablet as on a phone
                val name = (height * 0.118f).value.coerceIn(13f, 40f).sp
                Text(
                    item.title, color = Color.White,
                    style = TextStyle(
                        fontSize = name, lineHeight = name * 1.04f, fontFamily = Lilita,
                        fontWeight = FontWeight.Normal, lineBreak = LineBreak.Heading,
                    ),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 14.dp, end = 20.dp, bottom = 10.dp),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(painterResource(Res.drawable.ic_play_outline), contentDescription = null, tint = Slate500, modifier = Modifier.size(maxOf(13.dp, width * 0.13f)))
                }
            }
        }
    }
}

@Composable
private fun TopTenTall(rank: Int, item: Title, width: Dp, height: Dp, onOpen: (Title) -> Unit) {
    val radius = width * 0.24f
    val wide = rank > 9
    val glyph = height * if (wide) 0.40f else 0.60f
    val lead = width * if (wide) 0.46f else 0.34f
    val shape = RoundedCornerShape(radius.value.dp)
    Box(Modifier.width(width + lead).height(height).pressClick { onOpen(item) }) {
        RankNumeral(
            rank, glyph, Color.White.copy(alpha = 0.82f), 3.dp,
            Modifier.align(Alignment.BottomStart).offset(y = glyph * 0.2f),
        )
        Box(
            Modifier.align(Alignment.BottomEnd).size(width, height)
                .clip(shape).background(Color.White.copy(alpha = 0.05f)),
        ) {
            val poster = item.poster
            if (poster != null) {
                AsyncImage(model = poster, contentDescription = item.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(painterResource(Res.drawable.ic_play_outline), contentDescription = null, tint = Slate500, modifier = Modifier.size(maxOf(13.dp, width * 0.13f)))
                }
            }
        }
    }
}
