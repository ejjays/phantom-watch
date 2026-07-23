package dev.phantomwatch.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.exoplayer.ExoPlayer

object Pip {
    var active = false
    var inPip by mutableStateOf(false)
    var playing by mutableStateOf(false)
    var player: ExoPlayer? = null
}
