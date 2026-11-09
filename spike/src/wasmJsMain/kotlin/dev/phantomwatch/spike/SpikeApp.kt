package dev.phantomwatch.spike

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.phantomwatch.core.PlaySource
import dev.phantomwatch.core.SubSource
import dev.phantomwatch.core.pwLog
import dev.phantomwatch.core.resolveSources
import dev.phantomwatch.data.Title
import dev.phantomwatch.ui.BrowseState
import dev.phantomwatch.ui.DetailScreen
import dev.phantomwatch.ui.HomeLiquidTabs
import dev.phantomwatch.ui.HomeScreen
import dev.phantomwatch.ui.WebTrailer
import dev.phantomwatch.ui.WideDetail
import dev.phantomwatch.ui.settings.SettingsAction
import dev.phantomwatch.ui.settings.SettingsData
import dev.phantomwatch.ui.settings.SettingsScreen
import kotlinx.browser.window
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private const val REPO = "https://github.com/ejjays/phantom-watch"

@Composable
fun SpikeApp() {
    val browse = remember { BrowseState() }
    val settings = remember { mutableStateMapOf<String, Int>() }
    val scope = rememberCoroutineScope()
    var sources by remember { mutableStateOf<List<PlaySource>>(emptyList()) }
    var subs by remember { mutableStateOf<List<SubSource>>(emptyList()) }
    var playing by remember { mutableStateOf<Title?>(null) }
    var playSeason by remember { mutableStateOf("") }
    var playEpisode by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(playing) {
        pwLog("nav", if (playing == null) "-> Home" else "-> Player(back=Home)")
    }
    LaunchedEffect(browse.loaded, browse.rails) {
        if (!browse.loaded) return@LaunchedEffect
        pwLog("trending", "${browse.trending.size}")
        pwLog(
            "rails",
            browse.rails.joinToString(" ") { "${it.key}=${it.items.size}" } + " failed=${browse.failed}",
        )
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(Modifier.fillMaxSize()) {
            val tabBackdrop = rememberLayerBackdrop()
            val now = playing
            if (now != null && sources.isNotEmpty()) {
                PlayerScreen(
                    media = now, season = playSeason, episode = playEpisode,
                    sources = sources, subs = subs,
                    onBack = {
                        playing = null
                        sources = emptyList()
                    },
                    onOpenTitle = { t ->
                        playing = null
                        sources = emptyList()
                        browse.openTitle = t
                    },
                )
            } else {
                Box(Modifier.fillMaxSize().layerBackdrop(tabBackdrop)) {
                    HomeScreen(
                        browse = browse,
                        onWatch = { item, season, episode ->
                            if (!busy) {
                                busy = true
                                error = null
                                scope.launch {
                                    pwLog("direct", "${item.kind}/${item.id} s=$season e=$episode \"${item.title}\"")
                                    try {
                                        val r = withTimeout(45_000) {
                                            resolveSources(PW_URL, PW_KEY, item.kind, item.id, season, episode)
                                        }
                                        if (r.formats.isEmpty()) {
                                            pwLog("resolve", "no source for ${item.title}")
                                            error = "No source for ${item.title}"
                                        } else {
                                            playing = item
                                            playSeason = season
                                            playEpisode = episode
                                            sources = r.formats
                                            subs = r.subs
                                        }
                                    } catch (e: Exception) {
                                        pwLog("resolve", "failed: ${e::class.simpleName}: ${e.message}")
                                        error = e.message ?: "resolve failed"
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        },
                        detailPane = { item, onClose, onWatch, startEpisodes ->
                            if (item != null) DetailScreen(
                                item, onClose, onWatch, startEpisodes,
                                wideDetail = { a ->
                                    val art = (a.item.backdrop ?: a.item.poster) ?: (a.d.backdrop ?: a.d.poster)
                                    WideDetail(
                                        a.item, a.d, a.pending, a.photosReady, a.checking,
                                        a.onClose, a.onWatch, a.onWatchPress, a.onViewAll,
                                        hero = { m -> WebTrailer(a.d.trailer, art, m) },
                                    )
                                },
                            )
                        },
                        youScreen = {
                            SettingsScreen(
                                SettingsData(
                                    version = "1.0 (web)",
                                    providerCount = 8,
                                    settings = settings,
                                    onValue = { key, value -> settings[key] = value },
                                    onAction = { action ->
                                        when (action) {
                                            SettingsAction.ClearHistory -> browse.recent = emptyList()
                                            SettingsAction.Updates -> window.open("$REPO/releases", "_blank")
                                            SettingsAction.Licences -> window.open("$REPO#licence", "_blank")
                                            SettingsAction.Privacy -> window.open("$REPO#privacy", "_blank")
                                            SettingsAction.Feedback -> window.open("$REPO/issues", "_blank")
                                        }
                                    },
                                ),
                            )
                        },
                    )
                }
                Box(
                    Modifier.align(Alignment.BottomCenter)
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp)
                        .widthIn(max = 480.dp),
                ) {
                    HomeLiquidTabs(
                        backdrop = tabBackdrop,
                        selectedTabIndex = { browse.tab },
                        onSelect = browse::selectTab,
                    )
                }
            }
            if (busy || error != null) {
                Box(
                    Modifier.fillMaxSize().background(Color(0xCC000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (busy) {
                            CircularProgressIndicator()
                            Text("Finding a source…", color = Color.White, modifier = Modifier.padding(top = 16.dp))
                        } else {
                            Text(error ?: "", color = Color.White, modifier = Modifier.padding(24.dp))
                            TextButton(onClick = { error = null }) { Text("Dismiss") }
                        }
                    }
                }
            }
        }
    }
}
