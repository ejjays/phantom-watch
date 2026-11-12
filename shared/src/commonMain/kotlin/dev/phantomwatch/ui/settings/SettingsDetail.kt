package dev.phantomwatch.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.phantomwatch.res.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SettingsDetail(
    category: SettingCategory,
    state: Map<String, Int>,
    gutter: Dp,
    showBack: Boolean,
    onBack: () -> Unit,
    onValue: (String, Int) -> Unit,
    onAction: (SettingsAction) -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.TopCenter).widthIn(max = SettingsContentMax).fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .padding(start = if (showBack) 8.dp else gutter, end = gutter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showBack) {
                    BackButton(onBack)
                    Spacer(Modifier.width(2.dp))
                }
                Text(
                    stringResource(category.title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = SettingsText,
                )
            }
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(top = 20.dp, bottom = SettingsNavClearance),
            ) {
                item("items") {
                    SettingsGroup(
                        items = category.items,
                        state = state,
                        gutter = gutter,
                        onValue = onValue,
                        onAction = { action ->
                            if (action == SettingsAction.ClearHistory) confirmClear = true else onAction(action)
                        },
                    )
                }
            }
        }
    }

    if (confirmClear) {
        SettingsConfirmDialog(
            onDismiss = { confirmClear = false },
            onConfirm = { onAction(SettingsAction.ClearHistory) },
        )
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_back),
            contentDescription = stringResource(Res.string.common_back),
            tint = SettingsText,
            modifier = Modifier.size(24.dp).scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f),
        )
    }
}
