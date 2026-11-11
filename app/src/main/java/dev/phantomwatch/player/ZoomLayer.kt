package dev.phantomwatch.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import dev.phantomwatch.core.AppLog

// one pinch fills the stage, a pinch the other way fits it again; one finger still reaches the
// tap zones, and a two-finger drag is consumed so it cannot turn into a brightness swipe
internal fun Modifier.pinchZoom(zoom: () -> Float, fill: () -> Float, onZoom: (Float) -> Unit): Modifier =
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
                    val target = if (acc > 1f) fill().coerceAtLeast(1f) else 1f
                    if (target != zoom()) {
                        AppLog.d("pinch ${if (target > 1f) "fill" else "fit"} ${"%.2f".format(target)}")
                        onZoom(target)
                    }
                }
            }
        }
    }
