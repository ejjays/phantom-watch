package dev.phantomwatch.player

import dev.phantomwatch.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SkipTimesTest {
    private val opening = listOf("op", "mixed-op")

    @Test
    fun picksTheOpeningWindowInMillis() {
        val json = JSONObject(
            """{"found":true,"results":[
                {"skipType":"op","interval":{"startTime":55.2,"endTime":145.0}},
                {"skipType":"ed","interval":{"startTime":1338.0,"endTime":1428.0}}]}""",
        )
        assertEquals(55200L..145000L, SkipTimes.windowOf(json, opening))
    }

    @Test
    fun acceptsMixedOpeningButIgnoresEndingsAndRecaps() {
        val json = JSONObject(
            """{"found":true,"results":[
                {"skipType":"ed","interval":{"startTime":10.0,"endTime":20.0}},
                {"skipType":"recap","interval":{"startTime":30.0,"endTime":40.0}},
                {"skipType":"mixed-op","interval":{"startTime":186.0,"endTime":276.0}}]}""",
        )
        assertEquals(186000L..276000L, SkipTimes.windowOf(json, opening))
    }

    @Test
    fun picksTheEndingWindowWhenAskedForIt() {
        val json = JSONObject(
            """{"found":true,"results":[
                {"skipType":"op","interval":{"startTime":3.392,"endTime":93.392}},
                {"skipType":"ed","interval":{"startTime":1317.66,"endTime":1375.66}}]}""",
        )
        assertEquals(1317660L..1375660L, SkipTimes.windowOf(json, listOf("ed")))
    }

    @Test
    fun noOpeningMeansNoWindow() {
        assertNull(SkipTimes.windowOf(JSONObject("""{"found":false,"results":[]}"""), opening))
        assertNull(SkipTimes.windowOf(JSONObject("{}"), opening))
    }

    @Test
    fun openEndedCreditsRunToTheEndOfTheFile() {
        val json = JSONObject("""{"credits":[{"start_ms":3183000,"end_ms":null}]}""")
        assertEquals(3183000L..3240000L, SkipTimes.creditsWindow(json, 3240000L))
    }

    @Test
    fun creditsFromALongerCutAreClampedOrDropped() {
        val json = JSONObject("""{"credits":[{"start_ms":3183000,"end_ms":3400000}]}""")
        assertEquals(3183000L..3240000L, SkipTimes.creditsWindow(json, 3240000L))
        assertNull(SkipTimes.creditsWindow(json, 3000000L))
    }

    @Test
    fun creditsAtZeroMeanUntimed() {
        assertNull(SkipTimes.creditsWindow(JSONObject("""{"credits":[{"start_ms":0,"end_ms":0}]}"""), 3_000_000L))
        assertNull(SkipTimes.creditsWindow(JSONObject("""{"intro":[{"start_ms":1000,"end_ms":2000}]}"""), 3_000_000L))
    }
}
