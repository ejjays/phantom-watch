package dev.phantomwatch

import dev.phantomwatch.ui.airLabel
import dev.phantomwatch.ui.playerClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubsTest {
    @Test
    fun rankPrefersDialogueOverSigns() {
        val names = listOf(
            "en:S&S@Tenrai-Sensei",
            "en:Dialogue@Tenrai-Sensei [Non-Honorific]",
            "en:Dialogue@Tenrai-Sensei [Honorific]",
        )
        assertEquals(1, rankEmbed(names))
    }

    @Test
    fun rankSkipsNonEnglish() {
        assertEquals(-1, rankEmbed(listOf("ja:Japanese", "ko:Korean")))
    }

    @Test
    fun rankSingleUnknownTrackScoresNothing() {
        assertEquals(-1, rankEmbed(listOf("und")))
    }

    @Test
    fun rankTakesFirstEnglishWhenNoKeywords() {
        assertEquals(0, rankEmbed(listOf("en", "en:SDH")))
    }

    @Test
    fun rankPrefersFullOverSignsSong() {
        val names = listOf("en:Signs & Songs", "en:Full Subtitles")
        assertEquals(1, rankEmbed(names))
    }

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

    @Test
    fun srtParsesCues() {
        val srt = "1\n00:00:01,000 --> 00:00:02,000\nHello\n\n2\n00:00:03,500 --> 00:00:04,000\nWorld\n"
        val cues = VidLove.parseCues(srt)
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
        val cues = VidLove.parseAss(ass)
        assertEquals(2, cues.size)
        assertEquals("First line", cues[0].text)
        assertTrue(cues[1].text.contains("Second"))
        assertTrue(!cues[1].text.contains("{\\i1}"))
    }

    @Test
    fun cuesRenderToWebVtt() {
        val cues = listOf(VidLove.Cue(1000L, 2000L, "Hi"))
        val vtt = VidLove.renderCues(cues)
        assertTrue(vtt.startsWith("WEBVTT"))
        assertTrue(vtt.contains("Hi"))
    }

    @Test
    fun playerClockFormatsMinutesAndHours() {
        assertEquals("0:00", playerClock(0))
        assertEquals("1:05", playerClock(65000))
        assertEquals("1:02:03", playerClock(3723000))
    }

    @Test
    fun englishFirstPutsEnglishOnTop() {
        val subs = listOf(Sub("Spanish", "a"), Sub("English SDH", "b"), Sub("French", "c"))
        assertEquals(listOf("b", "a", "c"), VidLove.englishFirst(subs).map { it.url })
    }

    @Test
    fun torrentsBestPrefersSeeded() {
        val risky = TEntry("risky", "1 GB", 1, "hashA")
        val solid = TEntry("solid", "1 GB", 9, "hashB")
        assertEquals(solid, Torrents.best(listOf(risky, solid)))
    }

    @Test
    fun torrentsBestSkipsDeadReleases() {
        val dead = TEntry("dead", "1 GB", 20, "hashC")
        Torrents.DeadReleases.hashes.add("hashC")
        try {
            assertNull(Torrents.best(listOf(dead)))
        } finally {
            Torrents.DeadReleases.hashes.remove("hashC")
        }
    }

    @Test
    fun shiftSubFileMovesCues() {
        val dir = java.nio.file.Files.createTempDirectory("phantom-subs").toFile()
        val f = VidLove.shiftSubFile("1\n00:00:01,000 --> 00:00:02,000\nHi\n", 1000, dir)
        assertEquals(2000L, VidLove.parseCues(f!!.readText())[0].start)
    }

    @Test
    fun subsForPrefersOwnProvider() {
        val subs = listOf(Sub("English", "a", "vidlink"), Sub("English", "b", "love"), Sub("English", "c"))
        val f = VFormat("u", false, "vidlink-1080p", 1080)
        assertEquals(listOf("a", "c"), Providers.subsFor(f, subs).map { it.url })
    }

    @Test
    fun subsForKeepsForeignSubsOffProvidersWithoutOwn() {
        val subs = listOf(Sub("English", "a", "vidlink"), Sub("English", "b", "love"))
        val f = VFormat("u", false, "vap-auto", 1080)
        assertTrue(Providers.subsFor(f, subs).isEmpty())
    }

    @Test
    fun subsForSkipsRock() {
        val f = VFormat("u", false, "rock-mp4", 1080)
        assertTrue(Providers.subsFor(f, listOf(Sub("English", "a", "love"))).isEmpty())
    }
}
