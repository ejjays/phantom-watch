package dev.phantomwatch.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

// org.json's shape, backed by kotlinx-serialization so it runs on Android and Wasm alike
// overloads, not defaults, where org.json tells absent from default: optDouble returns NaN for a
// missing key, which callers use to mean "no rating"
class JSONObject internal constructor(private val map: MutableMap<String, JsonElement>) {
    constructor() : this(LinkedHashMap())
    constructor(text: String) : this(LinkedHashMap(Json.parseToJsonElement(text).jsonObject))

    fun put(name: String, value: Any?): JSONObject {
        if (value == null) map.remove(name) else map[name] = toElement(value)
        return this
    }

    fun has(name: String): Boolean = map.containsKey(name)

    fun remove(name: String): Any? = map.remove(name)

    fun keys(): Iterator<String> = map.keys.iterator()

    fun names(): JSONArray? =
        if (map.isEmpty()) null else JSONArray().also { arr -> map.keys.forEach { arr.put(it) } }

    fun length(): Int = map.size

    fun optString(name: String): String = optString(name, "")

    fun optString(name: String, fallback: String): String {
        val p = map[name].primitive() ?: return fallback
        return p.content.ifEmpty { fallback }
    }

    fun optInt(name: String, fallback: Int = 0): Int {
        val p = map[name].primitive() ?: return fallback
        return p.intOrNull ?: p.doubleOrNull?.toInt() ?: fallback
    }

    fun optLong(name: String, fallback: Long = 0L): Long {
        val p = map[name].primitive() ?: return fallback
        return p.longOrNull ?: p.doubleOrNull?.toLong() ?: fallback
    }

    fun optDouble(name: String): Double {
        val p = map[name].primitive() ?: return Double.NaN
        return p.doubleOrNull ?: Double.NaN
    }

    fun optDouble(name: String, fallback: Double): Double {
        val d = optDouble(name)
        return if (d.isNaN()) fallback else d
    }

    fun optBoolean(name: String, fallback: Boolean = false): Boolean {
        val p = map[name].primitive() ?: return fallback
        return p.booleanOrNull ?: fallback
    }

    fun optJSONObject(name: String): JSONObject? =
        (map[name] as? JsonObject)?.let { JSONObject(LinkedHashMap(it)) }

    fun optJSONArray(name: String): JSONArray? =
        (map[name] as? JsonArray)?.let { JSONArray(it.toMutableList()) }

    fun getString(name: String): String = optString(name)

    fun getInt(name: String): Int = optInt(name)

    fun getJSONObject(name: String): JSONObject = optJSONObject(name) ?: JSONObject()

    fun getJSONArray(name: String): JSONArray = optJSONArray(name) ?: JSONArray()

    internal fun element(): JsonObject = JsonObject(map)

    override fun toString(): String = element().toString()

    companion object {
        fun quote(s: String): String = JsonPrimitive(s).toString()
    }
}

class JSONArray internal constructor(private val list: MutableList<JsonElement>) {
    constructor() : this(arrayListOf<JsonElement>())
    constructor(text: String) : this(Json.parseToJsonElement(text).jsonArray.toMutableList())

    fun length(): Int = list.size

    fun put(value: Any?): JSONArray {
        list.add(toElement(value))
        return this
    }

    fun optJSONObject(index: Int): JSONObject? =
        (list.getOrNull(index) as? JsonObject)?.let { JSONObject(LinkedHashMap(it)) }

    fun optJSONArray(index: Int): JSONArray? =
        (list.getOrNull(index) as? JsonArray)?.let { JSONArray(it.toMutableList()) }

    fun optString(index: Int, fallback: String = ""): String {
        val p = list.getOrNull(index).primitive() ?: return fallback
        return p.content.ifEmpty { fallback }
    }

    fun optInt(index: Int, fallback: Int = 0): Int {
        val p = list.getOrNull(index).primitive() ?: return fallback
        return p.intOrNull ?: p.doubleOrNull?.toInt() ?: fallback
    }

    internal fun element(): JsonArray = JsonArray(list)

    override fun toString(): String = element().toString()
}

private fun JsonElement?.primitive(): JsonPrimitive? =
    (this as? JsonPrimitive)?.takeIf { it !is JsonNull }

private fun toElement(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JSONObject -> value.element()
    is JSONArray -> value.element()
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Int -> JsonPrimitive(value)
    is Long -> JsonPrimitive(value)
    is Double -> JsonPrimitive(value)
    is Float -> JsonPrimitive(value)
    else -> JsonPrimitive(value.toString())
}
