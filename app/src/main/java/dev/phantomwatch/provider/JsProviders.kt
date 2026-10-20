package dev.phantomwatch.provider

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object JsProviders {
    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(8)
    private val voePool = Executors.newSingleThreadExecutor()
    private val seq = AtomicInteger()
    private val started = AtomicBoolean()
    private val created = CompletableDeferred<Unit>()
    private val ready = CompletableDeferred<Unit>()
    private val waiting = ConcurrentHashMap<String, CompletableDeferred<JSONObject?>>()
    private val longClient by lazy { Http.client.newBuilder().readTimeout(30, TimeUnit.SECONDS).build() }

    private var app: Context? = null
    private var script = ""
    @Volatile
    private var view: WebView? = null

    @Volatile
    var engineDown = false
        private set

    fun init(ctx: Context) {
        app = ctx.applicationContext
        script = try {
            ctx.applicationContext.assets.open("providers.js").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            AppLog.d("js asset ${e.javaClass.simpleName}")
            ""
        }
    }

    suspend fun resolve(leg: String, args: JSONObject, timeoutSec: Long = 20): JSONObject? {
        val t0 = System.currentTimeMillis()
        val out = withTimeoutOrNull(timeoutSec * 1000) {
            val w = view() ?: return@withTimeoutOrNull null
            ready.await()
            val id = "r" + seq.incrementAndGet()
            val d = CompletableDeferred<JSONObject?>()
            waiting[id] = d
            val js = "window.__pwRun(${JSONObject.quote(id)}, ${JSONObject.quote(leg)}, ${JSONObject.quote(args.toString())})"
            main.post { w.evaluateJavascript(js, null) }
            try {
                d.await()
            } finally {
                waiting.remove(id)
            }
        }
        AppLog.d("js $leg ${if (out == null) "none" else "${System.currentTimeMillis() - t0}ms"}")
        return out
    }

    // canonical pick order lives in providers.js; this asks for it
    suspend fun sortOrder(formats: List<VideoFormat>): List<VideoFormat>? {
        if (formats.size < 2) return formats
        val arr = JSONArray()
        formats.forEach { f ->
            arr.put(
                JSONObject()
                    .put("url", f.url)
                    .put("isHls", f.isHls)
                    .put("label", f.label)
                    .put("height", f.height)
                    .put("english", f.english)
                    .put("enSubs", f.enSubs)
                    .put("enAudio", f.enAudio)
                    .put("dub", f.dub.orEmpty()),
            )
        }
        val out = withTimeoutOrNull(8000) {
            val w = view() ?: return@withTimeoutOrNull null
            ready.await()
            val id = "s" + seq.incrementAndGet()
            val d = CompletableDeferred<JSONObject?>()
            waiting[id] = d
            val js = "window.__pwSort(${JSONObject.quote(id)}, ${JSONObject.quote(arr.toString())})"
            main.post { w.evaluateJavascript(js, null) }
            try {
                d.await()
            } finally {
                waiting.remove(id)
            }
        }
        val order = out?.optJSONArray("order") ?: return null
        val byUrl = formats.associateBy { it.url }.toMutableMap()
        val sorted = mutableListOf<VideoFormat>()
        for (i in 0 until order.length()) {
            byUrl.remove(order.optString(i))?.let { sorted.add(it) }
        }
        sorted.addAll(byUrl.values)
        return sorted
    }

    private suspend fun view(): WebView? {
        if (started.compareAndSet(false, true)) {
            main.post {
                view = build()
                created.complete(Unit)
            }
        }
        created.await()
        return view
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun build(): WebView? {
        val ctx = app ?: run { engineDown = true; return null }
        val src = RemoteConfig.script() ?: script
        if (src.isEmpty()) {
            engineDown = true
            return null
        }
        val w = WebView(ctx)
        w.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            domStorageEnabled = false
            blockNetworkLoads = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }
        w.addJavascriptInterface(Bridge(), "pw")
        w.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView, url: String) {
                v.evaluateJavascript(src, null)
                ready.complete(Unit)
            }
        }
        w.loadDataWithBaseURL("https://phantom.local/", "<html></html>", "text/html", "utf-8", null)
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
                        .header("User-Agent", Providers.UA)
                    req.optString("referer").takeIf { it.isNotEmpty() }?.let { b.header("Referer", it) }
                    req.optJSONObject("headers")?.let { h ->
                        h.keys().forEach { k -> b.header(k, h.optString(k)) }
                    }
                    val method = req.optString("method", "GET").trim().uppercase().ifEmpty { "GET" }
                    val body = req.optString("body")
                    val type = req.optString("contentType", "application/json").ifEmpty { "application/json" }
                    if (method == "GET" || method == "HEAD") b.method(method, null)
                    else b.method(method, body.toRequestBody(type.toMediaType()))
                    val sec = req.optLong("timeout", 10).coerceIn(1, 20)
                    val call = (if (sec > 10) longClient else Http.client).newCall(b.build())
                    call.timeout().deadline(sec, TimeUnit.SECONDS)
                    call.execute().use { r ->
                        val body = r.body?.string().orEmpty()
                        val host = req.optString("url").substringAfter("://").substringBefore('/')
                        if (r.code !in 200..299) AppLog.d("http $host ${r.code}")
                        val hs = JSONObject()
                        r.headers.toMultimap().forEach { (k, v) -> hs.put(k, JSONArray(v)) }
                        JSONObject().put("code", r.code).put("headers", hs).put("body", body)
                    }
                } catch (e: Exception) {
                    AppLog.d("http ${reqJson.take(80)} ${e.toString().take(80)}")
                    JSONObject().put("error", e.javaClass.simpleName)
                }
                JsProviders.eval(
                    "window.__pwBridge.httpDone(${JSONObject.quote(id)}, ${JSONObject.quote(res.toString())})",
                )
            }
        }

        // blocks this thread until the extractor WebView finds the media URL
        @JavascriptInterface
        fun voe(id: String, code: String) {
            val ctx = app
            if (ctx == null) {
                JsProviders.eval("window.__pwBridge.voeDone(${JSONObject.quote(id)}, ${JSONObject.quote(JSONObject().put("error", "nocx").toString())})")
                return
            }
            voePool.execute {
                val res = try {
                    VoeExtract.resolve(ctx, code)
                } catch (e: Exception) {
                    JSONObject().put("error", e.javaClass.simpleName)
                }
                JsProviders.eval("window.__pwBridge.voeDone(${JSONObject.quote(id)}, ${JSONObject.quote(res.toString())})")
            }
        }

        @JavascriptInterface
        fun web(id: String, url: String) {
            val ctx = app
            if (ctx == null) {
                JsProviders.eval("window.__pwBridge.webDone(${JSONObject.quote(id)}, ${JSONObject.quote(JSONObject().put("error", "nocx").toString())})")
                return
            }
            voePool.execute {
                val res = try {
                    WebExtract.resolve(ctx, url, 25_000)
                } catch (e: Exception) {
                    JSONObject().put("error", e.javaClass.simpleName)
                }
                JsProviders.eval("window.__pwBridge.webDone(${JSONObject.quote(id)}, ${JSONObject.quote(res.toString())})")
            }
        }

        @JavascriptInterface
        fun done(id: String, result: String) {
            val json = try {
                JSONObject(result)
            } catch (_: Exception) {
                null
            }
            JsProviders.waiting.remove(id)?.complete(json)
        }

        @JavascriptInterface
        fun fail(id: String, msg: String) {
            AppLog.d("js fail $msg")
            JsProviders.waiting.remove(id)?.complete(null)
        }

        @JavascriptInterface
        fun log(msg: String) {
            AppLog.d("js $msg")
        }
    }
}
