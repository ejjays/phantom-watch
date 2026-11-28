package dev.phantomwatch.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.phantomwatch.data.SettingsKeys
import dev.phantomwatch.res.*
import dev.phantomwatch.ui.Badge
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
internal fun SettingsAvatar(
    picture: String?,
    size: Dp,
    anonymous: Boolean = false,
    onEdit: (() -> Unit)? = null,
) {
    Box(
        Modifier.size(size).clickable(
            enabled = onEdit != null,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { onEdit?.invoke() },
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (picture == null && anonymous) {
            Image(
                painter = painterResource(Res.drawable.anonymous_avatar),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size),
            )
        } else {
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
                    Image(
                        painter = painterResource(Res.drawable.guest_avatar),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
        }
    }
}

@Composable
internal fun SettingsAccountHeader(
    name: String,
    subtitle: String?,
    picture: String?,
    anonymous: Boolean = false,
    onEditAvatar: (() -> Unit)? = null,
) {
    val nameClick = if (onEditAvatar != null) Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = { onEditAvatar?.invoke() },
    ) else Modifier
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SettingsAvatar(picture, 76.dp, anonymous, onEditAvatar)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = SettingsText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).then(nameClick),
                )
                onEditAvatar?.let { edit ->
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        painter = painterResource(Res.drawable.ic_pen),
                        contentDescription = stringResource(Res.string.you_edit_avatar),
                        tint = SettingsText2,
                        modifier = Modifier.size(16.dp).clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = edit,
                        ),
                    )
                }
            }
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = SettingsText2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

