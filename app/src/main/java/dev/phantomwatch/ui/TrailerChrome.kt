package dev.phantomwatch.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// the clips feed lifts the player's chrome clear of the floating tab bar; nothing else moves
internal data class TrailerChrome(
    val progress: Boolean = true,
    val topInset: Dp = 0.dp,
    val bottomInset: Dp = 0.dp,
    // set to park the mute button bottom-right instead, above the clips poster
    val muteBottom: Dp? = null,
    val muteEnd: Dp = 12.dp,
    // the clip below the current one: prepared and buffered, silent, not yet on screen
    val prewarm: Boolean = false,
    // a sheet covers the player, so keep the picture and drop the sound
    val paused: Boolean = false,
)

internal val LocalTrailerChrome = compositionLocalOf { TrailerChrome() }
