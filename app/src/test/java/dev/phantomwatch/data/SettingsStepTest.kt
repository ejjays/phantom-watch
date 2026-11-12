package dev.phantomwatch.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsStepTest {
    @Test
    fun pickerIndexMapsToItsLabel() {
        assertEquals(listOf(5, 10, 15, 30), (0..3).map { Settings.seekSeconds(it) })
        assertEquals(listOf(5, 10, 15), (0..2).map { Settings.nextSeconds(it) })
    }

    @Test
    fun aStoredStepFromAnotherBuildFallsBackToTen() {
        assertEquals(10, Settings.seekSeconds(15))
        assertEquals(10, Settings.nextSeconds(30))
    }
}
