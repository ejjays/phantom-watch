package dev.phantomwatch.json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonFacadeTest {
    private val body = """
        {"id":603,"title":"The Matrix","vote_average":8.2,"adult":false,"video":null,
         "runtime":136,"genres":[{"id":28,"name":"Action"},{"id":878,"name":"Science Fiction"}]}
    """.trimIndent()

    @Test
    fun readsPrimitives() {
        val o = JSONObject(body)
        assertEquals(603, o.optInt("id"))
        assertEquals(603L, o.optLong("id"))
        assertEquals("The Matrix", o.optString("title"))
        assertEquals(8.2, o.optDouble("vote_average"), 0.0001)
        assertFalse(o.optBoolean("adult", true))
        assertEquals(136, o.optInt("runtime"))
    }

    @Test
    fun missingDoubleIsNaN() {
        val o = JSONObject("""{"title":"x"}""")
        assertTrue(o.optDouble("vote_average").isNaN())
        assertEquals(7.5, o.optDouble("vote_average", 7.5), 0.0001)
    }

    @Test
    fun nullValueReadsAsEmpty() {
        val o = JSONObject(body)
        assertEquals("", o.optString("video"))
        assertEquals("", o.optString("missing"))
        assertFalse(o.has("video") && o.optString("video") == "null")
    }

    @Test
    fun nestedObjectsAndArrays() {
        val o = JSONObject(body)
        val genres = o.optJSONArray("genres")!!
        assertEquals(2, genres.length())
        assertEquals("Action", genres.optJSONObject(0)?.optString("name"))
        assertEquals(28, genres.optJSONObject(0)?.optInt("id"))
        assertNull(genres.optJSONObject(9))
        assertNull(o.optJSONArray("missing"))
    }

    @Test
    fun putMutatesAndNullRemoves() {
        val o = JSONObject()
        o.put("a", 1).put("b", "two").put("c", true)
        assertEquals(3, o.length())
        o.put("a", null)
        assertFalse(o.has("a"))
        assertEquals(2, o.length())
    }

    @Test
    fun toStringRoundTrips() {
        val o = JSONObject("""{"a":1,"b":["x","y"],"c":{"d":2.5}}""")
        val again = JSONObject(o.toString())
        assertEquals(1, again.optInt("a"))
        assertEquals("y", again.optJSONArray("b")?.optString(1))
        assertEquals(2.5, again.optJSONObject("c")?.optDouble("d") ?: 0.0, 0.0001)
    }

    @Test
    fun quoteEscapes() {
        assertEquals("\"a\\\"b\"", JSONObject.quote("a\"b"))
        assertEquals("\"line\"", JSONObject.quote("line"))
    }

    @Test
    fun namesEmptyIsNull() {
        assertNull(JSONObject().names())
        assertEquals(2, JSONObject("""{"a":1,"b":2}""").names()?.length())
    }

    @Test
    fun keysIterate() {
        val keys = JSONObject("""{"a":1,"b":2}""").keys().asSequence().toSet()
        assertEquals(setOf("a", "b"), keys)
    }
}
