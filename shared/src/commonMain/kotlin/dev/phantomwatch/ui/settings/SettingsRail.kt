package dev.phantomwatch.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.phantomwatch.data.Account
import dev.phantomwatch.res.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SettingsRail(
    categories: List<SettingCategory>,
    selected: Int,
    version: String,
    width: Dp,
    account: Account?,
    onSelect: (Int) -> Unit,
) {
    Column(
        Modifier.width(width)
            .fillMaxHeight()
            .background(SettingsCanvas)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 24.dp)
            .selectableGroup(),
    ) {
        SettingsProfile(
            name = account?.let { it.name.ifBlank { it.email.substringBefore('@') } }
                ?: stringResource(Res.string.you_profile_guest),
            subtitle = account?.email ?: version,
            compact = true,
            picture = account?.picture,
        )
        Spacer(Modifier.height(24.dp))
        categories.forEachIndexed { index, category ->
            RailItem(
                icon = category.icon,
                label = category.title,
                selected = selected == index,
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun RailItem(
    icon: DrawableResource,
    label: StringResource,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) SettingsSurface else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(2.dp).height(18.dp).clip(CircleShape)
                .background(if (selected) SettingsAccent else Color.Transparent),
        )
        Spacer(Modifier.width(10.dp))
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (selected) SettingsText else SettingsText2,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(label),
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) SettingsText else SettingsText2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
