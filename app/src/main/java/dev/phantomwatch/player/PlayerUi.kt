package dev.phantomwatch.player

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// the debug log FAB must stay off the player
object PlayerUi {
    var fullscreen by mutableStateOf(false)
    var token = 0

    // survives the screen rebuild on episode change, cleared when leaving the player
    var brightness: Float? = null

    val hidesChrome: Boolean get() = fullscreen || Pip.inPip

    fun applyBrightness(activity: Activity?, value: Float?) {
        val w = activity?.window ?: return
        val lp = w.attributes
        lp.screenBrightness = value ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        w.attributes = lp
    }
}
