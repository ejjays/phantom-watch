package dev.phantomwatch.subs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OsSubsTest {
    @Test
    fun picksTheMostDownloadedFile() {
        val body = """
            {"data":[
              {"attributes":{"download_count":3,"files":[{"file_id":11}]}},
              {"attributes":{"download_count":9,"files":[{"file_id":22}]}}
            ]}
        """.trimIndent()
        assertEquals(22L, OsSubs.pickFileId(body))
    }

    @Test
    fun ignoresEntriesWithoutFiles() {
        val body = """{"data":[{"attributes":{"download_count":9,"files":[]}}]}"""
        assertNull(OsSubs.pickFileId(body))
    }

    @Test
    fun survivesGarbage() {
        assertNull(OsSubs.pickFileId("not json"))
        assertNull(OsSubs.pickFileId("{}"))
    }

    @Test
    fun readsTheDownloadLink() {
        assertEquals("https://dl/x.srt", OsSubs.linkOf("""{"link":"https://dl/x.srt"}"""))
        assertNull(OsSubs.linkOf("""{"link":"ftp://x"}"""))
        assertNull(OsSubs.linkOf("nope"))
    }

    @Test
    fun osHashMatchesReferenceVector() {
        val head = ByteArray(65536) { i -> (i % 256).toByte() }
        val tail = ByteArray(65536) { i -> ((i * 7) % 256).toByte() }
        assertEquals("202020207a627600", OsSubs.oshash(head, tail, 1514370560L))
    }

    @Test
    fun osHashDiffersOnDifferentBytes() {
        val head = ByteArray(65536) { 1 }
        val tail = ByteArray(65536) { 2 }
        val a = OsSubs.oshash(head, tail, 1000L)
        tail[0] = 3
        val b = OsSubs.oshash(head, tail, 1000L)
        assertTrue(a != b)
        assertEquals(16, a.length)
    }
}
