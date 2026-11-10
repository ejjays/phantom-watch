package dev.phantomwatch.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// the debug log FAB must stay off the player
object PlayerUi {
    var fullscreen by mutableStateOf(false)
    var token = 0

    val hidesChrome: Boolean get() = fullscreen || Pip.inPip
}
