package dev.phantomwatch.provider

// emergency copy of the pick order that providers.js owns (__pwRank), used only
// when the __pwSort round-trip fails; both sides are pinned by the same fixture
internal object FormatOrder {
    fun sort(formats: List<VideoFormat>): List<VideoFormat> = formats.sortedWith(
        compareByDescending<VideoFormat> { if (it.height < 0) 0 else 1 }
            .thenByDescending { if (it.dub == null) 1 else 0 }
            .thenByDescending { if (it.enSubs && it.height >= 1080 && !it.label.endsWith("-auto")) 1 else 0 }
            .thenByDescending { if (it.label.endsWith("-auto")) 0 else 1 }
            .thenByDescending { if (it.enSubs) 1 else 0 }
            .thenByDescending { if (it.english) 1 else 0 }
            .thenByDescending { it.height },
    )
}
