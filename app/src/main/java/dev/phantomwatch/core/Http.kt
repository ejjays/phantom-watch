package dev.phantomwatch.core

import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object Http {
    const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Safari/537.36"
    private const val PLAYER = "https://player.vidlove.cc"

    val client: OkHttpClient = OkHttpClient.Builder()
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val all = Dns.SYSTEM.lookup(hostname)
                return all.filterIsInstance<Inet4Address>().ifEmpty { all }
            }
        })
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    fun getJson(url: String, referer: String): JSONObject? {
        var last: Exception? = null
        repeat(3) { attempt ->
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", UA)
                    .header("Referer", referer)
                    .header("Origin", PLAYER)
                    .build()
                client.newCall(req).execute().use { res ->
                    if (res.code != 200) throw RuntimeException("http ${res.code}")
                    val body = res.body?.string().orEmpty()
                    if (!body.startsWith("{")) throw RuntimeException("not json: ${body.take(60)}")
                    return JSONObject(body)
                }
            } catch (e: Exception) {
                last = e
                if (e is RuntimeException && e.message?.startsWith("http ") == true) throw e
                try {
                    Thread.sleep(1000L * (attempt + 1))
                } catch (_: InterruptedException) {
                }
            }
        }
        throw RuntimeException("${last?.javaClass?.simpleName}: ${last?.message}")
    }
}
