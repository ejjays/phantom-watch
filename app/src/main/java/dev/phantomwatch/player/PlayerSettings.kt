package dev.phantomwatch.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phantomwatch.R
import dev.phantomwatch.core.AppLog
import dev.phantomwatch.data.Dub
import dev.phantomwatch.ui.Cyan
import dev.phantomwatch.ui.Rubik

// panel hangs under the top bar like a real player's gear menu: same edge as the gear, one page deep
@Composable
internal fun PlayerSettingsMenu(
    topPad: Dp,
    qualities: List<Rung>,
    quality: Int,
    qualityNow: String?,
    onPickQuality: (Int) -> Unit,
    dubs: List<Dub>,
    current: String?,
    onPick: (Dub) -> Unit,
    onDismiss: () -> Unit,
) {
    var page by remember { mutableStateOf(0) }
    BoxWithConstraints(
        Modifier.fillMaxSize().clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onDismiss,
        ),
    ) {
        Column(
            Modifier.align(Alignment.TopEnd).padding(top = topPad, end = 12.dp)
                .widthIn(max = minOf(maxWidth * 0.55f, 232.dp))
                .heightIn(max = maxHeight * 0.55f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {}
                .verticalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
        ) {
            when (page) {
                1 -> {
                    SettingsHeader("Quality") { page = 0 }
                    SettingsRow("Auto", qualityNow, 0, chevron = false, selected = quality <= 0) {
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
                2 -> {
                    SettingsHeader("Audio") { page = 0 }
                    dubs.forEach { d ->
                        val playing = if (d.base) current == null else d.label == current
                        SettingsRow(d.label, null, 0, chevron = false, selected = playing) {
                            AppLog.d("settings pick ${d.label}")
                            onPick(d)
                        }
                    }
                }
                else -> {
                    if (qualities.size > 1) {
                        val pinned = if (quality > 0) qualities.firstOrNull { it.height == quality }?.label else null
                        val qVal = pinned ?: (if (qualityNow != null) "Auto · $qualityNow" else "Auto")
                        SettingsRow("Quality", qVal, R.drawable.ic_quality, chevron = true) {
                            page = 1
                            AppLog.d("settings quality ${qualities.joinToString { it.label }}")
                        }
                    }
                    if (dubs.isNotEmpty()) {
                        SettingsRow("Audio", current ?: "Sub", R.drawable.ic_volume_on, chevron = true) {
                            page = 2
                            AppLog.d("settings dubs=${dubs.size} current=${current ?: "sub"}")
                        }
                    }
                    if (AiSubtitles.available) {
                        SettingsRow(
                            "AI subtitles",
                            if (AiSubtitles.showing) "On" else "Off",
                            R.drawable.ic_cc_on,
                            chevron = false,
                        ) {
                            AiSubtitles.toggle()
                            AppLog.d("settings ai subs=${if (AiSubtitles.showing) "on" else "off"}")
                        }
                    }
                    if (qualities.size <= 1 && dubs.isEmpty() && !AiSubtitles.available) {
                        Text(
                            "No options for this source",
                            color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, fontFamily = Rubik,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onBack)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            contentDescription = "Back",
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
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != 0) {
            Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(
            label, color = if (selected) Cyan else Color.White, fontSize = 14.sp, fontFamily = Rubik,
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
                contentDescription = "Selected", tint = Cyan, modifier = Modifier.size(16.dp),
            )
        }
    }
}
