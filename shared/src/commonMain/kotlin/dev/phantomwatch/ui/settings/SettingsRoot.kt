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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.phantomwatch.data.SettingsKeys
import dev.phantomwatch.res.*
import dev.phantomwatch.ui.Mono
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SettingsRoot(
    categories: List<SettingCategory>,
    state: Map<String, Int>,
    providersLabel: String,
    version: String,
    gutter: Dp,
    onValue: (String, Int) -> Unit,
    onOpen: (Int) -> Unit,
) {
    val quick = remember(categories) {
        categories.flatMap { it.items }
            .filterIsInstance<SettingItem.Toggle>()
            .filter { it.key == SettingsKeys.AUTOPLAY || it.key == SettingsKeys.SKIP_INTRO || it.key == SettingsKeys.SUBS_ON }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.align(Alignment.TopCenter).widthIn(max = SettingsContentMax).fillMaxWidth(),
            contentPadding = PaddingValues(start = gutter, end = gutter, top = 20.dp, bottom = SettingsNavClearance),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            item("header") {
                Column {
                    Text(
                        stringResource(Res.string.you_title),
                        style = MaterialTheme.typography.headlineLarge,
                        color = SettingsText,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(Res.string.you_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = SettingsText2,
                    )
                }
            }
            item("quick") {
                Column {
                    SettingsGroupLabel(Res.string.you_quick)
                    SettingsGroup(
                        items = quick,
                        state = state,
                        onValue = onValue,
                        onAction = {},
                        onChoice = {},
                    )
                }
            }
            item("categories") {
                Column {
                    SettingsGroupLabel(Res.string.you_all_settings)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        categories.forEachIndexed { index, category ->
                            CategoryRow(
                                category = category,
                                summary = categorySummary(category, state, providersLabel, version),
                                shape = segmentShape(index, categories.size),
                                onClick = { onOpen(index) },
                            )
                        }
                    }
                }
            }
            item("version") {
                Text(
                    version,
                    style = MaterialTheme.typography.labelSmall,
                    color = SettingsText3,
                    fontFamily = Mono,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: SettingCategory,
    summary: String,
    shape: Shape,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(SettingsRaised)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF191D24)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(category.icon),
                contentDescription = null,
                tint = SettingsText2,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(category.title),
                style = MaterialTheme.typography.titleMedium,
                color = SettingsText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = SettingsText2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        SettingsChevron()
    }
}

@Composable
private fun categorySummary(
    category: SettingCategory,
    state: Map<String, Int>,
    providersLabel: String,
    version: String,
): String {
    fun on(key: String): Boolean = (state[key] ?: 1) != 0
    val onWord = stringResource(Res.string.you_on)
    val offWord = stringResource(Res.string.you_off)
    fun word(value: Boolean): String = if (value) onWord else offWord
    return when (category.id) {
        "playback" -> stringResource(
            Res.string.you_summary_playback,
            word(on(SettingsKeys.AUTOPLAY)),
            word(on(SettingsKeys.SKIP_INTRO)),
        )
        "subtitles" -> {
            if (!on(SettingsKeys.SUBS_ON)) {
                stringResource(Res.string.you_summary_subtitles_off)
            } else {
                val choice = category.items
                    .filterIsInstance<SettingItem.Choice>()
                    .firstOrNull { it.key == SettingsKeys.SUBS_SIZE }
                var label = ""
                if (choice != null) {
                    val selected = (state[choice.key] ?: choice.default).coerceIn(0, choice.options.lastIndex)
                    label = stringResource(choice.options[selected])
                }
                stringResource(Res.string.you_summary_subtitles_on, label)
            }
        }
        "library" -> stringResource(
            Res.string.you_summary_library,
            word(on(SettingsKeys.SAVE_HISTORY)),
            word(on(SettingsKeys.CONTINUE_WATCHING)),
        )
        "providers" -> stringResource(
            Res.string.you_summary_providers,
            providersLabel,
            word(on(SettingsKeys.AUTO_REFRESH)),
        )
        "about" -> stringResource(Res.string.you_summary_about, version)
        else -> ""
    }
}
