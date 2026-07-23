package dev.phantomwatch.subs

import dev.phantomwatch.data.Sub
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitlesTest {
    @Test
    fun srtParsesCues() {
        val srt = "1\n00:00:01,000 --> 00:00:02,000\nHello\n\n2\n00:00:03,500 --> 00:00:04,000\nWorld\n"
        val cues = Subtitles.parseCues(srt)
        assertEquals(2, cues.size)
        assertEquals("Hello", cues[0].text)
        assertEquals(1000L, cues[0].start)
        assertEquals(3500L, cues[1].start)
    }

    @Test
    fun assParsesDialogue() {
        val ass = "[Script Info]\n[V4+ Styles]\n[Events]\n" +
            "Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,First line\n" +
            "Dialogue: 0,0:00:03.00,0:00:04.00,Default,,0,0,0,,Second {\\i1}line\n"
        val cues = Subtitles.parseAss(ass)
        assertEquals(2, cues.size)
        assertEquals("First line", cues[0].text)
        assertTrue(cues[1].text.contains("Second"))
        assertTrue(!cues[1].text.contains("{\\i1}"))
    }

    @Test
    fun cuesRenderToWebVtt() {
        val cues = listOf(SubtitleCue(1000L, 2000L, "Hi"))
        val vtt = Subtitles.renderCues(cues)
        assertTrue(vtt.startsWith("WEBVTT"))
        assertTrue(vtt.contains("Hi"))
    }

    @Test
    fun englishFirstPutsEnglishOnTop() {
        val subs = listOf(Sub("Spanish", "a"), Sub("English SDH", "b"), Sub("French", "c"))
        assertEquals(listOf("b", "a", "c"), Subtitles.englishFirst(subs).map { it.url })
    }

    @Test
    fun shiftSubFileMovesCues() {
        val dir = java.nio.file.Files.createTempDirectory("phantom-subs").toFile()
        val f = Subtitles.shiftSubFile("1\n00:00:01,000 --> 00:00:02,000\nHi\n", 1000, dir)
        assertEquals(2000L, Subtitles.parseCues(f!!.readText())[0].start)
    }
}
