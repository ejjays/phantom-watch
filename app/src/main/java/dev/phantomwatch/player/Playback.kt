package dev.phantomwatch.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dev.phantomwatch.MainActivity

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
        // one shared player can't shrink LoadControl for file:// streams: restore the torrent readahead if torrents come back
        val selector = DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setPreferredAudioLanguage("en")
                .setPreferredTextLanguage("en")
                .build()
        }
        val load = loadControl()
        // start ABR at 1080p-class rates so it opens on the top rung and only drops when the CDN can't feed it
        val meter = DefaultBandwidthMeter.Builder(context)
            .setInitialBitrateEstimate(8_000_000)
            .build()
        return ExoPlayer.Builder(context)
            .setTrackSelector(selector)
            .setLoadControl(load)
            .setBandwidthMeter(meter)
            .setRenderersFactory(AiAudio.renderers(context))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
    }
}

// prioritize time to prevent buffer stalls
@OptIn(UnstableApi::class)
internal fun loadControl(maxBufferMs: Int = 90_000): DefaultLoadControl =
    DefaultLoadControl.Builder()
        .setBufferDurationsMsForStreaming(30_000, maxBufferMs, 1_500, 5_000)
        .setPrioritizeTimeOverSizeThresholds(true)
        .setTargetBufferBytes(96 * 1024 * 1024)
        .setBackBuffer(10_000, false)
        .build()

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
