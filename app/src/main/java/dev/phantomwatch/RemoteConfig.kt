package dev.phantomwatch

import android.content.Context
import android.util.Base64
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.TimeUnit
import okhttp3.Request
import org.json.JSONObject

object RemoteConfig {
    private const val PREFS = "phantom_cfg"
    private const val KEY = "providers"

    @Volatile
    private var root: JSONObject = JSONObject()

    @Volatile
    private var script: String? = null

    @Volatile
    private var pub: PublicKey? = null

    private var prefs: android.content.SharedPreferences? = null

    fun load(ctx: Context) {
        val app = ctx.applicationContext
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        pub = pubKey(app)
        val asset = try {
            app.assets.open("providers.json").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            AppLog.d("cfg asset ${e.javaClass.simpleName}")
            null
        }
        val assetVer = asset?.let { runCatching { JSONObject(it).optInt("version") }.getOrDefault(0) } ?: 0
        val cached = prefs?.getString(KEY, null)
        if (cached != null && applySigned(cached) && root.optInt("version") >= assetVer) {
            AppLog.d("cfg load v${root.optInt("version")} js=${script?.length ?: 0}")
            return
        }
        if (asset != null && applyBare(asset)) {
            AppLog.d("cfg asset v${root.optInt("version")} js=${script?.length ?: 0}")
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
                val seen = root.optInt("version")
                if (!applySigned(body, seen + 1)) {
                    AppLog.d("cfg remote skipped v$seen")
                    return
                }
                val sp = prefs ?: ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                sp.edit().putString(KEY, body).apply()
                AppLog.d("cfg v${root.optInt("version")} legs=${root.getJSONObject("legs").length()} js=${script?.length ?: 0}")
            }
        } catch (e: Exception) {
            AppLog.d("cfg fail ${e.javaClass.simpleName}")
        }
    }

    fun on(leg: String): Boolean = leg(leg)?.optBoolean("enabled", true) ?: true

    fun legNames(): List<String> = root.optJSONObject("legs")?.keys()?.asSequence()?.toList().orEmpty()

    fun legJson(name: String): JSONObject =
        leg(name)?.let { JSONObject(it.toString()).apply { remove("enabled") } } ?: JSONObject()

    fun s(leg: String, field: String, def: String): String =
        leg(leg)?.optString(field).orEmpty().ifEmpty { def }

    fun list(leg: String, field: String, def: List<String>): List<String> {
        val a = leg(leg)?.optJSONArray(field) ?: return def
        val out = buildList { for (i in 0 until a.length()) add(a.optString(i)) }
        return out.ifEmpty { def }
    }

    fun top(field: String, def: String): String = root.optString(field).ifEmpty { def }

    fun script(): String? = script

    private fun leg(name: String): JSONObject? = root.optJSONObject("legs")?.optJSONObject(name)

    private fun applyBare(raw: String): Boolean = try {
        val cfg = JSONObject(raw)
        if (!accept(cfg)) return false
        root = cfg
        script = cfg.optString("script").ifEmpty { null }
        true
    } catch (_: Exception) {
        false
    }

    private fun applySigned(raw: String, minVersion: Int = 0): Boolean = try {
        val env = JSONObject(raw)
        val p64 = env.optString("payload")
        val s64 = env.optString("sig")
        if (p64.isEmpty() || s64.isEmpty()) return false
        val payload = Base64.decode(p64, Base64.DEFAULT)
        if (!verify(payload, s64)) {
            AppLog.d("cfg sig bad")
            return false
        }
        val cfg = JSONObject(String(payload, Charsets.UTF_8))
        if (!accept(cfg) || cfg.optInt("version") < minVersion) return false
        root = cfg
        script = cfg.optString("script").ifEmpty { null }
        true
    } catch (_: Exception) {
        false
    }

    private fun verify(payload: ByteArray, sigB64: String): Boolean {
        val key = pub ?: return false
        return try {
            val s = Signature.getInstance("SHA256withECDSA")
            s.initVerify(key)
            s.update(payload)
            s.verify(Base64.decode(sigB64, Base64.DEFAULT))
        } catch (_: Exception) {
            false
        }
    }

    private fun pubKey(app: Context): PublicKey? = try {
        val pem = app.assets.open("ota_pub.pem").bufferedReader().use { it.readText() }
        val b64 = pem.replace(Regex("-----[^-]+-----"), "").replace(Regex("\\s"), "")
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.decode(b64, Base64.DEFAULT)))
    } catch (e: Exception) {
        AppLog.d("cfg pub ${e.javaClass.simpleName}")
        null
    }

    // a config that switches every leg off would leave the app with no sources at all
    private fun accept(cfg: JSONObject): Boolean {
        val legs = cfg.optJSONObject("legs") ?: return false
        if (legs.length() == 0) return false
        return legs.keys().asSequence().any { legs.optJSONObject(it)?.optBoolean("enabled", true) == true }
    }
}
