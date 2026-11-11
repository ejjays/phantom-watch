package dev.phantomwatch.data

import dev.phantomwatch.core.Log
import dev.phantomwatch.json.JSONArray
import dev.phantomwatch.json.JSONObject
import dev.phantomwatch.core.httpJson
import dev.phantomwatch.core.ioDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

object Tmdb {
    suspend fun imdbOf(tmdb: String, kind: String, key: String): String? = kotlinx.coroutines.withContext(ioDispatcher) {
        try {
            val path = if (kind == "movie") "movie" else "tv"
            val data = httpJson(
                "${TmdbHost.base}/$path/$tmdb/external_ids?api_key=$key",
                "https://www.themoviedb.org/",
            ) ?: return@withContext null
            data.optString("imdb_id").takeIf { it.startsWith("tt") }
        } catch (_: Exception) {
            null
        }
    }

    internal fun img(path: String?, size: String): String? =
        path?.takeIf { it.startsWith("/") }?.let { "${TmdbHost.image}$size$it" }

    // talk, news and reality fill TMDB's tv lists; this app is movies and scripted series
    private val unscripted = setOf(10763, 10764, 10767)

    private fun isUnscripted(e: JSONObject): Boolean {
        val type = e.optString("media_type").ifEmpty { if (e.has("title")) "movie" else "tv" }
        if (type != "tv") return false
        val g = e.optJSONArray("genre_ids") ?: return false
        for (i in 0 until g.length()) if (g.optInt(i, -1) in unscripted) return true
        return false
    }

    internal fun titleOf(e: JSONObject): Title? {
        val type = e.optString("media_type").ifEmpty {
            if (e.has("title")) "movie" else "tv"
        }
        if (type != "movie" && type != "tv") return null
        val id = e.optLong("id", -1)
        val name = e.optString("title").ifEmpty { e.optString("name") }
        if (id < 0 || name.isEmpty()) return null
        return Aliases.resolve(
            Title(
                id.toString(), type, name,
                img(e.optString("poster_path"), "w342"),
                img(e.optString("backdrop_path"), "w1280"),
                e.optDouble("vote_average").takeIf { !it.isNaN() },
                (e.optString("release_date").ifEmpty { e.optString("first_air_date") }).take(4).ifEmpty { null },
                e.optString("overview").ifEmpty { null },
            ),
        )
    }

    suspend fun trending(apiKey: String): List<Title> {
        val data = httpJson(
            "${TmdbHost.base}/trending/all/week?api_key=$apiKey",
            "https://www.themoviedb.org/",
        ) ?: return emptyList()
        val results = data.optJSONArray("results") ?: return emptyList()
        val out = mutableListOf<Title>()
        for (i in 0 until results.length()) {
            val e = results.optJSONObject(i) ?: continue
            if (isUnscripted(e)) continue
            titleOf(e)?.let { out.add(it) }
        }
        Log.d("trending ${out.size}")
        return out.distinctBy { it.kind to it.id }
    }

    // null means the request failed; a page count of 1 would end pagination
    suspend fun rail(apiKey: String, path: String, page: Int = 1): RailPage? {
        return try {
            val data = httpJson(
                // a discover path carries its own query, so the key cannot assume a leading ?
                "${TmdbHost.base}/$path${if ('?' in path) "&" else "?"}api_key=$apiKey&page=$page",
                "https://www.themoviedb.org/",
            ) ?: return null
            val results = data.optJSONArray("results") ?: return null
            val out = mutableListOf<Title>()
            for (i in 0 until results.length()) {
                val e = results.optJSONObject(i) ?: continue
                if (isUnscripted(e)) continue
                titleOf(e)?.let { out.add(it) }
            }
            RailPage(out.distinctBy { it.kind to it.id }, data.optInt("total_pages", 1).coerceAtLeast(1))
        } catch (_: Exception) {
            null
        }
    }

