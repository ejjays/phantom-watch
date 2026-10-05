package dev.phantomwatch.core

// one console line per step: index.html defines the sink before the module loads, the same way
// player.js defines pwAttach
external fun pwLog(tag: String, msg: String)
