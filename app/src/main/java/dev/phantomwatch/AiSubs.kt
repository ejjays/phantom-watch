package dev.phantomwatch

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object AiSubs {
    suspend fun keys(): List<String> = withContext(Dispatchers.IO) {
        val personal = BuildConfig.GROQ_API_KEY.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val out = mutableListOf<String>()
        out.addAll(personal)
        out.addAll(fetchRemoteKeys())
        AppLog.d("ai keys=${out.distinct().size}")
        return@withContext out.distinct().take(6)
    }

    private suspend fun fetchRemoteKeys(): List<String> {
        val out = mutableListOf<String>()
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        if (base.isEmpty()) return out
        repeat(3) { attempt ->
            try {
                val token = SubsApi.userToken() ?: return out
                val url = "$base/rest/v1/ai_service_keys?provider=eq.groq&is_active=eq.true&select=api_key"
                val req = okhttp3.Request.Builder()
                    .url(url)
                    .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    .header("Authorization", "Bearer $token")
                    .build()
                val call = VidLove.client().newCall(req)
                call.timeout().deadline(15, TimeUnit.SECONDS)
                val code: Int
                val body: String?
                call.execute().use { res ->
                    code = res.code
                    body = if (code == 200) res.body?.string() else null
                }
                if (code == 401) {
                    SubsApi.dropSession()
                    return@repeat
                }
                if (body == null) {
                    if (attempt < 2) kotlinx.coroutines.delay(2000)
                    return@repeat
                }
                val arr = org.json.JSONArray(body)
                for (i in 0 until arr.length()) {
                    val k = arr.optJSONObject(i)?.optString("api_key")?.trim() ?: ""
                    if (k.isNotEmpty()) out.add(k)
                }
                return out
            } catch (_: Exception) {
                if (attempt < 2) kotlinx.coroutines.delay(2000)
            }
        }
        return out
    }

    private fun parseNumberedLines(text: String, count: Int): List<String>? {
        val byNumber = mutableMapOf<Int, String>()
        for (raw in text.split('\n')) {
            val m = Regex("^[(\\[]?\\*{0,2}(\\d+)\\W+(.+)$").find(raw.trim()) ?: continue
            val line = m.groupValues[2].trim().trim('"', '\'').trim()
            if (line.isEmpty()) continue
            byNumber[m.groupValues[1].toIntOrNull() ?: continue] = line
        }
        if (byNumber.size == count) {
            return (1..count).map { byNumber[it] ?: return null }
        }
        return null
    }

    suspend fun translateLines(texts: List<String>, keys: List<String>, from: String = "ja"): List<String>? {
        if (texts.isEmpty() || keys.isEmpty()) return emptyList()
        val fromName = when (from) {
            "ja" -> "Japanese"
            "ko" -> "Korean"
            "zh" -> "Chinese"
            "th" -> "Thai"
            "ar" -> "Arabic"
            "ru" -> "Russian"
            else -> from
        }
        val out = mutableListOf<String>()
        var at = 0
        var rr = 0
        while (at < texts.size) {
            val batch = texts.subList(at, minOf(at + 10, texts.size))
            var done: List<String>? = null
            var attempt = 0
            while (attempt < maxOf(2, keys.size) && done == null) {
                if (attempt > 0) kotlinx.coroutines.delay(12000)
                val key = keys[(rr + attempt) % keys.size]
                done = translateBatch(batch, key, fromName)
                attempt += 1
            }
            if (done == null) return null
            rr = (rr + 1) % keys.size
            out.addAll(done)
            at += 10
        }
        AppLog.d("ai qwen lines=${out.size} from=$from")
        return out.map { balanceParens(it) }
    }

    private fun balanceParens(s: String): String {
        var t = s.replace('（', '(').replace('）', ')')
        val m = Regex("^([A-Za-z][A-Za-z .'-]*)\\)\\s*(.*)$").find(t.trim())
        if (m != null) t = "(${m.groupValues[1]}) ${m.groupValues[2]}".trim()
        return t
    }

    private fun translateBatch(texts: List<String>, key: String, fromName: String = "Japanese"): List<String>? {
        return try {
            val isCerebras = key.startsWith("csk-")
            val endpoint = if (isCerebras) "https://api.cerebras.ai/v1/chat/completions"
            else "https://api.groq.com/openai/v1/chat/completions"
            val model = if (isCerebras) "qwen-3.8-27b" else "qwen/qwen3.8-27b"
            val numbered = texts.mapIndexed { i, t -> "${i + 1}. $t" }.joinToString("\n")
            val payload = JSONObject()
                .put("model", model)
                .put("temperature", 0.2)
                .put("max_tokens", 400)
                .put(
                    "messages",
                    org.json.JSONArray()
                        .put(
                            JSONObject()
                                .put("role", "system")
                                .put(
                                    "content",
                                    "You translate $fromName TV subtitles to natural English. " +
                                        "Return ONLY the translated lines, numbered exactly like the input, " +
                                        "one per line. Never merge, split, drop, or explain lines. " +
                                        "Convert full-width brackets （） to balanced half-width parentheses ( ). " +
                                        "No intro or commentary.",
                                ),
                        )
                        .put(JSONObject().put("role", "user").put("content", numbered)),
                )
                .toString()
            val req = okhttp3.Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer $key")
                .header("Content-Type", "application/json")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            val call = VidLove.client().newCall(req)
            call.timeout().deadline(60, TimeUnit.SECONDS)
            val body = call.execute().use { res ->
                val b = res.body?.string()
                if (res.code == 429 || res.code !in 200..299) {
                    AppLog.d("ai qwen http=${res.code} ${(b ?: "").take(160)}")
                    return null
                }
                b ?: return null
            }
            val content = JSONObject(body)
                .optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")?.optString("content") ?: return null
            if (content.isBlank()) {
                AppLog.d("ai qwen empty content")
                return null
            }
            parseNumberedLines(content, texts.size)
        } catch (e: Exception) {
            AppLog.d("ai qwen fail ${e.message?.take(160)}")
            null
        }
    }
}
