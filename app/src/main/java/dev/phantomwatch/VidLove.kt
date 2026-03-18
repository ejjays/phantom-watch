package dev.phantomwatch

import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class Title(
    val id: String,
    val kind: String,
    val title: String,
    val poster: String?,
    val backdrop: String? = null,
    val rating: Double? = null,
    val year: String? = null,
)

data class Person(
    val name: String,
    val photo: String?,
)

data class SeasonInfo(
    val number: Int,
    val name: String,
    val count: Int,
)

data class TitleDetail(
    val id: String,
    val kind: String,
    val title: String,
    val overview: String,
    val poster: String?,
    val backdrop: String?,
    val year: String?,
    val runtime: Int,
    val rating: Double,
    val votes: Int,
    val genres: List<String>,
    val cert: String?,
    val director: Person?,
    val cast: List<Person>,
    val seasons: List<SeasonInfo>,
)

data class Episode(
    val n: Int,
    val name: String,
    val overview: String,
    val still: String?,
)

data class Sub(val label: String, val url: String)

data class Stream(
    val url: String,
    val isHls: Boolean,
    val subs: List<Sub>,
    val referer: String,
    val sampleMp4: String?,
    val inSync: Boolean = false,
    val complete: Boolean = true,
    val subKey: String = "",
)

private const val VIDLOVE_API = "https://api.vidlove.cc"
private const val PLAYER = "https://player.vidlove.cc"
private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Safari/537.36"

object AppLog {
    val lines = mutableStateListOf<String>()
    private val fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
    @Synchronized
    fun d(msg: String) {
        lines.add("${fmt.format(java.time.LocalTime.now())} $msg")
        if (lines.size > 300) lines.removeRange(0, lines.size - 300)
    }
}

private val http = OkHttpClient.Builder()
    .dns(object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val all = Dns.SYSTEM.lookup(hostname)
            return all.filterIsInstance<Inet4Address>().ifEmpty { all }
        }
    })
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .callTimeout(25, TimeUnit.SECONDS)
    .build()

fun probe(url: String): String {
    val started = System.currentTimeMillis()
    return try {
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        http.newCall(req).execute().use { res ->
            "${Uri.parse(url).host} ${res.code} in ${System.currentTimeMillis() - started}ms"
        }
    } catch (e: Exception) {
        "${Uri.parse(url).host} ${e.javaClass.simpleName} in ${System.currentTimeMillis() - started}ms"
    }
}

private fun get(url: String, referer: String): JSONObject? {
    var last: Exception? = null
    repeat(3) { attempt ->
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Referer", referer)
                .header("Origin", PLAYER)
                .build()
            http.newCall(req).execute().use { res ->
                if (res.code != 200) throw RuntimeException("http ${res.code}")
                val body = res.body?.string().orEmpty()
                if (!body.startsWith("{")) throw RuntimeException("not json: ${body.take(60)}")
                return JSONObject(body)
            }
        } catch (e: Exception) {
            last = e
            if (e is RuntimeException && e.message?.startsWith("http ") == true) throw e
            try {
                Thread.sleep(1000L * (attempt + 1))
            } catch (_: InterruptedException) {
            }
        }
    }
    throw RuntimeException("${last?.javaClass?.simpleName}: ${last?.message}")
}

object VidLove {
    fun imdbOf(tmdb: String, kind: String, key: String): String? {
        return try {
            val path = if (kind == "movie") "movie" else "tv"
            val data = get(
                "https://api.themoviedb.org/3/$path/$tmdb/external_ids?api_key=$key",
                "https://www.themoviedb.org/",
            ) ?: return null
            data.optString("imdb_id").takeIf { it.startsWith("tt") }
        } catch (_: Exception) {
            null
        }
    }

    private fun img(path: String?, size: String): String? =
        path?.takeIf { it.startsWith("/") }?.let { "https://image.tmdb.org/t/p/$size$it" }

