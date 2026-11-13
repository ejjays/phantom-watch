package dev.phantomwatch.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.phantomwatch.data.Account
import dev.phantomwatch.ui.PlatformBackHandler
import dev.phantomwatch.ui.screenWidthDp

data class SettingsData(
    val version: String,
    val settings: Map<String, Int>,
    val updateVersion: String? = null,
    val account: Account? = null,
    val accountBusy: Boolean = false,
    val accountError: String? = null,
    val googleReady: Boolean = false,
    val onValue: (String, Int) -> Unit,
    val onAction: (SettingsAction) -> Unit,
    val onSignIn: () -> Unit = {},
    val onSignOut: () -> Unit = {},
)

@Composable
fun SettingsScreen(data: SettingsData) {
    SettingsTheme {
        val pane = settingsPane(screenWidthDp())
        val categories = remember(data.version, data.updateVersion) {
            settingsCatalog(data.version, data.updateVersion)
        }
        val state = rememberSaveable(saver = SettingsStateSaver) {
            defaultSettings(categories).apply { putAll(data.settings) }
        }
        var open by rememberSaveable { mutableIntStateOf(if (isTwoPane(pane)) 0 else OverviewIndex) }
        LaunchedEffect(data.settings) {
            data.settings.forEach { (key, value) -> state[key] = value }
        }
        LaunchedEffect(pane) {
            if (isTwoPane(pane) && open == OverviewIndex) open = 0
        }
        val onValue: (String, Int) -> Unit = { key, value ->
            state[key] = value
            data.onValue(key, value)
        }

        Box(Modifier.fillMaxSize().background(SettingsCanvas)) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                if (isTwoPane(pane)) {
                    Row(Modifier.fillMaxSize()) {
                        SettingsRail(
                            categories = categories,
                            selected = open,
                            version = data.version,
                            width = settingsRailWidth(pane),
                            account = data.account,
                            onSelect = { open = it },
                        )
                        Box(Modifier.width(1.dp).fillMaxHeight().background(SettingsHairline))
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            SettingsDetail(
                                category = categories.getOrNull(open) ?: categories.first(),
                                state = state,
                                gutter = settingsGutter(pane),
                                showBack = false,
                                onBack = {},
                                onValue = onValue,
                                onAction = data.onAction,
                            )
                        }
                    }
                } else {
                    PlatformBackHandler(enabled = open >= 0) { open = OverviewIndex }
                    val holder = rememberSaveableStateHolder()
                    AnimatedContent(
                        targetState = open,
                        transitionSpec = {
                            val forward = targetState > initialState
                            val enter = fadeIn(tween(180)) +
                                slideInHorizontally(tween(220)) { if (forward) it / 20 else -it / 20 }
                            val exit = fadeOut(tween(140)) +
                                slideOutHorizontally(tween(220)) { if (forward) -it / 24 else it / 24 }
                            enter togetherWith exit
                        },
                        label = "settings",
                    ) { index ->
                        holder.SaveableStateProvider(index) {
                            val category = categories.getOrNull(index)
                            if (category != null) {
                                SettingsDetail(
                                    category = category,
                                    state = state,
                                    gutter = settingsGutter(pane),
                                    showBack = true,
                                    onBack = { open = OverviewIndex },
                                    onValue = onValue,
                                    onAction = data.onAction,
                                )
                            } else {
                                SettingsRoot(
                                    categories = categories,
                                    gutter = settingsGutter(pane),
                                    onOpen = { open = it },
                                    data = data,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

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
                    is SettingItem.Toggle -> put(item.key, if (item.default) 1 else 0)
                    is SettingItem.Choice -> put(item.key, item.default)
                    else -> Unit
                }
            }
        }
    }
