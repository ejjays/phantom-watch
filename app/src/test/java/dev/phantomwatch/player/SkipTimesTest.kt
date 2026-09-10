package dev.phantomwatch.player

import dev.phantomwatch.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SkipTimesTest {
    @Test
    fun picksTheOpeningWindowInMillis() {
        val json = JSONObject(
            """{"found":true,"results":[
                {"skipType":"op","interval":{"startTime":55.2,"endTime":145.0}},
                {"skipType":"ed","interval":{"startTime":1338.0,"endTime":1428.0}}]}""",
        )
        assertEquals(55200L..145000L, SkipTimes.windowOf(json))
    }

    @Test
    fun acceptsMixedOpeningButIgnoresEndingsAndRecaps() {
        val json = JSONObject(
            """{"found":true,"results":[
                {"skipType":"ed","interval":{"startTime":10.0,"endTime":20.0}},
                {"skipType":"recap","interval":{"startTime":30.0,"endTime":40.0}},
                {"skipType":"mixed-op","interval":{"startTime":186.0,"endTime":276.0}}]}""",
        )
        assertEquals(186000L..276000L, SkipTimes.windowOf(json))
    }

    @Test
    fun noOpeningMeansNoWindow() {
        assertNull(SkipTimes.windowOf(JSONObject("""{"found":false,"results":[]}""")))
        assertNull(SkipTimes.windowOf(JSONObject("{}")))
    }
}
