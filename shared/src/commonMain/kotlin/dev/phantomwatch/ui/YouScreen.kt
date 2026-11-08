package dev.phantomwatch.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.border
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phantomwatch.res.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// values come from the android side; this module has no preference store of its own
data class YouData(
    val version: String,
    val providers: String,
    val settings: Map<String, Int>,
    val onValue: (String, Int) -> Unit,
    val onClearHistory: () -> Unit,
    val onCheckUpdates: () -> Unit,
    val onOpenLicences: () -> Unit,
    val onOpenPrivacy: () -> Unit,
    val onSendFeedback: () -> Unit,
)

@Composable
fun YouScreen(data: YouData) {
    val categories = settingsCategories(data)
    val state = rememberSaveable(saver = SettingsStateSaver) { defaultSettings(categories) }
    var open by rememberSaveable { mutableIntStateOf(-1) }
    LaunchedEffect(data.settings) {
        data.settings.forEach { (key, value) -> state[key] = value }
    }
    val onValue: (String, Int) -> Unit = { key, value ->
        state[key] = value
        data.onValue(key, value)
    }
    Box(Modifier.fillMaxSize().background(PageBrush)) {
        if (isExpanded(screenWidthDp())) {
            TabletSettings(categories, state, onValue, data, open.coerceAtLeast(0)) { open = it }
        } else {
            AnimatedContent(
                targetState = open,
                transitionSpec = {
                    val forward = targetState > initialState
                    val enter = slideInHorizontally(tween(240)) { if (forward) it / 5 else -it / 5 }
                    val exit = slideOutHorizontally(tween(240)) { if (forward) -it / 8 else it / 8 }
                    (enter + fadeIn(tween(200))) togetherWith (exit + fadeOut(tween(150)))
                },
                label = "settings-page",
            ) { index ->
                if (index in categories.indices) {
                    DetailSettings(categories[index], state, onValue, data) { open = -1 }
                } else {
                    RootSettings(categories, data.version) { open = it }
                }
            }
        }
    }
}

