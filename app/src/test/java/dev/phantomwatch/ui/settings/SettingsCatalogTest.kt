package dev.phantomwatch.ui.settings

import dev.phantomwatch.data.SettingsKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCatalogTest {
    private val catalog = settingsCatalog(version = "1.0 (1)")

    private fun keys(): List<String> = catalog.flatMap { it.items }.mapNotNull {
        when (it) {
            is SettingItem.Toggle -> it.key
            is SettingItem.Choice -> it.key
            else -> null
        }
    }

    @Test
    fun everyControlWritesAKnownKey() {
        val keys = keys()
        assertTrue(keys.isNotEmpty())
        assertEquals(emptySet<String>(), keys.toSet() - SettingsKeys.ALL)
    }

    @Test
    fun everyKnownKeyHasAControl() {
        assertEquals(SettingsKeys.ALL, keys().toSet())
    }

    @Test
    fun noKeyIsShownTwice() {
        val keys = keys()
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun categoriesKeepTheirOrder() {
        assertEquals(
            listOf("playback", "subtitles", "library", "about"),
            catalog.map { it.id },
        )
    }

    @Test
    fun versionReachesTheScreen() {
        val values = catalog.flatMap { it.items }.filterIsInstance<SettingItem.Value>()
        assertTrue(values.any { it.value == "1.0 (1)" })
    }
}
