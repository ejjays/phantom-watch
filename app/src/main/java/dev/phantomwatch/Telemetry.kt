package dev.phantomwatch

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object Telemetry {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun send(body: JSONObject) {
        val url = BuildConfig.TELEMETRY_URL
        if (url.isEmpty()) return
        body.put("k", BuildConfig.TELEMETRY_KEY)
        body.put("v", BuildConfig.VERSION_NAME)
        scope.launch {
            runCatching {
                val req = Request.Builder().url(url)
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                VidLove.client().newCall(req).execute().close()
            }
        }
    }
}
