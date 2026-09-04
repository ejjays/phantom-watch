package dev.phantomwatch.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phantomwatch.R
import dev.phantomwatch.core.AppLog
import dev.phantomwatch.data.Dub
import dev.phantomwatch.ui.Cyan
import dev.phantomwatch.ui.Rubik
import dev.phantomwatch.ui.Slate400

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

// "English · HiAnime" reads as just "English" here; the leg name is noise for a viewer
private fun dubLabel(l: String): String = l.substringBefore(" · ")

internal fun speedLabel(s: Float): String = if (s == s.toInt().toFloat()) "${s.toInt()}×" else "${s}×"

// panel hangs under the top bar like a real player's gear menu: same edge as the gear, one page deep
@Composable
internal fun PlayerSettingsMenu(
    topPad: Dp,
    paneH: Dp? = null,
    qualities: List<Rung>,
    quality: Int,
    qualityNow: String?,
    onPickQuality: (Int) -> Unit,
    speed: Float,
    onPickSpeed: (Float) -> Unit,
    dubs: List<Dub>,
    current: String?,
    onPick: (Dub) -> Unit,
    onDismiss: () -> Unit,
) {
    var page by remember { mutableStateOf(0) }
    // the sheet is 232dp at most, so the pinned resolution has to give way to the row's own title
    val phone = LocalConfiguration.current.smallestScreenWidthDp < 600
    val first = remember { FocusRequester() }
    val keyboard = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    LaunchedEffect(Unit) { if (keyboard) first.requestFocus() }
    BackHandler(onBack = onDismiss)
    BoxWithConstraints(
        Modifier.fillMaxSize().focusProperties { canFocus = false }.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onDismiss,
        ),
    ) {
        Column(
            Modifier.align(Alignment.TopEnd).padding(top = topPad, end = 12.dp)
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                .widthIn(max = minOf(maxWidth * 0.55f, 232.dp))
                // the portrait pane is short and the sheet covers its lower half, so fit the pane
                .heightIn(max = paneH?.minus(topPad)?.coerceAtLeast(96.dp) ?: (maxHeight * 0.55f))
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.92f))
                .focusRequester(first)
                .focusRing(RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {}
                .verticalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
        ) {
            when (page) {
                1 -> {
                    SettingsHeader(stringResource(R.string.player_settings_quality)) { page = 0 }
                    SettingsRow(stringResource(R.string.player_settings_auto), if (quality <= 0) qualityNow else null, 0, chevron = false, selected = quality <= 0) {
                        AppLog.d("quality auto")
                        onPickQuality(0)
                    }
                    qualities.forEach { r ->
                        SettingsRow(r.label, null, 0, chevron = false, selected = r.height == quality) {
                            AppLog.d("quality ${r.label} h=${r.height}")
                            onPickQuality(r.height)
                        }
                    }
                }
                3 -> {
                    SettingsHeader(stringResource(R.string.player_settings_speed)) { page = 0 }
                    SPEEDS.forEach { s ->
                        SettingsRow(
                            speedLabel(s), null, 0, chevron = false, selected = s == speed,
                            note = if (s == 1f) stringResource(R.string.player_settings_normal) else null,
                        ) {
                            AppLog.d("speed ${speedLabel(s)}")
                            onPickSpeed(s)
                        }
                    }
                }
                2 -> {
                    SettingsHeader(stringResource(R.string.player_settings_dubs)) { page = 0 }
                    dubs.forEach { d ->
                        val playing = if (d.base) current == null else d.label == current
                        SettingsRow(dubLabel(d.label), null, 0, chevron = false, selected = playing) {
                            AppLog.d("settings pick ${d.label}")
                            onPick(d)
                        }
                    }
                }
                else -> {
                    if (qualities.size > 1) {
                        val pinned = if (quality > 0) qualities.firstOrNull { it.height == quality }?.label else null
                        val autoLabel = if (qualityNow != null && !phone) {
                            stringResource(R.string.player_settings_auto_now, qualityNow)
                        } else {
                            stringResource(R.string.player_settings_auto)
                        }
                        val qVal = pinned ?: autoLabel
                        SettingsRow(stringResource(R.string.player_settings_quality), qVal, R.drawable.ic_quality, chevron = true) {
                            page = 1
                            AppLog.d("settings quality ${qualities.joinToString { it.label }}")
                        }
                    }
                    SettingsRow(stringResource(R.string.player_settings_speed), speedLabel(speed), R.drawable.ic_speed, chevron = true) {
                        page = 3
                        AppLog.d("settings speed ${speedLabel(speed)}")
                    }
                    if (dubs.isNotEmpty()) {
                        val base = dubs.firstOrNull { it.base }?.let { dubLabel(it.label) }
                            ?: stringResource(R.string.player_settings_sub)
                        SettingsRow(stringResource(R.string.player_settings_dubs), current?.let(::dubLabel) ?: base, R.drawable.ic_dubs, chevron = true) {
                            page = 2
                            AppLog.d("settings dubs=${dubs.size} current=${current ?: "sub"}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().focusRing(RoundedCornerShape(8.dp)).clickable(onClick = onBack)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            contentDescription = stringResource(R.string.common_back),
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp).rotate(180f),
        )
        Spacer(Modifier.width(6.dp))
        Text(title, color = Color.White, fontSize = 14.sp, fontFamily = Rubik, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SettingsRow(
    label: String,
    value: String?,
    icon: Int,
    chevron: Boolean,
    selected: Boolean = false,
    note: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().focusRing(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != 0) {
            Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = if (selected) Cyan else Color.White)) { append(label) }
                if (note != null) withStyle(SpanStyle(color = Slate400)) { append("  $note") }
            },
            fontSize = 14.sp, fontFamily = Rubik,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(value, color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, fontFamily = Rubik)
        }
        if (chevron) {
            Spacer(Modifier.width(6.dp))
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null, tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp),
            )
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(
                painterResource(R.drawable.ic_check),
                contentDescription = stringResource(R.string.player_settings_selected), tint = Cyan, modifier = Modifier.size(16.dp),
            )
        }
    }
}
