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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phantomwatch.res.*
import dev.phantomwatch.ui.Lilita
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val GeneralIds = setOf("playback", "subtitles", "library")

@Composable
internal fun SettingsRoot(
    categories: List<SettingCategory>,
    gutter: Dp,
    onOpen: (Int) -> Unit,
    data: SettingsData,
) {
    val general = categories.filter { it.id in GeneralIds }
    val app = categories.filterNot { it.id in GeneralIds }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.TopCenter).widthIn(max = SettingsContentMax).fillMaxWidth()) {
            Text(
                stringResource(Res.string.you_title_settings),
                color = SettingsText,
                fontSize = 28.sp,
                fontFamily = Lilita,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
            )
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(top = 12.dp, bottom = SettingsNavClearance),
            ) {
                item("account") {
                    val account = data.account
                    Column(Modifier.padding(horizontal = gutter)) {
                        SettingsAccountHeader(
                            name = account?.let { it.name.ifBlank { it.email.substringBefore('@') } }
                                ?: stringResource(Res.string.you_profile_guest),
                            subtitle = account?.email ?: stringResource(Res.string.you_profile_note),
                            picture = account?.picture,
                        )
                        if (data.googleReady && account == null) {
                            Spacer(Modifier.height(16.dp))
                            SignInButton(data.accountBusy, data.onSignIn)
                        }
                        data.accountError?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, color = SettingsError, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                item("banner") {
                    Spacer(Modifier.height(28.dp))
                    Box(Modifier.padding(horizontal = gutter)) {
                        SettingsDownloadCard(
                            title = stringResource(Res.string.you_offline_title),
                            subtitle = stringResource(Res.string.you_offline_note),
                            onClick = { data.onAction(SettingsAction.GetPhantom) },
                        )
                    }
                }
                item("general") {
                    SettingsSectionLabel(
                        stringResource(Res.string.you_section_general),
                        Modifier.padding(start = gutter, end = gutter, top = 20.dp, bottom = 8.dp),
                    )
                    CategoryList(general, categories, gutter, onOpen)
                }
                item("app") {
                    SettingsSectionLabel(
                        stringResource(Res.string.you_section_app),
                        Modifier.padding(start = gutter, end = gutter, top = 24.dp, bottom = 8.dp),
                    )
                    CategoryList(app, categories, gutter, onOpen)
                }
                if (data.googleReady && data.account != null) {
                    item("signout") {
                        Column(Modifier.padding(start = gutter, end = gutter, top = 24.dp)) {
                            SettingsDangerRow(Res.drawable.ic_row_logout, stringResource(Res.string.you_sign_out)) {
                                data.onSignOut()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryList(rows: List<SettingCategory>, all: List<SettingCategory>, gutter: Dp, onOpen: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEach { category ->
            CategoryRow(category, gutter) { onOpen(all.indexOf(category)) }
        }
    }
}

@Composable
private fun CategoryRow(category: SettingCategory, gutter: Dp, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(category.icon),
            contentDescription = null,
            tint = SettingsText,
            modifier = Modifier.size(22.dp),
        )
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                stringResource(category.title),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
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
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(SettingsSurface)
            .clickable(enabled = !busy, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(color = SettingsAccent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Text(
                stringResource(Res.string.you_sign_in_google),
                color = SettingsAccent,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
