package dev.phantomwatch

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import org.json.JSONObject

object JsProviders {
    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(4)
    private val seq = AtomicInteger()
    private val started = AtomicBoolean()
    private val created = CompletableDeferred<Unit>()
    private val ready = CompletableDeferred<Unit>()
    private val waiting = ConcurrentHashMap<String, CompletableDeferred<JSONObject?>>()

    private var app: Context? = null
    private var script = ""
    @Volatile
    private var view: WebView? = null

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
        val ctx = app ?: return null
        val src = RemoteConfig.script() ?: script
        if (src.isEmpty()) return null
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
                        .header("Referer", req.optString("referer"))
                    if (req.optString("method", "GET") == "POST") {
                        b.post(req.optString("body").toRequestBody("application/json".toMediaType()))
                    }
                    val call = VidLove.client().newCall(b.build())
                    call.timeout().deadline(req.optLong("timeout", 10).coerceIn(1, 20), TimeUnit.SECONDS)
                    call.execute().use { r ->
                        JSONObject().put("code", r.code).put("body", r.body?.string().orEmpty())
                    }
                } catch (e: Exception) {
                    JSONObject().put("error", e.javaClass.simpleName)
                }
                JsProviders.eval(
                    "window.__pwBridge.httpDone(${JSONObject.quote(id)}, ${JSONObject.quote(res.toString())})",
                )
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
