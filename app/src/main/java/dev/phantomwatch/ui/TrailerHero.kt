package dev.phantomwatch.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.TextureView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
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
import dev.phantomwatch.player.TrailerCache
import dev.phantomwatch.player.TrailerBridge
import dev.phantomwatch.player.loadControl
import dev.phantomwatch.player.playerClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

// falls back to the poster while it resolves, or if YouTube refuses
@Composable
internal fun TrailerHero(
    videoId: String,
    poster: String?,
    modifier: Modifier = Modifier,
    onSurface: (TextureView?) -> Unit = {},
    audio: TrailerAudio? = null,
) {
    val chrome = LocalTrailerChrome.current
    val ctx = LocalContext.current
    var stream by remember(videoId) { mutableStateOf<TrailerBridge.Result?>(null) }
    var muted by remember { mutableStateOf(false) }
    var paused by remember(videoId) { mutableStateOf(false) }
    var started by remember(videoId) { mutableStateOf(false) }
    var buffering by remember(videoId) { mutableStateOf(true) }
    var pos by remember(videoId) { mutableStateOf(0L) }
    // read inside the draw scope only, so a frame tick repaints the bar without recomposing
    val frac = remember(videoId) { mutableFloatStateOf(0f) }
    // a prewarmed clip only needs enough lead to start on a swipe; the rest would be stolen
    // from the clip that is actually playing
    val exo = remember(videoId) {
        val b = if (audio != null) ExoPlayer.Builder(ctx, audio.renderers(ctx)) else ExoPlayer.Builder(ctx)
        val cap = if (chrome.prewarm) 12_000 else 30_000
        b.setLoadControl(loadControl(cap, startMs = 800, minBufferMs = cap))
            .build().also { AppLog.d("trailer make $videoId ${System.identityHashCode(it)}") }
    }
    val fade by animateFloatAsState(if (started) 1f else 0f, label = "trailerFade")

    fun toggle() {
        if (exo.playWhenReady) exo.pause() else exo.play()
        paused = !exo.playWhenReady
    }

    DisposableEffect(videoId) {
        val l = object : Player.Listener {
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
        }
        exo.addListener(l)
        onDispose {
            exo.removeListener(l)
            AppLog.d("trailer release $videoId")
            exo.release()
            onSurface(null)
        }
    }
    LaunchedEffect(videoId) {
        TrailerBridge.warm()
        stream = TrailerBridge.resolve(videoId)
    }
    // keyed on the url: the same stream arriving twice must not reload the source mid-play
    LaunchedEffect(stream?.hls ?: stream?.video) {
        val s = stream ?: return@LaunchedEffect
        AppLog.d("trailer src ${if (s.hls != null) "hls" else "adaptive"} audio=${audio != null}")
        exo.setMediaSource(source(ctx, s))
        exo.volume = if (muted || chrome.prewarm) 0f else 1f
        exo.repeatMode = Player.REPEAT_MODE_ONE
        exo.playWhenReady = !chrome.prewarm
        exo.prepare()
    }
    LaunchedEffect(stream) {
        while (isActive) {
            pos = exo.currentPosition.coerceAtLeast(0)
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
        exo.volume = if (muted || chrome.prewarm) 0f else 1f
    }
    // the buffered clip takes over the moment the swipe lands on it
    LaunchedEffect(chrome.prewarm, chrome.paused, stream) {
        if (stream == null) return@LaunchedEffect
        exo.volume = if (muted || chrome.prewarm) 0f else 1f
        exo.playWhenReady = !chrome.prewarm && !chrome.paused
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
        if (stream != null && buffering && poster == null) {
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
                    contentDescription = stringResource(R.string.common_play),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        if (stream != null) {
            IconButton(
                onClick = { muted = !muted },
                modifier = (if (chrome.muteBottom != null) {
                    Modifier.align(Alignment.BottomEnd).padding(end = chrome.muteEnd, bottom = chrome.muteBottom)
                } else {
                    Modifier.align(Alignment.TopEnd).padding(top = 12.dp + chrome.topInset, end = 12.dp)
                })
                    .size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.42f)),
            ) {
                Icon(
                    painter = painterResource(if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume_on),
                    contentDescription = if (muted) stringResource(R.string.player_unmute) else stringResource(R.string.player_mute),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        if (chrome.progress && stream != null) {
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.62f)))
                    .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 12.dp + chrome.bottomInset),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.weight(1f).height(3.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.26f))
                        .drawBehind {
                            val done = size.width * frac.floatValue
                            if (done <= 0f) return@drawBehind
                            drawRoundRect(
                                color = Color.White,
                                size = Size(done, size.height),
                                cornerRadius = CornerRadius(size.height / 2f),
                            )
                            drawCircle(Color.White, size.height * 1.7f, Offset(done, size.height / 2f))
                        },
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    playerClock(pos),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

private fun source(ctx: Context, s: TrailerBridge.Result): MediaSource {
    val hls = s.hls
    if (hls != null) {
        return HlsMediaSource.Factory(TrailerCache.dataSource(ctx)).createMediaSource(MediaItem.fromUri(hls))
    }
    val factory = ProgressiveMediaSource.Factory(TrailerCache.dataSource(ctx))
    return MergingMediaSource(
        factory.createMediaSource(MediaItem.fromUri(s.video!!)),
        factory.createMediaSource(MediaItem.fromUri(s.audio!!)),
    )
}
