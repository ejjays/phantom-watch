package dev.phantomwatch.core

import dev.phantomwatch.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import android.content.Context
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object Telemetry {
    @Volatile
    var appContext: Context? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private const val PREFS = "phantom_settings"
    private const val OPT_OUT = "telemetry_off"

    fun enabled(ctx: Context): Boolean =
        !ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(OPT_OUT, false)

    fun setEnabled(ctx: Context, on: Boolean) {
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(OPT_OUT, !on).apply()
    }

    fun send(body: JSONObject) {
        val url = BuildConfig.TELEMETRY_URL
        if (url.isEmpty()) return
        val ctx = appContext ?: return
        if (!enabled(ctx)) return
        body.put("k", BuildConfig.TELEMETRY_KEY)
        body.put("v", BuildConfig.VERSION_NAME)
        scope.launch {
            runCatching {
                val req = Request.Builder().url(url)
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                Http.client.newCall(req).execute().close()
            }
        }
    }
}
