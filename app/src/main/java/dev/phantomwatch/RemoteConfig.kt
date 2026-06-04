package dev.phantomwatch

import android.content.Context
import java.util.concurrent.TimeUnit
import okhttp3.Request
import org.json.JSONObject

object RemoteConfig {
    private const val PREFS = "phantom_cfg"
    private const val KEY = "providers"

    @Volatile
    private var root: JSONObject = JSONObject()

    fun load(ctx: Context) {
        val app = ctx.applicationContext
        val cached = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        val raw = cached ?: try {
            app.assets.open("providers.json").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            AppLog.d("cfg asset ${e.javaClass.simpleName}")
            return
        }
        try {
            val cfg = JSONObject(raw)
            if (!accept(cfg)) {
                AppLog.d("cfg rejected")
                return
            }
            root = cfg
        } catch (_: Exception) {
            AppLog.d("cfg bad json")
        }
    }

    fun refresh(ctx: Context) {
        if (BuildConfig.CFG_URL.isBlank()) return
        try {
            val call = VidLove.client().newCall(Request.Builder().url(BuildConfig.CFG_URL.trim()).build())
            call.timeout().deadline(4, TimeUnit.SECONDS)
            call.execute().use { res ->
                if (!res.isSuccessful) {
                    AppLog.d("cfg http ${res.code}")
                    return
                }
                val body = res.body?.string().orEmpty()
                val cfg = try {
                    JSONObject(body)
                } catch (_: Exception) {
                    AppLog.d("cfg bad json")
                    return
                }
                if (!accept(cfg)) {
                    AppLog.d("cfg rejected")
                    return
                }
                root = cfg
                ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY, body).apply()
                AppLog.d("cfg v${cfg.optInt("version")} ${cfg.getJSONObject("legs").length()} legs")
            }
        } catch (e: Exception) {
            AppLog.d("cfg fail ${e.javaClass.simpleName}")
        }
    }

    fun on(leg: String): Boolean = leg(leg)?.optBoolean("enabled", true) ?: true

    fun s(leg: String, field: String, def: String): String =
        leg(leg)?.optString(field).orEmpty().ifEmpty { def }

    fun list(leg: String, field: String, def: List<String>): List<String> {
        val a = leg(leg)?.optJSONArray(field) ?: return def
        val out = buildList { for (i in 0 until a.length()) add(a.optString(i)) }
        return out.ifEmpty { def }
    }

    fun top(field: String, def: String): String = root.optString(field).ifEmpty { def }

    private fun leg(name: String): JSONObject? = root.optJSONObject("legs")?.optJSONObject(name)

    // a config that switches every leg off would leave the app with no sources at all
    private fun accept(cfg: JSONObject): Boolean {
        val legs = cfg.optJSONObject("legs") ?: return false
        if (legs.length() == 0) return false
        return legs.keys().asSequence().any { legs.optJSONObject(it)?.optBoolean("enabled", true) == true }
    }
}
