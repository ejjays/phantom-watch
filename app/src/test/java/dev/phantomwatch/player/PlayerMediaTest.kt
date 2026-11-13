package dev.phantomwatch.player

import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
import dev.phantomwatch.data.Stream
import dev.phantomwatch.data.Sub
import dev.phantomwatch.subs.SubtitleCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerMediaTest {
    private fun cue(text: String) = SubtitleCue(0, 1000, text)

    @Test
    fun detectsJapanese() {
        assertEquals("ja", detectLang(listOf(cue("こんにちは"), cue("ありがとう"), cue("你好"))))
    }

    @Test
    fun detectsEnglish() {
        assertEquals("en", detectLang(listOf(cue("hello there"), cue("general kenobi"))))
    }

    @Test
    fun ignoresEmptyCues() {
        assertEquals(null, detectLang(emptyList()))
        assertEquals(null, detectLang(listOf(cue("   "))))
    }

    @Test
    fun explicitHeadersWinOverReferer() {
        val s = Stream("https://a/x.m3u8", true, emptyList(), "https://ref/", null, headers = mapOf("User-Agent" to "x"))
        val h = mediaHeaders(s)
        assertEquals("x", h["User-Agent"])
        assertFalse(h.containsKey("Referer"))
    }

    @Test
    fun refererBecomesRefererAndOrigin() {
        val s = Stream("https://a/x.m3u8", true, emptyList(), "https://host/path", null)
        val h = mediaHeaders(s)
        assertEquals("https://host/path", h["Referer"])
        assertEquals("https://host", h["Origin"])
    }

    @Test
    fun authBecomesAuthorizationHeader() {
        val s = Stream("https://a/x.m3u8", true, emptyList(), "", null, auth = "Bearer t")
        assertEquals("Bearer t", mediaHeaders(s)["Authorization"])
    }

    @Test
    fun fileStreamsGetNoReferer() {
        val s = Stream("file:///data/x.mp4", false, emptyList(), "https://host/", null)
        assertFalse(mediaHeaders(s).containsKey("Referer"))
    }

    @Test
    fun ccOffDisablesTextTrackType() {
        val off = TrackSelectionParameters.DEFAULT.buildUpon().textTrack(true).build()
        assertTrue(off.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
        assertFalse(off.disabledTrackTypes.contains(C.TRACK_TYPE_AUDIO))

        val on = off.buildUpon().textTrack(false).build()
        assertFalse(on.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
    }

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
    fun playerClockFormatsMinutesAndHours() {
        assertEquals("0:00", playerClock(0))
        assertEquals("1:05", playerClock(65000))
        assertEquals("1:02:03", playerClock(3723000))
    }

    @Test
    fun onlyEnglishProviderSubsFillTheGap() {
        assertFalse(hasEnglishSubs(listOf(Sub("Deutsch", "de"), Sub("Français", "fr"))))
        assertTrue(hasEnglishSubs(listOf(Sub("Deutsch", "de"), Sub("English", "en"))))
    }

    @Test
    fun loveSubsNotTrustedForOsSkip() {
        // love subs: not reliable, so OS should still run
        val loveSubs = listOf(Sub("English", "https://cdn/en.vtt", "love"))
        assertFalse(hasReliableEnSubs(loveSubs))
        // hianime subs: trusted, so OS can skip
        val hianimeSubs = listOf(Sub("English", "https://cdn/en.vtt", "hianime"))
        assertTrue(hasReliableEnSubs(hianimeSubs))
        // mixed: reliable source wins
        val mixed = listOf(Sub("English", "https://cdn/en.vtt", "love"), Sub("English", "https://cdn2/en.vtt", "paradise"))
        assertTrue(hasReliableEnSubs(mixed))
    }

    @Test
    fun loadControlConfiguresBufferDurations() {
        val lc = loadControl(90_000)
        // a -10s double tap must land inside the retained window, or it re-fetches from the CDN
        assertEquals(30_000_000L, lc.getBackBufferDurationUs(androidx.media3.exoplayer.analytics.PlayerId.UNSET))
        assertTrue(lc.retainBackBufferFromKeyframe(androidx.media3.exoplayer.analytics.PlayerId.UNSET))
    }
}
