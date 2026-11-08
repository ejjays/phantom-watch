package dev.phantomwatch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {
    @Test
    fun sizeOrderIsSmallerThanMediumSmallerThanLarge() {
        val small = Settings.sizeFraction(Settings.SIZE_SMALL)
        val medium = Settings.sizeFraction(Settings.SIZE_MEDIUM)
        val large = Settings.sizeFraction(Settings.SIZE_LARGE)
        assertTrue(small < medium)
        assertTrue(medium < large)
    }

    @Test
    fun unknownSizeFallsBackToTheDefault() {
        assertEquals(Settings.sizeFraction(Settings.SIZE_MEDIUM), Settings.sizeFraction(99))
    }

    @Test
    fun turningTheBackgroundOffYieldsNoPaint() {
        assertEquals(0f, Settings.bgAlpha(Settings.BG_OFF))
    }

    @Test
    fun solidIsMoreOpaqueThanDim() {
        assertTrue(Settings.bgAlpha(Settings.BG_SOLID) > Settings.bgAlpha(Settings.BG_DIM))
    }

    @Test
    fun everySizeNominalStaysUnderAFifthOfThePicture() {
        listOf(Settings.SIZE_SMALL, Settings.SIZE_MEDIUM, Settings.SIZE_LARGE).forEach {
            assertTrue(Settings.sizeFraction(it) < 0.2f)
        }
    }
}
