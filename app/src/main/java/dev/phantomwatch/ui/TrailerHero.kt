package dev.phantomwatch.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.TextureView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import dev.phantomwatch.R
import dev.phantomwatch.core.AppLog
import dev.phantomwatch.player.TrailerAudio
import dev.phantomwatch.player.TrailerBridge
import dev.phantomwatch.player.playerClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Autoplaying trailer over the poster. Falls back to the poster while it resolves or if YouTube refuses. */
@Composable
internal fun TrailerHero(
    videoId: String,
    poster: String?,
    modifier: Modifier = Modifier,
    onSurface: (TextureView?) -> Unit = {},
    audio: TrailerAudio? = null,
) {
    val ctx = LocalContext.current
    var stream by remember(videoId) { mutableStateOf<TrailerBridge.Result?>(null) }
    var muted by remember { mutableStateOf(false) }
    var paused by remember(videoId) { mutableStateOf(false) }
    var started by remember(videoId) { mutableStateOf(false) }
    var buffering by remember(videoId) { mutableStateOf(true) }
    var pos by remember(videoId) { mutableStateOf(0L) }
    var dur by remember(videoId) { mutableStateOf(0L) }
    // read inside the draw scope only, so a frame tick repaints the bar without recomposing
    val frac = remember(videoId) { mutableFloatStateOf(0f) }
    val exo = remember(videoId) {
        if (audio != null) ExoPlayer.Builder(ctx, audio.renderers(ctx)).build() else ExoPlayer.Builder(ctx).build()
    }
    val fade by animateFloatAsState(if (started) 1f else 0f, label = "trailerFade")

    fun toggle() {
        if (exo.playWhenReady) exo.pause() else exo.play()
        paused = !exo.playWhenReady
    }

    DisposableEffect(videoId) {
        onDispose {
            exo.release()
            onSurface(null)
        }
    }
    LaunchedEffect(videoId) {
        TrailerBridge.warm()
        stream = TrailerBridge.resolve(videoId)
    }
    LaunchedEffect(stream) {
        val s = stream ?: return@LaunchedEffect
        AppLog.d("trailer src ${if (s.hls != null) "hls" else "adaptive"} audio=${audio != null}")
        exo.setMediaSource(source(ctx, s))
        exo.volume = if (muted) 0f else 1f
        exo.repeatMode = Player.REPEAT_MODE_ONE
        exo.playWhenReady = true
        exo.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() {
                started = true
            }

            override fun onPlayerError(error: PlaybackException) {
                AppLog.d("trailer err ${error.errorCodeName} ${error.message}")
                AppLog.d("trailer cause ${error.cause?.stackTraceToString()?.take(1200)}")
            }

            override fun onPlaybackStateChanged(state: Int) {
                AppLog.d("trailer state $state")
            }
        })
        exo.prepare()
    }
    LaunchedEffect(stream) {
        while (isActive) {
            pos = exo.currentPosition.coerceAtLeast(0)
            dur = exo.duration.coerceAtLeast(0)
            buffering = exo.playbackState == Player.STATE_BUFFERING || exo.playbackState == Player.STATE_IDLE
            delay(250)
        }
    }
    LaunchedEffect(stream) {
        while (isActive) {
            withFrameNanos { }
            val d = exo.duration
            if (d > 0) frac.floatValue = (exo.currentPosition.toFloat() / d).coerceIn(0f, 1f)
        }
    }
    LaunchedEffect(muted) {
        exo.volume = if (muted) 0f else 1f
    }

    Box(modifier) {
        if (poster != null) {
            AsyncImage(model = poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(SurfaceColor))
        }
        if (stream != null) {
            AndroidView(
                factory = { c ->
                    val v = LayoutInflater.from(c).inflate(R.layout.trailer_view, null) as PlayerView
                    v.player = exo
                    onSurface(v.videoSurfaceView as? TextureView)
                    v
                },
                update = { v -> v.player = exo },
                modifier = Modifier.fillMaxSize().alpha(fade),
            )
        }
        Box(Modifier.fillMaxSize().pointerInput(stream) {
            detectTapGestures { if (stream != null) toggle() }
        })
        if (stream != null && buffering) {
            CircularProgressIndicator(
                color = Cyan,
                strokeWidth = 3.dp,
                modifier = Modifier.align(Alignment.Center).size(40.dp),
            )
        }
        if (stream != null && paused && !buffering) {
            Box(
                Modifier.align(Alignment.Center).size(54.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.52f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        if (stream != null) {
            IconButton(
                onClick = { muted = !muted },
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                    .size(32.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
            ) {
                Icon(
                    painter = painterResource(if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume_on),
                    contentDescription = if (muted) "Unmute" else "Mute",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (stream != null && started) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.62f)))
                    .padding(start = 14.dp, end = 14.dp, top = 26.dp, bottom = 12.dp),
            ) {
                Text(
                    "${playerClock(pos)} / ${playerClock(dur)}",
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.fillMaxWidth().height(3.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f))
                        .drawBehind {
                            if (frac.floatValue > 0f) {
                                drawRoundRect(
                                    color = Color.White,
                                    size = Size(size.width * frac.floatValue, size.height),
                                    cornerRadius = CornerRadius(size.height / 2f),
                                )
                            }
                        },
                )
            }
        }
    }
}

private fun source(ctx: Context, s: TrailerBridge.Result): MediaSource {
    val hls = s.hls
    if (hls != null) {
        return HlsMediaSource.Factory(DefaultDataSource.Factory(ctx)).createMediaSource(MediaItem.fromUri(hls))
    }
    val factory = ProgressiveMediaSource.Factory(DefaultDataSource.Factory(ctx))
    return MergingMediaSource(
        factory.createMediaSource(MediaItem.fromUri(s.video!!)),
        factory.createMediaSource(MediaItem.fromUri(s.audio!!)),
    )
}
