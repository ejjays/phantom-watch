package dev.phantomwatch.provider

import dev.phantomwatch.data.Dub
import dev.phantomwatch.data.Stream
import dev.phantomwatch.data.Sub
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvidersTest {
    @Test
    fun identifiesTopTierFormat() {
        val top = VideoFormat("https://a/x.m3u8", true, "vidlink-1080p", 1080, enSubs = true)
        val auto = VideoFormat("https://a/x.m3u8", true, "vidlink-auto", 1080, enSubs = true)
        val noSub = VideoFormat("https://a/x.m3u8", true, "vidlink-1080p", 1080, enSubs = false)
        val p720 = VideoFormat("https://a/x.m3u8", true, "vidlink-720p", 720, enSubs = true)
        val dub = VideoFormat("https://a/x.m3u8", true, "vidlink-1080p", 1080, enSubs = true, dub = "English")
        val p4k = VideoFormat("https://a/x.m3u8", true, "vidlink-2160p", 2160, enSubs = true)
        assertTrue(Providers.isTopTier(top))
        assertTrue(Providers.isTopTier(p4k))
        assertTrue(Providers.isTopTier(dub))
        assertFalse(Providers.isTopTier(auto))
        assertFalse(Providers.isTopTier(noSub))
        assertFalse(Providers.isTopTier(p720))
    }
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
    fun mapsIntroWindow() {
        val js = legJson(
            """{"url":"https://a/x.m3u8","label":"hianime-auto","intro":{"start":55,"end":143}}""",
            """{"url":"https://a/y.m3u8","label":"vix-auto"}""",
        )
        val f = Providers.formatsOf(js)
        assertEquals(55_000L..143_000L, f[0].intro)
        assertNull(f[1].intro)
    }

    @Test
    fun mapsDubCuts() {
        val js = legJson(
            """{"url":"https://a/s.m3u8","label":"hianime-1080p"}""",
            """{"url":"https://a/d.m3u8","label":"hianime-dub-1080p","dub":"English","subs":[{"label":"English","url":"https://s/dub.vtt","src":"hianime"}]}""",
            """{"url":"https://a/da.m3u8","label":"hianime-dub-auto","dub":"English","subs":[{"label":"English","url":"https://s/dub.vtt","src":"hianime"}]}""",
        )
        val f = Providers.formatsOf(js)
        assertNull(f[0].dub)
        assertEquals("English", f[1].dub)
        val dubs = Providers.dubsOf(f, f[0], emptyList())
        assertEquals(2, dubs.size)
        assertTrue(dubs[0].base)
        assertEquals("https://a/da.m3u8", dubs[1].url)
        assertEquals(listOf("https://s/dub.vtt"), dubs[1].subs.map { it.url })
    }

    @Test
    fun dubDoesNotBorrowTheLegSubs() {
        val js = JSONObject()
            .put("formats", JSONArray().put(JSONObject("""{"url":"https://a/d.m3u8","label":"hianime-dub-1080p","dub":"English"}""")))
            .put("subs", JSONArray().put(JSONObject("""{"label":"English","url":"https://s/sub-cut.vtt","src":"hianime"}""")))
        val f = Providers.formatsOf(js)
        assertTrue(Providers.dubsOf(f, f[0], emptyList()).last().subs.isEmpty())
    }

    @Test
    fun dubKeepsSubsSharedWithTheSubCut() {
        val shared = "https://s/shared.vtt"
        val js = JSONObject()
            .put(
                "formats",
                JSONArray().put(
                    JSONObject(
                        """{"url":"https://a/d.m3u8","label":"hianime-dub-auto","dub":"English","subs":[{"label":"English","url":"$shared","src":"hianime"}]}""",
                    ),
                ),
            )
            .put("subs", JSONArray().put(JSONObject("""{"label":"English","url":"$shared","src":"hianime"}""")))
        val f = Providers.formatsOf(js)
        // hianime gives both cuts one vtt; the dub cut shares the timeline, so it is not the sub cut's alone
        assertEquals(listOf(shared), Providers.dubsOf(f, f[0], emptyList()).last().subs.map { it.url })
    }

    @Test
    fun dubsNameTheProviderTheySwitchTo() {
        val js = legJson(
            """{"url":"https://a/p.m3u8","label":"paradise-auto","height":1080}""",
            """{"url":"https://a/d.m3u8","label":"hianime-dub-auto","dub":"English"}""",
        )
        val f = Providers.formatsOf(js)
        val dubs = Providers.dubsOf(f, f[0], emptyList())
        assertEquals(listOf("Sub · AnimeParadise", "English · HiAnime"), dubs.map { it.label })
        assertEquals(listOf("https://a/p.m3u8", "https://a/d.m3u8"), dubs.map { it.url })
    }

    @Test
    fun restoresTheSavedCutByItsLabel() {
        val js = legJson(
            """{"url":"https://a/s.m3u8","label":"hianime-1080p"}""",
            """{"url":"https://a/d.m3u8","label":"hianime-dub-auto","dub":"English"}""",
        )
        val f = Providers.formatsOf(js)
        val s = Providers.restore(Stream(f[0].url, true, emptyList(), "", null, dubs = Providers.dubsOf(f, f[0], emptyList())), "English · HiAnime")
        assertEquals("https://a/d.m3u8", s.url)
        assertEquals("English · HiAnime", s.dub)
    }

    @Test
    fun restoresTheLanguageFromAnotherLeg() {
        val js = legJson(
            """{"url":"https://a/p.m3u8","label":"paradise-auto","height":1080}""",
            """{"url":"https://a/pd.m3u8","label":"paradise-dub","dub":"English"}""",
        )
        val f = Providers.formatsOf(js)
        val s = Providers.restore(Stream(f[0].url, true, emptyList(), "", null, dubs = Providers.dubsOf(f, f[0], emptyList())), "English · HiAnime")
        assertEquals("https://a/pd.m3u8", s.url)
    }

    @Test
    fun restoreKeepsTheBaseWhenTheCutIsGone() {
        val js = legJson(
            """{"url":"https://a/s.m3u8","label":"hianime-1080p"}""",
            """{"url":"https://a/d.m3u8","label":"hianime-dub-auto","dub":"English"}""",
        )
        val f = Providers.formatsOf(js)
        val s = Providers.restore(Stream(f[0].url, true, emptyList(), "", null, dubs = Providers.dubsOf(f, f[0], emptyList())), "Tagalog · Pinoy")
        assertEquals(f[0].url, s.url)
        assertNull(s.dub)
    }

    @Test
    fun dubBaseDropsToTheOriginalCut() {
        val js = legJson(
            """{"url":"https://a/s.m3u8","label":"hianime-1080p"}""",
            """{"url":"https://a/d.m3u8","label":"hianime-dub-auto","dub":"English"}""",
        )
        val f = Providers.formatsOf(js)
        val dubs = Providers.dubsOf(f, f[0], emptyList())
        val onDub = Providers.restore(Stream(f[0].url, true, emptyList(), "", null, dubs = dubs), "English · HiAnime")
        assertEquals("https://a/d.m3u8", onDub.url)
        assertEquals("https://a/s.m3u8", Providers.dubBase(onDub)?.url)
        assertNull(Providers.dubBase(onDub)?.dub)
        assertNull(Providers.dubBase(Stream(f[0].url, true, emptyList(), "", null, dubs = dubs)))
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
    fun providerWithoutOwnSubsGetsNone() {
        val subs = listOf(Sub("English", "a", "vidlink"), Sub("Español", "b", "love"))
        val f = VideoFormat("u", false, "vap-auto", 1080)
        assertTrue(Providers.subsFor(f, subs).isEmpty())
    }

    @Test
    fun aBannedHostIsRankedLastAndStillListed() {
        DeadHosts.markHang("https://ban1.example/a", 5)
        val banned = VideoFormat("https://ban1.example/a", true, "ban1-1080p", 1080)
        val ok = VideoFormat("https://ok1.example/a", true, "ok1-1080p", 1080)
        val out = Providers.rankLast(listOf(banned, ok)) { DeadHosts.blocked(it) }
        assertEquals(listOf(ok, banned), out)
    }

    @Test
    fun aSavedDubOnABannedHostIsStillReturned() {
        DeadHosts.markHang("https://ban2.example/a", 5)
        val d = Dub("English · HiAnime", "https://ban2.example/a", true, "")
        assertEquals(d, Providers.savedDub(listOf(d), "English · HiAnime"))
    }

    @Test
    fun aDubNeverBorrows() {
        val subs = listOf(Sub("English", "a", "vidlink"))
        val f = VideoFormat("u", false, "hianime-dub-1080p", 1080, dub = "English")
        assertTrue(Providers.subsFor(f, subs).isEmpty())
    }

    @Test
    fun namesProviders() {
        assertEquals("VidLove", Providers.providerName(VideoFormat("u", true, "love-dcloud-1080p", 1080)))
        assertEquals("HiAnime", Providers.providerName(VideoFormat("u", true, "hianime-auto", 0)))
        assertEquals("weird", Providers.providerName(VideoFormat("u", true, "weird", 0)))
    }
}
