package dev.phantomwatch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {
    @Test
    fun sizeOrderIsSmallerThanMediumSmallerThanLarge() {
        val small = Settings.sizeScale(Settings.SIZE_SMALL)
        val medium = Settings.sizeScale(Settings.SIZE_MEDIUM)
        val large = Settings.sizeScale(Settings.SIZE_LARGE)
        assertTrue(small < medium)
        assertTrue(medium < large)
    }

    @Test
    fun unknownSizeFallsBackToTheDefault() {
        assertEquals(Settings.sizeScale(Settings.SIZE_MEDIUM), Settings.sizeScale(99))
    }

    @Test
    fun mediumKeepsTheSizeThePlayerAlreadyUsed() {
        assertEquals(1f, Settings.sizeScale(Settings.SIZE_MEDIUM))
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
            assertTrue(Settings.SUBS_BASE_FRACTION * Settings.sizeScale(it) < 0.2f)
        }
    }
}
