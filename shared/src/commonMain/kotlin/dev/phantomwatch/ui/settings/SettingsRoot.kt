package dev.phantomwatch.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.phantomwatch.res.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SettingsRoot(
    categories: List<SettingCategory>,
    gutter: Dp,
    onOpen: (Int) -> Unit,
    data: SettingsData,
) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.align(Alignment.TopCenter).widthIn(max = SettingsContentMax).fillMaxWidth(),
            contentPadding = PaddingValues(start = gutter, end = gutter, top = 24.dp, bottom = SettingsNavClearance),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item("profile") {
                val account = data.account
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    SettingsProfile(
                        name = account?.let { it.name.ifBlank { it.email.substringBefore('@') } }
                            ?: stringResource(Res.string.you_profile_guest),
                        subtitle = account?.email ?: stringResource(Res.string.you_profile_note),
                        picture = account?.picture,
                    )
                    if (data.googleReady) {
                        Spacer(Modifier.height(16.dp))
                        if (account == null) {
                            SignInButton(data.accountBusy, data.onSignIn)
                        } else {
                            TextButton(onClick = data.onSignOut) {
                                Text(
                                    stringResource(Res.string.you_sign_out),
                                    color = SettingsText2,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                        data.accountError?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, color = Color(0xFFF87171), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item("categories") {
                Column(Modifier.clip(RoundedCornerShape(16.dp)).background(SettingsSurface)) {
                    categories.forEachIndexed { index, category ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 52.dp),
                                thickness = 1.dp,
                                color = SettingsHairline,
                            )
                        }
                        CategoryRow(category) { onOpen(index) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(category: SettingCategory, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(category.icon),
            contentDescription = null,
            tint = SettingsText,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(category.title),
                style = MaterialTheme.typography.titleMedium,
                color = SettingsText,
            )
            Text(
                stringResource(category.blurb),
                style = MaterialTheme.typography.bodyMedium,
                color = SettingsText2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        SettingsChevron()
    }
}

@Composable
private fun SignInButton(busy: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.widthIn(max = 320.dp).fillMaxWidth().heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(SettingsText)
            .clickable(enabled = !busy, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(color = SettingsCanvas, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Text(
                stringResource(Res.string.you_sign_in_google),
                color = SettingsCanvas,
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}
