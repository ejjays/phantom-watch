package dev.phantomwatch.data

// TMDB duplicate listings that should behave as the canonical title
object Aliases {
    private class Canon(
        val id: String, val title: String, val poster: String, val backdrop: String, val year: String,
    )

    private val map = mapOf(
        "tv/312359" to Canon(
            "62715", "Dragon Ball Super",
            "/qEUrbXJ2qt4Rg84Btlx4STOhgte.jpg", "/qA2UwUQbj05aeBMCuC0mHSQ4loE.jpg", "2015",
        ),
    )

    fun resolve(t: Title): Title = map["${t.kind}/${t.id}"]?.let {
        Title(it.id, "tv", it.title, Tmdb.img(it.poster, "w342"), Tmdb.img(it.backdrop, "w1280"), year = it.year)
    } ?: t
}
