package dev.phantomwatch

// Emergency copy of the pick order that providers.js owns (__pwRank). Used only
// when the __pwSort round-trip fails; both sides are pinned by the same fixture.
internal object FormatOrder {
    fun sort(formats: List<VFormat>): List<VFormat> = formats.sortedWith(
        compareByDescending<VFormat> { if (it.height < 0) 0 else 1 }
            .thenByDescending { if (it.enSubs && it.height >= 1080) 1 else 0 }
            .thenByDescending { if (it.label.endsWith("-auto")) 1 else 0 }
            .thenByDescending { if (it.enSubs) 1 else 0 }
            .thenByDescending { if (it.english) 1 else 0 }
            .thenByDescending { it.height },
    )
}
