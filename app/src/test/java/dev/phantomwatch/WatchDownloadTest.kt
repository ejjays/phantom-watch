package dev.phantomwatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchDownloadTest {
    @Test
    fun picksEnglishSubtitle() {
        val subs = listOf(
            Sub("Spanish", "https://example.com/es.vtt"),
            Sub("English", "https://example.com/en.vtt"),
        )
        assertEquals("https://example.com/en.vtt", WatchDownload.englishSub(subs)?.url)
    }

    @Test
    fun ignoresNonEnglishSubtitles() {
        assertNull(WatchDownload.englishSub(listOf(Sub("Spanish", "https://example.com/es.vtt"))))
    }

    @Test
    fun namesEpisodeAndSanitizesTitle() {
        assertEquals(
            "Normal People S01E01",
            WatchDownload.baseName("Normal/People", "1", "1"),
        )
    }

    @Test
    fun onlyHttpStreamsAreDownloadable() {
        assertTrue(WatchDownload.isHttpUrl("https://example.com/master.m3u8"))
        assertTrue(!WatchDownload.isHttpUrl("file:///tmp/movie.mp4"))
    }
}
