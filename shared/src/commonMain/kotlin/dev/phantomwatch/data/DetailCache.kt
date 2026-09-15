package dev.phantomwatch.data

object DetailCache {
    private var key: String? = null
    private var last: TitleDetail? = null
    private var lastSeason = -1
    private val bySeason = mutableMapOf<Triple<String, String, Int>, List<Episode>>()
    private val similarByKey = mutableMapOf<String, List<Title>>()

    private fun k(kind: String, id: String) = "$kind:$id"

    fun detail(kind: String, id: String): TitleDetail? = last?.takeIf { key == k(kind, id) }

    fun put(d: TitleDetail) {
        val nk = k(d.kind, d.id)
        if (nk != key) {
            key = nk
            lastSeason = -1
        }
        last = d
    }

    fun season(kind: String, id: String): Int = lastSeason.takeIf { key == k(kind, id) } ?: -1

    fun putSeason(kind: String, id: String, n: Int) {
        if (key == k(kind, id)) lastSeason = n
    }

    fun episodes(kind: String, id: String, n: Int): List<Episode>? = bySeason[Triple(kind, id, n)]

    fun putEpisodes(kind: String, id: String, n: Int, eps: List<Episode>) {
        if (bySeason.size >= 40) bySeason.clear()
        bySeason[Triple(kind, id, n)] = eps
    }

    fun similar(kind: String, id: String): List<Title>? = similarByKey[k(kind, id)]

    fun putSimilar(kind: String, id: String, list: List<Title>) {
        if (similarByKey.size >= 40) similarByKey.clear()
        similarByKey[k(kind, id)] = list
    }
}
