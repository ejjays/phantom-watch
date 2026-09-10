package dev.phantomwatch.data

// Android points these at the signed remote config, the web build leaves the defaults
// Tmdb never reads platform config directly
object TmdbHost {
    var key: String = ""
    var base: String = "https://api.themoviedb.org/3"
    var image: String = "https://image.tmdb.org/t/p/"
}
