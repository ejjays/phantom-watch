package dev.phantomwatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeTest {
    @Test
    fun belowTenSecondsIsCleared() {
        assertTrue(Resume.shouldClear(9_999, 0))
        assertFalse(Resume.shouldClear(10_000, 0))
    }

    @Test
    fun nearTheEndIsCleared() {
        assertTrue(Resume.shouldClear(600_000, 600_000))
        assertTrue(Resume.shouldClear(571_000, 600_000))
        assertFalse(Resume.shouldClear(569_000, 600_000))
    }

    @Test
    fun unknownDurationNeverLooksFinished() {
        assertFalse(Resume.shouldClear(600_000, 0))
        assertFalse(Resume.shouldClear(600_000, -1))
    }

    @Test
    fun startAtZeroWhenFinished() {
        assertEquals(0L, Resume.startAt(600_000, 600_000))
        assertEquals(120_000L, Resume.startAt(120_000, 600_000))
    }
}