    private fun titleOf(e: JSONObject): Title? {
        val type = e.optString("media_type").ifEmpty {
            if (e.has("title")) "movie" else "tv"
        }
        if (type != "movie" && type != "tv") return null
        val id = e.optLong("id", -1)
        val name = e.optString("title").ifEmpty { e.optString("name") }
        if (id < 0 || name.isEmpty()) return null
        return Title(
            id.toString(), type, name,
            img(e.optString("poster_path"), "w342"),
            img(e.optString("backdrop_path"), "w780"),
            e.optDouble("vote_average").takeIf { !it.isNaN() },
            (e.optString("release_date").ifEmpty { e.optString("first_air_date") }).take(4).ifEmpty { null },
        )
    }

    fun trending(apiKey: String): List<Title> {
        val data = get(
            "https://api.themoviedb.org/3/trending/all/week?api_key=$apiKey",
            "https://www.themoviedb.org/",
        ) ?: return emptyList()
        val results = data.optJSONArray("results") ?: return emptyList()
        val out = mutableListOf<Title>()
        for (i in 0 until results.length()) {
            titleOf(results.optJSONObject(i) ?: continue)?.let { out.add(it) }
        }
        AppLog.d("trending ${out.size}")
        return out
    }

    data class RailPage(
        val items: List<Title>,
        val totalPages: Int,
    )

    fun rail(apiKey: String, path: String, page: Int = 1): RailPage {
        return try {
            val data = get(
                "https://api.themoviedb.org/3/$path?api_key=$apiKey&page=$page",
                "https://www.themoviedb.org/",
            ) ?: return RailPage(emptyList(), 1)
            val results = data.optJSONArray("results") ?: return RailPage(emptyList(), 1)
            val out = mutableListOf<Title>()
            for (i in 0 until results.length()) {
                titleOf(results.optJSONObject(i) ?: continue)?.let { out.add(it) }
            }
            RailPage(out, data.optInt("total_pages", 1).coerceAtLeast(1))
        } catch (_: Exception) {
            RailPage(emptyList(), 1)
        }
    }