@Composable
internal fun SettingsProfile(
    name: String,
    subtitle: String? = null,
    compact: Boolean = false,
    picture: String? = null,
    anonymous: Boolean = false,
    onEditAvatar: (() -> Unit)? = null,
) {
    val avatar = if (compact) 44.dp else 96.dp
    val nameClick = if (onEditAvatar != null) Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = { onEditAvatar?.invoke() },
    ) else Modifier
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        SettingsAvatar(picture, avatar, anonymous, onEditAvatar)
        Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                name,
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                color = SettingsText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = nameClick,
            )
            onEditAvatar?.let { edit ->
                Spacer(Modifier.width(6.dp))
                Icon(
                    painter = painterResource(Res.drawable.ic_pen),
                    contentDescription = stringResource(Res.string.you_edit_avatar),
                    tint = SettingsText2,
                    modifier = Modifier.size(16.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = edit,
                    ),
                )
            }
        }
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
    val bgOff = (state[SettingsKeys.SUBS_STYLE] ?: SettingsKeys.STYLE_OUTLINED) == SettingsKeys.STYLE_OUTLINED
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            when (item) {
                is SettingItem.Toggle -> {
                    val checked = (state[item.key] ?: if (item.default) 1 else 0) != 0
                    ToggleRow(item, checked, gutter) { onValue(item.key, if (it) 1 else 0) }
                }
                is SettingItem.Choice -> {
                    val selected = (state[item.key] ?: item.default).coerceIn(0, item.options.lastIndex)
                    if (item.preview) {
                        CaptionStylePreview(item, selected, gutter) { onValue(item.key, it) }
                    } else {
                        val enabled = !(item.key == SettingsKeys.SUBS_BG && bgOff)
                        ChoiceRow(item, selected, gutter, { onValue(item.key, it) }, enabled)
                    }
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
    noteColor: Color = SettingsText2,
    noteText: String? = null,
    badge: Boolean = false,
    animate: Boolean = false,
    trailing: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (animate) {
            Crossfade(icon, animationSpec = tween(180), label = "settingsIcon") { res ->
                Icon(
                    painter = painterResource(res),
                    contentDescription = null,
                    tint = titleColor,
                    modifier = Modifier.size(22.dp),
                )
            }
        } else {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = titleColor,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                if (animate) {
                    Crossfade(title, animationSpec = tween(180), label = "settingsTitle") { res ->
                        Text(
                            stringResource(res),
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                            color = titleColor,
                        )
                    }
                } else {
                    Text(
                        stringResource(title),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        color = titleColor,
                    )
                }
                if (badge) BadgeDot()
            }
            val line = noteText ?: note?.let { stringResource(it) }
            if (line != null) {
                Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = noteColor,
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

@Composable
private fun CaptionStylePreview(
    item: SettingItem.Choice,
    selected: Int,
    gutter: Dp,
    onSelect: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(item.icon), null, tint = SettingsText, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
            Text(
                stringResource(item.title),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                color = SettingsText,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item.options.forEachIndexed { index, option ->
                StylePreviewCard(
                    label = stringResource(option),
                    chosen = index == selected,
                    outlined = index == SettingsKeys.STYLE_OUTLINED,
                    modifier = Modifier.weight(1f),
                ) { onSelect(index) }
            }
        }
    }
}

@Composable
private fun StylePreviewCard(
    label: String,
    chosen: Boolean,
    outlined: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp))
            .border(1.dp, if (chosen) SettingsAccent else Color(0xFF3A4252), RoundedCornerShape(14.dp))
            .background(Color(0xFF20242F))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF151922)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            SampleCaption(outlined, Modifier.padding(bottom = 6.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (chosen) {
                Icon(painterResource(Res.drawable.ic_check), null, tint = SettingsAccent,
                    modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(label, color = if (chosen) SettingsAccent else SettingsText, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SampleCaption(outlined: Boolean, modifier: Modifier = Modifier) {
    val d = LocalDensity.current
    val px = with(d) { 13.sp.toPx() }
    val sample = stringResource(Res.string.you_subs_style_sample)
    val base = TextStyle(
        fontSize = 13.sp,
        lineHeight = 16.sp,
        textAlign = TextAlign.Center,
    )
    Box(modifier) {
        if (outlined) {
            Text(sample, color = Color.Black,
                style = base.copy(drawStyle = Stroke(maxOf(1f, px * 0.05f)), fontWeight = FontWeight.SemiBold))
            Text(sample, color = Color.White,
                style = base.copy(fontWeight = FontWeight.SemiBold,
                    shadow = Shadow(Color.Black, blurRadius = px * 0.3f, offset = Offset(0f, px * 0.12f))))
        } else {
            Text(
                sample,
                color = Color.White,
                style = base,
                modifier = Modifier.background(Color(0xFF080C10).copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

private val ChoiceMenuWidth = 132.dp
private val ChoiceValueWidth = 88.dp

@Composable
private fun ChoiceRow(
    item: SettingItem.Choice,
    selected: Int,
    gutter: Dp,
    onSelect: (Int) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val dim = SettingsText2.copy(alpha = 0.45f)
    Box {
        RowShell(
            icon = item.icon,
            title = item.title,
            note = item.note,
            gutter = gutter,
            titleColor = if (enabled) SettingsText else dim,
            noteColor = if (enabled) SettingsText2 else dim,
            modifier = Modifier.clickable(enabled = enabled, role = Role.Button) { expanded = true },
        ) {
            Text(
                item.shortLabels?.getOrNull(selected)?.takeIf { it.isNotEmpty() }
                    ?: stringResource(item.options[selected]),
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) SettingsText2 else dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (enabled) SettingsChevron(Modifier.padding(start = 6.dp))
        }
        DropdownMenu(
            expanded = enabled && expanded,
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
        badge = item.badge,
        animate = item.animate,
    ) {
        if (!item.destructive) SettingsChevron()
    }
}

@Composable
internal fun BadgeDot() {
    Box(Modifier.padding(start = 6.dp, top = 4.dp).size(7.dp).background(Badge, CircleShape))
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
internal fun SignInButton(busy: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(SettingsSurface)
            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
            .clickable(enabled = !busy, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(color = SettingsAccent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Image(
                painterResource(Res.drawable.ic_google_g),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(Res.string.you_sign_in_google),
                color = SettingsText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
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
