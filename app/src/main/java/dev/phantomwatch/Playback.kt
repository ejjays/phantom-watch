package dev.phantomwatch

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Contrast
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

internal object Playback {
    @Volatile
    var player: ExoPlayer? = null

    var lastScreen: Screen? = null

    fun ensure(context: Context): ExoPlayer {
        player?.let { return it }
        synchronized(this) {
            player?.let { return it }
            val p = build(context.applicationContext)
            player = p
            context.applicationContext.startService(Intent(context, PlaybackService::class.java))
            return p
        }
    }

    @OptIn(UnstableApi::class)
    private fun build(context: Context): ExoPlayer {
        // shortcut: one shared player can't shrink LoadControl for file:// streams; restore the torrent readahead if torrents come back
        val selector = DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setPreferredAudioLanguage("en")
                .setPreferredTextLanguage("en")
                .setForceHighestSupportedBitrate(true)
                .build()
        }
        return ExoPlayer.Builder(context).setTrackSelector(selector).build().apply {
            // shortcut: one shared player can't shrink LoadControl for file:// streams; restore the torrent readahead if torrents come back
            try {
                setVideoEffects(listOf(Contrast(0.1f)))
            } catch (_: Exception) {
            }
        }
    }
}

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = Playback.player ?: return
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: Intent(this, MainActivity::class.java)
        session = MediaSession.Builder(this, player)
            .setSessionActivity(PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE))
            .build()
            .also { addSession(it) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0 || p.playbackState == Player.STATE_ENDED) {
            stopSelf()
            return
        }
        p.pause()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        Playback.player = null
        Playback.lastScreen = null
        super.onDestroy()
    }
}
