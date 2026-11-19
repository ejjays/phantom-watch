package dev.phantomwatch.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailTest {
    @Test
    fun airFormatsRelativeDate() {
        val today = java.time.Instant.now().atZone(java.time.ZoneOffset.UTC).toLocalDate()
        assertEquals("today", airLabel(today.toString()))
        assertEquals("3 days ago", airLabel(today.minusDays(3).toString()))
        assertEquals("in 3 days", airLabel(today.plusDays(3).toString()))
        assertEquals("1 month ago", airLabel(today.minusDays(40).toString()))
    }

    @Test
    fun airPassesGarbageThrough() {
        assertEquals("", airLabel(""))
        assertEquals("soon", airLabel("soon"))
        assertEquals("2026-13-40", airLabel("2026-13-40"))
    }
}
