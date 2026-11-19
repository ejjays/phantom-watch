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
    val trailer: String? = null,
    val language: String? = null,
)

data class Episode(
    val n: Int,
    val name: String,
    val overview: String,
    val still: String?,
    val air: String?,
)

data class Sub(val label: String, val url: String, val src: String = "")

// dub is separate cut, not audio track, switching means new stream at same position
data class Dub(
    val label: String,
    val url: String,
    val isHls: Boolean,
    val referer: String,
    val headers: Map<String, String> = emptyMap(),
    val subs: List<Sub> = emptyList(),
    val enSubs: Boolean = false,
    val base: Boolean = false,
)

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
    val intro: LongRange? = null,
    val recap: LongRange? = null,
    val dub: String? = null,
    val dubs: List<Dub> = emptyList(),
    val outro: LongRange? = null,
)

// dub plays only own captions, sub cut timing drifts against dub audio
fun Stream.withDub(d: Dub): Stream = copy(
    url = d.url, isHls = d.isHls, referer = d.referer, headers = d.headers,
    dub = if (d.base) null else d.label, subs = d.subs, enSubs = d.enSubs,
)

data class RailPage(
    val items: List<Title>,
    val totalPages: Int,
)

data class Account(
    val name: String,
    val email: String,
    val picture: String?,
)

data class Recent(
    val kind: String,
    val id: String,
    val title: String,
    val poster: String?,
    val season: String,
    val episode: String,
    val positionMs: Long,
    val durationMs: Long,
    // set only for library entries backed by locally downloaded file
    val localUri: String? = null,
)
