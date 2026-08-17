package dev.phantomwatch.subs

import dev.phantomwatch.core.AppLog
import dev.phantomwatch.core.Http
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject

/**
 * Streaming transcriber. Unlike a multimodal model it is built for this: it answers with interim
 * text about a second behind the audio and with word-level timestamps on every final.
 */
internal class DeepgramLive(
    private val key: String,
    private val language: String,
    private val onEvent: (Ev) -> Unit,
) {
    sealed interface Ev {
        data object Ready : Ev
        data class Final(val text: String, val start: Double, val end: Double) : Ev
        data class Failed(val why: String) : Ev
    }

    private val ready = CompletableDeferred<Boolean>()
    private var ws: WebSocket? = null

    @Volatile
    var isOpen = false
        private set

    suspend fun open(timeoutMs: Long = 8000): Boolean {
        ws = client.newWebSocket(
            Request.Builder().url("$ENDPOINT?model=nova-3&language=$language&$QUERY")
                .addHeader("Authorization", "Token $key").build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    isOpen = true
                    ready.complete(true)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    handle(bytes.utf8())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handle(text)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    isOpen = false
                    ready.complete(false)
                    AppLog.d("ai subs closed $code $reason")
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    isOpen = false
                    ready.complete(false)
                    AppLog.d("ai subs socket ${t.javaClass.simpleName}: ${t.message}")
                    onEvent(Ev.Failed("${t.javaClass.simpleName}: ${t.message}"))
                }
            },
        )
        val ok = withTimeoutOrNull(timeoutMs) { ready.await() } ?: false
        if (!ok) close()
        return ok
    }

    private fun handle(text: String) {
        val ev = runCatching { parseEvent(JSONObject(text)) }.getOrNull() ?: return
        if (ev is Ev.Ready) ready.complete(true)
        onEvent(ev)
    }

    fun send(frame: ShortArray) {
        if (!isOpen) return
        val w = ws ?: return
        // a half-open socket keeps accepting sends into an unbounded queue; 10 s of backlog
        // means the far end stopped reading, so drop it instead of growing the heap
        if (w.queueSize() > MAX_QUEUE) {
            AppLog.d("ai subs socket stalled")
            close()
            return
        }
        val bytes = ByteArray(frame.size * 2)
        for (i in frame.indices) {
            val v = frame[i].toInt()
            bytes[i * 2] = (v and 0xFF).toByte()
            bytes[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        w.send(ByteString.of(*bytes))
    }

    // forces the current segment out so a line never waits for the speaker to pause
    fun finalize() {
        if (isOpen) ws?.send("""{"type":"Finalize"}""")
    }

    fun close() {
        if (isOpen) ws?.send("""{"type":"CloseStream"}""")
        isOpen = false
        runCatching { ws?.close(1000, null) }
        ws = null
    }

    companion object {
        private const val ENDPOINT = "wss://api.deepgram.com/v1/listen"

        // 10 s of 16 kHz mono at 32 KB/s
        private const val MAX_QUEUE = 320_000

        // endpointing ends a segment after 300ms of quiet, which is what makes the line break
        // at sentence boundaries instead of on a timer
        private const val QUERY =
            "encoding=linear16&sample_rate=16000&channels=1" +
                "&interim_results=true&punctuate=true&smart_format=true&endpointing=300"

        // media3 reports 639-1, 639-2 or a regional tag; deepgram wants its own code
        internal fun languageOf(tag: String?): String {
            val t = tag?.trim()?.lowercase() ?: return "multi"
            if (t == "yue" || t.startsWith("zh-hk") || t.startsWith("zh-yue")) return "zh-HK"
            if (t.startsWith("zh-tw") || t.startsWith("zh-hant")) return "zh-TW"
            return when (t.substringBefore('-').substringBefore('_')) {
                "en", "eng" -> "en"
                "es", "spa" -> "es"
                "fr", "fra", "fre" -> "fr"
                "de", "deu", "ger" -> "de"
                "hi", "hin" -> "hi"
                "ru", "rus" -> "ru"
                "pt", "por" -> "pt"
                "ja", "jpn" -> "ja"
                "it", "ita" -> "it"
                "nl", "nld", "dut" -> "nl"
                "ko", "kor" -> "ko"
                "th", "tha" -> "th"
                "zh", "zho", "chi", "cmn" -> "zh"
                else -> "multi"
            }
        }

        private val client by lazy {
            Http.client.newBuilder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .callTimeout(0, TimeUnit.MILLISECONDS)
                .build()
        }

        internal fun parseEvent(msg: JSONObject): Ev? = when (msg.optString("type")) {
            "Metadata" -> Ev.Ready
            "Results" -> {
                val alt = msg.optJSONObject("channel")
                    ?.optJSONArray("alternatives")
                    ?.optJSONObject(0)
                val text = alt?.optString("transcript")?.trim().orEmpty()
                // interim results grow word by word; drawing them reads like a phone call, not
                // a subtitle, so only finished segments become lines
                if (text.isEmpty() || !msg.optBoolean("is_final")) {
                    null
                } else {
                    val start = msg.optDouble("start", 0.0)
                    Ev.Final(text, start, start + msg.optDouble("duration", 0.0))
                }
            }
            "Error" -> Ev.Failed(msg.optString("description").ifEmpty { "deepgram error" })
            else -> null
        }
    }
}
