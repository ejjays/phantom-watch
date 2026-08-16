package dev.phantomwatch.subs

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepgramLiveTest {

    @Test
    fun metadataMeansConnected() {
        assertTrue(DeepgramLive.parseEvent(JSONObject("""{"type":"Metadata","request_id":"x"}""")) is DeepgramLive.Ev.Ready)
    }

    @Test
    fun interimResultIsIgnored() {
        // drawing the growing interim text is what made the captions read like a live call
        assertNull(
            DeepgramLive.parseEvent(
                JSONObject(
                    """{"type":"Results","is_final":false,"start":5.12,"duration":1.0,
                       "channel":{"alternatives":[{"transcript":"he broke my"}]}}""",
                ),
            ),
        )
    }

    @Test
    fun finalCarriesItsOwnWordTiming() {
        // this is the whole reason for the switch: cue times come from the provider, not a guess
        val ev = DeepgramLive.parseEvent(
            JSONObject(
                """{"type":"Results","is_final":true,"start":5.12,"duration":2.08,
                   "channel":{"alternatives":[{"transcript":" He broke my squishy bones. "}]}}""",
            ),
        )
        assertEquals(DeepgramLive.Ev.Final("He broke my squishy bones.", 5.12, 7.20), ev)
    }

    @Test
    fun emptySegmentIsDropped() {
        assertNull(
            DeepgramLive.parseEvent(
                JSONObject("""{"type":"Results","is_final":true,"start":1.0,"duration":0.5,
                               "channel":{"alternatives":[{"transcript":""}]}}"""),
            ),
        )
    }

    @Test
    fun errorIsReported() {
        val ev = DeepgramLive.parseEvent(JSONObject("""{"type":"Error","description":"bad key"}"""))
        assertEquals(DeepgramLive.Ev.Failed("bad key"), ev)
    }
}
