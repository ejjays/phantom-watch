package dev.phantomwatch.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.phantomwatch.R
import dev.phantomwatch.core.RemoteConfig
import dev.phantomwatch.data.Title
import dev.phantomwatch.data.Tmdb
import dev.phantomwatch.ui.Cyan
import dev.phantomwatch.data.DetailCache
import dev.phantomwatch.ui.Mono
import dev.phantomwatch.ui.Rubik
import dev.phantomwatch.ui.Slate300
import dev.phantomwatch.ui.Slate400
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val NEXT_SECONDS = 10

internal data class NextUp(val season: String, val episode: String, val name: String)

internal fun nextInSeason(numbers: List<Int>, current: Int): Int? =
    numbers.filter { it > current }.minOrNull()

internal suspend fun findNextUp(id: String, season: String, episode: String): NextUp? {
    val s = season.toIntOrNull() ?: return null
    val e = episode.toIntOrNull() ?: return null
    val key = RemoteConfig.tmdbKey()
    val eps = DetailCache.episodes("tv", id, s)
        ?: Tmdb.season(key, id, s).also { if (it.isNotEmpty()) DetailCache.putEpisodes("tv", id, s, it) }
    val n = nextInSeason(eps.map { it.n }, e)
    if (n != null) return NextUp(s.toString(), n.toString(), eps.firstOrNull { it.n == n }?.name.orEmpty())
    val next = DetailCache.detail("tv", id)?.seasons?.firstOrNull { it.number > s } ?: return null
    val more = DetailCache.episodes("tv", id, next.number)
        ?: Tmdb.season(key, id, next.number).also { if (it.isNotEmpty()) DetailCache.putEpisodes("tv", id, next.number, it) }
    val first = more.firstOrNull() ?: return null
    return NextUp(next.number.toString(), first.n.toString(), first.name)
}

@Composable
internal fun rememberNextUp(media: Title, season: String, episode: String): NextUp? {
    var up by remember(media.id, season, episode) { mutableStateOf<NextUp?>(null) }
    LaunchedEffect(media.id, season, episode) {
        if (media.kind != "tv") return@LaunchedEffect
        up = withContext(Dispatchers.IO) { runCatching { findNextUp(media.id, season, episode) }.getOrNull() }
    }
    return up
}

@Composable
internal fun NextUpHost(
    player: ExoPlayer,
    media: Title,
    season: String,
    episode: String,
    onEpisode: (String, String) -> Unit,
) {
    if (media.kind != "tv" || Pip.inPip) return
    var ended by remember { mutableStateOf(false) }
    var next by remember(media.id, season, episode) { mutableStateOf<NextUp?>(null) }
    var left by remember { mutableStateOf(NEXT_SECONDS) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                ended = state == Player.STATE_ENDED
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(ended) {
        if (!ended) return@LaunchedEffect
        if (next == null) next = withContext(Dispatchers.IO) { runCatching { findNextUp(media.id, season, episode) }.getOrNull() }
        val up = next ?: return@LaunchedEffect
        left = NEXT_SECONDS
        while (left > 0) {
            delay(1000)
            left--
        }
        ended = false
        next = null
        onEpisode(up.season, up.episode)
    }
    if (!ended) return
    val up = next ?: return
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(20.dp))
                .background(Color(0xF21A1A1A)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.next_up_title), color = Cyan, fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.season_episode, up.season, up.episode),
                color = Color.White, fontFamily = Rubik, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            )
            if (up.name.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    up.name, color = Slate300, fontFamily = Rubik, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.next_up_playing_in, left), color = Slate400, fontFamily = Mono, fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(Cyan)
                        .clickable {
                            ended = false
                            next = null
                            onEpisode(up.season, up.episode)
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text(
                        stringResource(R.string.next_up_play_now), color = Color(0xFF0B0B0B), fontFamily = Rubik,
                        fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    )
                }
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f))
                        .clickable { ended = false }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text(
                        stringResource(R.string.common_cancel), color = Slate300, fontFamily = Rubik,
                        fontWeight = FontWeight.Medium, fontSize = 13.sp,
                    )
                }
            }
        }
    }
}
