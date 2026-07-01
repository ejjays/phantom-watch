package dev.phantomwatch

import dev.phantomwatch.ui.playerVideoShift
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerLayoutTest {
    // 360x800dp portrait at density 3, 24dp status bar -> 776dp stage
    private val stage = 2328f
    private val video169 = 607.5f
    private val video43 = 810f
    private val panel = stage - video169

    private fun videoTop(shift: Float, video: Float) = (stage - video) / 2f - shift / 2f

    private fun videoCenter(shift: Float) = stage / 2f - shift / 2f

    @Test
    fun sixteenNineStartsAtStageTop() {
        val shift = playerVideoShift(panel, stage, video169)
        assertEquals(0f, videoTop(shift, video169), 0.01f)
    }

    @Test
    fun sixteenNineTracksTheSheet() {
        val sheet = panel / 2f
        val shift = playerVideoShift(panel - sheet, stage, video169)
        assertEquals((video169 + sheet) / 2f, videoCenter(shift), 0.01f)
    }

    @Test
    fun tallVideoNeverOverflowsTheTop() {
        val shift = playerVideoShift(panel, stage, video43)
        assertEquals(0f, videoTop(shift, video43), 0.01f)
    }

    @Test
    fun theaterCentresInTheFullStage() {
        val shift = playerVideoShift(0f, stage, video169)
        assertEquals(0f, shift, 0.01f)
        assertEquals(stage / 2f, videoCenter(shift), 0.01f)
    }

    @Test
    fun overscrollDoesNotPushTheVideoDown() {
        assertEquals(0f, playerVideoShift(-50f, stage, video169), 0.01f)
    }
}
