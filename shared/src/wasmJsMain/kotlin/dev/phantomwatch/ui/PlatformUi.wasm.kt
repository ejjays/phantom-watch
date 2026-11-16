package dev.phantomwatch.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.browser.window

// browser has no back button to hand the app, so an entry of our own stands in for it: while a
// screen wants back handling that entry sits ahead of page, Back pops it, innermost handler runs
// and a fresh entry goes straight back on. with no handler left nothing goes back on and Back
// leaves the page, as it does on android
//
// every entry pushed here carries a marker, and what pop lands on decides what happens. a flag
// alone cannot: a pop asked for below is queued rather than immediate, so it can arrive after a
// screen already pushed its own entry and take that one instead. reading marker off entry the
// browser actually landed on makes that harmless, and a spent entry is noticed and replaced
// before the press that spent it is handled
private val backHandlers = mutableListOf<() -> Unit>()
private var trapInstalled = false
private var release = -1
private var settle = -1
private var selfPops = 0

// screen taking over composes a frame after the one it replaces goes away, so entry is only
// dropped once that frame had its chance to register
private const val RELEASE_MS = 150

// pop asked for here reports back within a frame or two. if it never does the page left the
// document and count has to let go of it, or next real press is taken for our own
private const val SETTLE_MS = 400

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => { window.history.pushState({ pw: 1 }, ''); }")
private external fun pushEntry()

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => !!(window.history.state && window.history.state.pw)")
private external fun onOwnEntry(): Boolean

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => { window.history.back(); }")
private external fun popEntry()

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(u) => { window.history.replaceState(window.history.state, '', u); }")
private external fun replaceUrl(url: String)

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val latest = rememberUpdatedState(onBack)
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose {}
        installTrap()
        val entry: () -> Unit = { latest.value() }
        backHandlers.add(entry)
        cancelRelease()
        hold()
        onDispose {
            backHandlers.remove(entry)
            if (backHandlers.isEmpty()) scheduleRelease()
        }
    }
}

// keeps an entry of ours ahead of page, so next Back lands here and not outside the app
private fun hold() {
    if (selfPops > 0 || onOwnEntry()) return
    pushEntry()
}

private fun cancelRelease() {
    if (release < 0) return
    window.clearTimeout(release)
    release = -1
}

private fun scheduleRelease() {
    cancelRelease()
    val drop: () -> JsAny? = {
        release = -1
        if (backHandlers.isEmpty() && onOwnEntry()) {
            selfPops += 1
            popEntry()
            settleUp()
        }
        null
    }
    release = window.setTimeout(drop, RELEASE_MS)
}

private fun settleUp() {
    if (settle >= 0) window.clearTimeout(settle)
    val done: () -> JsAny? = {
        settle = -1
        selfPops = 0
        if (backHandlers.isNotEmpty()) hold()
        null
    }
    settle = window.setTimeout(done, SETTLE_MS)
}

private fun installTrap() {
    if (trapInstalled) return
    trapInstalled = true
    window.addEventListener("popstate") {
        if (settle >= 0) {
            window.clearTimeout(settle)
            settle = -1
        }
        val mine = selfPops > 0
        if (mine) selfPops -= 1
        // landed on an entry of ours: nothing spent, whoever asked for the pop
        if (onOwnEntry()) return@addEventListener
        if (mine) {
            if (backHandlers.isNotEmpty()) hold()
            return@addEventListener
        }
        val top = backHandlers.lastOrNull() ?: return@addEventListener
        // entry goes back on before handler runs, so a handler that unregisters itself still
        // leaves next press somewhere inside the app
        hold()
        top()
    }
}

@Composable
actual fun OnResumeEffect(block: () -> Unit) {
    LaunchedEffect(Unit) { block() }
}

actual fun initialPath(): String? = window.location.hash.removePrefix("#").ifBlank { null }

actual fun syncRoute(path: String) {
    if (window.location.hash.removePrefix("#") != path) replaceUrl("#$path")
}