    fun detail(apiKey: String, kind: String, id: String): TitleDetail? {
        return try {
            val path = if (kind == "movie") "movie" else "tv"
            val e = get(
                "https://api.themoviedb.org/3/$path/$id?api_key=$apiKey&append_to_response=credits,content_ratings,release_dates",
                "https://www.themoviedb.org/",
            ) ?: return null
            val name = e.optString("title").ifEmpty { e.optString("name") }
            if (name.isEmpty()) return null
            val date = e.optString("release_date").ifEmpty { e.optString("first_air_date") }
            val genres = mutableListOf<String>()
            e.optJSONArray("genres")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.optString("name")?.takeIf { it.isNotEmpty() }?.let { genres.add(it) }
                }
            }
            var cert: String? = null
            if (kind == "movie") {
                e.optJSONObject("release_dates")?.optJSONArray("results")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val r = arr.optJSONObject(i) ?: continue
                        if (r.optString("iso_3166_1") != "US") continue
                        r.optJSONArray("release_dates")?.let { rd ->
                            for (j in 0 until rd.length()) {
                                rd.optJSONObject(j)?.optString("certification")?.takeIf { it.isNotEmpty() }?.let {
                                    cert = it
                                    break
                                }
                            }
                        }
                    }
                }
            } else {
                e.optJSONObject("content_ratings")?.optJSONArray("results")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val r = arr.optJSONObject(i) ?: continue
                        if (r.optString("iso_3166_1") == "US") {
                            cert = r.optString("rating").takeIf { it.isNotEmpty() }
                            break
                        }
                    }
                }
            }
            val credits = e.optJSONObject("credits")
            val cast = mutableListOf<Person>()
            credits?.optJSONArray("cast")?.let { arr ->
                for (i in 0 until minOf(arr.length(), 8)) {
                    val c = arr.optJSONObject(i) ?: continue
                    val n = c.optString("name")
                    if (n.isEmpty()) continue
                    cast.add(Person(n, img(c.optString("profile_path"), "w185")))
                }
            }
            var director: Person? = null
            credits?.optJSONArray("crew")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val c = arr.optJSONObject(i) ?: continue
                    if (c.optString("job") != "Director") continue
                    val n = c.optString("name")
                    if (n.isEmpty()) continue
                    director = Person(n, img(c.optString("profile_path"), "w185"))
                    break
                }
            }
            val seasons = mutableListOf<SeasonInfo>()
            e.optJSONArray("seasons")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val s = arr.optJSONObject(i) ?: continue
                    val n = s.optInt("season_number", -1)
                    val count = s.optInt("episode_count", 0)
                    if (n < 0 || count <= 0) continue
                    seasons.add(SeasonInfo(n, s.optString("name").ifEmpty { "Season $n" }, count))
                }
            }
            var runtime = e.optInt("runtime", 0)
            if (runtime <= 0) {
                e.optJSONArray("episode_run_time")?.let { arr ->
                    if (arr.length() > 0) runtime = arr.optInt(0)
                }
            }
            TitleDetail(
                id, kind, name, e.optString("overview"),
                img(e.optString("poster_path"), "w342"),
                img(e.optString("backdrop_path"), "w780"),
                date.take(4).ifEmpty { null }, runtime,
                e.optDouble("vote_average"), e.optInt("vote_count"),
                genres, cert, director, cast, seasons,
            )
        } catch (_: Exception) {
            null
        }
    }

    fun season(apiKey: String, tvId: String, season: Int): List<Episode> {
        return try {
            val e = get(
                "https://api.themoviedb.org/3/tv/$tvId/season/$season?api_key=$apiKey",
                "https://www.themoviedb.org/",
            ) ?: return emptyList()
            val out = mutableListOf<Episode>()
            e.optJSONArray("episodes")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val p = arr.optJSONObject(i) ?: continue
                    out.add(
                        Episode(
                            p.optInt("episode_number", i + 1),
                            p.optString("name").ifEmpty { "Episode ${i + 1}" },
                            p.optString("overview"),
                            img(p.optString("still_path"), "w300"),
                        ),
                    )
                }
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun client(): OkHttpClient = http

    private val LUNA = listOf(
        "https://watchluna.gd",
        "https://watchluna.com",
        "https://watchluna.to",
        "https://watchluna.io",
    )

    fun search(term: String): List<Title> {
        val q = term.trim()
        if (q.length < 2) return emptyList()
        for (origin in LUNA) {
            try {
                val req = Request.Builder()
                    .url("$origin/api/search?q=${Uri.encode(q)}")
                    .header("User-Agent", UA)
                    .build()
                val call = http.newCall(req)
                call.timeout().deadline(10, TimeUnit.SECONDS)
                val body = call.execute().use { res ->
                    if (res.code != 200) return@use null
                    res.body?.string()
                } ?: continue
                val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
                val out = mutableListOf<Title>()
                for (i in 0 until results.length()) {
                    titleOf(results.optJSONObject(i) ?: continue)?.let { out.add(it) }
                }
                AppLog.d("search \"$q\" ${out.size} via $origin")
                return out
            } catch (_: Exception) {
                continue
            }
        }
        AppLog.d("search \"$q\" all origins failed")
        return emptyList()
    }

    fun englishFirst(subs: List<Sub>): List<Sub> =
        subs.sortedBy { if (it.label.contains("english", true)) 0 else 1 }

    data class Cue(var start: Long, var end: Long, val text: String)

    private val TIMING = Regex("""(\d+):(\d+):(\d+)[,.](\d+)\s*-->\s*(\d+):(\d+):(\d+)[,.](\d+)""")

    private fun ms(h: String, m: String, s: String, ms: String): Long =
        h.toLong() * 3600000 + m.toLong() * 60000 + s.toLong() * 1000 + ms.padEnd(3, '0').take(3).toLong()

    fun parseAss(text: String): List<Cue> {
        val out = mutableListOf<Cue>()
        val dlg = Regex("^Dialogue:\\s*[^,]*,([^,]+),([^,]+),[^,]*,[^,]*,[^,]*,[^,]*,[^,]*,[^,]*,(.*)$", RegexOption.IGNORE_CASE)
        for (raw in text.replace("\r\n", "\n").split('\n')) {
            val m = dlg.find(raw.trim()) ?: continue
            val s = stampToMs(m.groupValues[1].trim())
            val e = stampToMs(m.groupValues[2].trim())
            if (s == null || e == null || e <= s) continue
            val body = m.groupValues[3].replace(Regex("\\{[^}]*\\}"), "")
                .replace("\\N", "\n").replace("\\n", "\n").trim()
            if (body.isEmpty()) continue
            out.add(Cue(s, e, body))
        }
        return out.sortedBy { it.start }
    }

    private fun stampToMs(stamp: String): Long? {
        return try {
            val parts = stamp.trim().split(':')
            if (parts.size != 3) return null
            val h = parts[0].toLong()
            val m = parts[1].toLong()
            val sec = parts[2].replace(',', '.').toDouble()
            (h * 3600000 + m * 60000 + (sec * 1000)).toLong()
        } catch (_: Exception) {
            null
        }
    }

    fun parseCues(text: String): List<Cue> {
        val out = mutableListOf<Cue>()
        for (block in text.split(Regex("""\r?\n\r?\n"""))) {
            val lines = block.split(Regex("""\r?\n""")).map { it.trim() }.filter { it.isNotEmpty() }
            val ti = lines.indexOfFirst { TIMING.containsMatchIn(it) }
            if (ti < 0) continue
            val m = TIMING.find(lines[ti]) ?: continue
            val body = lines.drop(ti + 1).joinToString("\n")
                .replace(Regex("""<[^>]*>"""), "").trim()
            if (body.isEmpty()) continue
            val g = m.groupValues
            out.add(Cue(ms(g[1], g[2], g[3], g[4]), ms(g[5], g[6], g[7], g[8]), body))
        }
        return out
    }

    private fun stamp(t: Long): String {
        val v = maxOf(0, t)
        return "%02d:%02d:%02d.%03d".format(v / 3600000, (v / 60000) % 60, (v / 1000) % 60, v % 1000)
    }

    fun renderCues(cues: List<Cue>): String {
        val sb = StringBuilder("WEBVTT\n\n")
        for (c in cues) {
            if (c.end <= c.start) continue
            sb.append("${stamp(c.start)} --> ${stamp(c.end)}\n${c.text}\n\n")
        }
        return sb.toString()
    }

    fun cuesToFile(cues: List<Cue>, cacheDir: java.io.File): java.io.File? {
        return try {
            if (cues.isEmpty()) return null
            val f = java.io.File(cacheDir, "ai-en.vtt")
            f.writeText(renderCues(cues))
            f
        } catch (_: Exception) {
            null
        }
    }

    fun shiftSubFile(raw: String, offsetMs: Long, cacheDir: java.io.File): java.io.File? {
        return try {
            val shifted = parseCues(raw).map { it.copy(start = it.start + offsetMs, end = it.end + offsetMs) }
                .filter { it.end > 0 }
            if (shifted.isEmpty()) return null
            val sb = StringBuilder("WEBVTT\n\n")
            for (c in shifted) sb.append("${stamp(c.start)} --> ${stamp(c.end)}\n${c.text}\n\n")
            val f = java.io.File(cacheDir, "shifted.vtt")
            f.writeText(sb.toString())
            f
        } catch (_: Exception) {
            null
        }
    }

    fun mimeOf(url: String): String {
        val path = Uri.parse(url).path.orEmpty()
        return if (path.endsWith(".srt", true)) "application/x-subrip" else "text/vtt"
    }
}
