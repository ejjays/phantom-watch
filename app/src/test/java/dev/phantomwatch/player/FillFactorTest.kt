package dev.phantomwatch.player

import org.junit.Assert.assertEquals
import org.junit.Test

class FillFactorTest {
    @Test
    fun cinemascopeOnAWidePhoneNeedsALittle() {
        assertEquals(1.075f, fillFactor(2700f, 1224f, 2.3704f), 0.005f)
    }

    @Test
    fun sixteenNineOnAWidePhoneNeedsAQuarter() {
        assertEquals(1.241f, fillFactor(2700f, 1224f, 16f / 9f), 0.005f)
    }

    @Test
    fun aPictureThatAlreadyCoversIsLeftAlone() {
        assertEquals(1f, fillFactor(1920f, 1080f, 16f / 9f), 0.001f)
    }

    @Test
    fun cinemascopeOnASixteenNineStage() {
        assertEquals(1.35f, fillFactor(1920f, 1080f, 2.4f), 0.005f)
    }

    @Test
    fun degenerateInputsDoNotZoom() {
        assertEquals(1f, fillFactor(0f, 0f, 0f), 0.001f)
        assertEquals(1f, fillFactor(100f, 100f, 0f), 0.001f)
    }
}
