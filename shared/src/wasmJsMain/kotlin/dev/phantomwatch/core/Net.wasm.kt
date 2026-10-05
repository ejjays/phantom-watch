package dev.phantomwatch.core

import dev.phantomwatch.json.JSONObject
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay

internal val client = HttpClient(Js) {
    expectSuccess = false
    install(HttpTimeout) {
        requestTimeoutMillis = 25_000
        connectTimeoutMillis = 10_000
    }
}

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default

// a browser drops User-Agent/Referer on its own, so sending them keeps the call sites identical
// to Android. providers that demand them still cannot work here
actual suspend fun httpJson(url: String, referer: String): JSONObject? {
    var last: Exception? = null
    repeat(3) { attempt ->
        try {
            val res = client.get(url) {
                header("User-Agent", BROWSER_UA)
                header("Referer", referer)
            }
            if (res.status.value != 200) {
                if (res.status.value == 429 || res.status.value in 500..599) {
                    throw RuntimeException("retry ${res.status.value}")
                }
                throw RuntimeException("http ${res.status.value}")
            }
            val body = res.bodyAsText()
            if (!body.startsWith("{")) throw RuntimeException("not json: ${body.take(60)}")
            return JSONObject(body)
        } catch (e: Exception) {
            last = e
            pwLog("http", "${url.substringAfter("://").take(64)} ${e::class.simpleName}: ${e.message}")
            if (e is RuntimeException && e.message?.startsWith("http ") == true) throw e
            delay(1000L * (attempt + 1))
        }
    }
    throw RuntimeException("${last?.let { it::class.simpleName }}: ${last?.message}")
}
