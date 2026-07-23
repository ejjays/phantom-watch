package dev.phantomwatch.provider

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatOrderTest {
    private data class Fx(val url: String, val label: String, val height: Int, val english: Boolean, val enSubs: Boolean)

    private fun fixture(): Pair<List<Fx>, List<String>> {
        val text = javaClass.getResourceAsStream("/pick_order.txt")!!.bufferedReader().use { it.readText() }
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        val split = lines.indexOf("order")
        val formats = lines.subList(0, split).map { l ->
            val p = l.split("|")
            Fx(p[0], p[1], p[2].toInt(), p[3].toBoolean(), p[4].toBoolean())
        }
        return formats to lines.subList(split + 1, lines.size)
    }

    @Test
    fun matchesSharedFixture() {
        val (fx, expected) = fixture()
        val formats = fx.map { VideoFormat(it.url, true, it.label, it.height, it.english, "", it.enSubs) }
        assertEquals(expected, FormatOrder.sort(formats).map { it.url })
    }

    @Test
    fun realHeightBeatsAdaptivePseudoHeight() {
        val auto = VideoFormat("a", true, "love-moviebox-auto", -1080, true, "", true)
        val real = VideoFormat("b", true, "love-dcloud-720p", 720)
        assertEquals(listOf("b", "a"), FormatOrder.sort(listOf(auto, real)).map { it.url })
    }

    @Test
    fun englishSubsAt1080Win() {
        val plain = VideoFormat("a", true, "love-dcloud-1080p", 1080)
        val en = VideoFormat("b", true, "love-dcloud-1080p", 1080, true, "", true)
        assertEquals(listOf("b", "a"), FormatOrder.sort(listOf(plain, en)).map { it.url })
    }
}
