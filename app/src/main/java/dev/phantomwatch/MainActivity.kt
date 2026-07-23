package dev.phantomwatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import dev.phantomwatch.core.AppLog
import dev.phantomwatch.core.RemoteConfig
import dev.phantomwatch.player.Pip
import dev.phantomwatch.player.Playback
import dev.phantomwatch.provider.JsProviders
import dev.phantomwatch.social.Social

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_PhantomWatch)
        super.onCreate(savedInstanceState)
        // phones stay portrait; tablets and desktop windows follow the display
        if (resources.configuration.smallestScreenWidthDp < 600) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        Pip.inPip = false
        RemoteConfig.load(this)
        JsProviders.init(applicationContext)
        Social.init(applicationContext)
        Thread { RemoteConfig.refresh(applicationContext) }.start()
        androidx.core.content.ContextCompat.registerReceiver(
            this, pipRecv,
            android.content.IntentFilter().apply {
                addAction(ACT_PIP_TOGGLE)
                addAction(ACT_PIP_CLOSE)
            },
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) { AppRoot() }
            }
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(pipRecv)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        Pip.inPip = isInPictureInPictureMode
        AppLog.d("pip mode $isInPictureInPictureMode")
        val gone = isFinishing || !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        if (!isInPictureInPictureMode && gone) pauseForPipClose("mode")
    }

    override fun onStop() {
        super.onStop()
        if (Pip.inPip) pauseForPipClose("stop")
    }

    private fun pauseForPipClose(why: String) {
        AppLog.d("pip close pause $why")
        Playback.player?.pause()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val p = Pip.player
        AppLog.d("pip leave active=${Pip.active} player=${p != null}")
        if (android.os.Build.VERSION.SDK_INT >= 31) return
        if (Pip.active && p != null) {
            try {
                val ok = pipParams(p.isPlaying)?.let { enterPictureInPictureMode(it) } ?: false
                AppLog.d("pip enter $ok")
            } catch (e: Exception) {
                AppLog.d("pip fail ${e.message}")
            }
        }
    }

    fun armPip(on: Boolean, playing: Boolean) {
        try {
            pipParams(playing, auto = on)?.let {
                setPictureInPictureParams(it)
                AppLog.d("pip arm ok on=$on playing=$playing")
            }
        } catch (e: Exception) {
            AppLog.d("pip arm fail on=$on ${e.message}")
        }
    }

    private val pipRecv = object : android.content.BroadcastReceiver() {
        override fun onReceive(ctx: android.content.Context, intent: android.content.Intent) {
            val p = Pip.player ?: return
            when (intent.action) {
                ACT_PIP_TOGGLE -> {
                    try {
                        if (p.isPlaying) p.pause() else p.play()
                        pipParams(p.isPlaying)?.let { setPictureInPictureParams(it) }
                    } catch (_: Exception) {
                    }
                }
                ACT_PIP_CLOSE -> {
                    try {
                        p.pause()
                    } catch (_: Exception) {
                    }
                    finish()
                }
            }
        }
    }

    private fun pipParams(playing: Boolean, auto: Boolean = false): android.app.PictureInPictureParams? = try {
        val toggle = android.app.RemoteAction(
            android.graphics.drawable.Icon.createWithResource(
                this,
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            ),
            if (playing) "Pause" else "Play", "",
            android.app.PendingIntent.getBroadcast(
                this, 1,
                android.content.Intent(ACT_PIP_TOGGLE).setPackage(packageName),
                android.app.PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        val close = android.app.RemoteAction(
            android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
            "Close", "",
            android.app.PendingIntent.getBroadcast(
                this, 2,
                android.content.Intent(ACT_PIP_CLOSE).setPackage(packageName),
                android.app.PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        val b = android.app.PictureInPictureParams.Builder()
            .setAspectRatio(android.util.Rational(16, 9))
            .setActions(listOf(toggle, close))
        if (android.os.Build.VERSION.SDK_INT >= 31) b.setAutoEnterEnabled(auto)
        b.build()
    } catch (e: Exception) {
        AppLog.d("pip params fail ${e.message}")
        null
    }
}

private const val ACT_PIP_TOGGLE = "dev.phantomwatch.PIP_TOGGLE"
private const val ACT_PIP_CLOSE = "dev.phantomwatch.PIP_CLOSE"
