package dev.phantomwatch

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteConfigTest {
    private fun cfg(vararg legs: Pair<String, Boolean>): JSONObject {
        val o = JSONObject()
        legs.forEach { (name, on) -> o.put(name, JSONObject().put("enabled", on)) }
        return JSONObject().put("version", 1).put("legs", o)
    }

    @Test
    fun acceptsAConfigWithOneLiveLeg() {
        assertTrue(RemoteConfig.acceptConfig(cfg("love" to true, "rock" to false)))
    }

    @Test
    fun rejectsAConfigWithEveryLegOff() {
        assertFalse(RemoteConfig.acceptConfig(cfg("love" to false, "rock" to false)))
    }

    @Test
    fun rejectsAConfigWithoutLegs() {
        assertFalse(RemoteConfig.acceptConfig(JSONObject().put("version", 1)))
        assertFalse(RemoteConfig.acceptConfig(JSONObject().put("version", 1).put("legs", JSONObject())))
    }

    @Test
    fun remoteOfTheSameVersionIsAccepted() {
        // the bundled asset can be newer than what is deployed
        assertTrue(RemoteConfig.acceptsRemote(14, 14))
        assertTrue(RemoteConfig.acceptsRemote(14, 15))
        assertFalse(RemoteConfig.acceptsRemote(14, 13))
    }

    @Test
    fun legsDefaultToEnabled() {
        val o = JSONObject().put("love", JSONObject().put("api", "https://x"))
        val c = JSONObject().put("version", 1).put("legs", o)
        assertTrue(RemoteConfig.acceptConfig(c))
    }
}

class OsSubsTest {
    @Test
    fun picksTheMostDownloadedFile() {
        val body = """
            {"data":[
              {"attributes":{"download_count":3,"files":[{"file_id":11}]}},
              {"attributes":{"download_count":9,"files":[{"file_id":22}]}}
            ]}
        """.trimIndent()
        assertEquals(22L, OsSubs.pickFileId(body))
    }

    @Test
    fun ignoresEntriesWithoutFiles() {
        val body = """{"data":[{"attributes":{"download_count":9,"files":[]}}]}"""
        assertNull(OsSubs.pickFileId(body))
    }

    @Test
    fun survivesGarbage() {
        assertNull(OsSubs.pickFileId("not json"))
        assertNull(OsSubs.pickFileId("{}"))
    }

    @Test
    fun readsTheDownloadLink() {
        assertEquals("https://dl/x.srt", OsSubs.linkOf("""{"link":"https://dl/x.srt"}"""))
        assertNull(OsSubs.linkOf("""{"link":"ftp://x"}"""))
        assertNull(OsSubs.linkOf("nope"))
    }
}

class AiSubsTest {
    @Test
    fun parsesNumberedLines() {
        val text = """
            1. First line
            2) Second line
            **3** Third line
        """.trimIndent()
        assertEquals(listOf("First line", "Second line", "Third line"), AiSubs.parseNumberedLines(text, 3))
    }

    @Test
    fun rejectsAPartialAnswer() {
        assertNull(AiSubs.parseNumberedLines("1. only one", 3))
    }
}
