package dev.phantomwatch.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackWatchdogTest {
    @Test
    fun aResumeNearTheEndCountsAsUnplayable() {
        assertTrue(resumeIsPastTail(started = false, positionMs = 1_390_000, durationMs = 1_400_000))
    }

    @Test
    fun aResumeMidFileIsJustSlow() {
        assertFalse(resumeIsPastTail(started = false, positionMs = 700_000, durationMs = 1_400_000))
    }

    @Test
    fun playbackThatAlreadyStartedIsNeverRewound() {
        assertFalse(resumeIsPastTail(started = true, positionMs = 1_390_000, durationMs = 1_400_000))
    }

    @Test
    fun anUnknownDurationIsNotATail() {
        assertFalse(resumeIsPastTail(started = false, positionMs = 1_390_000, durationMs = 0))
    }
}
