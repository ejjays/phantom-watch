package dev.phantomwatch.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import dev.phantomwatch.data.Title

@Composable
internal fun PlayerBuildFailed(error: String, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Player failed: $error")
        Button(onClick = onBack) { Text("Back") }
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
    val stageH = (minOf(cfg.screenWidthDp, cfg.screenHeightDp).toFloat() * 9 / 16).dp
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxWidth().statusBarsPadding().height(stageH)) {
            PlayerStage(
                title = if (season.isNotEmpty() && episode.isNotEmpty()) "$title · S$season E$episode" else title,
                fullscreen = false, isPlaying = false, stalled = true,
                positionMs = 0, durationMs = 0, bufferedMs = 0, controlsVisible = true,
                flash = resolveErr, seekMark = null, seekInset = 0.dp, locked = false,
                subsOn = false, canSubs = false,
                onBack = onBack, onFullscreen = {}, onTogglePlay = {}, onToggleLock = {},
                onSeek = {}, onInteract = {}, onZoneTap = {}, onToggleSubs = {},
                onSwipe = { _, _ -> }, onSwipeEnd = {},
                video = { mod -> Box(mod.fillMaxSize().background(Color.Black)) },
            )
        }
        PlayerPanel(
            media = media, season = season, episode = episode,
            overview = overview, onEpisode = onEpisode, onOpenTitle = onOpenTitle,
        )
        if (resolveErr != null) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                Button(onClick = onRetry) { Text("Retry") }
                TextButton(onClick = onPickList) { Text("Choose") }
            }
        }
    }
}
