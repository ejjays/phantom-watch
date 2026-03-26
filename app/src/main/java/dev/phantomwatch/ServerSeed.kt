package dev.phantomwatch

import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder

data class SeedFile(val path: String, val size: Long, val progress: Double)

object Seedbox {
    data class Cfg(val base: String, val user: String, val pass: String)

    fun cfg(): Cfg? {
        val base = BuildConfig.SEEDBOX_URL.trim().trimEnd('/')
        if (base.isEmpty()) return null
        return Cfg(base, BuildConfig.SEEDBOX_USER, BuildConfig.SEEDBOX_PASS)
    }

    fun authHeader(c: Cfg): String {
        val raw = "${c.user}:${c.pass}".toByteArray()
        return "Basic " + android.util.Base64.encodeToString(raw, android.util.Base64.NO_WRAP)
    }

    private fun call(c: Cfg, path: String, form: FormBody? = null): String {
        val b = Request.Builder()
            .url(c.base + path)
            .header("User-Agent", "PhantomWatch/1.0")
            .header("Authorization", authHeader(c))
        if (form != null) b.post(form) else b.get()
        VidLove.client().newCall(b.build()).execute().use { res ->
            if (res.code !in 200..299) throw RuntimeException("seedbox http ${res.code}")
            return res.body?.string().orEmpty()
        }
    }

    fun add(c: Cfg, magnet: String, saveName: String): Boolean {
        val form = FormBody.Builder()
            .add("urls", magnet)
            .add("savepath", "/srv/seed/$saveName")
            .add("sequentialDownload", "true")
            .add("firstLastPiecePrio", "true")
            .build()
        return try {
            call(c, "/qbt/api/v2/torrents/add", form)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun files(c: Cfg, hash: String): List<SeedFile> {
        return try {
            val arr = JSONArray(call(c, "/qbt/api/v2/torrents/files?hash=$hash"))
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(SeedFile(o.optString("name"), o.optLong("size"), o.optDouble("progress")))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun fileUrl(c: Cfg, hash: String, path: String): String {
        val enc = path.split("/").joinToString("/") {
            URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
        return "${c.base}/files/$hash/$enc"
    }

    fun delete(c: Cfg, hash: String) {
        try {
            call(
                c, "/qbt/api/v2/torrents/delete",
                FormBody.Builder().add("hashes", hash).add("deleteFiles", "true").build(),
            )
        } catch (_: Exception) {
        }
    }
}
