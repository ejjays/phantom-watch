package dev.phantomwatch.ui

// cap at the video's own letterbox, or taller-than-16:9 content slides under the status bar
internal fun playerVideoShift(shiftPx: Float, stagePx: Float, videoPx: Float): Float =
    shiftPx.coerceAtMost((stagePx - videoPx).coerceAtLeast(0f)).coerceAtLeast(0f)
