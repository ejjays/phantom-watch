package dev.phantomwatch

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class VFormat(val url: String, val isHls: Boolean, val label: String, val height: Int, val english: Boolean = true, val referer: String = "", val enSubs: Boolean = false, val enAudio: Boolean = false, val headers: Map<String, String> = emptyMap())

// Every leg runs in providers.js; this object only talks to that engine.
object Providers {
    internal val UA get() = RemoteConfig.top("ua", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Safari/537.36")

    private data class LegResult(
        val name: String,
        val formats: List<VFormat>,
        val subs: List<Sub>,
        val skipped: Boolean,
        val ms: Long,
    )

    private fun jsArg(
        tmdbId: String, kind: String, season: String, episode: String, title: String, imdb: String?,
    ): JSONObject = JSONObject().apply {
        put("tmdbId", tmdbId)
        put("kind", kind)
        put("season", season)
        put("episode", episode)
        put("title", title)
        if (imdb != null) put("imdb", imdb)
    }

    private fun jsArgs(
        leg: String,
        tmdbId: String,
        kind: String,
        season: String,
        episode: String,
        title: String,
        imdb: String?,
    ): JSONObject = jsArg(tmdbId, kind, season, episode, title, imdb).apply {
        put("cfg", RemoteConfig.legJson(leg))
        put("tmdbKey", RemoteConfig.tmdbKey())
        put("tmdbBase", RemoteConfig.tmdbBase())
    }

    suspend fun directAll(tmdbId: String, kind: String, season: String, episode: String, title: String): Triple<List<VFormat>, List<Sub>, String?> =
        withContext(Dispatchers.IO) {
            AppLog.d("direct $kind/$tmdbId s=$season e=$episode \"$title\"")
            withTimeoutOrNull(40000) {
                coroutineScope {
                    val t0 = System.currentTimeMillis()
                    val imdb = async { VidLove.imdbOf(tmdbId, kind, RemoteConfig.tmdbKey()) }
                    val order = listOf("love", "rock", "vix", "zee", "paradise", "vap", "vidlink", "hianime")
                    val names = order + RemoteConfig.legNames().filter { it !in order }
                    val results = names.map { name ->
                        async {
                            val tLeg = System.currentTimeMillis()
                            if (!RemoteConfig.on(name)) {
                                return@async LegResult(name, emptyList(), emptyList(), true, 0)
                            }
                            val imdbArg = if (name == "vap") imdb.await() else null
                            val args = jsArgs(name, tmdbId, kind, season, episode, title, imdbArg)
                            // hianime walks five sequential calls; 12s was only safe with the old native fallback
                            val budget = when (name) {
                                "paradise" -> 28L
                                "hianime" -> 27L
                                else -> 12L
                            }
                            val js = JsProviders.resolve(name, args, budget)
                            if (js == null) AppLog.d("leg $name no js result")
                            // a dead bridge is not a provider outage; keep it out of the leg stats
                            val skipped = js == null || js.optBoolean("skipped")
                            LegResult(
                                name,
                                formatsOf(js),
                                subsOf(js),
                                skipped,
                                System.currentTimeMillis() - tLeg,
                            )
                        }
                    }.awaitAll()
                    AppLog.d(
                        "direct legs " + results.joinToString(" ") { "${it.name}=${it.formats.size}" } +
                            " @${System.currentTimeMillis() - t0}ms",
                    )
                    val all = results.flatMap { it.formats }
                    val subs = results.flatMap { it.subs }
                    // bans outlive transient 403/429s; one 1-byte probe re-admits a host that recovered
                    val blocked = all.count { Torrents.DeadHosts.blocked(it.url) }
                    if (blocked > 0) AppLog.d("direct dead hosts $blocked")
                    all.filter { Torrents.DeadHosts.blocked(it.url) }
                        .distinctBy { Torrents.DeadHosts.hostOf(it.url) }
                        .forEach { f -> if (alive(f)) Torrents.DeadHosts.forgive(f.url) }
                    val live = all
                        .filter { it.url !in Torrents.DeadReleases.urls && !Torrents.DeadHosts.blocked(it.url) }
                        .distinctBy { it.url }
                    val ordered = JsProviders.sortOrder(live) ?: FormatOrder.sort(live)
                    Triple(ordered, subs.distinctBy { it.url }, imdb.await())
                        .also { report(tmdbId, kind, season, episode, System.currentTimeMillis() - t0, live.size, ordered.firstOrNull(), results) }
                }
            } ?: Triple(emptyList(), emptyList(), null)
        }

    private fun alive(f: VFormat): Boolean {
        return try {
            val b = Request.Builder().url(f.url).header("User-Agent", UA)
            if (f.headers.isNotEmpty()) {
                f.headers.forEach { (k, v) -> b.header(k, v) }
            } else if (f.referer.isNotEmpty()) {
                b.header("Referer", f.referer)
            }
            val call = VidLove.client().newCall(b.header("Range", "bytes=0-0").build())
            call.timeout().deadline(6, java.util.concurrent.TimeUnit.SECONDS)
            call.execute().use { it.code in 200..299 }
        } catch (_: Exception) {
            false
        }
    }

    private fun report(
        tmdbId: String,
        kind: String,
        season: String,
        episode: String,
        ms: Long,
        formats: Int,
        best: VFormat?,
        results: List<LegResult>,
    ) {
        val legs = JSONObject()
        results.forEach { r ->
            if (!r.skipped) {
                legs.put(
                    r.name,
                    JSONArray().put(r.formats.size).put(r.ms)
                        .put(r.formats.maxOfOrNull { it.height } ?: 0)
                        .put(if (r.formats.any { it.enSubs }) 1 else 0),
                )
            }
        }
        Telemetry.send(
            JSONObject()
                .put("ev", "resolve")
                .put("cfg", RemoteConfig.version())
                .put("kind", kind)
                .put("id", tmdbId)
                .put("s", season)
                .put("e", episode)
                .put("ms", ms)
                .put("f", formats)
                .put("pick", best?.label.orEmpty())
                .put("h", best?.height ?: 0)
                .put("ens", best?.enSubs == true)
                .put("ena", best?.enAudio == true)
                .put("hls", best?.isHls == true)
                .put("legs", legs),
        )
    }

    internal fun formatsOf(j: JSONObject?): List<VFormat> {
        val a = j?.optJSONArray("formats") ?: return emptyList()
        return buildList {
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val url = o.optString("url")
                if (url.isEmpty()) continue
                val headers = o.optJSONObject("headers")?.let { h ->
                    buildMap { h.keys().forEach { k -> put(k, h.optString(k)) } }
                }.orEmpty()
                add(
                    VFormat(
                        url,
                        o.optBoolean("isHls"),
                        o.optString("label"),
                        o.optInt("height"),
                        o.optBoolean("english", true),
                        o.optString("referer"),
                        o.optBoolean("enSubs"),
                        o.optBoolean("enAudio"),
                        headers,
                    ),
                )
            }
        }
    }

    internal fun subsOf(j: JSONObject?): List<Sub> {
        val a = j?.optJSONArray("subs") ?: return emptyList()
        return buildList {
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val url = o.optString("url")
                if (url.isEmpty()) continue
                add(Sub(o.optString("label").ifEmpty { "Unknown" }, url, o.optString("src")))
            }
        }
    }

    // own-provider subs fit this cut; others drift, so they're only a fallback
    fun subsFor(f: VFormat, subs: List<Sub>): List<Sub> {
        if (f.label.startsWith("rock-")) return emptyList()
        return subs.filter { it.src.isEmpty() || f.label.startsWith(it.src) }
    }

    fun providerName(f: VFormat): String = when {
        f.label.startsWith("love-") -> "VidLove"
        f.label.startsWith("rock-") -> "VidRock"
        f.label.startsWith("vix-") -> "VixSrc"
        f.label.startsWith("zee-") -> "VidZee"
        f.label.startsWith("paradise-") -> "AnimeParadise"
        f.label.startsWith("vap-") -> "VAPlayer"
        f.label.startsWith("vidlink-") -> "VidLink"
        f.label.startsWith("hianime-") -> "HiAnime"
        else -> f.label
    }
}
