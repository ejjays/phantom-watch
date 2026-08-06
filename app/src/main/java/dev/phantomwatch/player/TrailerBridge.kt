package dev.phantomwatch.player

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.phantomwatch.core.AppLog
import dev.phantomwatch.core.Http
import dev.phantomwatch.core.RemoteConfig
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

// a hidden youtube.com WebView mints a PO token, then asks innertube for a stream
object TrailerBridge {
    data class Result(val hls: String?, val video: String?, val audio: String?)

    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(4)
    private val seq = AtomicInteger()
    private val started = AtomicBoolean()
    private val booted = CompletableDeferred<Unit>()
    private val waiting = ConcurrentHashMap<String, CompletableDeferred<JSONObject?>>()
    private val cache = ConcurrentHashMap<String, Pair<Long, Result>>()

    private var app: Context? = null
    private var script = ""

    @Volatile
    private var view: WebView? = null

    fun init(ctx: Context) {
        app = ctx.applicationContext
        script = try {
            ctx.applicationContext.assets.open("trailer.js").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            AppLog.d("trailer asset ${e.javaClass.simpleName}")
            ""
        }
    }

    // mint the token early; the detail sheet usually outlives this
    fun warm() {
        main.post { ensure() }
    }

    suspend fun resolve(videoId: String): Result? {
        cache[videoId]?.let { (at, r) -> if (System.currentTimeMillis() - at < 30 * 60_000) return r }
        val t0 = System.currentTimeMillis()
        val out = withTimeoutOrNull(45_000) {
            main.post { ensure() }
            booted.await()
            val w = view ?: return@withTimeoutOrNull null
            val id = "t" + seq.incrementAndGet()
            val d = CompletableDeferred<JSONObject?>()
            waiting[id] = d
            val js = "window.__trResolve(${JSONObject.quote(id)}, ${JSONObject.quote(videoId)})"
            main.post { w.evaluateJavascript(js, null) }
            try {
                d.await()
            } finally {
                waiting.remove(id)
            }
        }
        val ms = System.currentTimeMillis() - t0
        val res = out?.takeIf { it.optBoolean("ok") }?.let {
            val pair = it.optJSONObject("pair")
            Result(
                hls = it.optString("url").takeIf { u -> u.isNotEmpty() },
                video = pair?.optString("video")?.takeIf { u -> u.isNotEmpty() },
                audio = pair?.optString("audio")?.takeIf { u -> u.isNotEmpty() },
            )
        }?.takeIf { it.hls != null || (it.video != null && it.audio != null) }
        if (res != null) cache[videoId] = System.currentTimeMillis() to res
        AppLog.d("trailer $videoId ${if (res == null) out?.optString("error")?.take(60) ?: "timeout" else "ok ${ms}ms"}")
        return res
    }

    private fun ensure() {
        if (view != null || script.isEmpty() || !started.compareAndSet(false, true)) return
        view = build()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun build(): WebView? {
        val ctx = app ?: return null
        val w = WebView(ctx)
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }
        w.addJavascriptInterface(Bridge(), "TrailerBridge")
        w.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView, url: String) {
                val cfg = RemoteConfig.trailer()
                v.evaluateJavascript((if (cfg == null) "" else "window.__trCfg=$cfg;") + script, null)
            }
        }
        w.loadDataWithBaseURL("https://www.youtube.com/", "<html><head><meta charset=\"utf-8\"></head><body></body></html>", "text/html", "utf-8", null)
        return w
    }

    private fun eval(js: String) {
        main.post { view?.evaluateJavascript(js, null) }
    }

    private class Bridge {
        @JavascriptInterface
        fun http(id: String, reqJson: String) {
            pool.execute {
                val res = try {
                    val req = JSONObject(reqJson)
                    val b = Request.Builder().url(req.optString("url"))
                    var ua = false
                    var type = "application/json"
                    req.optJSONObject("headers")?.let { h ->
                        h.keys().forEach { k ->
                            val v = h.optString(k)
                            if (k.equals("User-Agent", true)) ua = true
                            if (k.equals("Content-Type", true)) type = v.ifEmpty { type }
                            try {
                                b.header(k, v)
                            } catch (_: Exception) {
                            }
                        }
                    }
                    if (!ua) b.header("User-Agent", Http.UA)
                    val method = req.optString("method", "GET").uppercase().ifEmpty { "GET" }
                    val body = req.optString("body")
                    // okhttp replaces Content-Type with the body's, so the media type has to match the header
                    if (method == "GET" || method == "HEAD") b.method(method, null) else b.method(method, body.toRequestBody(type.toMediaTypeOrNull()))
                    val call = Http.client.newCall(b.build())
                    call.timeout().deadline(25, TimeUnit.SECONDS)
                    call.execute().use { r ->
                        val text = r.body?.string().orEmpty()
                        if (r.code !in 200..299) AppLog.d("trailer http ${r.code} ${req.optString("url").takeLast(28)}")
                        val hs = JSONObject()
                        r.headers.toMultimap().forEach { (k, v) -> hs.put(k, v.joinToString(", ")) }
                        JSONObject().put("code", r.code).put("headers", hs).put("body", text)
                    }
                } catch (e: Exception) {
                    AppLog.d("trailer http ${e.javaClass.simpleName}")
                    JSONObject().put("error", e.javaClass.simpleName)
                }
                eval("window.__trHttpDone(${JSONObject.quote(id)}, ${JSONObject.quote(res.toString())})")
            }
        }

        @JavascriptInterface
        fun post(msg: String) {
            val json = try {
                JSONObject(msg)
            } catch (_: Exception) {
                return
            }
            if (json.has("boot")) {
                booted.complete(Unit)
                json.optString("error").takeIf { it.isNotEmpty() }?.let { AppLog.d("trailer boot $it") }
                return
            }
            json.optString("log").takeIf { it.isNotEmpty() }?.let { AppLog.d("trailer $it") }
            val id = json.optString("reqId")
            if (id.isNotEmpty()) waiting.remove(id)?.complete(json)
        }
    }
}
