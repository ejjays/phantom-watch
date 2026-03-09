package dev.movieproto

import android.content.Context
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

private const val MAX_VTT = 200_000

object SubsApi {
    private var appCtx: Context? = null
    private var anonToken: String? = null

    fun init(ctx: Context) {
        if (appCtx == null) appCtx = ctx.applicationContext
    }

    private fun prefs() =
        appCtx?.getSharedPreferences("phantom_auth", Context.MODE_PRIVATE)

    suspend fun userToken(): String? {
        anonToken?.let { return it }
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        val anon = BuildConfig.SUPABASE_ANON_KEY
        if (base.isEmpty() || anon.isEmpty()) return null
        val p = prefs()
        if (p != null) {
            val now = System.currentTimeMillis()
            val at = p.getString("access_token", null)
            if (at != null && p.getLong("expires_at", 0) - 60_000 > now) {
                anonToken = at
                return at
            }
            val rt = p.getString("refresh_token", null)
            if (rt != null && refreshSession(base, anon, rt)) return anonToken
        }
        return freshSession(base, anon)
    }

    fun dropSession() {
        anonToken = null
        try {
            prefs()?.edit()?.remove("access_token")?.remove("refresh_token")
                ?.remove("expires_at")?.apply()
        } catch (_: Exception) {
        }
    }

    private fun freshSession(base: String, anon: String): String? {
        val json = postJson(
            "$base/auth/v1/signup", mapOf("apikey" to anon), "{}",
        ) ?: return null
        return saveSession(base, anon, json)
    }

    private fun refreshSession(base: String, anon: String, rt: String): Boolean {
        val json = postJson(
            "$base/auth/v1/token?grant_type=refresh_token", mapOf("apikey" to anon),
            JSONObject().put("refresh_token", rt).toString(),
        ) ?: return false
        return saveSession(base, anon, json) != null
    }

    private fun saveSession(base: String, anon: String, json: JSONObject): String? {
        val at = json.optString("access_token").ifEmpty { return null }
        val uid = json.optJSONObject("user")?.optString("id") ?: ""
        val expIn = json.optLong("expires_in", 3600)
        try {
            prefs()?.edit()
                ?.putString("access_token", at)
                ?.putString("refresh_token", json.optString("refresh_token"))
                ?.putLong("expires_at", System.currentTimeMillis() + expIn * 1000)
                ?.putString("uid", uid)
                ?.apply()
        } catch (_: Exception) {
        }
        anonToken = at
        if (uid.isNotEmpty()) {
            AppLog.d("ai uid=$uid")
            ensureProfile(base, anon, at, uid)
        }
        return at
    }

    private fun ensureProfile(base: String, anon: String, token: String, uid: String) {
        try {
            val p = prefs() ?: return
            var name = p.getString("username", null)
            if (name.isNullOrEmpty()) {
                name = "proto" + (1..6).map { "0123456789abcdef".random() }.joinToString("")
                p.edit().putString("username", name).apply()
            }
            val body = JSONArray().put(
                JSONObject().put("id", uid).put("username", name),
            ).toString()
            val code = postCode(
                "$base/rest/v1/profiles", mapOf(
                    "apikey" to anon,
                    "Authorization" to "Bearer $token",
                    "Prefer" to "resolution=merge-duplicates",
                ), body,
            )
            AppLog.d("ai profile $code")
        } catch (_: Exception) {
        }
    }

    private fun postJson(url: String, headers: Map<String, String>, body: String): JSONObject? {
        return try {
            val b = Request.Builder().url(url).header("Content-Type", "application/json")
            for ((k, v) in headers) b.header(k, v)
            b.post(body.toRequestBody("application/json".toMediaType()))
            val call = VidLove.client().newCall(b.build())
            call.timeout().deadline(15, TimeUnit.SECONDS)
            call.execute().use { res ->
                if (res.code !in 200..299) return null
                try {
                    JSONObject(res.body?.string() ?: return null)
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun postCode(url: String, headers: Map<String, String>, body: String): Int {
        return try {
            val b = Request.Builder().url(url).header("Content-Type", "application/json")
            for ((k, v) in headers) b.header(k, v)
            b.post(body.toRequestBody("application/json".toMediaType()))
            val call = VidLove.client().newCall(b.build())
            call.timeout().deadline(15, TimeUnit.SECONDS)
            call.execute().use { res ->
                res.body?.close()
                res.code
            }
        } catch (_: Exception) {
            -1
        }
    }

    suspend fun store(key: String, label: String, vtt: String, source: String = "ai") {
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        val anon = BuildConfig.SUPABASE_ANON_KEY
        if (base.isEmpty() || anon.isEmpty() || vtt.isEmpty() || vtt.length > MAX_VTT) return
        try {
            val token = userToken() ?: return
            val payload = JSONArray().put(
                JSONObject()
                    .put("key", key)
                    .put("lang", "en")
                    .put("label", label)
                    .put("vtt", vtt)
                    .put("source", source),
            ).toString()
            val code = postCode(
                "$base/rest/v1/subtitles", mapOf(
                    "apikey" to anon,
                    "Authorization" to "Bearer $token",
                    "Prefer" to "resolution=merge-duplicates",
                ), payload,
            )
            if (code in 200..299) AppLog.d("subs cached $key")
            else AppLog.d("subs cache write $code")
        } catch (e: Exception) {
            AppLog.d("subs cache fail ${e.message}")
        }
    }
}
