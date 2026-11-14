package dev.phantomwatch.ui.settings

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class SettingsPane { Compact, Medium, Expanded, Large }

const val OverviewIndex = -1

fun settingsPane(width: Dp): SettingsPane = when {
    width < 600.dp -> SettingsPane.Compact
    width < 840.dp -> SettingsPane.Medium
    width < 1200.dp -> SettingsPane.Expanded
    else -> SettingsPane.Large
}

fun isTwoPane(pane: SettingsPane): Boolean =
    pane == SettingsPane.Expanded || pane == SettingsPane.Large

fun settingsRailWidth(pane: SettingsPane): Dp = if (pane == SettingsPane.Large) 280.dp else 260.dp

fun settingsGutter(pane: SettingsPane): Dp = if (pane == SettingsPane.Compact) 20.dp else 32.dp

val SettingsContentMax = 640.dp

// floating tab bar is 64dp tall and lifts 8dp over the nav bar screen already pads
val SettingsNavClearance = 96.dp
