package dev.phantomwatch.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.phantomwatch.json.JSONObject
import kotlinx.browser.localStorage

// phone keeps these in SharedPreferences. browser keeps same key/value pairs in localStorage, so
// a toggle like continue watching survives the reload it is meant to outlive
object WebSettings {
    private const val STORE = "phantom_settings"

    var values by mutableStateOf(load())
        private set

    fun on(key: String, fallback: Int = 1): Int = values[key] ?: fallback

    fun set(key: String, value: Int) {
        values = values + (key to value)
        val o = JSONObject()
        values.forEach { (k, v) -> o.put(k, v) }
        runCatching { localStorage.setItem(STORE, o.toString()) }
    }

    private fun load(): Map<String, Int> = runCatching {
        val o = JSONObject(localStorage.getItem(STORE) ?: "{}")
        val out = mutableMapOf<String, Int>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            out[k] = o.optInt(k)
        }
        out
    }.getOrDefault(emptyMap())
}
