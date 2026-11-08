package dev.phantomwatch.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.phantomwatch.R
import dev.phantomwatch.core.AppLog
import dev.phantomwatch.data.DetailCache
import dev.phantomwatch.core.RemoteConfig
import dev.phantomwatch.data.Stream
import dev.phantomwatch.data.Sub
import dev.phantomwatch.data.Title
import dev.phantomwatch.data.Tmdb
import dev.phantomwatch.provider.Providers
import dev.phantomwatch.provider.VideoFormat
import dev.phantomwatch.provider.DeadHosts
import dev.phantomwatch.provider.DeadReleases
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
internal fun SourcesScreen(
    title: Title,
    season: String,
    episode: String,
    autoplay: Boolean,
    onStreamReady: (Stream) -> Unit,
    onBack: () -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    var formats by remember { mutableStateOf<List<VideoFormat>>(emptyList()) }
    var formatSubs by remember { mutableStateOf<List<Sub>>(emptyList()) }
    var err by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var imdbState by remember { mutableStateOf<String?>(null) }
    var gone by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val lang = remember(title.id) {
        Providers.langName(DetailCache.detail(title.kind, title.id)?.language) ?: "Sub"
    }
    BackHandler(onBack = onBack)
    LaunchedEffect(title.id, season, episode) {
        try {
            status = context.getString(R.string.sources_trying_direct)
            val directJob = async(Dispatchers.IO) {
                Providers.directAll(title.id, title.kind, season, episode, title.title)
            }
            val imdbJob = async(Dispatchers.IO) {
                Tmdb.imdbOf(title.id, title.kind, RemoteConfig.tmdbKey())?.removePrefix("tt")
            }
            val all = directJob.await()
            val imdbDigits = imdbJob.await()
            imdbState = imdbDigits
            AppLog.d("imdb ${imdbDigits ?: "none"}")
            val live = all.first.filter { it.url !in DeadReleases.urls }
            AppLog.d("direct ${live.size} formats subs=${all.second.size}")
            if (live.isNotEmpty()) {
                formats = live
                formatSubs = all.second
                if (autoplay) {
                    val best = live.first()
                    AppLog.d(
                        "direct play provider=${Providers.providerName(best)} label=${best.label} " +
                            "height=${best.height} hls=${best.isHls} enSubs=${best.enSubs} " +
                            "enAudio=${best.enAudio} streamSubs=${Providers.subsFor(best, all.second).size}",
                    )
                    gone = true
                    onStreamReady(
                        Stream(
                            url = best.url, isHls = best.isHls,
                            subs = Providers.subsFor(best, all.second),
                            referer = best.referer, sampleMp4 = null,
                            imdb = imdbDigits, headers = best.headers, enSubs = best.enSubs,
                            intro = best.intro,
                            dub = best.dub, dubs = Providers.dubsOf(live, best, Providers.subsFor(best, all.second), lang) ,
                        ),
                    )
                    return@LaunchedEffect
                }
            } else {
                err = context.getString(R.string.player_no_streams)
                return@LaunchedEffect
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            err = e.message
        } finally {
            loading = false
        }
    }
    if ((autoplay && loading || gone) && err == null) {
        val cfg = LocalConfiguration.current
        val stageH = (minOf(cfg.screenWidthDp, cfg.screenHeightDp).toFloat() * 9 / 16).dp
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Box(Modifier.fillMaxWidth().statusBarsPadding().height(stageH)) {
                PlayerStage(
                    title = if (season.isNotEmpty() && episode.isNotEmpty()) stringResource(R.string.title_season_episode, title.title, season, episode) else title.title,
                    fullscreen = false, isPlaying = false, stalled = true,
                    positionMs = 0, durationMs = 0, bufferedMs = 0, controlsVisible = true,
                    flash = null, seekMark = null, seekInset = 0.dp, locked = false,
                    subsOn = false, canSubs = true,
                    onBack = onBack, onFullscreen = {}, onTogglePlay = {}, onToggleLock = {},
                    onSeek = {}, onInteract = {}, onZoneTap = {}, onToggleSubs = {},
                    onSwipe = { _, _ -> }, onSwipeEnd = {},
                    video = { mod -> Box(mod.fillMaxSize().background(Color.Black)) },
                )
            }
            PlayerPanel(media = title, season = season, episode = episode)
        }
        return
    }
    val listPad = ((LocalConfiguration.current.screenWidthDp.dp - 720.dp) / 2).coerceAtLeast(0.dp)
    Column(Modifier.fillMaxSize().padding(horizontal = listPad).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.sources_back)) }
            Text(title.title, Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.titleMedium)
        }
        when {
            loading -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(status ?: stringResource(R.string.sources_finding_release), style = MaterialTheme.typography.bodySmall)
            }
            err != null && formats.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(err ?: stringResource(R.string.sources_no_streams))
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(formats, key = { it.url }) { f ->
                    Column(
                        Modifier.fillMaxWidth().clickable {
                            onStreamReady(
                                Stream(
                                    url = f.url, isHls = f.isHls, subs = Providers.subsFor(f, formatSubs),
                                    referer = f.referer, sampleMp4 = null,
                                    imdb = imdbState, headers = f.headers, enSubs = f.enSubs,
                                    intro = f.intro,
                                    dub = f.dub, dubs = Providers.dubsOf(formats, f, Providers.subsFor(f, formatSubs), lang),
                                ),
                            )
                        }.padding(12.dp),
                    ) {
                        Text(f.label, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            (if (f.height > 0) "${f.height}p · " else "") + if (f.isHls) "HLS" else "MP4",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
