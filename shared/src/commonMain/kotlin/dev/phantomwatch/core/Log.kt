package dev.phantomwatch.core

// the app owns the real logger, so shared code writes through this sink and the host installs it
object Log {
    var sink: (String) -> Unit = {}

    fun d(msg: String) {
        sink(msg)
    }
}
