package dev.phantomwatch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DubTest {
    private val subCut = Sub("English", "https://s/sub-cut.vtt", "hianime")
    private val dub = Dub("English", "https://v/dub.m3u8", true, "https://megaplay.buzz/")

    @Test
    fun dubWithoutOwnSubsPlaysWithoutSubs() {
        val next = Stream("https://v/sub.m3u8", true, listOf(subCut), "", null).withDub(dub)
        assertEquals("https://v/dub.m3u8", next.url)
        assertEquals("English", next.dub)
        assertTrue(next.subs.isEmpty())
    }

    @Test
    fun dubPlaysItsOwnSubs() {
        val own = Sub("English", "https://s/dub.vtt", "hianime")
        val next = Stream("https://v/sub.m3u8", true, listOf(subCut), "", null)
            .withDub(dub.copy(subs = listOf(own)))
        assertEquals(listOf(own), next.subs)
    }

    @Test
    fun baseChoiceGoesBackToTheSubCut() {
        val base = Dub("Sub · HiAnime", "https://v/sub.m3u8", true, "", subs = listOf(subCut), base = true)
        val next = Stream("https://v/dub.m3u8", true, emptyList(), "", null).withDub(base)
        assertNull(next.dub)
        assertEquals(listOf(subCut), next.subs)
        assertEquals("https://v/sub.m3u8", next.url)
    }
}
