package dev.phantomwatch.social

import android.content.Context
import android.content.SharedPreferences
import dev.phantomwatch.BuildConfig
import dev.phantomwatch.core.AppLog
import dev.phantomwatch.core.Http
import java.time.OffsetDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

internal object Social {
    const val LIKE = 1
    const val DISLIKE = -1

    data class Stats(val likes: Int = 0, val dislikes: Int = 0, val mine: Int = 0)
    data class Comment(
        val id: Long,
        val body: String,
        val at: Long,
        val author: String,
        val parent: Long,
    )
    data class Page(val items: List<Comment>, val total: Int)

    private class Resp(val code: Int, val body: String, val headers: Map<String, String>)

    private val json = "application/json".toMediaType()
    private val lock = Mutex()
    private val statsCache = HashMap<String, Stats>()
    private var prefs: SharedPreferences? = null

    fun cachedStats(kind: String, id: Int): Stats? = statsCache["$kind:$id"]

    val enabled: Boolean
        get() = BuildConfig.SUPABASE_URL.startsWith("https://") && BuildConfig.SUPABASE_ANON_KEY.isNotEmpty()

    fun init(ctx: Context) {
        prefs = ctx.applicationContext.getSharedPreferences("community", Context.MODE_PRIVATE)
    }

    fun me(): String? = prefs?.getString("uid", null)

    fun ago(at: Long): String {
        if (at <= 0) return ""
        val d = System.currentTimeMillis() - at
        return when {
            d < 60_000 -> "now"
            d < 3_600_000 -> "${d / 60_000}m"
            d < 86_400_000 -> "${d / 3_600_000}h"
            d < 7 * 86_400_000 -> "${d / 86_400_000}d"
            else -> "${d / (7 * 86_400_000)}w"
        }
    }

    suspend fun stats(kind: String, id: Int): Stats? {
        val (at, _) = session() ?: return null
        val r = call(
            "/rest/v1/rpc/title_stats", "POST", at,
            JSONObject().put("p_kind", kind).put("p_tmdb", id).toString(),
        )
        if (r.code !in 200..299) return null
        val row = runCatching { JSONArray(r.body).optJSONObject(0) }.getOrNull() ?: return null
        return Stats(row.optInt("likes"), row.optInt("dislikes"), row.optInt("mine"))
            .also { statsCache["$kind:$id"] = it }
    }

    suspend fun vote(kind: String, id: Int, value: Int): Stats? {
        val (at, _) = session() ?: return null
        val r = call(
            "/rest/v1/rpc/cast_vote", "POST", at,
            JSONObject().put("p_kind", kind).put("p_tmdb", id).put("p_value", value).toString(),
        )
        if (r.code !in 200..299) return null
        val row = runCatching { JSONArray(r.body).optJSONObject(0) }.getOrNull() ?: return null
        return Stats(row.optInt("likes"), row.optInt("dislikes"), row.optInt("mine"))
            .also { statsCache["$kind:$id"] = it }
    }

    suspend fun comments(kind: String, id: Int): Page? {
        val (at, _) = session() ?: return null
        val base = "/rest/v1/comments?kind=eq.$kind&tmdb_id=eq.$id" +
            "&hidden=eq.false&order=created_at.desc&limit=200"
        val headers = mapOf("Prefer" to "count=exact")
        // parent_id is absent until the reply migration lands: fall back to the flat shape
        val r = call("$base&select=id,body,created_at,user_id,parent_id", "GET", at, headers = headers)
            .takeIf { it.code in 200..299 }
            ?: call("$base&select=id,body,created_at,user_id", "GET", at, headers = headers)
        if (r.code !in 200..299) return null
        val arr = runCatching { JSONArray(r.body) }.getOrNull() ?: return null
        val items = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Comment(
                id = o.optLong("id"),
                body = o.optString("body"),
                at = parseTime(o.optString("created_at")),
                author = o.optString("user_id").take(4),
                parent = o.optLong("parent_id"),
            )
        }
        val total = r.headers["content-range"]?.substringAfter('/')?.toIntOrNull() ?: items.size
        return Page(items, total)
    }

    suspend fun post(kind: String, id: Int, text: String, parent: Long = 0): String? {
        val (at, uid) = session() ?: return "Sign-in unavailable"
        val payload = JSONObject()
            .put("user_id", uid).put("kind", kind).put("tmdb_id", id).put("body", text.trim())
        if (parent > 0) payload.put("parent_id", parent)
        val r = call(
            "/rest/v1/comments", "POST", at, payload.toString(),
            mapOf("Prefer" to "return=minimal"),
        )
        return when {
            r.code in 200..299 -> null
            r.body.contains("slow down") -> "Slow down a bit"
            else -> "Could not post"
        }
    }

    private suspend fun session(): Pair<String, String>? {
        if (!enabled) return null
        val p = prefs ?: return null
        return lock.withLock {
            val now = System.currentTimeMillis()
            val at = p.getString("at", null)
            val uid = p.getString("uid", null)
            if (at != null && uid != null && p.getLong("exp", 0) - 60_000 > now) {
                return@withLock at to uid
            }
            p.getString("rt", null)?.let { refresh(it, p) }?.let { return@withLock it }
            signup(p)
        }
    }

    private suspend fun signup(p: SharedPreferences): Pair<String, String>? {
        val r = call("/auth/v1/signup", "POST", null, "{}")
        if (r.code !in 200..299) {
            AppLog.d("community signup ${r.code}")
            return null
        }
        return runCatching { saveSession(p, JSONObject(r.body)) }.getOrNull()
    }

    private suspend fun refresh(rt: String, p: SharedPreferences): Pair<String, String>? {
        val r = call(
            "/auth/v1/token?grant_type=refresh_token", "POST", null,
            JSONObject().put("refresh_token", rt).toString(),
        )
        if (r.code !in 200..299) return null
        return runCatching { saveSession(p, JSONObject(r.body)) }.getOrNull()
    }

    private fun saveSession(p: SharedPreferences, o: JSONObject): Pair<String, String>? {
        val at = o.optString("access_token").takeIf { it.isNotEmpty() } ?: return null
        val uid = o.optJSONObject("user")?.optString("id")?.takeIf { it.isNotEmpty() } ?: return null
        val exp = o.optLong("expires_at", 0).takeIf { it > 0 }
            ?: (System.currentTimeMillis() / 1000 + o.optLong("expires_in", 3600))
        p.edit()
            .putString("at", at)
            .putString("rt", o.optString("refresh_token"))
            .putString("uid", uid)
            .putLong("exp", exp * 1000)
            .apply()
        return at to uid
    }

    private suspend fun call(
        path: String,
        method: String,
        bearer: String?,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): Resp = withContext(Dispatchers.IO) {
        val b = Request.Builder()
            .url(BuildConfig.SUPABASE_URL.trimEnd('/') + path)
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
        if (bearer != null) b.header("Authorization", "Bearer $bearer")
        headers.forEach { (k, v) -> b.header(k, v) }
        if (method == "POST") b.post((body ?: "{}").toRequestBody(json)) else b.get()
        Http.client.newCall(b.build()).execute().use { r ->
            val h = r.headers.names().associate { it.lowercase() to r.header(it).orEmpty() }
            Resp(r.code, r.body?.string().orEmpty(), h)
        }
    }

    private fun parseTime(s: String): Long =
        runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrDefault(0L)
}
