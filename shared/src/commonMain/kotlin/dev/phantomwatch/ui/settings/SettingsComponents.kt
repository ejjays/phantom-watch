package dev.phantomwatch.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.phantomwatch.res.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SettingsProfile(
    name: String,
    subtitle: String? = null,
    compact: Boolean = false,
    picture: String? = null,
) {
    val avatar = if (compact) 44.dp else 96.dp
    val glyph = if (compact) 20.dp else 40.dp
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(avatar)
                .clip(CircleShape)
                .background(if (compact) SettingsSurface else Color(0xFF262626))
                .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (picture != null) {
                AsyncImage(
                    model = picture,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Icon(
                    painter = painterResource(Res.drawable.ic_row_person),
                    contentDescription = null,
                    tint = SettingsText2,
                    modifier = Modifier.size(glyph),
                )
            }
        }
        Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
        Text(
            name,
            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
            color = SettingsText,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(3.dp))
            Text(
                subtitle,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium,
                color = SettingsText2,
            )
        }
    }
}

@Composable
internal fun SettingsGroup(
    items: List<SettingItem>,
    state: Map<String, Int>,
    onValue: (String, Int) -> Unit,
    onAction: (SettingsAction) -> Unit,
) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SettingsSurface)) {
        items.forEachIndexed { index, item ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp),
                    thickness = 1.dp,
                    color = SettingsHairline,
                )
            }
            when (item) {
                is SettingItem.Toggle -> {
                    val checked = (state[item.key] ?: if (item.default) 1 else 0) != 0
                    ToggleRow(item, checked) { onValue(item.key, if (it) 1 else 0) }
                }
                is SettingItem.Choice -> {
                    val selected = (state[item.key] ?: item.default).coerceIn(0, item.options.lastIndex)
                    ChoiceRow(item, selected) { onValue(item.key, it) }
                }
                is SettingItem.Value -> ValueRow(item)
                is SettingItem.Action -> ActionRow(item) { onAction(item.action) }
            }
        }
    }
}

@Composable
private fun ToggleRow(item: SettingItem.Toggle, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .toggleable(value = checked, onValueChange = onToggle, role = Role.Switch)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(item.title, item.note, Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = null, colors = settingsSwitchColors())
    }
}

@Composable
private fun settingsSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color(0xFF04272E),
    checkedTrackColor = SettingsAccent,
    checkedBorderColor = Color.Transparent,
    uncheckedThumbColor = SettingsText2,
    uncheckedTrackColor = Color(0xFF2A313A),
    uncheckedBorderColor = Color(0xFF6B7684),
)

@Composable
private fun ChoiceRow(item: SettingItem.Choice, selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth()
                .clickable(role = Role.Button) { expanded = true }
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowText(item.title, item.note, Modifier.weight(1f).padding(end = 12.dp))
            Text(
                stringResource(item.options[selected]),
                style = MaterialTheme.typography.bodyMedium,
                color = SettingsText2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            SettingsChevron(Modifier.padding(start = 4.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            item.options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(option),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (index == selected) SettingsAccent else SettingsText,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(index)
                    },
                )
            }
        }
    }
}

@Composable
private fun ValueRow(item: SettingItem.Value) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(item.title, item.note, Modifier.weight(1f).padding(end = 12.dp))
        Text(
            item.value,
            style = MaterialTheme.typography.bodyMedium,
            color = SettingsText2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ActionRow(item: SettingItem.Action, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(
            item.title,
            item.note,
            Modifier.weight(1f).padding(end = 12.dp),
            color = if (item.destructive) SettingsError else SettingsText,
        )
        if (!item.destructive) SettingsChevron()
    }
}

@Composable
private fun RowText(
    title: StringResource,
    note: StringResource?,
    modifier: Modifier = Modifier,
    color: Color = SettingsText,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = color)
        if (note != null) {
            Text(stringResource(note), style = MaterialTheme.typography.bodyMedium, color = SettingsText2)
        }
    }
}

@Composable
internal fun SettingsChevron(modifier: Modifier = Modifier) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Icon(
        painter = painterResource(Res.drawable.ic_chevron_right),
        contentDescription = null,
        tint = SettingsText3,
        modifier = modifier.size(18.dp).scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f),
    )
}

@Composable
internal fun SettingsConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.you_clear_title)) },
        text = { Text(stringResource(Res.string.you_clear_body)) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(stringResource(Res.string.you_clear_confirm), color = SettingsError)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
        },
    )
}
