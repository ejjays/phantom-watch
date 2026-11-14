package dev.phantomwatch.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// clips feed lifts player chrome clear of floating tab bar, nothing else moves
internal data class TrailerChrome(
    val progress: Boolean = true,
    val topInset: Dp = 0.dp,
    val bottomInset: Dp = 0.dp,
    // set to park mute button bottom right instead, above clips poster
    val muteBottom: Dp? = null,
    val muteEnd: Dp = 12.dp,
    // clip below current one, prepared buffered silent not on screen yet
    val prewarm: Boolean = false,
    // sheet covers player, keep picture drop sound
    val paused: Boolean = false,
)

internal val LocalTrailerChrome = compositionLocalOf { TrailerChrome() }
