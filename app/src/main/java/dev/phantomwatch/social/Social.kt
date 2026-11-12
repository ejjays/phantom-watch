package dev.phantomwatch.social

import android.content.Context
import android.content.SharedPreferences
import dev.phantomwatch.BuildConfig
import dev.phantomwatch.R
import dev.phantomwatch.core.AppLog
import dev.phantomwatch.core.Http
import dev.phantomwatch.data.Account
import java.time.OffsetDateTime
import kotlinx.coroutines.CancellationException
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
        val mine: Boolean = false,
    )

    enum class ReportReason(val wire: String) {
        SPAM("spam"), HARASSMENT("harassment"), SPOILER("spoiler"), OTHER("other"),
    }
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

    fun account(): Account? {
        val p = prefs ?: return null
        val email = p.getString("email", null)?.takeIf { it.isNotEmpty() } ?: return null
        return Account(
            name = p.getString("name", null).orEmpty(),
            email = email,
            picture = p.getString("picture", null)?.takeIf { it.isNotEmpty() },
        )
    }

    suspend fun signInWithGoogle(idToken: String): Boolean {
        val p = prefs ?: return false
        val r = call(
            "/auth/v1/token?grant_type=id_token", "POST", null,
            JSONObject().put("provider", "google").put("id_token", idToken).toString(),
        )
        if (r.code !in 200..299) {
            AppLog.d("community google ${r.code} ${r.body.take(160)}")
            return false
        }
        return runCatching { saveSession(p, JSONObject(r.body)) != null }.getOrDefault(false)
    }

    fun signOut() {
        prefs?.edit()
            ?.remove("at")?.remove("rt")?.remove("uid")?.remove("exp")
            ?.remove("name")?.remove("email")?.remove("picture")?.apply()
        statsCache.clear()
    }

    internal fun validCommentBody(body: String): Boolean = body.isNotBlank() && body.trim().length <= 500

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
            "&hidden=eq.false&moderation_status=eq.visible&order=created_at.desc&limit=200"
        val headers = mapOf("Prefer" to "count=exact")
        // parent_id is absent until the reply migration lands: fall back to the flat shape
        val r = call("$base&select=id,body,created_at,user_id,parent_id,moderation_status", "GET", at, headers = headers)
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
                mine = o.optString("user_id") == me(),
            )
        }
        val total = r.headers["content-range"]?.substringAfter('/')?.toIntOrNull() ?: items.size
        return Page(items, total)
    }

    suspend fun post(kind: String, id: Int, text: String, parent: Long = 0): Int? {
        val (at, _) = session() ?: return R.string.social_signin_unavailable
        val body = text.trim()
        if (kind !in setOf("movie", "tv") || id <= 0) return R.string.social_invalid_title
        if (!validCommentBody(body)) return R.string.social_bad_body
        val payload = JSONObject()
            .put("kind", kind).put("tmdb_id", id).put("body", body)
        if (parent > 0) payload.put("parent_id", parent)
        val r = call(
            "/rest/v1/comments", "POST", at, payload.toString(),
            mapOf("Prefer" to "return=minimal"),
        )
        return when {
            r.code in 200..299 -> null
            r.body.contains("rate limit") || r.body.contains("wait before posting") || r.body.contains("slow down") -> R.string.social_rate_limited
            r.body.contains("invalid reply") -> R.string.social_reply_gone
            r.body.contains("invalid title") -> R.string.social_invalid_title
            r.body.contains("comment must be") -> R.string.social_bad_body
            else -> R.string.social_post_failed
        }
    }

    suspend fun deleteComment(id: Long): Boolean {
        val (at, _) = session() ?: return false
        if (id <= 0) return false
        val r = call(
            "/rest/v1/comments?id=eq.$id", "DELETE", at,
            headers = mapOf("Prefer" to "return=representation"),
        )
        if (r.code !in 200..299) return false
        return r.body.isBlank() || r.body.trim() != "[]"
    }

    suspend fun reportComment(id: Long, reason: ReportReason, details: String = ""): Boolean {
        val (at, _) = session() ?: return false
        if (id <= 0 || details.length > 300) return false
        val r = call(
            "/rest/v1/rpc/report_comment", "POST", at,
            JSONObject().put("p_comment", id).put("p_reason", reason.wire)
                .put("p_details", details.trim()).toString(),
        )
        return r.code in 200..299
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
        val user = o.optJSONObject("user") ?: return null
        val uid = user.optString("id").takeIf { it.isNotEmpty() } ?: return null
        val exp = o.optLong("expires_at", 0).takeIf { it > 0 }
            ?: (System.currentTimeMillis() / 1000 + o.optLong("expires_in", 3600))
        val edit = p.edit()
            .putString("at", at)
            .putString("rt", o.optString("refresh_token"))
            .putString("uid", uid)
            .putLong("exp", exp * 1000)
        val meta = user.optJSONObject("user_metadata")
        val name = meta?.optString("full_name").orEmpty().ifBlank { meta?.optString("name").orEmpty() }
        val email = user.optString("email").orEmpty()
        // anonymous sessions have neither, so they never wipe the google profile
        if (name.isNotEmpty() || email.isNotEmpty()) {
            edit.putString("name", name)
                .putString("email", email)
                .putString("picture", meta?.optString("avatar_url").orEmpty())
        }
        edit.apply()
        return at to uid
    }

    private suspend fun call(
        path: String,
        method: String,
        bearer: String?,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): Resp = withContext(Dispatchers.IO) {
        try {
            val b = Request.Builder()
                .url(BuildConfig.SUPABASE_URL.trimEnd('/') + path)
                .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            if (bearer != null) b.header("Authorization", "Bearer $bearer")
            headers.forEach { (k, v) -> b.header(k, v) }
            when (method) {
                "POST" -> b.post((body ?: "{}").toRequestBody(json))
                "DELETE" -> b.delete()
                else -> b.get()
            }
            Http.client.newCall(b.build()).execute().use { r ->
                val h = r.headers.names().associate { it.lowercase() to r.header(it).orEmpty() }
                Resp(r.code, r.body?.string().orEmpty(), h)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.d("community ${e.javaClass.simpleName}")
            Resp(0, "", emptyMap())
        }
    }

    private fun parseTime(s: String): Long =
        runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrDefault(0L)
}
