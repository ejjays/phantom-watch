package dev.phantomwatch.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailTest {
    @Test
    fun airFormatsIsoDate() {
        assertEquals("Jan. 15, 2026", airLabel("2026-01-15"))
        assertEquals("Dec. 26, 2026", airLabel("2026-12-26"))
        assertEquals("Oct. 27, 1999", airLabel("1999-10-27"))
    }

    @Test
    fun airPassesGarbageThrough() {
        assertEquals("", airLabel(""))
        assertEquals("soon", airLabel("soon"))
        assertEquals("2026-13-40", airLabel("2026-13-40"))
    }
}
