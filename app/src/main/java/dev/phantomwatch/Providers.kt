package dev.phantomwatch

import java.net.URL
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class VFormat(val url: String, val isHls: Boolean, val label: String, val height: Int, val english: Boolean = true, val referer: String = "", val enSubs: Boolean = false, val enAudio: Boolean = false)

object Providers {
    internal val UA get() = RemoteConfig.top("ua", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Safari/537.36")
    private fun cfg(leg: String, field: String, def: String) = RemoteConfig.s(leg, field, def)

    private val ROCK_API get() = cfg("rock", "api", "https://vidrock.net/api")
    private val ROCK_REFERER get() = cfg("rock", "referer", "https://vidrock.net/")
    private val ROCK_KEY get() = cfg(
        "rock", "key",
        "7f3e9c2a8b5d1f4e6a9c3b7d2e5f8a1c4b6d9e2f5a8c1b4d7e9f2a5c8b1d4e7f",
    )
    private val ROCK_NAMES get() = RemoteConfig.list("rock", "candidates", listOf("flix", "hind", "telugu", "tamil", "hindi"))
    private val LOVE_API get() = cfg("love", "api", "https://api.vidlove.cc")
    private val LOVE_PLAYER get() = cfg("love", "player", "https://player.vidlove.cc")
    private val LOVE_SOURCES get() = RemoteConfig.list("love", "sources", listOf("", "vidapi", "moviebox", "dcloud", "tcloud", "moviebox2"))
    private val VIX_SITE get() = cfg("vix", "site", "https://vixsrc.to")
    private val VIX_REFERER get() = "$VIX_SITE/"
    private val ZEE_API get() = cfg("zee", "api", "https://core.vidzee.wtf")
    private val ZEE_ORIGIN get() = cfg("zee", "origin", "https://player.vidzee.wtf")
    private val ZEE_SOURCE get() = cfg("zee", "source", "dcloud")
    private val PARA_API get() = cfg("paradise", "api", "https://api.animeparadise.moe")
    private val PARA_STREAM get() = cfg("paradise", "stream", "https://stream.animeparadise.moe")
    private val PARA_ORIGIN get() = cfg("paradise", "origin", "https://animeparadise.moe")
    private val VAPLAYER_API get() = cfg("vap", "api", "https://streamdata.vaplayer.ru/api.php")
    private val VAPLAYER_ORIGIN get() = cfg("vap", "origin", "https://nextgencloudfabric.com")
    private val VL_API get() = cfg("vidlink", "api", "https://vidlink.pro/api/b")
    private val VL_ENC get() = cfg("vidlink", "enc", "https://enc-dec.app/api/enc-vidlink?text=")
    private val VL_REFERER get() = cfg("vidlink", "referer", "https://vidlink.pro/")
    private val MEGA_ORIGIN get() = cfg("hianime", "origin", "https://megaplay.buzz")
    // megaplay player JS crypto; breaks when they bump lib/e1-player (see probe.js)
    private val MEGA_KEY get() = MEGA_KEY_S.toByteArray().copyOf(32)
    private val MEGA_IV get() = cfg("hianime", "iv", "hlsMediaAttached").toByteArray()
    private val MEGA_KEY_S get() = cfg("hianime", "key", "i?LMTAx0Q6,:}50U")

    private fun hexBytes(hex: String): ByteArray {
        val c = hex.trim()
        return ByteArray(c.length / 2) { i ->
            c.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    fun rockDecrypt(enc: String): String {
        var s = enc.trim().replace('-', '+').replace('_', '/')
        repeat((4 - s.length % 4) % 4) { s += "=" }
        val raw = android.util.Base64.decode(s, android.util.Base64.DEFAULT)
        require(raw.size >= 28)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(hexBytes(ROCK_KEY), "AES"),
            GCMParameterSpec(128, raw, 0, 12),
        )
        val pt = c.doFinal(raw, 12, raw.size - 12)
        return String(pt, Charsets.UTF_8)
    }

    private fun fetch(url: String, referer: String, timeoutSec: Long = 10): Pair<Int, String>? {
        return try {
            val req = Request.Builder().url(url)
                .header("User-Agent", UA)
                .header("Referer", referer)
                .build()
            val call = VidLove.client().newCall(req)
            call.timeout().deadline(timeoutSec, java.util.concurrent.TimeUnit.SECONDS)
            call.execute().use { res ->
                res.code to (res.body?.string().orEmpty())
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun alive(url: String, referer: String): Boolean {
        return try {
            val req = Request.Builder().url(url)
                .header("User-Agent", UA)
                .header("Referer", referer)
                .header("Range", "bytes=0-0")
                .build()
            val call = VidLove.client().newCall(req)
            call.timeout().deadline(6, java.util.concurrent.TimeUnit.SECONDS)
            call.execute().use { it.code in 200..299 }
        } catch (_: Exception) {
            false
        }
    }

    private fun absUrl(rel: String, base: String): String {
        return try {
            if (rel.startsWith("http")) rel else URL(URL(base), rel).toString()
        } catch (_: Exception) {
            rel
        }
    }

    private fun masterVariants(text: String, base: String): List<VFormat> {
        val out = mutableListOf<VFormat>()
        var pending = -1
        for (raw in text.lines()) {
            val t = raw.trim()
            if (t.startsWith("#EXT-X-STREAM-INF")) {
                pending = Regex("RESOLUTION=\\d+x(\\d+)").find(t)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            } else if (t.isNotEmpty() && !t.startsWith("#") && pending >= 0) {
                out.add(VFormat(absUrl(t, base), true, "${if (pending > 0) "${pending}p" else "auto"}", pending))
                pending = -1
            }
        }
        return out.sortedByDescending { it.height }
    }

    // EXT-X-MEDIA renditions declared by a master playlist
    private fun enRenditions(text: String): Pair<Boolean, Boolean> {
        val media = Regex("""#EXT-X-MEDIA:[^\r\n]*""").findAll(text).map { it.value }
        val en = Regex("""LANGUAGE="?en""")
        return media.any { "TYPE=SUBTITLES" in it && en.containsMatchIn(it) } to
            media.any { "TYPE=AUDIO" in it && en.containsMatchIn(it) }
    }

    private fun jsonLevels(text: String): List<VFormat>? {
        return try {
            val arr = JSONArray(text)
            if (arr.length() == 0) return null
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val u = o.optString("url")
                    if (!u.startsWith("http")) continue
                    val h = o.optInt("resolution", 0)
                    add(VFormat(u, u.contains(".m3u8"), if (h > 0) "${h}p" else "source", h))
                }
            }.sortedByDescending { it.height }.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    private data class RockCandidate(val name: String, val url: String, val type: String, val english: Boolean)

    private suspend fun probeRock(c: RockCandidate): List<VFormat> = withContext(Dispatchers.IO) {
        if (c.type == "mp4" && !c.url.contains(".m3u8")) {
            return@withContext listOf(VFormat(c.url, false, "rock-${c.name}", 1080, c.english))
        }
        val (c2, text) = fetch(c.url, ROCK_REFERER, 8) ?: return@withContext emptyList()
        if (c2 !in 200..299 || !text.contains("#EXTM3U")) {
            if (c.url.contains(".mp4")) return@withContext listOf(VFormat(c.url, false, "rock-${c.name}", 1080, c.english))
            return@withContext emptyList()
        }
        if (!text.contains("#EXT-X-STREAM-INF")) {
            return@withContext listOf(VFormat(c.url, true, "rock-${c.name}", 0, c.english))
        }
        masterVariants(text, c.url).map { it.copy(label = "rock-${c.name}-${it.label}", english = c.english) }
    }

    private suspend fun vidrock(tmdbId: String, kind: String, season: String, episode: String): List<VFormat> =
        coroutineScope {
            if (!RemoteConfig.on("rock")) return@coroutineScope emptyList()
            val path = if (kind == "movie") "movie/$tmdbId" else "tv/$tmdbId/$season/$episode"
            val (code, body) = fetch("$ROCK_API/$path", ROCK_REFERER) ?: return@coroutineScope emptyList()
            if (code != 200) return@coroutineScope emptyList()
            val api = try {
                JSONObject(body)
            } catch (_: Exception) {
                return@coroutineScope emptyList()
            }
            val keys = ROCK_NAMES.flatMap { listOf(it, "${it}_dub", "${it}_sub") }
            val names = (api.keys().asSequence().toList() + keys).distinct()
            val cands = names.take(6).mapNotNull { name ->
                val e = api.optJSONObject(name) ?: return@mapNotNull null
                val enc = e.optString("url")
                if (enc.isEmpty()) return@mapNotNull null
                val url = try {
                    rockDecrypt(enc)
                } catch (_: Exception) {
                    return@mapNotNull null
                }
                if (!url.startsWith("http")) return@mapNotNull null
                val lang = e.optString("language")
                RockCandidate(name, url, e.optString("type"), lang.isEmpty() || lang.startsWith("english", true))
            }
            cands.map { async { probeRock(it) } }.awaitAll().flatten()
                .map { it.copy(referer = ROCK_REFERER) }
        }

    private suspend fun vidlove(tmdbId: String, kind: String, season: String, episode: String): Pair<List<VFormat>, List<Sub>> =
        coroutineScope {
            if (!RemoteConfig.on("love")) return@coroutineScope emptyList<VFormat>() to emptyList()
            val base = if (kind == "movie") "$LOVE_API/movie?id=$tmdbId&mode=json"
            else "$LOVE_API/tv?id=$tmdbId&season=$season&episode=$episode&mode=json"
            val embed = if (kind == "movie") "$LOVE_PLAYER/embed/movie/$tmdbId"
            else "$LOVE_PLAYER/embed/tv/$tmdbId/$season/$episode"
            val referer = embed
            val urls = LOVE_SOURCES.map { if (it.isEmpty()) base else "$base&sources=$it" }
            val bodies: List<String> = urls.map { u ->
                async {
                    fetch(u, referer, 10)?.takeIf { it.first == 200 }?.second
                }
            }.awaitAll().filterNotNull()
            val formats = mutableListOf<VFormat>()
            val subs = mutableListOf<Sub>()
            val seenSubs = mutableSetOf<String>()
            for (body in bodies) {
                val root = try {
                    JSONObject(body)
                } catch (_: Exception) {
                    continue
                }
                val src = root.optJSONObject("source") ?: continue
                val srcUrl = src.optString("url")
                if (srcUrl.isEmpty()) continue
                val tag = src.optString("label").ifEmpty { "love" }
                val box = tag.contains("moviebox", true)
                for (q in 0 until (src.optJSONArray("qualities")?.length() ?: 0)) {
                    val e = src.optJSONArray("qualities")!!.optJSONObject(q) ?: continue
                    val u = e.optString("url")
                    if (!u.startsWith("http")) continue
                    val h = Regex("(\\d{3,4})p").find(e.optString("quality"))?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    formats.add(VFormat(u, u.contains(".m3u8"), "love-$tag-${if (h > 0) "${h}p" else "src"}", if (box) -h else h))
                }
                val manifest = src.optString("manifest")
                if (manifest.contains("#EXTM3U") && manifest.contains("#EXT-X-STREAM-INF")) {
                    formats.addAll(
                        masterVariants(manifest, srcUrl).map {
                            VFormat(it.url, true, "love-$tag-${it.label}", if (box) -it.height else it.height)
                        },
                    )
                } else if (manifest.contains("#EXTM3U")) {
                    formats.add(VFormat(srcUrl, true, "love-$tag-src", 0))
                }
                root.optJSONArray("subtitles")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val f = arr.optJSONObject(i)?.optString("file").orEmpty()
                        val l = arr.optJSONObject(i)?.optString("label")?.trim().orEmpty().ifEmpty { "Unknown" }
                        if (f.startsWith("http") && seenSubs.add(f)) subs.add(Sub(l, f, "love"))
                    }
                }
            }
            if (formats.isEmpty()) AppLog.d("leg love empty ${bodies.size}/${urls.size} bodies")
            val enSubs = subs.any { it.label.contains("english", true) }
            formats.sortedByDescending { it.height }.map { it.copy(referer = embed, enSubs = enSubs) } to subs
        }

    // dcloud is the only live variant: tik serves png, ipcloud 403 (re-tested 2026-10-16)
    private suspend fun vidzee(tmdbId: String, kind: String, season: String, episode: String): List<VFormat> =
        withContext(Dispatchers.IO) {
            if (!RemoteConfig.on("zee")) return@withContext emptyList()
            val path = if (kind == "movie") "/streams/movie/$tmdbId" else "/streams/tv/$tmdbId/$season/$episode"
            val (code, body) = fetch("$ZEE_API$path?s=$ZEE_SOURCE&e=0", "$ZEE_ORIGIN/", 8) ?: return@withContext emptyList()
            if (code != 200) return@withContext emptyList()
            val url = try {
                JSONObject(body).optString("url")
            } catch (_: Exception) {
                return@withContext emptyList()
            }
            if (!url.startsWith("http")) return@withContext emptyList()
            listOf(VFormat(url, true, "zee-dcloud-auto", 0, true, "$ZEE_ORIGIN/"))
        }

    private suspend fun vixsrc(tmdbId: String, kind: String, season: String, episode: String): List<VFormat> =
        withContext(Dispatchers.IO) {
            if (!RemoteConfig.on("vix")) return@withContext emptyList()
            val api = if (kind == "movie") "$VIX_SITE/api/movie/$tmdbId"
            else "$VIX_SITE/api/tv/$tmdbId/$season/$episode"
            val (c1, t1) = fetch(api, VIX_REFERER, 8) ?: return@withContext emptyList()
            if (c1 !in 200..299) return@withContext emptyList()
            val src = try {
                JSONObject(t1).optString("src")
            } catch (_: Exception) {
                return@withContext emptyList()
            }
            if (!src.startsWith("/embed/")) return@withContext emptyList()
            val (_, html) = fetch("$VIX_SITE$src", VIX_REFERER, 8) ?: return@withContext emptyList()
            val token = Regex("['\"]token['\"]:\\s?['\"]([^'\"]*)['\"]").find(html)?.groupValues?.get(1).orEmpty()
            val expires = Regex("['\"]expires['\"]:\\s?['\"]([^'\"]*)['\"]").find(html)?.groupValues?.get(1).orEmpty()
            val pageUrl = Regex("url:\\s?['\"]([^'\"]*)['\"]").find(html)?.groupValues?.get(1).orEmpty()
            if (token.isEmpty() || expires.isEmpty() || pageUrl.isEmpty()) return@withContext emptyList()
            val base = try {
                URL(URL(pageUrl), "")
            } catch (_: Exception) {
                return@withContext emptyList()
            }
            val q = base.query?.let { "$it&" }.orEmpty()
            val master = "${base.protocol}://${base.host}${base.path}.m3u8?${q}token=$token&expires=$expires&h=1"
            val (_, m3u8) = fetch(master, VIX_REFERER, 8) ?: return@withContext emptyList()
            masterVariants(m3u8, master).map {
                it.copy(label = "vix-${it.label}", referer = VIX_REFERER)
            }.let { rungs ->
                if (rungs.isEmpty()) emptyList()
                else {
                    val (enSubs, enAudio) = enRenditions(m3u8)
                    listOf(VFormat(master, true, "vix-auto", rungs.maxOf { it.height }, true, VIX_REFERER, enSubs, enAudio)) + rungs
                }
            }
        }

    private suspend fun vaplayer(imdb: String, kind: String, season: String, episode: String): List<VFormat> =
        withContext(Dispatchers.IO) {
            if (!RemoteConfig.on("vap")) return@withContext emptyList()
            val query = if (kind == "movie") "imdb=$imdb&type=movie"
            else "imdb=$imdb&type=tv&season=$season&episode=$episode"
            val referer = if (kind == "movie") "$VAPLAYER_ORIGIN/embed/movie/$imdb"
            else "$VAPLAYER_ORIGIN/embed/tv/$imdb/$season/$episode"
            val (code, body) = fetch("$VAPLAYER_API?$query", referer, 10) ?: return@withContext emptyList()
            if (code != 200) return@withContext emptyList()
            val master = try {
                JSONObject(body).optJSONObject("data")?.optJSONArray("stream_urls")?.optString(0)
            } catch (_: Exception) {
                null
            }
            if (master.isNullOrEmpty() || !master.startsWith("http")) return@withContext emptyList()
            val (_, manifest) = fetch(master, "$VAPLAYER_ORIGIN/", 8) ?: return@withContext emptyList()
            if (!manifest.contains("#EXTM3U")) return@withContext emptyList()
            val variants = masterVariants(manifest, master).map {
                it.copy(label = "vap-${it.label}", referer = "$VAPLAYER_ORIGIN/")
            }
            val (enSubs, enAudio) = enRenditions(manifest)
            listOf(VFormat(master, true, "vap-auto", variants.maxOfOrNull { it.height } ?: 0, true, "$VAPLAYER_ORIGIN/", enSubs, enAudio)) + variants
        }

    private fun normTitle(title: String): String =
        title.lowercase().replace(Regex("[^a-z0-9]+"), "")

    private suspend fun vidlink(tmdbId: String, kind: String, season: String, episode: String): Pair<List<VFormat>, List<Sub>> =
        withContext(Dispatchers.IO) {
            if (!RemoteConfig.on("vidlink")) return@withContext (emptyList<VFormat>() to emptyList())
            val enc = fetch("$VL_ENC$tmdbId", VL_REFERER, 8)?.takeIf { it.first == 200 }?.second?.let {
                try {
                    JSONObject(it).optString("result")
                } catch (_: Exception) {
                    ""
                }
            }.orEmpty()
            if (enc.isEmpty()) return@withContext (emptyList<VFormat>() to emptyList())
            val path = if (kind == "movie") "movie/$enc" else "tv/$enc/$season/$episode"
            val (code, body) = fetch("$VL_API/$path", VL_REFERER, 10) ?: return@withContext (emptyList<VFormat>() to emptyList())
            if (code != 200) return@withContext (emptyList<VFormat>() to emptyList())
            val stream = try {
                JSONObject(body).optJSONObject("stream")
            } catch (_: Exception) {
                null
            } ?: return@withContext (emptyList<VFormat>() to emptyList())
            val subs = mutableListOf<Sub>()
            stream.optJSONArray("captions")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val u = o.optString("url")
                    val l = o.optString("language").ifEmpty { "Unknown" }
                    if (u.startsWith("http")) subs.add(Sub(l, u, "vidlink"))
                }
            }
            val enSubs = subs.any { it.label.equals("English", true) }
            val formats = mutableListOf<VFormat>()
            stream.optJSONObject("qualities")?.let { q ->
                for (key in q.keys()) {
                    val u = q.optJSONObject(key)?.optString("url").orEmpty()
                    if (!u.startsWith("http")) continue
                    val h = key.toIntOrNull() ?: 0
                    formats.add(VFormat(u, false, "vidlink-${if (h > 0) "${h}p" else "src"}", h, true, VL_REFERER, enSubs))
                }
            }
            formats.sortedByDescending { it.height } to subs
        }

    private fun postJson(url: String, referer: String, json: String, timeoutSec: Long = 10): JSONObject? =
        try {
            val req = Request.Builder().url(url)
                .header("User-Agent", UA)
                .header("Referer", referer)
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()
            val call = VidLove.client().newCall(req)
            call.timeout().deadline(timeoutSec, java.util.concurrent.TimeUnit.SECONDS)
            call.execute().use { res ->
                if (res.code != 200) null else JSONObject(res.body?.string().orEmpty())
            }
        } catch (_: Exception) {
            null
        }

    private fun megaDecrypt(enc: String): String {
        var s = enc.trim().replace('-', '+').replace('_', '/')
        repeat((4 - s.length % 4) % 4) { s += "=" }
        val raw = android.util.Base64.decode(s, android.util.Base64.DEFAULT)
        val c = Cipher.getInstance("AES/CBC/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(MEGA_KEY, "AES"), IvParameterSpec(MEGA_IV))
        val pt = c.doFinal(raw)
        val pad = pt.last().toInt() and 0xFF
        return if (pad in 1..16) String(pt, 0, pt.size - pad, Charsets.UTF_8) else String(pt, Charsets.UTF_8)
    }

    private suspend fun hianime(tmdbId: String, kind: String, season: String, episode: String): Pair<List<VFormat>, List<Sub>> =
        withContext(Dispatchers.IO) {
            if (!RemoteConfig.on("hianime")) return@withContext (emptyList<VFormat>() to emptyList())
            val ep = if (kind == "movie") "movie" else "tv"
            val meta = fetch("https://api.themoviedb.org/3/$ep/$tmdbId?api_key=${BuildConfig.TMDB_API_KEY}", "https://www.themoviedb.org/")?.takeIf { it.first == 200 }?.second
            val title = try {
                val o = JSONObject(meta.orEmpty())
                (o.optString("name").ifEmpty { o.optString("title") }).substringBefore(':').trim()
            } catch (_: Exception) {
                ""
            }
            if (title.isEmpty()) return@withContext (emptyList<VFormat>() to emptyList())
            val gql = "{\"query\":\"query(\$s:String){Media(search:\$s,type:ANIME){idMal}}\",\"variables\":{\"s\":" + JSONObject.quote(title) + "}}"
            val mal = postJson("https://graphql.anilist.co", "https://anilist.co/", gql, 8)
                ?.optJSONObject("data")?.optJSONObject("Media")?.optInt("idMal", 0) ?: 0
            if (mal <= 0) return@withContext (emptyList<VFormat>() to emptyList())
            val epNum = if (kind == "movie") 1 else episode.toIntOrNull()?.takeIf { it > 0 } ?: 1
            val page = "$MEGA_ORIGIN/stream/mal/$mal/$epNum/sub"
            val (_, html) = fetch(page, page, 10) ?: return@withContext (emptyList<VFormat>() to emptyList())
            val dataId = Regex("""data-id=["'](\d+)["']""").find(html)?.groupValues?.get(1)
            AppLog.d("ani mal=$mal page=${html.length} id=$dataId")
            if (dataId == null) return@withContext (emptyList<VFormat>() to emptyList())
            val srcObj = try {
                fetch("$MEGA_ORIGIN/stream/getSources?id=$dataId&id=$dataId", page, 10)
                    ?.takeIf { it.first == 200 }?.second?.let { JSONObject(it) }
            } catch (_: Exception) {
                null
            }
            val enc = srcObj?.optString("enc").orEmpty()
            if (enc.isEmpty()) return@withContext (emptyList<VFormat>() to emptyList())
            val master = try {
                val m = Regex("""([a-z0-9.-]+\.[a-z]+/anime/[a-f0-9]+/[a-f0-9]+/master\.m3u8)""").find(megaDecrypt(enc))
                if (m != null) "https://${m.groupValues[1]}" else ""
            } catch (_: Exception) {
                ""
            }
            AppLog.d("ani enc=${enc.length} master=${master.take(60)}")
            if (master.isEmpty()) return@withContext (emptyList<VFormat>() to emptyList())
            val (_, manifest) = fetch(master, "$MEGA_ORIGIN/", 10) ?: return@withContext (emptyList<VFormat>() to emptyList())
            if (!manifest.contains("#EXTM3U")) return@withContext (emptyList<VFormat>() to emptyList())
            val subs = mutableListOf<Sub>()
            val tracks = srcObj?.optJSONArray("tracks")
            for (i in 0 until (tracks?.length() ?: 0)) {
                val t = tracks!!.optJSONObject(i) ?: continue
                val u = t.optString("file")
                val l = t.optString("label")
                if (u.startsWith("http") && l.equals("English", true)) subs.add(Sub("English", u, "hianime"))
            }
            val (mEnSubs, mEnAudio) = enRenditions(manifest)
            val enSubs = mEnSubs || subs.isNotEmpty()
            val rungs = masterVariants(manifest, master).map {
                it.copy(label = "hianime-${it.label}", referer = "$MEGA_ORIGIN/", enSubs = enSubs)
            }
            val auto = VFormat(master, true, "hianime-auto", rungs.maxOfOrNull { it.height } ?: 0, true, "$MEGA_ORIGIN/", enSubs, mEnAudio)
            AppLog.d("ani fmts=${rungs.size + 1} max=${rungs.maxOfOrNull { it.height } ?: 0} subs=${subs.size}")
            (listOf(auto) + rungs) to subs
        }

    private suspend fun paradise(tmdbId: String, kind: String, season: String, episode: String, title: String): Pair<List<VFormat>, List<Sub>> =
        withContext(Dispatchers.IO) {
            if (!RemoteConfig.on("paradise")) return@withContext (emptyList<VFormat>() to emptyList())
            if (kind != "tv" || season != "1" || title.isEmpty()) return@withContext (emptyList<VFormat>() to emptyList<Sub>())
            fun getJson(path: String): JSONObject? {
                val (code, body) = fetch("$PARA_API$path", "$PARA_ORIGIN/", 8) ?: return null
                if (code != 200) return null
                return try {
                    JSONObject(body)
                } catch (_: Exception) {
                    null
                }
            }
            val want = normTitle(title)
            var showId = ""
            var link = ""
            run {
                val data = getJson("/search?q=${java.net.URLEncoder.encode(title, "UTF-8")}&limit=20")?.optJSONArray("data")
                    ?: return@run
                for (i in 0 until data.length()) {
                    val o = data.optJSONObject(i) ?: continue
                    val li = o.optString("link")
                    if (normTitle(o.optString("title")) != want && normTitle(li) != want) continue
                    val id = o.optString("_id")
                    if (id.isEmpty() || li.isEmpty()) continue
                    val type = getJson("/anime/$li")?.optJSONObject("data")?.optString("type")
                    if (type != "TV") continue
                    showId = id
                    link = li
                    break
                }
            }
            if (showId.isEmpty()) return@withContext (emptyList<VFormat>() to emptyList<Sub>())
            var uid = ""
            val eps = getJson("/anime/$showId/episode")?.optJSONArray("data")
            if (eps != null) {
                for (i in 0 until eps.length()) {
                    val o = eps.optJSONObject(i) ?: continue
                    if ((o.opt("number")?.toString() ?: "") == episode && o.optString("uid").isNotEmpty()) {
                        uid = o.optString("uid")
                        break
                    }
                }
            }
            if (uid.isEmpty()) return@withContext (emptyList<VFormat>() to emptyList<Sub>())
            val ep = getJson("/ep/$uid?origin=$showId")?.optJSONObject("data")?.optJSONObject("episode")
            val streamLink = ep?.optString("streamLink").orEmpty()
            if (streamLink.isEmpty()) return@withContext (emptyList<VFormat>() to emptyList<Sub>())
            val subs = mutableListOf<Sub>()
            ep?.optJSONArray("subData")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val u = o.optString("src")
                    val l = o.optString("label")
                    if (o.optString("type") == "vtt" && u.startsWith("http") && l.contains("english", true)) subs.add(Sub(l, u, "paradise"))
                }
            }
            val masterUrl = "$PARA_STREAM/m3u8?url=${java.net.URLEncoder.encode(streamLink, "UTF-8")}"
            val (_, manifest) = fetch(masterUrl, "$PARA_ORIGIN/", 8) ?: return@withContext (emptyList<VFormat>() to emptyList<Sub>())
            if (!manifest.contains("#EXTM3U")) return@withContext (emptyList<VFormat>() to emptyList<Sub>())
            val (rSubs, rAudio) = enRenditions(manifest)
            val enSubs = rSubs || subs.isNotEmpty()
            val variants = masterVariants(manifest, masterUrl).map {
                it.copy(label = "paradise-${it.label}", referer = "$PARA_ORIGIN/", enSubs = enSubs)
            }
            val auto = VFormat(masterUrl, true, "paradise-auto", variants.maxOfOrNull { it.height } ?: 0, true, "$PARA_ORIGIN/", enSubs, rAudio)
            (listOf(auto) + variants) to subs
        }

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
        put("tmdbKey", BuildConfig.TMDB_API_KEY)
    }

    suspend fun directAll(tmdbId: String, kind: String, season: String, episode: String, title: String): Triple<List<VFormat>, List<Sub>, String?> =
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(30000) {
                coroutineScope {
                    val t0 = System.currentTimeMillis()
                    val imdb = async { VidLove.imdbOf(tmdbId, kind, BuildConfig.TMDB_API_KEY) }
                    suspend fun <T> leg(name: String, block: suspend () -> T): T {
                        val r = block()
                        AppLog.d("leg $name ${System.currentTimeMillis() - t0}ms")
                        return r
                    }
                    val order = listOf("love", "rock", "vix", "zee", "paradise", "vap", "vidlink", "hianime")
                    val names = order + RemoteConfig.legNames().filter { it !in order }
                    val kt: Map<String, suspend () -> Pair<List<VFormat>, List<Sub>>> = mapOf(
                        "love" to { vidlove(tmdbId, kind, season, episode) },
                        "rock" to { vidrock(tmdbId, kind, season, episode).orEmpty() to emptyList<Sub>() },
                        "vix" to { vixsrc(tmdbId, kind, season, episode).orEmpty() to emptyList<Sub>() },
                        "zee" to { vidzee(tmdbId, kind, season, episode).orEmpty() to emptyList<Sub>() },
                        "paradise" to { paradise(tmdbId, kind, season, episode, title) },
                        "vap" to { imdb.await()?.let { vaplayer(it, kind, season, episode) }.orEmpty() to emptyList<Sub>() },
                        "vidlink" to { vidlink(tmdbId, kind, season, episode) },
                        "hianime" to { hianime(tmdbId, kind, season, episode) },
                    )
                    val results = names.map { name ->
                        async {
                            if (RemoteConfig.on(name)) {
                                val imdbArg = if (name == "vap") imdb.await() else null
                                val js = JsProviders.resolve(name, jsArgs(name, tmdbId, kind, season, episode, title, imdbArg), 12)
                                val pair = formatsOf(js)?.let { it to subsOf(js) }
                                    ?: kt[name]?.let { f -> leg(name) { withTimeoutOrNull(15000) { f() } } }
                                    ?: (emptyList<VFormat>() to emptyList<Sub>())
                                if (pair.first.isEmpty() && name !in kt) AppLog.d("leg $name empty")
                                name to pair
                            } else {
                                name to (emptyList<VFormat>() to emptyList<Sub>())
                            }
                        }
                    }.awaitAll()
                    AppLog.d(
                        "direct legs " + results.joinToString(" ") { "${it.first}=${it.second.first.size}" } +
                            " @${System.currentTimeMillis() - t0}ms",
                    )
                    val all = results.flatMap { it.second.first }
                    val subs = results.flatMap { it.second.second }
                    // bans outlive transient 403/429s; one 1-byte probe re-admits a host that recovered
                    all.filter { Torrents.DeadHosts.blocked(it.url) }
                        .distinctBy { Torrents.DeadHosts.hostOf(it.url) }
                        .forEach { f -> if (alive(f.url, f.referer)) Torrents.DeadHosts.forgive(f.url) }
                    val live = all
                        .filter { it.url !in Torrents.DeadReleases.urls && !Torrents.DeadHosts.blocked(it.url) }
                        // 1080p+ with EN subs first, then adaptive masters, then height
                        .sortedWith(
                            compareByDescending<VFormat> { if (it.height < 0) 0 else 1 }
                                .thenByDescending { if (it.enSubs && it.height >= 1080) 1 else 0 }
                                .thenByDescending { if (it.label.endsWith("-auto")) 1 else 0 }
                                .thenByDescending { if (it.enSubs) 1 else 0 }
                                .thenByDescending { if (it.english) 1 else 0 }
                                .thenByDescending { it.height },
                        )
                    Triple(live.distinctBy { it.url }, subs.distinctBy { it.url }, imdb.await())
                }
            } ?: Triple(emptyList(), emptyList(), null)
        }

    private fun formatsOf(j: JSONObject?): List<VFormat>? {
        val a = j?.optJSONArray("formats") ?: return null
        return buildList {
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val url = o.optString("url")
                if (url.isEmpty()) continue
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
                    ),
                )
            }
        }
    }

    private fun subsOf(j: JSONObject?): List<Sub> {
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
        f.label.startsWith("para-") -> "AnimeParadise"
        f.label.startsWith("vap-") -> "VAPlayer"
        f.label.startsWith("vidlink-") -> "VidLink"
        f.label.startsWith("hianime-") -> "HiAnime"
        else -> f.label
    }
}
