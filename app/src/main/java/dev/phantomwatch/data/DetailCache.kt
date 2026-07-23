package dev.phantomwatch.data

object DetailCache {
    private var id: String? = null
    private var last: TitleDetail? = null
    private var lastSeason = -1
    private val bySeason = mutableMapOf<Pair<String, Int>, List<Episode>>()
    private val similarById = mutableMapOf<String, List<Title>>()

    fun detail(id: String): TitleDetail? = last?.takeIf { this.id == id }

    fun put(d: TitleDetail) {
        if (d.id != id) {
            id = d.id
            lastSeason = -1
        }
        last = d
    }

    fun season(id: String): Int = lastSeason.takeIf { this.id == id } ?: -1

    fun putSeason(id: String, n: Int) {
        if (this.id == id) lastSeason = n
    }

    fun episodes(id: String, n: Int): List<Episode>? = bySeason[id to n]

    fun putEpisodes(id: String, n: Int, eps: List<Episode>) {
        bySeason[id to n] = eps
    }

    fun similar(id: String): List<Title>? = similarById[id]

    fun putSimilar(id: String, list: List<Title>) {
        similarById[id] = list
    }
}
