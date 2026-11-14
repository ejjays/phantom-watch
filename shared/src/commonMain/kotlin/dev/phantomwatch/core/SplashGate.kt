package dev.phantomwatch.core

// holds launcher window until cached feed restored, no halfbuilt home shows
object SplashGate {
    @Volatile
    var ready = false
}