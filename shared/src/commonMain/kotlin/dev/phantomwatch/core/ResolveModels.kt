package dev.phantomwatch.core

// resolve result contract shared by phone and wasm clients, one relay response shape
data class PlaySource(val url: String, val label: String, val h: Int, val hls: Boolean, val dub: String = "")

data class SubSource(val label: String, val url: String)

data class Resolved(val formats: List<PlaySource>, val subs: List<SubSource>)
