package dev.phantomwatch.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import dev.phantomwatch.core.AppLog

// one pinch fills the stage, a pinch the other way fits it again; one finger still reaches the
// tap zones, and a two-finger drag is consumed so it cannot turn into a brightness swipe
internal fun Modifier.pinchZoom(mode: () -> Int, safe: () -> Boolean, onMode: (Int) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var acc = 1f
            var done = false
            while (true) {
                val e = awaitPointerEvent()
                val down = e.changes.count { it.pressed }
                if (down < 2) {
                    if (down == 0) break
                    continue
                }
                if (done) {
                    e.changes.forEach { it.consume() }
                    continue
                }
                acc *= e.calculateZoom()
                if (acc > 1.06f || acc < 0.94f) {
                    done = true
                    e.changes.forEach { it.consume() }
                    val m = mode()
                    val next = if (acc > 1f) {
                        if (m == 0 && safe()) 1 else 2
                    } else {
                        if (m == 2 && safe()) 1 else 0
                    }
                    if (next != m) {
                        AppLog.d("pinch ${when (next) { 0 -> "fit"; 1 -> "safe"; else -> "full" }}")
                        onMode(next)
                    }
                }
            }
        }
    }
