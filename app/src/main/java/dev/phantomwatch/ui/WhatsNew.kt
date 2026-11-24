package dev.phantomwatch.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.phantomwatch.R
import dev.phantomwatch.data.WhatsNewEntry
import dev.phantomwatch.ui.settings.BackButton
import dev.phantomwatch.ui.settings.SettingsCanvas
import dev.phantomwatch.ui.settings.SettingsText
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private data class Star(val x: Float, val y: Float, val r: Float, val phase: Float, val speed: Int, val sparkle: Boolean = false)

private val Stars = listOf(
    Star(108f, 62f, 1.6f, 0f, 1),
    Star(140f, 155f, 1.3f, 1.7f, 2),
    Star(292f, 140f, 1.5f, 0.9f, 1),
    Star(555f, 122f, 1.4f, 2.4f, 2),
    Star(712f, 165f, 1.6f, 3.1f, 1),
    Star(784f, 70f, 1.3f, 4.2f, 2),
    Star(590f, 282f, 1.4f, 5.0f, 1),
    Star(365f, 205f, 1.2f, 1.1f, 2),
    Star(210f, 92f, 1.1f, 3.8f, 1),
    Star(660f, 235f, 1.2f, 5.6f, 2),
    Star(68f, 216f, 2.6f, 0.5f, 1, true),
    Star(722f, 246f, 1.9f, 2.8f, 2, true),
    Star(628f, 90f, 1.3f, 4.6f, 1, true),
)

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
                WhatsNewHero()
                WhatsNewBody(entry)
                WhatsNewOkay(onDismiss)
            }
        }
    }
}

@Composable
private fun WhatsNewHero() {
    val floatAnim = rememberInfiniteTransition(label = "float")
    val f by floatAnim.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "floatY",
    )
    val cloudAnim = rememberInfiniteTransition(label = "clouds")
    val c by cloudAnim.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift",
    )
    Box(Modifier.fillMaxWidth().height(210.dp).clipToBounds().background(Color(0xFF1B1829))) {
        Image(
            painterResource(R.drawable.cloud_back),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = 1.14f
                scaleY = 1.12f
                translationX = (-12f + 24f * c).dp.toPx()
                translationY = (-3.5f + 7f * c).dp.toPx()
            },
        )
        TwinklingStars(Modifier.fillMaxSize())
        Image(
            painterResource(R.drawable.cloud_front),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = 1.14f
                scaleY = 1.12f
                translationY = (3.5f - 7f * c + (-2f + 4f * f)).dp.toPx()
            },
        )
        Image(
            painterResource(R.drawable.ic_whatsnew_rocket),
            contentDescription = null,
            modifier = Modifier.align(Alignment.Center).offset(y = (-2f + 4f * f).dp).size(120.dp),
        )
        GhostEyes(
            Modifier.align(Alignment.Center).offset(y = (-2f + 4f * f).dp).size(120.dp),
        )
    }
}

@Composable
private fun ColumnScope.WhatsNewBody(entry: WhatsNewEntry) {
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
                fontFamily = Lilita,
                fontSize = 22.sp,
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
}

@Composable
private fun WhatsNewOkay(onDismiss: () -> Unit) {
    val okaySrc = remember { MutableInteractionSource() }
    val okayPressed by okaySrc.collectIsPressedAsState()
    val okayScale by animateFloatAsState(if (okayPressed) 0.97f else 1f, label = "okayPress")
    Box(
        Modifier.fillMaxWidth().background(Card)
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().height(46.dp)
                .graphicsLayer { scaleX = okayScale; scaleY = okayScale }
                .clip(RoundedCornerShape(50)).background(Cta)
                .clickable(interactionSource = okaySrc, indication = null, onClick = onDismiss),
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

@Composable
fun WhatsNewScreen(entries: List<WhatsNewEntry>, onClose: () -> Unit) {
    PlatformBackHandler(enabled = true, onBack = onClose)
    Box(Modifier.fillMaxSize().background(SettingsCanvas)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 8.dp, end = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackButton(onClose)
                Spacer(Modifier.width(2.dp))
                Text(
                    "What's new",
                    color = SettingsText,
                    fontFamily = WorkSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 30.sp,
                    lineHeight = 36.sp,
                    letterSpacing = (-0.5).sp,
                )
            }
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
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
                            fontFamily = Lilita,
                            fontSize = 22.sp,
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
private fun GhostEyes(modifier: Modifier) {
    val lid = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        suspend fun blink() {
            lid.animateTo(1f, tween(Random.nextInt(60, 90)))
            lid.animateTo(0f, tween(Random.nextInt(80, 120)))
        }
        delay(Random.nextLong(1800, 2600))
        while (true) {
            val count = Random.nextInt(1, 3)
            repeat(count) {
                blink()
                delay(Random.nextLong(90, 190))
            }
            delay(Random.nextLong(2600, 6500))
        }
    }
    Canvas(modifier) {
        val s = size.width / 512f
        val brush = Brush.verticalGradient(
            listOf(Color(0xFFD0D2D9), Color(0xFF97989F)),
            startY = 262f * s,
            endY = 292f * s,
        )
        listOf(230f, 253f).forEach { cx ->
            drawOval(
                brush = brush,
                topLeft = Offset((cx - 13f) * s, 260f * s),
                size = Size(26f * s, 32f * s),
                alpha = lid.value,
            )
        }
    }
}

@Composable
private fun TwinklingStars(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "stars")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Reverse),
        label = "twinkle",
    )
    Canvas(modifier) {
        val scale = size.width / 870f
        Stars.forEach { star ->
            val angle = (t * 2f * PI.toFloat() * star.speed + star.phase).toDouble()
            val wave = 0.5f + 0.5f * sin(angle).toFloat()
            val alpha = 0.22f + 0.55f * wave
            val cx = star.x * scale
            val cy = star.y * scale
            if (star.sparkle) {
                val r = star.r * scale
                val path = Path().apply {
                    moveTo(cx, cy - r * 2f)
                    quadraticTo(cx + r * 0.35f, cy - r * 0.35f, cx + r * 2f, cy)
                    quadraticTo(cx + r * 0.35f, cy + r * 0.35f, cx, cy + r * 2f)
                    quadraticTo(cx - r * 0.35f, cy + r * 0.35f, cx - r * 2f, cy)
                    quadraticTo(cx - r * 0.35f, cy - r * 0.35f, cx, cy - r * 2f)
                    close()
                }
                drawPath(path, Color.White.copy(alpha = alpha))
            } else {
                drawCircle(Color.White.copy(alpha = alpha), radius = star.r * scale, center = Offset(cx, cy))
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
