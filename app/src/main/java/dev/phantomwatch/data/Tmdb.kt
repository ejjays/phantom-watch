package dev.phantomwatch.data

import dev.phantomwatch.core.AppLog
import dev.phantomwatch.core.Http
import dev.phantomwatch.core.RemoteConfig

object Tmdb {
    suspend fun imdbOf(tmdb: String, kind: String, key: String): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val path = if (kind == "movie") "movie" else "tv"
            val data = Http.getJson(
                "${RemoteConfig.tmdbBase()}/$path/$tmdb/external_ids?api_key=$key",
                "https://www.themoviedb.org/",
            ) ?: return@withContext null
            data.optString("imdb_id").takeIf { it.startsWith("tt") }
        } catch (_: Exception) {
            null
        }
    }

    internal fun img(path: String?, size: String): String? =
        path?.takeIf { it.startsWith("/") }?.let { "${RemoteConfig.tmdbImage()}$size$it" }

    internal fun titleOf(e: org.json.JSONObject): Title? {
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
            img(e.optString("backdrop_path"), "w1280"),
            e.optDouble("vote_average").takeIf { !it.isNaN() },
            (e.optString("release_date").ifEmpty { e.optString("first_air_date") }).take(4).ifEmpty { null },
            e.optString("overview").ifEmpty { null },
        )
    }

    fun trending(apiKey: String): List<Title> {
        val data = Http.getJson(
            "${RemoteConfig.tmdbBase()}/trending/all/week?api_key=$apiKey",
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

    /** Null means the request failed — never a page count of 1, which would end pagination. */
    fun rail(apiKey: String, path: String, page: Int = 1): RailPage? {
        return try {
            val data = Http.getJson(
                "${RemoteConfig.tmdbBase()}/$path?api_key=$apiKey&page=$page",
                "https://www.themoviedb.org/",
            ) ?: return null
            val results = data.optJSONArray("results") ?: return null
            val out = mutableListOf<Title>()
            for (i in 0 until results.length()) {
                titleOf(results.optJSONObject(i) ?: continue)?.let { out.add(it) }
            }
            RailPage(out, data.optInt("total_pages", 1).coerceAtLeast(1))
        } catch (_: Exception) {
            null
        }
    }

    fun detail(apiKey: String, kind: String, id: String): TitleDetail? {
        return try {
            val path = if (kind == "movie") "movie" else "tv"
            val e = Http.getJson(
                "${RemoteConfig.tmdbBase()}/$path/$id?api_key=$apiKey&append_to_response=credits,content_ratings,release_dates,videos",
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
            var trailer: String? = null
            var best = -1
            e.optJSONObject("videos")?.optJSONArray("results")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val v = arr.optJSONObject(i) ?: continue
                    if (v.optString("site") != "YouTube" || v.optString("type") != "Trailer") continue
                    val key = v.optString("key")
                    if (key.isEmpty()) continue
                    val score = (if (v.optBoolean("official")) 2 else 0) + (if (v.optString("iso_639_1") == "en") 1 else 0)
                    if (score >= best) {
                        best = score
                        trailer = key
                    }
                }
            }
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

    fun similar(apiKey: String, kind: String, id: String): List<Title> {
        return try {
            val path = if (kind == "movie") "movie" else "tv"
            val data = Http.getJson(
                "${RemoteConfig.tmdbBase()}/$path/$id/similar?api_key=$apiKey",
                "https://www.themoviedb.org/",
            ) ?: return emptyList()
            val results = data.optJSONArray("results") ?: return emptyList()
            val out = mutableListOf<Title>()
            for (i in 0 until results.length()) {
                titleOf(results.optJSONObject(i) ?: continue)?.let { out.add(it) }
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun season(apiKey: String, tvId: String, season: Int): List<Episode> {
        return try {
            val e = Http.getJson(
                "${RemoteConfig.tmdbBase()}/tv/$tvId/season/$season?api_key=$apiKey",
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
}
