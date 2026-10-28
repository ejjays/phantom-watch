package dev.phantomwatch.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoBarsTest {
    private val w = 48
    private val h = 48

    private fun frame(top: Int, bottom: Int): IntArray =
        IntArray(w * h) { i -> if (i / w in top until h - bottom) 0xFF808080.toInt() else 0 }

    @Test
    fun measuresSymmetricPadding() {
        assertEquals(0.25f, barFraction(frame(6, 6), w, h)!!, 0.001f)
    }

    @Test
    fun anUnpaddedFrameHasNoPadding() {
        assertEquals(0f, barFraction(frame(0, 0), w, h)!!, 0.001f)
    }

    @Test
    fun aBlackFrameIsNotPadding() {
        assertNull(barFraction(IntArray(w * h), w, h))
    }

    @Test
    fun skewedPaddingIsIgnored() {
        assertEquals(0f, barFraction(frame(2, 9), w, h)!!, 0.001f)
    }
}
