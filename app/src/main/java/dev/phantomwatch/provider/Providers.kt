package dev.phantomwatch.provider

import dev.phantomwatch.core.AppLog
import dev.phantomwatch.core.Http
import dev.phantomwatch.core.RemoteConfig
import dev.phantomwatch.core.Telemetry
import dev.phantomwatch.data.Dub
import dev.phantomwatch.data.Stream
import dev.phantomwatch.data.withDub
import dev.phantomwatch.data.Sub
import dev.phantomwatch.data.Title
import dev.phantomwatch.data.Tmdb
import dev.phantomwatch.torrent.Torrents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class VideoFormat(val url: String, val isHls: Boolean, val label: String, val height: Int, val english: Boolean = true, val referer: String = "", val enSubs: Boolean = false, val enAudio: Boolean = false, val headers: Map<String, String> = emptyMap(), val intro: LongRange? = null, val dub: String? = null, val subs: List<Sub> = emptyList(), val outro: LongRange? = null)

// every leg runs in providers.js; this object only talks to that engine
object Providers {
    internal val UA get() = RemoteConfig.valueOr("ua", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Safari/537.36")

    private data class LegResult(
        val name: String,
        val formats: List<VideoFormat>,
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
        put("cfg", RemoteConfig.providerJson(leg))
        put("tmdbKey", RemoteConfig.tmdbKey())
        put("tmdbBase", RemoteConfig.tmdbBase())
    }

    internal fun isTopTier(f: VideoFormat): Boolean =
        f.height >= 1080 && (f.enSubs || f.enAudio || f.dub != null) && !f.label.endsWith("-auto")

    private fun legBudget(name: String): Long = when (name) {
        "paradise" -> 28L
        "hianime" -> 27L
        "pinoy" -> 22L
        "videasy", "net79" -> 25L
        else -> 12L
    }

    private suspend fun collectLegs(names: List<String>, ch: Channel<LegResult>, t0: Long): List<LegResult> {
        val completed = mutableListOf<LegResult>()
        var hasTopTier = false
        var topTierAt = 0L
        for (i in names.indices) {
            val hianimePending = names.contains("hianime") && completed.none { it.name == "hianime" }
            val timeoutMs = if (hasTopTier) {
                if (hianimePending) {
                    // hianime's dub scrape lands late; give it the longer budget
                    val remaining = 6_500L - (System.currentTimeMillis() - t0)
                    remaining.coerceAtLeast(0L)
                } else {
                    val elapsed = System.currentTimeMillis() - topTierAt
                    (800L - elapsed).coerceAtLeast(0L)
                }
            } else {
                40_000L
            }
            val leg = withTimeoutOrNull(timeoutMs) { ch.receive() } ?: break
            completed.add(leg)
            if (!hasTopTier && leg.formats.any { isTopTier(it) }) {
                hasTopTier = true
                topTierAt = System.currentTimeMillis()
            }
        }
        val completedNames = completed.map { it.name }.toSet()
        return completed + names.filter { it !in completedNames }.map {
            LegResult(it, emptyList(), emptyList(), true, 0)
        }
    }

    suspend fun directAll(tmdbId: String, kind: String, season: String, episode: String, title: String): Triple<List<VideoFormat>, List<Sub>, String?> =
        withContext(Dispatchers.IO) {
            AppLog.d("direct $kind/$tmdbId s=$season e=$episode \"$title\"")
            withTimeoutOrNull(40000) {
                coroutineScope {
                    val t0 = System.currentTimeMillis()
                    val imdb = async { Tmdb.imdbOf(tmdbId, kind, RemoteConfig.tmdbKey()) }
                    val order = listOf("hianime", "paradise", "rock", "vix", "zee", "vap", "vidlink")
                    val names = order + RemoteConfig.providerNames().filter { it !in order }
                    val ch = Channel<LegResult>(Channel.UNLIMITED)
                    val jobs = names.map { name ->
                        launch {
                            val tLeg = System.currentTimeMillis()
                            if (!RemoteConfig.isEnabled(name)) {
                                ch.send(LegResult(name, emptyList(), emptyList(), true, 0))
                                return@launch
                            }
                            val imdbArg = if (name == "vap") imdb.await() else null
                            val args = jsArgs(name, tmdbId, kind, season, episode, title, imdbArg)
                            val js = JsProviders.resolve(name, args, legBudget(name))
                            if (js == null) AppLog.d("leg $name no js result")
                            // a dead bridge is not a provider outage, so keep it out of the leg stats
                            val skipped = js == null || js.optBoolean("skipped")
                            ch.send(
                                LegResult(
                                    name,
                                    formatsOf(js),
                                    subsOf(js),
                                    skipped,
                                    System.currentTimeMillis() - tLeg,
                                ),
                            )
                        }
                    }
                    val results = collectLegs(names, ch, t0).also { jobs.forEach { j -> j.cancel() } }
                    AppLog.d(
                        "direct legs " + results.joinToString(" ") { "${it.name}=${it.formats.size}" } +
                            " @${System.currentTimeMillis() - t0}ms",
                    )
                    val byName = results.associateBy { it.name }
                    val all = names.flatMap { byName[it]?.formats.orEmpty() }
                    val subs = names.flatMap { byName[it]?.subs.orEmpty() }
                    // bans outlive transient 403/429s; one 1-byte probe re-admits a host that recovered
                    val blocked = all.filter { Torrents.DeadHosts.blocked(it.url) }
                        .distinctBy { Torrents.DeadHosts.hostOf(it.url) }
                    if (blocked.isNotEmpty()) {
                        AppLog.d("direct dead hosts ${blocked.size}")
                        withTimeoutOrNull(6000) {
                            coroutineScope {
                                blocked.map { f -> async { if (alive(f)) Torrents.DeadHosts.forgive(f.url) } }
                                    .awaitAll()
                            }
                        }
                    }
                    val live = all.filter { it.url !in Torrents.DeadReleases.urls }.distinctBy { it.url }
                    val ordered = rankLast(JsProviders.sortOrder(live) ?: FormatOrder.sort(live)) {
                        Torrents.DeadHosts.blocked(it)
                    }
                    Triple(ordered, subs.distinctBy { it.url }, imdb.await())
                        .also { report(tmdbId, kind, season, episode, System.currentTimeMillis() - t0, live.size, ordered.firstOrNull(), results) }
                }
            } ?: Triple(emptyList(), emptyList(), null)
        }

    private fun alive(f: VideoFormat): Boolean {
        return try {
            val b = Request.Builder().url(f.url).header("User-Agent", UA)
            if (f.headers.isNotEmpty()) {
                f.headers.forEach { (k, v) -> b.header(k, v) }
            } else if (f.referer.isNotEmpty()) {
                b.header("Referer", f.referer)
            }
            val call = Http.client.newCall(b.header("Range", "bytes=0-0").build())
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
        best: VideoFormat?,
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

    internal fun formatsOf(j: JSONObject?): List<VideoFormat> {
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
                    VideoFormat(
                        url,
                        o.optBoolean("isHls"),
                        o.optString("label"),
                        o.optInt("height"),
                        o.optBoolean("english", true),
                        o.optString("referer"),
                        o.optBoolean("enSubs"),
                        o.optBoolean("enAudio"),
                        headers,
                        o.optJSONObject("intro")?.let {
                            LongRange(it.optLong("start") * 1000, it.optLong("end") * 1000)
                        },
                        o.optString("dub").ifEmpty { null },
                        subsOf(o),
                        o.optJSONObject("outro")?.let {
                            LongRange(it.optLong("start") * 1000, it.optLong("end") * 1000)
                        },
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

    // a dub keeps the captions its own cut declares: hianime hands both cuts the same vtt because
    // they share one timeline (Solo Leveling s1e1: 1420.0s each), so filtering shared files hid real subs
    fun savedDub(dubs: List<Dub>, saved: String?): Dub? {
        if (saved == null) return null
        val pick = dubs.firstOrNull { !it.base && it.label == saved }
            ?: dubs.firstOrNull { !it.base && it.label.substringBefore(" · ") == saved.substringBefore(" · ") }
        return pick?.takeUnless { Torrents.DeadHosts.blocked(it.url) }
    }

    fun restore(s: Stream, saved: String?): Stream {
        val d = savedDub(s.dubs, saved) ?: return s
        AppLog.d("dub restored ${d.label}")
        return s.withDub(d)
    }

    fun dubBase(s: Stream): Stream? {
        if (s.dub == null) return null
        val b = s.dubs.firstOrNull { it.base } ?: return null
        return s.withDub(b)
    }

    fun langName(code: String?): String? {
        if (code.isNullOrBlank()) return null
        return java.util.Locale.forLanguageTag(code).getDisplayLanguage(java.util.Locale.ENGLISH).ifBlank { null }
    }

    fun dubsOf(formats: List<VideoFormat>, base: VideoFormat, baseSubs: List<Sub>, baseName: String = "Sub"): List<Dub> {
        val dubs = formats.mapNotNull { f -> f.dub?.let { "$it · ${providerName(f)}" to f } }
            .sortedByDescending { if (it.second.label.endsWith("-auto")) 1 else 0 }
            .distinctBy { it.first }
            .map { (label, f) -> Dub(label, f.url, f.isHls, f.referer, f.headers, f.subs, f.enSubs) }
        if (dubs.isEmpty()) return emptyList()
        val back = Dub("$baseName · ${providerName(base)}", base.url, base.isHls, base.referer, base.headers, baseSubs, base.enSubs, base = true)
        return listOf(back) + dubs
    }

    // own-provider subs fit this cut; other legs' files drift, so they are only a fallback
    fun subsFor(f: VideoFormat, subs: List<Sub>): List<Sub> {
        if (f.label.startsWith("rock-")) return emptyList()
        val own = subs.filter { it.src.isEmpty() || f.label.startsWith(it.src) }
        if (own.isNotEmpty() || f.dub != null) return own
        return subs.filter { it.label.contains("english", true) }
    }

    fun providerName(f: VideoFormat): String = when {
        f.label.startsWith("love-") -> "VidLove"
        f.label.startsWith("rock-") -> "VidRock"
        f.label.startsWith("vix-") -> "VixSrc"
        f.label.startsWith("zee-") -> "VidZee"
        f.label.startsWith("paradise-") -> "AnimeParadise"
        f.label.startsWith("vap-") -> "VAPlayer"
        f.label.startsWith("vidlink-") -> "VidLink"
        f.label.startsWith("hianime-") -> "HiAnime"
        f.label.startsWith("pinoy-") -> "Pinoy"
        else -> f.label
    }

    // a wrong ban must cost rank, not access: a banned host sinks so the title still plays
    internal fun rankLast(formats: List<VideoFormat>, blocked: (String) -> Boolean): List<VideoFormat> {
        val (ok, banned) = formats.partition { !blocked(it.url) }
        return ok + banned
    }

    // the best format the last resolve still holds, so a dead edge costs one switch
    internal fun nextFormat(
        formats: List<VideoFormat>,
        failedUrl: String,
        dead: Set<String>,
        blocked: (String) -> Boolean,
    ): VideoFormat? {
        val pool = formats.filter { it.url != failedUrl && it.url !in dead }
        val open = pool.filterNot { blocked(it.url) }.ifEmpty { pool }
        val first = open.firstOrNull() ?: return null
        // an "auto" rung advertises a height it may not serve; prefer a real rung when it keeps the subs
        if (!first.label.endsWith("-auto")) return first
        val real = open.filter { !it.label.endsWith("-auto") && it.enSubs == first.enSubs }
        return real.firstOrNull { it.height >= 720 } ?: real.firstOrNull() ?: first
    }

    suspend fun nextStream(media: Title, season: String, episode: String, failedUrl: String, fresh: Boolean = false): Stream? {
        // a later failure may mean the signed urls expired, so that one gets a fresh resolve
        val b = withTimeoutOrNull(20_000) { Resolve.streams(media, season, episode, fresh) } ?: return null
        val f = nextFormat(b.formats, failedUrl, Torrents.DeadReleases.urls, Torrents.DeadHosts::blocked)
            ?: return null
        AppLog.d("fallback ${providerName(f)} ${f.label} h=${f.height}")
        return Stream(
            url = f.url, isHls = f.isHls, subs = subsFor(f, b.subs), referer = f.referer,
            sampleMp4 = null, imdb = b.imdb, headers = f.headers, enSubs = f.enSubs, intro = f.intro,
            dub = f.dub, outro = f.outro,
        )
    }
}
