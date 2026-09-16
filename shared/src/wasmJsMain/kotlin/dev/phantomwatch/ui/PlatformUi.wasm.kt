package dev.phantomwatch.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.browser.window

// The root screen (browse) always registers, so anything past the first entry is a screen that
// back should close instead of letting the browser leave the site.
private val backHandlers = mutableListOf<() -> Unit>()
private var trapInstalled = false

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val latest = rememberUpdatedState(onBack)
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose {}
        installTrap()
        val entry: () -> Unit = { latest.value() }
        backHandlers.add(entry)
        if (backHandlers.size > 1) window.history.pushState(null, "")
        onDispose { backHandlers.remove(entry) }
    }
}

private fun installTrap() {
    if (trapInstalled) return
    trapInstalled = true
    window.addEventListener("popstate") {
        if (backHandlers.size > 1) {
            backHandlers.last()()
            // stay one entry deep so the next back closes the next screen, not the tab
            window.history.pushState(null, "")
        }
    }
}

@Composable
actual fun OnResumeEffect(block: () -> Unit) {
    LaunchedEffect(Unit) { block() }
}
