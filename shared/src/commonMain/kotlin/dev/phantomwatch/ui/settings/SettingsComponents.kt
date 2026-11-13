package dev.phantomwatch.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.phantomwatch.res.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SettingsSectionLabel(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = SettingsText3)
        HorizontalDivider(
            Modifier.weight(1f).padding(start = 12.dp),
            thickness = 1.dp,
            color = SettingsHairline,
        )
    }
}

@Composable
internal fun SettingsAvatar(picture: String?, size: Dp) {
    Box(
        Modifier.size(size)
            .clip(CircleShape)
            .background(SettingsTile)
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
                modifier = Modifier.size(size * 0.42f),
            )
        }
    }
}

@Composable
internal fun SettingsAccountHeader(name: String, subtitle: String, picture: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SettingsAvatar(picture, 76.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = SettingsText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = SettingsText2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
internal fun SettingsProfile(
    name: String,
    subtitle: String? = null,
    compact: Boolean = false,
    picture: String? = null,
) {
    val avatar = if (compact) 44.dp else 96.dp
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        SettingsAvatar(picture, avatar)
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
    gutter: Dp,
    onValue: (String, Int) -> Unit,
    onAction: (SettingsAction) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            when (item) {
                is SettingItem.Toggle -> {
                    val checked = (state[item.key] ?: if (item.default) 1 else 0) != 0
                    ToggleRow(item, checked, gutter) { onValue(item.key, if (it) 1 else 0) }
                }
                is SettingItem.Choice -> {
                    val selected = (state[item.key] ?: item.default).coerceIn(0, item.options.lastIndex)
                    ChoiceRow(item, selected, gutter) { onValue(item.key, it) }
                }
                is SettingItem.Value -> ValueRow(item, gutter)
                is SettingItem.Action -> ActionRow(item, gutter) { onAction(item.action) }
            }
        }
    }
}

@Composable
private fun RowShell(
    icon: DrawableResource,
    title: StringResource,
    note: StringResource?,
    gutter: Dp,
    modifier: Modifier = Modifier,
    titleColor: Color = SettingsText,
    noteText: String? = null,
    trailing: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = titleColor,
            modifier = Modifier.size(22.dp),
        )
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 12.dp)) {
            Text(
                stringResource(title),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                color = titleColor,
            )
            val line = noteText ?: note?.let { stringResource(it) }
            if (line != null) {
                Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SettingsText2,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        trailing()
    }
}

@Composable
private fun ToggleRow(item: SettingItem.Toggle, checked: Boolean, gutter: Dp, onToggle: (Boolean) -> Unit) {
    RowShell(
        icon = item.icon,
        title = item.title,
        note = item.note,
        gutter = gutter,
        modifier = Modifier.toggleable(value = checked, onValueChange = onToggle, role = Role.Switch),
    ) {
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

private val ChoiceMenuWidth = 132.dp
private val ChoiceValueWidth = 88.dp

@Composable
private fun ChoiceRow(item: SettingItem.Choice, selected: Int, gutter: Dp, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        RowShell(
            icon = item.icon,
            title = item.title,
            note = item.note,
            gutter = gutter,
            modifier = Modifier.clickable(role = Role.Button) { expanded = true },
        ) {
            Text(
                stringResource(item.options[selected]),
                style = MaterialTheme.typography.bodyMedium,
                color = SettingsText2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            SettingsChevron(Modifier.padding(start = 6.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset(-(ChoiceMenuWidth - ChoiceValueWidth), 0.dp),
        ) {
            item.options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    modifier = Modifier.fillMaxWidth(),
                    text = {
                        Text(
                            stringResource(option),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (index == selected) SettingsAccent else SettingsText,
                        )
                    },
                    trailingIcon = if (index == selected) {
                        {
                            Icon(
                                painter = painterResource(Res.drawable.ic_check),
                                contentDescription = null,
                                tint = SettingsAccent,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        null
                    },
                    contentPadding = PaddingValues(start = 16.dp, end = 12.dp),
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
private fun ValueRow(item: SettingItem.Value, gutter: Dp) {
    RowShell(icon = item.icon, title = item.title, note = item.note, gutter = gutter) {
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
private fun ActionRow(item: SettingItem.Action, gutter: Dp, onClick: () -> Unit) {
    RowShell(
        icon = item.icon,
        title = item.title,
        note = item.note,
        gutter = gutter,
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
        titleColor = if (item.destructive) SettingsError else SettingsText,
        noteText = item.noteText,
    ) {
        if (!item.destructive) SettingsChevron()
    }
}

@Composable
internal fun SettingsDangerRow(icon: DrawableResource, title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = SettingsError,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = SettingsError)
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
        title = {
            Text(
                stringResource(Res.string.you_clear_title),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                color = SettingsText,
            )
        },
        text = {
            Text(stringResource(Res.string.you_clear_body), style = MaterialTheme.typography.bodyMedium, color = SettingsText2)
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(stringResource(Res.string.you_clear_confirm), style = MaterialTheme.typography.labelMedium, color = SettingsError)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.common_cancel), style = MaterialTheme.typography.labelMedium, color = SettingsText)
            }
        },
        containerColor = SettingsTile,
        titleContentColor = SettingsText,
        textContentColor = SettingsText2,
    )
}
