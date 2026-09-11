package dev.phantomwatch.core

import dev.phantomwatch.json.JSONObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.time.Clock

const val BROWSER_UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Safari/537.36"

fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

expect val ioDispatcher: CoroutineDispatcher

expect suspend fun httpJson(url: String, referer: String): JSONObject?

private const val HEX = "0123456789ABCDEF"

fun urlEncode(s: String): String = buildString {
    for (b in s.encodeToByteArray()) {
        val c = b.toInt() and 0xFF
        val plain = c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code ||
            c == '-'.code || c == '_'.code || c == '.'.code || c == '~'.code
        if (plain) append(c.toChar()) else append('%').append(HEX[c shr 4]).append(HEX[c and 0xF])
    }
}
