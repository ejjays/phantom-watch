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
}