@Composable
private fun RootSettings(categories: List<SettingCategory>, version: String, onOpen: (Int) -> Unit) {
    SettingsList {
        item("topSpace") { Spacer(Modifier.height(20.dp)) }
        item("account") {
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(112.dp).border(3.dp, Cyan.copy(alpha = 0.65f), CircleShape)
                        .padding(8.dp).background(Color(0xFF252526), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_row_person), null,
                        tint = Color(0xFFBDBDBD), modifier = Modifier.size(48.dp),
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    stringResource(Res.string.you_profile_guest), color = Slate100,
                    fontFamily = Inter, fontSize = 22.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(Res.string.you_profile_note), color = Color(0xFFA9A9AD),
                    fontFamily = Inter, fontSize = 13.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
        RootGroups.forEach { ids ->
            item(ids.first()) {
                SettingsCard {
                    var first = true
                    ids.forEach { id ->
                        val index = categories.indexOfFirst { it.id == id }
                        if (index < 0) return@forEach
                        if (!first) SettingsDivider(textInset(wide = false))
                        first = false
                        val category = categories[index]
                        SettingsNavRow(
                            title = category.title,
                            note = null,
                            wide = false,
                            onClick = { onOpen(index) },
                            icon = category.icon,
                        )
                    }
                }
            }
        }
        item("version") {
            Text(
                version, color = Color(0xFF99999F), fontSize = 12.sp,
                fontFamily = Inter, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun DetailSettings(
    category: SettingCategory,
    state: SnapshotStateMap<String, Int>,
    onValue: (String, Int) -> Unit,
    data: YouData,
    onBack: () -> Unit,
) {
    SettingsList {
        item("header") { SettingsHeader(category.title, onBack) }
        item("card") { SettingsRows(category.items, state, wide = false, onValue = onValue, onAction = actionOf(data)) }
    }
}

private fun actionOf(data: YouData): (String) -> Unit = { key ->
    when (key) {
        "clearhistory" -> data.onClearHistory()
        "updates" -> data.onCheckUpdates()
        "licences" -> data.onOpenLicences()
        "privacy" -> data.onOpenPrivacy()
        "feedback" -> data.onSendFeedback()
    }
}

@Composable
private fun SettingsList(content: LazyListScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 680.dp).fillMaxWidth()
                .statusBarsPadding().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = NavClearance),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun TabletSettings(
    categories: List<SettingCategory>,
    state: SnapshotStateMap<String, Int>,
    onValue: (String, Int) -> Unit,
    data: YouData,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val index = selected.coerceIn(0, categories.lastIndex)
    Row(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.width(300.dp).fillMaxHeight().background(SettingsRailBg).padding(top = 28.dp)) {
            Text(
                stringResource(Res.string.tab_you), color = Slate100, fontSize = 26.sp, lineHeight = 32.sp,
                fontFamily = Inter, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp,
                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 20.dp),
            )
            HorizontalDivider(color = SettingsHairline)
            Spacer(Modifier.height(10.dp))
            categories.forEachIndexed { position, category ->
                SidebarItem(category, position == index) { onSelect(position) }
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight().drawBehind {
            drawRect(
                color = Color.White.copy(alpha = 0.06f),
                topLeft = Offset.Zero,
                size = Size(1.dp.toPx(), size.height),
            )
        }) {
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 760.dp).fillMaxWidth(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item("header") { SettingsHeader(categories[index].title, null) }
                item("card") { SettingsRows(categories[index].items, state, wide = true, onValue = onValue, onAction = actionOf(data)) }
                    }
        }
    }
}

@Composable
private fun SettingsTitle(title: String) {
    Text(
        title, color = Slate100, fontSize = 24.sp, lineHeight = 30.sp, fontFamily = Rubik,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp,
    )
}

@Composable
private fun SettingsHeader(title: String, onBack: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (onBack != null) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(Res.drawable.ic_back), contentDescription = null, tint = Slate100,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Text(
            title, color = Slate100, fontSize = 24.sp, lineHeight = 30.sp, fontFamily = Inter,
            fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp, maxLines = 1,
        )
    }
}

@Composable
private fun SidebarItem(category: SettingCategory, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
            .background(
                if (selected) Color.White.copy(alpha = 0.06f) else Color.Transparent,
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(category.icon), contentDescription = null,
            tint = if (selected) Slate100 else Slate400, modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            category.title, color = if (selected) Slate100 else Slate400, fontSize = 15.sp,
            fontFamily = Inter, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

// the floating tab bar is 64dp tall and lifts 8dp off the system bar, which the list already
// pads for: this keeps the last card clear of it
private val NavClearance = 72.dp + 24.dp

// phone root: cards grouped by category id
private val RootGroups = listOf(
    listOf("playback", "subtitles"),
    listOf("library"),
    listOf("providers"),
    listOf("about"),
)

@Composable
private fun settingsCategories(data: YouData): List<SettingCategory> = listOf(
    playbackCategory(),
    subtitlesCategory(),
    libraryCategory(),
    providersCategory(data),
    aboutCategory(data),
)

@Composable
private fun playbackCategory() = SettingCategory(
    id = "playback",
    icon = Res.drawable.ic_cat_playback,
    title = stringResource(Res.string.you_section_playback),
    blurb = stringResource(Res.string.you_section_playback_blurb),
    items = listOf(
        SettingItem.Value(
            key = "quality",
            title = stringResource(Res.string.you_playback_quality),
            note = stringResource(Res.string.you_playback_quality_note),
            value = stringResource(Res.string.you_not_built_yet),
        ),
        SettingItem.Toggle(
            key = "autoplay",
            title = stringResource(Res.string.you_playback_autoplay),
            note = stringResource(Res.string.you_playback_autoplay_note),
            on = true,
        ),
        SettingItem.Toggle(
            key = "skip",
            title = stringResource(Res.string.you_playback_skip),
            note = stringResource(Res.string.you_playback_skip_note),
            on = true,
        ),
        SettingItem.Value(
            key = "audio",
            title = stringResource(Res.string.you_playback_audio),
            note = stringResource(Res.string.you_playback_audio_note),
            value = stringResource(Res.string.you_not_built_yet),
        ),
        SettingItem.Value(
            key = "datasaver",
            title = stringResource(Res.string.you_playback_data_saver),
            note = stringResource(Res.string.you_playback_data_saver_note),
            value = stringResource(Res.string.you_not_built_yet),
        ),
    ),
)

@Composable
private fun subtitlesCategory() = SettingCategory(
    id = "subtitles",
    icon = Res.drawable.ic_cat_subtitles,
    title = stringResource(Res.string.you_section_subtitles),
    blurb = stringResource(Res.string.you_section_subtitles_blurb),
    items = listOf(
        SettingItem.Toggle(
            key = "subs",
            title = stringResource(Res.string.you_subs_on),
            note = stringResource(Res.string.you_subs_on_note),
            on = true,
        ),
        SettingItem.Value(
            key = "subslang",
            title = stringResource(Res.string.you_subs_language),
            note = stringResource(Res.string.you_subs_language_note),
            value = stringResource(Res.string.you_not_built_yet),
        ),
        SettingItem.Segments(
            key = "subssize",
            title = stringResource(Res.string.you_subs_size),
            note = stringResource(Res.string.you_subs_size_note),
            options = listOf(
                stringResource(Res.string.you_subs_size_small),
                stringResource(Res.string.you_subs_size_medium),
                stringResource(Res.string.you_subs_size_large),
            ),
            selected = 1,
        ),
        SettingItem.Segments(
            key = "subsbg",
            title = stringResource(Res.string.you_subs_background),
            note = stringResource(Res.string.you_subs_bg_note),
            options = listOf(
                stringResource(Res.string.you_subs_bg_off),
                stringResource(Res.string.you_subs_bg_dim),
                stringResource(Res.string.you_subs_bg_solid),
            ),
            selected = 1,
        ),
        SettingItem.Value(
            key = "subsai",
            title = stringResource(Res.string.you_subs_ai),
            note = stringResource(Res.string.you_subs_ai_note),
            value = stringResource(Res.string.you_not_built_yet),
        ),
    ),
)

@Composable
private fun libraryCategory() = SettingCategory(
    id = "library",
    icon = Res.drawable.ic_cat_library,
    title = stringResource(Res.string.you_section_library),
    blurb = stringResource(Res.string.you_section_library_blurb),
    items = listOf(
        SettingItem.Toggle(
            key = "continue",
            title = stringResource(Res.string.you_library_continue),
            note = stringResource(Res.string.you_library_continue_note),
            on = true,
        ),
        SettingItem.Toggle(
            key = "savehistory",
            title = stringResource(Res.string.you_library_history),
            note = stringResource(Res.string.you_library_history_note),
            on = true,
        ),
        SettingItem.Value(
            key = "watchlist",
            title = stringResource(Res.string.you_library_watchlist),
            note = stringResource(Res.string.you_library_watchlist_note),
            value = stringResource(Res.string.you_not_built_yet),
        ),
        SettingItem.Action(
            key = "clearhistory",
            title = stringResource(Res.string.you_library_clear),
            note = stringResource(Res.string.you_library_clear_note),
            destructive = true,
        ),
    ),
)

@Composable
private fun providersCategory(data: YouData) = SettingCategory(
    id = "providers",
    icon = Res.drawable.ic_cat_providers,
    title = stringResource(Res.string.you_section_providers),
    blurb = stringResource(Res.string.you_section_providers_blurb),
    items = listOf(
        SettingItem.Value(
            key = "health",
            title = stringResource(Res.string.you_providers_health),
            note = stringResource(Res.string.you_providers_health_note),
            value = data.providers,
        ),
        SettingItem.Toggle(
            key = "autorefresh",
            title = stringResource(Res.string.you_providers_refresh),
            note = stringResource(Res.string.you_providers_refresh_note),
            on = true,
        ),
        SettingItem.Toggle(
            key = "share",
            title = stringResource(Res.string.you_providers_share),
            note = stringResource(Res.string.you_providers_share_note),
            on = true,
        ),
    ),
)

@Composable
private fun aboutCategory(data: YouData) = SettingCategory(
    id = "about",
    icon = Res.drawable.ic_cat_about,
    title = stringResource(Res.string.you_section_about),
    blurb = stringResource(Res.string.you_section_about_blurb),
    items = listOf(
        SettingItem.Value(
            key = "version",
            title = stringResource(Res.string.you_about_version),
            value = data.version,
        ),
        SettingItem.Action(
            key = "updates",
            title = stringResource(Res.string.you_about_updates),
            note = stringResource(Res.string.you_about_updates_note),
        ),
        SettingItem.Action(
            key = "licences",
            title = stringResource(Res.string.you_about_licences),
            note = stringResource(Res.string.you_about_licences_note),
        ),
        SettingItem.Action(
            key = "privacy",
            title = stringResource(Res.string.you_about_privacy),
            note = stringResource(Res.string.you_about_privacy_note),
        ),
        SettingItem.Action(
            key = "feedback",
            title = stringResource(Res.string.you_about_feedback),
            note = stringResource(Res.string.you_about_feedback_note),
        ),
    ),
)

private val SettingsStateSaver: Saver<SnapshotStateMap<String, Int>, ArrayList<String>> = Saver(
    save = { map -> ArrayList(map.map { "${it.key}=${it.value}" }) },
    restore = { saved ->
        mutableStateMapOf<String, Int>().apply {
            saved.forEach { entry ->
                val at = entry.indexOf('=')
                if (at > 0) put(entry.substring(0, at), entry.substring(at + 1).toIntOrNull() ?: 0)
            }
        }
    },
)

private fun defaultSettings(categories: List<SettingCategory>): SnapshotStateMap<String, Int> =
    mutableStateMapOf<String, Int>().apply {
        categories.forEach { category ->
            category.items.forEach { item ->
                when (item) {
                    is SettingItem.Toggle -> put(item.key, if (item.on) 1 else 0)
                    is SettingItem.Segments -> put(item.key, item.selected)
                    else -> Unit
                }
            }
        }
    }
