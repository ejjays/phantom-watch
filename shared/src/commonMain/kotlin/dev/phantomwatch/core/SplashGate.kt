package dev.phantomwatch.core

// holds the launcher window until the cached feed is restored, so no half-built home is ever visible
object SplashGate {
    @Volatile
    var ready = false
}