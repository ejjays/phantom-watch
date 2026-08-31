package dev.phantomwatch.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phantomwatch.R
import dev.phantomwatch.data.Title
import kotlinx.coroutines.delay

@Composable
internal fun PlayerFailedOverlay(show: Boolean, onPick: () -> Unit, onBack: () -> Unit) {
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(show) {
        if (!show) {
            failed = false
            return@LaunchedEffect
        }
        delay(2500)
        failed = true
    }
    if (!failed) return
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.player_playback_failed), color = Color.White, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPick) { Text(stringResource(R.string.player_choose_source)) }
                TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
            }
        }
    }
}

@Composable
internal fun PlayerBuildFailed(error: String, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.player_build_failed, error))
        Button(onClick = onBack) { Text(stringResource(R.string.common_back)) }
    }
}

@Composable
internal fun PlayerResolving(
    title: String,
    season: String,
    episode: String,
    resolveErr: String?,
    media: Title,
    overview: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPickList: () -> Unit,
    onEpisode: (String, String) -> Unit,
    onOpenTitle: (Title) -> Unit,
) {
    BackHandler(onBack = onBack)
    val cfg = LocalConfiguration.current
    // Same shell the player uses once the stream lands, so resolving never flips the layout.
    val tablet = cfg.orientation == Configuration.ORIENTATION_LANDSCAPE && cfg.smallestScreenWidthDp >= 600
    val stageH = (minOf(cfg.screenWidthDp, cfg.screenHeightDp).toFloat() * 9 / 16).dp
    val stage: @Composable (Modifier) -> Unit = { mod ->
        Box(mod) {
            PlayerStage(
                title = if (season.isNotEmpty() && episode.isNotEmpty()) stringResource(R.string.title_season_episode, title, season, episode) else title,
                fullscreen = false, isPlaying = false, stalled = true,
                positionMs = 0, durationMs = 0, bufferedMs = 0, controlsVisible = true,
                flash = resolveErr, seekMark = null, seekInset = 0.dp, locked = false,
                subsOn = false, canSubs = false,
                onBack = onBack, onFullscreen = {}, onTogglePlay = {}, onToggleLock = {},
                onSeek = {}, onInteract = {}, onZoneTap = {}, onToggleSubs = {},
                onSwipe = { _, _ -> }, onSwipeEnd = {},
                video = { v -> Box(v.fillMaxSize().background(Color.Black)) },
            )
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (tablet) {
            PlayerPanel(
                media = media, season = season, episode = episode, overview = overview,
                onEpisode = onEpisode, onOpenTitle = onOpenTitle, video = stage,
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().statusBarsPadding().height(stageH)) { stage(Modifier.fillMaxSize()) }
                PlayerPanel(
                    media = media, season = season, episode = episode,
                    overview = overview, onEpisode = onEpisode, onOpenTitle = onOpenTitle,
                )
            }
        }
        if (resolveErr != null) {
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                Button(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
                TextButton(onClick = onPickList) { Text(stringResource(R.string.player_choose)) }
            }
        }
    }
}