    suspend fun detail(apiKey: String, kind: String, id: String): TitleDetail? {
        return try {
            val path = if (kind == "movie") "movie" else "tv"
            val e = httpJson(
                "${TmdbHost.base}/$path/$id?api_key=$apiKey&append_to_response=credits,content_ratings,release_dates,videos",
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
            val trailer = pickVideo(e.optJSONObject("videos")?.optJSONArray("results"))
            TitleDetail(
                id, kind, name, e.optString("overview"),
                img(e.optString("poster_path"), "w342"),
                img(e.optString("backdrop_path"), "w1280"),
                date.take(4).ifEmpty { null }, runtime,
                e.optDouble("vote_average"), e.optInt("vote_count"),
                genres, cert, director, cast, seasons, trailer,
                language = e.optString("original_language").ifEmpty { null },
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun similar(apiKey: String, kind: String, id: String): List<Title> {
        val path = if (kind == "movie") "movie" else "tv"
        val ref = "https://www.themoviedb.org/"
        return try {
            val (anchor, recs, sims) = coroutineScope {
                val a = async {
                    runCatching {
                        httpJson("${TmdbHost.base}/$path/$id?api_key=$apiKey&append_to_response=credits", ref)
                    }.getOrNull()
                }
                val r = async {
                    runCatching {
                        httpJson("${TmdbHost.base}/$path/$id/recommendations?api_key=$apiKey", ref)
                    }.getOrNull()
                }
                val s = async {
                    runCatching {
                        httpJson("${TmdbHost.base}/$path/$id/similar?api_key=$apiKey", ref)
                    }.getOrNull()
                }
                Triple(a.await(), r.await(), s.await())
            }
            val lang = anchor?.optString("original_language").orEmpty()
            val genres = mutableSetOf<Int>()
            anchor?.optJSONArray("genres")?.let { arr ->
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { genres.add(it.optInt("id", -1)) }
            }
            val year = anchor?.optString("release_date").orEmpty()
                .ifEmpty { anchor?.optString("first_air_date").orEmpty() }.take(4).toIntOrNull() ?: 0

            // the same director or lead beats a genre match, and is the only signal that pulls a
            // franchise title out of its own sequels
            val credits = anchor?.optJSONObject("credits")
            val crew = credits?.optJSONArray("crew")
            val director = if (crew == null) null else (0 until crew.length()).firstNotNullOfOrNull { i ->
                crew.optJSONObject(i)?.takeIf { it.optString("job") == "Director" }?.optInt("id", -1)?.takeIf { it > 0 }
            }
            val lead = credits?.optJSONArray("cast")?.optJSONObject(0)?.optInt("id", -1)?.takeIf { it > 0 }
            val shelves = if (kind != "movie") emptyList() else coroutineScope {
                listOfNotNull(
                    director?.let { "${TmdbHost.base}/discover/movie?api_key=$apiKey&with_crew=$it&sort_by=vote_count.desc&vote_count.gte=300" to 3.0 },
                    lead?.let { "${TmdbHost.base}/discover/movie?api_key=$apiKey&with_cast=$it&sort_by=vote_count.desc&vote_count.gte=300" to 2.6 },
                ).map { (url, weight) ->
                    async { picks(runCatching { httpJson(url, ref) }.getOrNull(), weight) }
                }.awaitAll()
            }

            val byId = LinkedHashMap<String, Pick>()
            for (p in picks(recs, 3.0) + picks(sims, 1.0) + shelves.flatten()) {
                val k = "${p.title.kind}:${p.title.id}"
                val old = byId[k]
                if (old == null || p.source > old.source) byId[k] = p
            }

            fun score(p: Pick): Double {
                var s = p.source
                if (lang.isNotEmpty() && p.lang == lang) s += 2.5
                s += p.genres.count { it in genres }.coerceAtMost(2) * 1.2
                // IMDb-style weighted rating: a 7.5 from 118 votes must not beat an 8.2 from 2 000
                val w = p.votes.toDouble()
                s += ((w * p.rating + 500 * 6.5) / (w + 500) - 6.5) * 0.9
                if (year > 0 && p.year > 0) s -= kotlin.math.abs(p.year - year).coerceAtMost(40) * 0.012
                return s
            }

            fun rank(list: Collection<Pick>) = list.sortedWith(
                compareByDescending<Pick> { score(it) }.thenByDescending { it.votes },
            )

            var ranked = rank(byId.values.filter { it.title.id != id && it.votes >= 25 && it.rating >= 5.5 })
            if (ranked.size < 8 && genres.isNotEmpty()) {
                val langParam = if (lang.isNotEmpty()) "&with_original_language=$lang" else ""
                val extra = runCatching {
                    httpJson(
                        "${TmdbHost.base}/discover/$path?api_key=$apiKey&with_genres=${genres.joinToString(",")}" +
                            "&sort_by=vote_count.desc&vote_count.gte=300$langParam",
                        ref,
                    )
                }.getOrNull()
                val have = byId.keys.toMutableSet()
                val add = picks(extra, 0.5)
                    .filter { it.rating >= 5.5 && have.add("${it.title.kind}:${it.title.id}") }
                ranked = rank(ranked + add)
            }
            // TMDB hands back every Batman film for a Batman film: a shared title word pushes the
            // rest of that family down, so the row stays varied
            val picked = mutableListOf<Pick>()
            val seen = HashMap<String, Int>()
            val pool = ranked.toMutableList()
            while (pool.isNotEmpty() && picked.size < 20) {
                var best = 0
                var bestScore = Double.NEGATIVE_INFINITY
                for (i in pool.indices) {
                    val s = score(pool[i]) - titleTokens(pool[i].title.title).sumOf { 1.8 * (seen[it] ?: 0) }
                    if (s > bestScore) {
                        bestScore = s
                        best = i
                    }
                }
                val p = pool.removeAt(best)
                titleTokens(p.title.title).forEach { seen[it] = (seen[it] ?: 0) + 1 }
                picked.add(p)
            }
            picked.map { it.title }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private val StopWords = setOf(
        "the", "and", "with", "from", "that", "this", "part", "movie", "film", "story", "into", "your", "their",
    )

    private fun titleTokens(title: String): Set<String> =
        title.lowercase().split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 4 && it !in StopWords }
            .toSet()

    private class Pick(
        val title: Title,
        val votes: Int,
        val rating: Double,
        val lang: String,
        val genres: Set<Int>,
        val year: Int,
        val source: Double,
    )

    private fun picks(data: JSONObject?, source: Double): List<Pick> {
        val results = data?.optJSONArray("results") ?: return emptyList()
        val out = mutableListOf<Pick>()
        for (i in 0 until results.length()) {
            val e = results.optJSONObject(i) ?: continue
            val t = titleOf(e) ?: continue
            if (t.poster == null) continue
            val genres = mutableSetOf<Int>()
            e.optJSONArray("genre_ids")?.let { arr ->
                for (j in 0 until arr.length()) genres.add(arr.optInt(j, -1))
            }
            out.add(
                Pick(
                    t, e.optInt("vote_count"), e.optDouble("vote_average", 0.0),
                    e.optString("original_language"), genres, t.year?.toIntOrNull() ?: 0, source,
                ),
            )
        }
        return out
    }

    suspend fun season(apiKey: String, tvId: String, season: Int): List<Episode> {
        return try {
            val e = httpJson(
                "${TmdbHost.base}/tv/$tvId/season/$season?api_key=$apiKey",
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
                            p.optString("air_date").takeIf { it.isNotEmpty() },
                        ),
                    )
                }
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    // the clip feed wants a key per title without paying for a full detail call
    suspend fun clipKey(apiKey: String, kind: String, id: String): String? = try {
        val path = if (kind == "movie") "movie" else "tv"
        val e = httpJson("${TmdbHost.base}/$path/$id/videos?api_key=$apiKey", "https://www.themoviedb.org/")
        pickVideo(e?.optJSONArray("results"))
    } catch (_: Exception) {
        null
    }

    // a full trailer beats a teaser beats a bare scene; official and english win the ties
    private fun pickVideo(arr: JSONArray?): String? {
        if (arr == null) return null
        var best = -1
        var key: String? = null
        for (i in 0 until arr.length()) {
            val v = arr.optJSONObject(i) ?: continue
            if (v.optString("site") != "YouTube") continue
            val rank = when (v.optString("type")) {
                "Trailer" -> 3
                "Teaser" -> 2
                "Clip" -> 1
                else -> continue
            }
            val k = v.optString("key")
            if (k.isEmpty()) continue
            val score = rank * 4 + (if (v.optBoolean("official")) 2 else 0) + (if (v.optString("iso_639_1") == "en") 1 else 0)
            if (score > best) {
                best = score
                key = k
            }
        }
        return key
    }
}
