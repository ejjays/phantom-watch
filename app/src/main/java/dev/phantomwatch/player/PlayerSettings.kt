package dev.phantomwatch.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phantomwatch.R
import dev.phantomwatch.core.AppLog
import dev.phantomwatch.data.Dub
import dev.phantomwatch.ui.Cyan
import dev.phantomwatch.ui.Rubik

@Composable
internal fun PlayerSettingsMenu(
    dubs: List<Dub>,
    current: String?,
    onPick: (Dub) -> Unit,
    onDismiss: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxSize().clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onDismiss,
        ),
    ) {
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)
                .width(232.dp).clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = 0.93f))
                .padding(vertical = 6.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {},
        ) {
            if (!open) {
                if (dubs.isNotEmpty()) {
                    SettingsRow("Dubs", current ?: "Sub", R.drawable.ic_volume_on, chevron = true) {
                        open = true
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
            } else {
                dubs.forEach { d ->
                    val playing = if (d.base) current == null else d.label == current
                    SettingsRow(d.label, if (playing) "Playing" else null, 0, chevron = false) {
                        AppLog.d("settings pick ${d.label}")
                        onPick(d)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    label: String,
    value: String?,
    icon: Int,
    chevron: Boolean,
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
        Text(label, color = Color.White, fontSize = 14.sp, fontFamily = Rubik)
        Spacer(Modifier.weight(1f))
        if (value != null) {
            Text(
                value,
                color = if (value == "Playing") Cyan else Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp, fontFamily = Rubik,
            )
        }
        if (chevron) {
            Spacer(Modifier.width(6.dp))
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null, tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
