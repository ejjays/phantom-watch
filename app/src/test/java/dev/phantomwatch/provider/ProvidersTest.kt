package dev.phantomwatch.provider

import dev.phantomwatch.data.Sub
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvidersTest {
    private fun legJson(vararg formats: String): JSONObject {
        val arr = JSONArray()
        formats.forEach { arr.put(JSONObject(it)) }
        return JSONObject().put("formats", arr)
    }

    @Test
    fun mapsFormatsFromJs() {
        val js = legJson(
            """{"url":"https://a/x.m3u8","isHls":true,"label":"vix-auto","height":1080,"english":true,"referer":"https://vixsrc.to/","enSubs":true,"enAudio":false}""",
            """{"url":"https://a/y.mp4","isHls":false,"label":"vidlink-720p","height":720,"headers":{"User-Agent":"okhttp/4.12.0"}}""",
        )
        val f = Providers.formatsOf(js)
        assertEquals(2, f.size)
        assertEquals("https://a/x.m3u8", f[0].url)
        assertTrue(f[0].isHls)
        assertTrue(f[0].enSubs)
        assertEquals("https://vixsrc.to/", f[0].referer)
        assertEquals(720, f[1].height)
        assertEquals(mapOf("User-Agent" to "okhttp/4.12.0"), f[1].headers)
    }

    @Test
    fun skipsEntriesWithoutUrl() {
        val js = legJson("""{"label":"broken"}""", """{"url":"https://a/z.m3u8"}""")
        assertEquals(1, Providers.formatsOf(js).size)
    }

    @Test
    fun mapsSubsWithSource() {
        val js = JSONObject().put(
            "subs",
            JSONArray()
                .put(JSONObject().put("label", "English").put("url", "https://s/en.vtt").put("src", "vidlink"))
                .put(JSONObject().put("url", "https://s/x.vtt")),
        )
        val subs = Providers.subsOf(js)
        assertEquals(2, subs.size)
        assertEquals("vidlink", subs[0].src)
        assertEquals("", subs[1].src)
    }

    @Test
    fun subsFollowTheFormatSource() {
        val subs = listOf(Sub("English", "a", "vidlink"), Sub("English", "b", "love"))
        val vidlink = VideoFormat("u", false, "vidlink-1080p", 1080)
        assertEquals(listOf("a"), Providers.subsFor(vidlink, subs).map { it.url })
        val rock = VideoFormat("u", false, "rock-mp4", 1080)
        assertTrue(Providers.subsFor(rock, subs).isEmpty())
    }

    @Test
    fun ownProviderSubsWinAndForeignOnesFollow() {
        val subs = listOf(Sub("English", "a", "vidlink"), Sub("English", "b", "love"), Sub("English", "c"))
        val f = VideoFormat("u", false, "vidlink-1080p", 1080)
        assertEquals(listOf("a", "c"), Providers.subsFor(f, subs).map { it.url })
    }

    @Test
    fun providerWithoutOwnSubsDropsForeignOnes() {
        val subs = listOf(Sub("English", "a", "vidlink"), Sub("English", "b", "love"))
        val f = VideoFormat("u", false, "vap-auto", 1080)
        assertTrue(Providers.subsFor(f, subs).isEmpty())
    }

    @Test
    fun namesProviders() {
        assertEquals("VidLove", Providers.providerName(VideoFormat("u", true, "love-dcloud-1080p", 1080)))
        assertEquals("HiAnime", Providers.providerName(VideoFormat("u", true, "hianime-auto", 0)))
        assertEquals("weird", Providers.providerName(VideoFormat("u", true, "weird", 0)))
    }
}
