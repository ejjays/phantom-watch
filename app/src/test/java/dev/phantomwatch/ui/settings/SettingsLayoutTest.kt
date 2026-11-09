package dev.phantomwatch.ui.settings

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLayoutTest {
    @Test
    fun paneBreakpointsMatchWindowClasses() {
        assertEquals(SettingsPane.Compact, settingsPane(320.dp))
        assertEquals(SettingsPane.Compact, settingsPane(599.dp))
        assertEquals(SettingsPane.Medium, settingsPane(600.dp))
        assertEquals(SettingsPane.Medium, settingsPane(839.dp))
        assertEquals(SettingsPane.Expanded, settingsPane(840.dp))
        assertEquals(SettingsPane.Expanded, settingsPane(1199.dp))
        assertEquals(SettingsPane.Large, settingsPane(1200.dp))
    }

    @Test
    fun onlyExpandedWindowsShowTheRail() {
        assertFalse(isTwoPane(SettingsPane.Compact))
        assertFalse(isTwoPane(SettingsPane.Medium))
        assertTrue(isTwoPane(SettingsPane.Expanded))
        assertTrue(isTwoPane(SettingsPane.Large))
    }

    @Test
    fun compactUsesTheNarrowGutter() {
        assertEquals(20.dp, settingsGutter(SettingsPane.Compact))
        assertEquals(32.dp, settingsGutter(SettingsPane.Medium))
        assertEquals(32.dp, settingsGutter(SettingsPane.Expanded))
        assertEquals(32.dp, settingsGutter(SettingsPane.Large))
    }

    @Test
    fun largeWindowGetsAWiderRail() {
        assertEquals(300.dp, settingsRailWidth(SettingsPane.Expanded))
        assertEquals(320.dp, settingsRailWidth(SettingsPane.Large))
    }
}
