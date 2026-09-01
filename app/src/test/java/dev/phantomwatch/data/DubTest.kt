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

    // a dub with no en subs must not inherit the sub cut's flag, or OpenSubtitles is skipped for
    // a reason that stopped being true the moment the cut changed
    @Test
    fun aDubBringsItsOwnEnSubsFlag() {
        val cut = Stream("https://v/sub.m3u8", true, emptyList(), "", null, enSubs = true)
        assertEquals(false, cut.withDub(dub).enSubs)
        assertTrue(cut.withDub(dub.copy(enSubs = true)).enSubs)
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
