package dev.phantomwatch.subs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiSubsTest {
    @Test
    fun parsesNumberedLines() {
        val text = """
            1. First line
            2) Second line
            **3** Third line
        """.trimIndent()
        assertEquals(listOf("First line", "Second line", "Third line"), AiSubs.parseNumberedLines(text, 3))
    }

    @Test
    fun rejectsAPartialAnswer() {
        assertNull(AiSubs.parseNumberedLines("1. only one", 3))
    }
}
