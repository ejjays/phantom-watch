package dev.phantomwatch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

internal val SettingsPageTop = Color(0xFF141414)
internal val SettingsPageBottom = Color(0xFF0B0B0B)
internal val SettingsRailBg = Color(0xFF131313)
internal val SettingsCardTop = Color(0xFF212121)
internal val SettingsCardBottom = Color(0xFF191919)
internal val SettingsHairline = Color.White.copy(alpha = 0.07f)
internal val SettingsBorderStrong = Color(0xFF3A4040)
internal val SettingsOnAccent = Color(0xFF111111)

internal val PageBrush = Brush.verticalGradient(listOf(SettingsPageTop, SettingsPageBottom))
private val CardBrush = Brush.verticalGradient(listOf(SettingsCardTop, SettingsCardBottom))
private val CardEdge = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = 0.11f), Color.White.copy(alpha = 0.035f)),
)

private val CardShape = RoundedCornerShape(28.dp)

internal sealed interface SettingItem {
    val key: String
    val title: String
    val note: String?

    data class Toggle(
        override val key: String,
        override val title: String,
        override val note: String? = null,
        val on: Boolean,
    ) : SettingItem

    data class Value(
        override val key: String,
        override val title: String,
        override val note: String? = null,
        val value: String,
    ) : SettingItem

    data class Segments(
        override val key: String,
        override val title: String,
        override val note: String? = null,
        val options: List<String>,
        val selected: Int,
    ) : SettingItem

    data class Meter(
        override val key: String,
        override val title: String,
        override val note: String? = null,
        val value: String,
        val fraction: Float,
    ) : SettingItem

    data class Action(
        override val key: String,
        override val title: String,
        override val note: String? = null,
        val destructive: Boolean = false,
    ) : SettingItem
}

internal data class SettingCategory(
    val id: String,
    val icon: DrawableResource,
    val title: String,
    val blurb: String,
    val items: List<SettingItem>,
)

@Composable
internal fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(CardShape).background(CardBrush)
            .border(0.5.dp, CardEdge, CardShape),
        content = content,
    )
}

@Composable
internal fun SettingsDivider(start: Dp = 0.dp) {
    HorizontalDivider(Modifier.padding(start = start), thickness = 0.5.dp, color = SettingsHairline)
}

// left edge of the text column: a divider starts under the text, not the icon
internal fun textInset(wide: Boolean): Dp =
    (if (wide) 20.dp else 16.dp) + iconSize(wide) + (if (wide) 16.dp else 14.dp)

private fun iconSize(wide: Boolean): Dp = if (wide) 24.dp else 22.dp

@Composable
internal fun SettingsNavRow(
    title: String,
    note: String?,
    wide: Boolean,
    onClick: () -> Unit,
    icon: DrawableResource? = null,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = if (wide) 20.dp else 16.dp, vertical = if (wide) 18.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).padding(end = 12.dp).then(if (wide) Modifier.widthIn(max = 560.dp) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    painterResource(icon), contentDescription = null, tint = Slate400,
                    modifier = Modifier.size(iconSize(wide)),
                )
                Spacer(Modifier.width(if (wide) 16.dp else 14.dp))
            }
            Column {
                Text(
                    title, color = Slate100, fontSize = 16.sp, lineHeight = 24.sp, fontFamily = Rubik,
                    fontWeight = FontWeight.Medium,
                )
                val body = note
                if (!body.isNullOrBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        body, color = Slate400, fontSize = 14.sp, lineHeight = 21.sp, fontFamily = Rubik,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SettingsRows(
    items: List<SettingItem>,
    state: SnapshotStateMap<String, Int>,
    wide: Boolean,
    onValue: (String, Int) -> Unit,
    onAction: (String) -> Unit = {},
) {
    SettingsCard {
        items.forEachIndexed { index, item ->
            if (index > 0) SettingsDivider()
            when (item) {
                is SettingItem.Toggle -> {
                    val on = (state[item.key] ?: if (item.on) 1 else 0) != 0
                    SettingsSwitchRow(item.title, item.note, on, wide) {
                        onValue(item.key, if (it) 1 else 0)
                    }
                }
                is SettingItem.Value -> SettingsValueRow(item, wide)
                is SettingItem.Segments -> {
                    val selected = state[item.key] ?: item.selected
                    SettingsChipRow(item, selected, wide) { onValue(item.key, it) }
                }
                is SettingItem.Meter -> SettingsMeterRow(item, wide)
                is SettingItem.Action -> SettingsActionRow(item, wide) { onAction(item.key) }
            }
        }
    }
}

@Composable
private fun RowText(
    title: String,
    note: String?,
    modifier: Modifier = Modifier,
    color: Color = Slate100,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title, color = color, fontSize = 16.sp, lineHeight = 24.sp, fontFamily = Rubik,
            fontWeight = FontWeight.Medium,
        )
        if (!note.isNullOrBlank()) {
            Text(note, color = Slate400, fontSize = 14.sp, lineHeight = 21.sp, fontFamily = Rubik)
        }
    }
}

private fun Modifier.rowPadding(wide: Boolean) = padding(
    horizontal = if (wide) 20.dp else 16.dp,
    vertical = if (wide) 18.dp else 16.dp,
)

@Composable
internal fun SettingsSwitchRow(
    title: String,
    note: String?,
    checked: Boolean,
    wide: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable { onToggle(!checked) }.rowPadding(wide),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(title, note, Modifier.weight(1f).padding(end = 12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.padding(start = 4.dp),
            colors = SwitchDefaults.colors(
                checkedThumbColor = SettingsOnAccent,
                checkedTrackColor = Cyan,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Slate400,
                uncheckedTrackColor = SettingsBorderStrong,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun SettingsValueRow(item: SettingItem.Value, wide: Boolean) {
    Row(Modifier.fillMaxWidth().rowPadding(wide), verticalAlignment = Alignment.CenterVertically) {
        RowText(item.title, item.note, Modifier.weight(1f).padding(end = 12.dp))
        Text(item.value, color = Slate400, fontSize = 15.sp, fontFamily = Rubik)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsChipRow(item: SettingItem.Segments, selected: Int, wide: Boolean, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().rowPadding(wide), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RowText(item.title, item.note)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item.options.forEachIndexed { index, label ->
                SettingsChip(label, index == selected) { onSelect(index) }
            }
        }
    }
}

@Composable
private fun SettingsChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.clip(shape)
            .background(if (selected) Color.White.copy(alpha = 0.13f) else Color.Transparent)
            .border(1.dp, if (selected) Color.White.copy(alpha = 0.30f) else SettingsBorderStrong, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(
            label, color = if (selected) Slate100 else Slate400, fontSize = 14.sp, fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold, maxLines = 1,
        )
    }
}

@Composable
private fun SettingsMeterRow(item: SettingItem.Meter, wide: Boolean) {
    Column(Modifier.fillMaxWidth().rowPadding(wide), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowText(item.title, item.note, Modifier.weight(1f).padding(end = 12.dp))
            Text(item.value, color = Slate400, fontSize = 15.sp, fontFamily = Rubik)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(SettingsBorderStrong)) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(item.fraction.coerceIn(0f, 1f))
                    .clip(CircleShape).background(Cyan),
            )
        }
    }
}

@Composable
private fun SettingsActionRow(item: SettingItem.Action, wide: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).rowPadding(wide),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(
            item.title, item.note, Modifier.weight(1f),
            color = if (item.destructive) WatchRed else Slate100,
        )
    }
}
