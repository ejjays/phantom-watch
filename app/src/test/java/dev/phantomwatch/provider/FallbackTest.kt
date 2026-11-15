package dev.phantomwatch.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FallbackTest {
    private fun f(url: String, label: String = "vix-auto", height: Int = 1080) =
        VideoFormat(url, true, label, height)

    @Test
    fun skipsTheStreamThatJustHung() {
        val out = Providers.nextFormat(listOf(f("https://a/x"), f("https://b/y")), "https://a/x", emptySet()) { false }
        assertEquals("https://b/y", out?.url)
    }

    @Test
    fun skipsDeadUrlsAndBlockedHosts() {
        val list = listOf(f("https://a/x"), f("https://b/y"), f("https://c/z"))
        val out = Providers.nextFormat(list, "https://z/q", setOf("https://a/x")) { it.startsWith("https://b") }
        assertEquals("https://c/z", out?.url)
    }

    @Test
    fun nullWhenNothingIsLeft() {
        assertNull(Providers.nextFormat(listOf(f("https://a/x")), "https://a/x", emptySet()) { false })
    }

    @Test
    fun aBannedHostStillPlaysWhenItIsAllThereIs() {
        val out = Providers.nextFormat(listOf(f("https://a/x")), "https://z/q", emptySet()) { true }
        assertEquals("https://a/x", out?.url)
    }

    @Test
    fun bannedFormatsSinkButKeepTheirOrder() {
        val list = listOf(f("https://a/x"), f("https://b/y"), f("https://c/z"))
        val out = Providers.rankLast(list) { it.startsWith("https://a") }
        assertEquals(listOf("https://b/y", "https://c/z", "https://a/x"), out.map { it.url })
    }

    @Test
    fun aRealRungBeatsAnAutoThatOnlyAdvertisesIt() {
        val list = listOf(f("https://vap/x", "vap-auto", 804), f("https://rock/y", "rock-1080p", 1080))
        assertEquals("https://rock/y", Providers.nextFormat(list, "https://z/q", emptySet()) { false }?.url)
    }

    @Test
    fun aRealRungBelowTheCapStillBeatsAuto() {
        val list = listOf(f("https://vap/x", "vap-auto", 804), f("https://rock/y", "rock-480p", 480))
        assertEquals("https://rock/y", Providers.nextFormat(list, "https://z/q", emptySet()) { false }?.url)
    }

    @Test
    fun autoIsStillUsedWhenNothingRealIsLeft() {
        val list = listOf(f("https://vap/x", "vap-auto", 804))
        assertEquals("https://vap/x", Providers.nextFormat(list, "https://z/q", emptySet()) { false }?.url)
    }

    @Test
    fun englishSubsBeatAHigherRealRung() {
        val auto = f("https://vix/x", "vix-auto", 720).copy(enSubs = true)
        val real = f("https://rock/y", "rock-1080p", 1080)
        assertEquals("https://vix/x", Providers.nextFormat(listOf(auto, real), "https://z/q", emptySet()) { false }?.url)
    }

    @Test
    fun aRealRungWithTheSameEnglishSubsBeatsAuto() {
        val auto = f("https://vix/x", "vix-auto", 1080).copy(enSubs = true)
        val real = f("https://vix/y", "vix-1080p", 1080).copy(enSubs = true)
        assertEquals("https://vix/y", Providers.nextFormat(listOf(auto, real), "https://z/q", emptySet()) { false }?.url)
    }

    @Test
    fun aHungRungFallsToTheSameLegsAuto() {
        val list = listOf(
            f("https://cdn/a", "hianime-1080p", 1080),
            f("https://cdn/b", "hianime-1080p", 1080),
            f("https://cdn/auto", "hianime-auto", 1080),
        )
        val out = Providers.nextFormat(list, "https://cdn/a", emptySet(), hang = true) { false }
        assertEquals("https://cdn/auto", out?.url)
    }

    @Test
    fun aHungDubRungFallsToTheDubAuto() {
        val list = listOf(
            f("https://cdn/dub-1080", "hianime-dub-1080p", 1080).copy(dub = "English"),
            f("https://cdn/sub-auto", "hianime-auto", 1080),
            f("https://cdn/dub-auto", "hianime-dub-auto", 1080).copy(dub = "English"),
        )
        val out = Providers.nextFormat(list, "https://cdn/dub-1080", emptySet(), hang = true) { false }
        assertEquals("https://cdn/dub-auto", out?.url)
    }

    @Test
    fun aHungRungWalksWhenTheAutoIsDead() {
        val list = listOf(
            f("https://cdn/a", "hianime-1080p", 1080),
            f("https://cdn/b", "hianime-1080p", 1080),
            f("https://cdn/auto", "hianime-auto", 1080),
        )
        val out = Providers.nextFormat(list, "https://cdn/a", setOf("https://cdn/auto"), hang = true) { false }
        assertEquals("https://cdn/b", out?.url)
    }

    @Test
    fun aBannedAutoDoesNotCatchAHung() {
        val list = listOf(
            f("https://cdn/a", "hianime-1080p", 1080),
            f("https://cdn/b", "hianime-1080p", 1080),
            f("https://cdn/auto", "hianime-auto", 1080),
        )
        val out = Providers.nextFormat(list, "https://cdn/a", emptySet(), hang = true) { it.contains("auto") }
        assertEquals("https://cdn/b", out?.url)
    }

    @Test
    fun aHungRungWithNoAutoStillWalks() {
        val list = listOf(
            f("https://a/1080", "rock-Orion-1080p", 1080),
            f("https://a/720", "rock-Orion-720p", 720),
        )
        val out = Providers.nextFormat(list, "https://a/1080", emptySet(), hang = true) { false }
        assertEquals("https://a/720", out?.url)
    }

    @Test
    fun aLowAutoDoesNotCatchAHung() {
        val list = listOf(
            f("https://cdn/a", "hianime-1080p", 1080),
            f("https://cdn/b", "hianime-720p", 720),
            f("https://cdn/auto", "hianime-auto", 480),
        )
        val out = Providers.nextFormat(list, "https://cdn/a", emptySet(), hang = true) { false }
        assertEquals("https://cdn/b", out?.url)
    }

    @Test
    fun aRelinkStillTakesTheSameRungOnAFreshUrl() {
        val list = listOf(
            f("https://cdn/a", "hianime-1080p", 1080),
            f("https://cdn/b", "hianime-1080p", 1080),
            f("https://cdn/auto", "hianime-auto", 1080),
        )
        val out = Providers.nextFormat(list, "https://cdn/a", emptySet()) { false }
        assertEquals("https://cdn/b", out?.url)
    }
}
