package dev.phantomwatch.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentTextTest {
    @Test
    fun acceptsOnlyNonblankCommentsWithinClientLimit() {
        assertTrue(Social.validCommentBody("hello"))
        assertTrue(Social.validCommentBody("x".repeat(500)))
        assertFalse(Social.validCommentBody("  \n\t"))
        assertFalse(Social.validCommentBody("x".repeat(501)))
    }

    @Test
    fun reportReasonsMatchDatabaseEnum() {
        assertEquals(setOf("spam", "harassment", "spoiler", "other"), Social.ReportReason.entries.map { it.wire }.toSet())
    }
}
