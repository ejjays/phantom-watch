package dev.phantomwatch.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.phantomwatch.res.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SettingsGroupLabel(text: StringResource) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.labelMedium,
        color = SettingsText3,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp).semantics { heading() },
    )
}

@Composable
internal fun SettingsGroup(
    items: List<SettingItem>,
    state: Map<String, Int>,
    onValue: (String, Int) -> Unit,
    onAction: (SettingsAction) -> Unit,
    onChoice: (SettingItem.Choice) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEachIndexed { index, item ->
            val shape = segmentShape(index, items.size)
            when (item) {
                is SettingItem.Toggle -> {
                    val checked = (state[item.key] ?: if (item.default) 1 else 0) != 0
                    ToggleRow(item, checked, shape) { onValue(item.key, if (it) 1 else 0) }
                }
                is SettingItem.Choice -> {
                    val selected = (state[item.key] ?: item.default).coerceIn(0, item.options.lastIndex)
                    ChoiceRow(item, selected, shape) { onChoice(item) }
                }
                is SettingItem.Value -> ValueRow(item, shape)
                is SettingItem.Action -> ActionRow(item, shape) { onAction(item.action) }
            }
        }
    }
}

internal fun segmentShape(index: Int, count: Int): Shape {
    if (count <= 1) return RoundedCornerShape(24.dp)
    val outer = 24.dp
    val inner = 6.dp
    return when (index) {
        0 -> RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner)
        count - 1 -> RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer)
        else -> RoundedCornerShape(inner)
    }
}

@Composable
private fun ToggleRow(item: SettingItem.Toggle, checked: Boolean, shape: Shape, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(SettingsRaised)
            .toggleable(value = checked, onValueChange = onToggle, role = Role.Switch)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(item.title, item.note, Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = null, colors = settingsSwitchColors())
    }
}

@Composable
private fun ChoiceRow(item: SettingItem.Choice, selected: Int, shape: Shape, onClick: () -> Unit) {
    val label = stringResource(item.options[selected])
    Row(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(SettingsRaised)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(item.title, item.note, Modifier.weight(1f).padding(end = 12.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = SettingsText2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        SettingsChevron(Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun ValueRow(item: SettingItem.Value, shape: Shape) {
    Row(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(SettingsRaised)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(item.title, item.note, Modifier.weight(1f).padding(end = 12.dp))
        Text(
            item.value,
            style = MaterialTheme.typography.titleMedium,
            color = SettingsText2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ActionRow(item: SettingItem.Action, shape: Shape, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(SettingsRaised)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
private fun settingsSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = SettingsAccentInk,
    checkedTrackColor = SettingsAccent,
    checkedBorderColor = Color.Transparent,
    uncheckedThumbColor = SettingsText2,
    uncheckedTrackColor = Color(0xFF2A313A),
    uncheckedBorderColor = Color(0xFF6B7684),
)

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
internal fun SettingsChoiceDialog(
    item: SettingItem.Choice,
    selected: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(item.title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                item.options.forEachIndexed { index, option ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected == index, role = Role.RadioButton) { onSelect(index) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == index, onClick = null)
                        Text(
                            stringResource(option),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_close)) }
        },
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
