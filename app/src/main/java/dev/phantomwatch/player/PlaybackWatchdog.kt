package dev.phantomwatch.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.media3.exoplayer.ExoPlayer
import dev.phantomwatch.core.AppLog
import dev.phantomwatch.data.Stream
import dev.phantomwatch.provider.DeadHosts
import dev.phantomwatch.provider.DeadReleases
import dev.phantomwatch.provider.Providers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val RESUME_MIN_MS = 60_000L
private const val RESUME_MS = 15_000L
private const val STUCK_MS = 30_000L
// a start that never renders shows in seconds; only a mid-play hang can be a slow edge
private const val START_STUCK_MS = 12_000L
// a hang is usually one bad edge, not a dead provider: keep the ban short so the host can recover
private const val HANG_BAN_MIN = 120L
// the host answered, so this may be a slow edge or a wedged decoder: a short sit-out is enough
private const val HANG_SOFT_MIN = 10L

// a stream that hangs never errors, so the fallback path never runs: ExoPlayer just buffers
// drop the resume, ban the host, then let the caller walk to the next format
@Composable
internal fun PlaybackWatchdog(
    exo: ExoPlayer,
    stalled: Boolean,
    started: Boolean,
    live: Stream,
    onStuck: suspend () -> Unit,
) {
    val stalledNow by rememberUpdatedState(stalled)
    val stuckNow by rememberUpdatedState(onStuck)
    LaunchedEffect(live.url, stalled, started) {
        if (!stalled) return@LaunchedEffect
        if (!started && exo.currentPosition > RESUME_MIN_MS) {
            delay(RESUME_MS)
            if (!stalledNow) return@LaunchedEffect
            AppLog.d("watchdog: resume ${exo.currentPosition / 1000}s never played, back to 0")
            exo.seekTo(0)
        }
        delay(if (started) STUCK_MS else START_STUCK_MS)
        if (!stalledNow) return@LaunchedEffect
        val host = DeadHosts.hostOf(live.url)
        val alive = withContext(Dispatchers.IO) { Providers.alive(live.url, live.headers, live.referer) }
        val ban = if (alive) HANG_SOFT_MIN else HANG_BAN_MIN
        AppLog.d("watchdog: $host stuck at ${exo.currentPosition / 1000}s, ban ${ban}m, next source")
        // ban the host, not the rung: the sibling heights hang the same way and each costs a full
        // timeout, which is how a dead 1080p source ends up walking down to 360p
        DeadHosts.markHang(live.url, ban)
        DeadReleases.urls.add(live.url)
        stuckNow()
    }
}
