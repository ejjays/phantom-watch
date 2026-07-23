package dev.phantomwatch.player

import dev.phantomwatch.data.Sub
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhantomDownloadTest {
    @Test
    fun picksEnglishSubtitle() {
        val subs = listOf(
            Sub("Spanish", "https://example.com/es.vtt"),
            Sub("English", "https://example.com/en.vtt"),
        )
        assertEquals("https://example.com/en.vtt", PhantomDownload.englishSub(subs)?.url)
    }

    @Test
    fun ignoresNonEnglishSubtitles() {
        assertNull(PhantomDownload.englishSub(listOf(Sub("Spanish", "https://example.com/es.vtt"))))
    }

    @Test
    fun namesEpisodeAndSanitizesTitle() {
        assertEquals(
            "Normal People S01E01",
            PhantomDownload.baseName("Normal/People", "1", "1"),
        )
    }

    @Test
    fun onlyHttpStreamsAreDownloadable() {
        assertTrue(PhantomDownload.isHttpUrl("https://example.com/master.m3u8"))
        assertTrue(!PhantomDownload.isHttpUrl("file:///tmp/movie.mp4"))
    }
}
