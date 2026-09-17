package dev.phantomwatch.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.browser.window

// One browser-history entry exists while any screen wants back handling. Popstate goes to the
// innermost handler; with none registered the browser's own back (leave the site) wins.
private val backHandlers = mutableListOf<() -> Unit>()
private var sentinel = false
private var trapInstalled = false

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val latest = rememberUpdatedState(onBack)
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose {}
        installTrap()
        val entry: () -> Unit = { latest.value() }
        backHandlers.add(entry)
        if (!sentinel) {
            sentinel = true
            window.history.pushState(null, "")
        }
        onDispose {
            backHandlers.remove(entry)
            if (backHandlers.isEmpty() && sentinel) {
                sentinel = false
                window.history.back()
            }
        }
    }
}

private fun installTrap() {
    if (trapInstalled) return
    trapInstalled = true
    window.addEventListener("popstate") {
        if (!sentinel) return@addEventListener
        sentinel = false
        val top = backHandlers.lastOrNull() ?: return@addEventListener
        top()
        sentinel = true
        window.history.pushState(null, "")
    }
}

@Composable
actual fun OnResumeEffect(block: () -> Unit) {
    LaunchedEffect(Unit) { block() }
}
