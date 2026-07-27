package dev.phantomwatch.data

data class Title(
    val id: String,
    val kind: String,
    val title: String,
    val poster: String?,
    val backdrop: String? = null,
    val rating: Double? = null,
    val year: String? = null,
    val overview: String? = null,
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
    val air: String?,
)

data class Sub(val label: String, val url: String, val src: String = "")

data class Stream(
    val url: String,
    val isHls: Boolean,
    val subs: List<Sub>,
    val referer: String,
    val sampleMp4: String?,
    val inSync: Boolean = false,
    val complete: Boolean = true,
    val subKey: String = "",
    val auth: String? = null,
    val imdb: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val enSubs: Boolean = false,
)

data class RailPage(
    val items: List<Title>,
    val totalPages: Int,
)
