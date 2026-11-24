package dev.phantomwatch.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.phantomwatch.R
import dev.phantomwatch.data.WhatsNewEntry

private val Hairline = Color.White.copy(alpha = 0.10f)
private val Card = Color(0xFF302C46)
private val Accent = Cyan
private val Cta = Cyan

@Composable
fun WhatsNewDialog(entry: WhatsNewEntry, onDismiss: () -> Unit) {
    val shape = RoundedCornerShape(32.dp)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.width(minOf(maxWidth * 0.86f, 380.dp))
                    .heightIn(max = maxHeight * 0.86f).clip(shape).background(Card),
            ) {
                Box(Modifier.fillMaxWidth().height(210.dp)) {
                    Image(
                        painterResource(R.drawable.whatsnew_sky),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Image(
                        painterResource(R.drawable.ic_whatsnew_rocket),
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.Center).size(120.dp),
                    )
                }
                val scroll = rememberScrollState()
                Box(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(scroll)
                            .padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 12.dp),
                    ) {
                        Text(
                            "What's new",
                            color = Accent,
                            fontFamily = Lilita,
                            fontSize = 24.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            entry.title,
                            color = Slate100,
                            fontFamily = WorkSans,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(entry.version, color = Slate500, fontFamily = WorkSans, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(22.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            entry.bullets.forEach { WhatsNewBullet(it) }
                        }
                    }
                    if (scroll.value > 0) {
                        Box(
                            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(22.dp)
                                .background(Brush.verticalGradient(listOf(Card, Card.copy(alpha = 0f)))),
                        )
                    }
                    if (scroll.value < scroll.maxValue) {
                        Box(
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(22.dp)
                                .background(Brush.verticalGradient(listOf(Card.copy(alpha = 0f), Card))),
                        )
                    }
                }
                Box(
                    Modifier.fillMaxWidth().background(Card)
                        .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(50))
                            .background(Cta).pressClick(onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Okay",
                            color = Color.Black,
                            fontFamily = WorkSans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WhatsNewScreen(entries: List<WhatsNewEntry>, onClose: () -> Unit) {
    PlatformBackHandler(enabled = true, onBack = onClose)
    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "What's new",
                    Modifier.weight(1f),
                    color = Slate100,
                    fontFamily = Lilita,
                    fontSize = 26.sp,
                )
                TextButton(onClick = onClose) {
                    Text("Okay", color = Accent, fontFamily = WorkSans, fontWeight = FontWeight.Medium)
                }
            }
            HorizontalDivider(color = Hairline)
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(36.dp),
            ) {
                items(entries) { entry ->
                    Column {
                        Text(
                            entry.version.uppercase(),
                            color = Slate500,
                            fontFamily = WorkSans,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.2.sp,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            entry.title,
                            color = Slate100,
                            fontFamily = WorkSans,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(18.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            entry.bullets.forEach { WhatsNewBullet(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WhatsNewBullet(text: String) {
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.padding(top = 8.dp).size(5.dp).background(Slate500, CircleShape))
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            Modifier.weight(1f),
            color = Slate100,
            fontFamily = WorkSans,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
    }
}
