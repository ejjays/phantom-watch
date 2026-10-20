package dev.phantomwatch.provider

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.phantomwatch.core.AppLog
import dev.phantomwatch.core.RemoteConfig
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject

object WebExtract {
    private val main = Handler(Looper.getMainLooper())
    private val ads = listOf(
        "doubleclick.net", "googlesyndication.com", "imasdk.googleapis.com",
        "rubiconproject.com", "scorecardresearch.com", "llvpn.com",
    )
    private val media = Regex("""https?://[^?#"'\s]+\.(?:m3u8|mp4)(?:[?#].*)?""")
    private const val HOOK = """(function(){
  if (window.__pwHook) return; window.__pwHook = 1; window.__pwCap = [];
  try {
    var oo = XMLHttpRequest.prototype.open;
    var os = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.open = function(m, u) { this.__url = String(u || ''); return oo.apply(this, arguments); };
    XMLHttpRequest.prototype.send = function() {
      if (this.__url && /sources/.test(this.__url)) this.addEventListener('load', function () { window.__pwCap.push(this.responseText); });
      return os.apply(this, arguments);
    };
  } catch (e) {}
})()"""
    private const val KICK = """try { var v = document.querySelector('video'); if (v) v.play(); } catch (e) {}
try { var b = document.querySelector('#play,.play-btn,[data-play]'); if (b) b.click(); } catch (e) {}"""

    fun resolve(ctx: Context, url: String, timeoutMs: Long = 20000): JSONObject {
        val s = Session(ctx, url)
        main.post { s.start() }
        if (!s.latch.await(timeoutMs, TimeUnit.MILLISECONDS)) s.settle(JSONObject().put("error", "timeout"))
        return s.out.get() ?: JSONObject().put("error", "none")
    }

    private fun blank(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", 200, "OK", emptyMap(), ByteArrayInputStream(ByteArray(0)))

    private fun origin(url: String): String = try {
        URI(url).let { it.scheme + "://" + it.host }
    } catch (e: Exception) {
        url
    }

    private fun fromRaw(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            List(a.length()) { a.optString(it) }
        }.getOrElse { emptyList() }
    }

    private class Session(private val ctx: Context, private val target: String) {
        val latch = CountDownLatch(1)
        val out = AtomicReference<JSONObject>()
        private val view = AtomicReference<WebView>()
        private val origin = origin(target)

        fun settle(res: JSONObject) {
            if (!out.compareAndSet(null, res)) return
            latch.countDown()
            main.post {
                view.getAndSet(null)?.let {
                    it.stopLoading()
                    it.destroy()
                }
            }
        }

        fun start() {
            if (out.get() != null) return
            val w = build()
            view.set(w)
            w.loadUrl(target)
        }

        @SuppressLint("SetJavaScriptEnabled")
        private fun build(): WebView {
            val w = WebView(ctx)
            w.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                blockNetworkLoads = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                mediaPlaybackRequiresUserGesture = false
                userAgentString = Providers.UA
            }
            w.webViewClient = object : WebViewClient() {
                override fun onPageStarted(v: WebView, url: String, icon: Bitmap?) {
                    AppLog.d("web start $url")
                }

                override fun shouldOverrideUrlLoading(v: WebView, req: WebResourceRequest): Boolean =
                    req.url.host?.let { h -> ads.any { h == it || h.endsWith(".$it") } } ?: true

                override fun shouldInterceptRequest(v: WebView, req: WebResourceRequest): WebResourceResponse? {
                    val u = req.url.toString()
                    val h = req.url.host ?: ""
                    if (ads.any { h == it || h.endsWith(".$it") }) return blank()
                    if (media.matches(u)) {
                        settle(
                            JSONObject().put("url", u)
                                .put("hls", u.contains(".m3u8"))
                                .put("referer", "$origin/"),
                        )
                        return blank()
                    }
                    return null
                }

                override fun onPageFinished(v: WebView, url: String) {
                    v.evaluateJavascript(HOOK, null)
                    v.evaluateJavascript(KICK, null)
                    pollCap(v, 0)
                }
            }
            return w
        }

        private fun pollCap(w: WebView, n: Int) {
            if (n > 24 || out.get() != null) return
            main.postDelayed({
                w.evaluateJavascript("JSON.stringify(window.__pwCap || [])") { raw ->
                    if (out.get() != null) return@evaluateJavascript
                    fromRaw(raw).firstOrNull { it.contains("\"sources\"") }
                        ?.let { settle(JSONObject().put("body", it).put("referer", "$origin/")) }
                        ?: pollCap(w, n + 1)
                }
            }, 400)
        }
    }
}
