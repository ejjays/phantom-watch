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
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject

// voe only hands out its media URL to a browser that passes /engine/update, so the page has to
// run. no JS bridge, and the WebView dies with the answer
object VoeExtract {
    private val main = Handler(Looper.getMainLooper())
    private val ads = listOf("phruk.org", "ads-twitter.com", "imasdk.googleapis.com", "doubleclick.net", "googlesyndication.com")

    // the first jwplayer() instance on the page is a telemetry ping, not the video
    private const val PICK = """(function(){
  function media(f){
    if(!f)return false;
    var p=String(f).split('?')[0].split('#')[0].toLowerCase();
    return p.slice(-5)==='.m3u8'||p.slice(-4)==='.mp4';
  }
  function pick(p){
    for(var j=0;j<(p||[]).length;j++){
      var e=p[j]||{},f=e.file||((e.sources&&e.sources[0]&&e.sources[0].file)||'');
      if(media(f))return f;
    }
    return '';
  }
  try{
    var els=document.querySelectorAll('.jwplayer');
    for(var i=0;i<els.length;i++){
      try{
        var pl=jwplayer(els[i]),u=pick(pl.getPlaylist());
        if(!u){try{var c=pl.getConfig();u=(c&&c.file)||'';}catch(e){}}
        if(media(u))return u;
      }catch(e){}
    }
    try{var d=jwplayer(),u2=pick(d.getPlaylist());if(media(u2))return u2;}catch(e){}
    return '';
  }catch(e){return ''}
})()"""

    private const val PLAY = """try{var e=document.querySelectorAll('.jwplayer');for(var i=0;i<e.length;i++){try{jwplayer(e[i]).play()}catch(x){}}}catch(x){}"""

    fun resolve(ctx: Context, code: String, timeoutMs: Long = 12000): JSONObject {
        val s = Session(ctx, code)
        val t0 = System.currentTimeMillis()
        main.post { s.start() }
        if (!s.latch.await(timeoutMs, TimeUnit.MILLISECONDS)) s.settle(JSONObject().put("error", "timeout"))
        val res = s.out.get() ?: JSONObject().put("error", "none")
        AppLog.d("voe $code ${System.currentTimeMillis() - t0}ms ${res.optString("url", res.optString("error"))}")
        return res
    }

    private fun blank(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", 200, "OK", emptyMap(), ByteArrayInputStream(ByteArray(0)))

    private class Session(private val ctx: Context, private val code: String) {
        val latch = CountDownLatch(1)
        val out = AtomicReference<JSONObject>()
        private val host = AtomicReference("voe.sx")
        private val view = AtomicReference<WebView>()
        // path must end in the extension; jwplayer's ping carries a media URL in its query
        private val media = Regex("""https?://[^?#"'\s]+\.(?:m3u8|mp4)(?:[?#].*)?""")

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

        private fun hit(url: String) {
            if (!media.matches(url)) return
            settle(
                JSONObject().put("url", url)
                    .put("hls", url.contains(".m3u8"))
                    .put("referer", "https://" + host.get() + "/"),
            )
        }

        fun start() {
            if (out.get() != null) return
            val w = build()
            view.set(w)
            w.loadUrl("https://voe.sx/e/$code")
        }

        @SuppressLint("SetJavaScriptEnabled")
        private fun build(): WebView {
            val w = WebView(ctx)
            w.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                mediaPlaybackRequiresUserGesture = false
                userAgentString = Providers.UA
            }
            w.webViewClient = object : WebViewClient() {
                override fun onPageStarted(v: WebView, url: String, icon: Bitmap?) {
                    url.substringAfter("://").substringBefore('/').takeIf { it.isNotEmpty() }?.let { host.set(it) }
                }

                override fun shouldOverrideUrlLoading(v: WebView, req: WebResourceRequest): Boolean {
                    val h = req.url.host ?: return true
                    if (h == host.get() || h == "voe.sx" || h.endsWith(".voe.sx")) return false
                    return !req.url.path.orEmpty().contains("/e/$code")
                }

                override fun shouldInterceptRequest(v: WebView, req: WebResourceRequest): WebResourceResponse? {
                    val u = req.url.toString()
                    val h = req.url.host ?: ""
                    if (ads.any { h == it || h.endsWith(".$it") }) return blank()
                    if (media.matches(u)) {
                        hit(u)
                        return blank()
                    }
                    return null
                }

                override fun onPageFinished(v: WebView, url: String) = poll(v, 0)
            }
            return w
        }

        private fun poll(w: WebView, n: Int) {
            if (n > 28 || out.get() != null) return
            main.postDelayed({
                w.evaluateJavascript(PICK) { raw ->
                    val url = raw?.let { runCatching { JSONArray("[$it]").optString(0) }.getOrNull() }.orEmpty()
                    when {
                        url.startsWith("http") -> hit(url)
                        url.startsWith("/") -> hit("https://" + host.get() + url)
                        else -> {
                            if (n == 5) w.evaluateJavascript(PLAY, null)
                            poll(w, n + 1)
                        }
                    }
                }
            }, 400)
        }
    }
}
