package dev.phantomwatch.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DubsTest {
    private fun f(label: String, dub: String? = null) =
        VideoFormat("https://x/$label", true, label, 1080, true, "", true, false, emptyMap(), null, dub)

    @Test
    fun aDubOnlyCutIsTheWholeList() {
        val dub = f("hianime-dub-1080p", "English")
        val out = Providers.dubsOf(listOf(dub), dub, emptyList())
        assertEquals(listOf("English · HiAnime"), out.map { it.label })
    }

    @Test
    fun theSubCutLeadsWhenBothCutsExist() {
        val sub = f("hianime-1080p")
        val dub = f("hianime-dub-1080p", "English")
        val out = Providers.dubsOf(listOf(sub, dub), sub, emptyList(), "Japanese")
        assertEquals(listOf("Japanese · HiAnime", "English · HiAnime"), out.map { it.label })
        assertTrue(out.first().base)
    }
}
