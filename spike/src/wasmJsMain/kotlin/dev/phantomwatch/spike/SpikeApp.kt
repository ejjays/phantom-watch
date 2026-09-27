package dev.phantomwatch.spike

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.phantomwatch.core.PlaySource
import dev.phantomwatch.core.resolveSources
import dev.phantomwatch.ui.BrowseState
import dev.phantomwatch.ui.DetailScreen
import dev.phantomwatch.ui.HomeScreen
import dev.phantomwatch.ui.WebPlayer
import dev.phantomwatch.ui.WebTrailer
import dev.phantomwatch.ui.WideDetail
import kotlinx.coroutines.launch

@Composable
fun SpikeApp() {
    val browse = remember { BrowseState() }
    val scope = rememberCoroutineScope()
    var sources by remember { mutableStateOf<List<PlaySource>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(Modifier.fillMaxSize()) {
            if (sources.isNotEmpty()) {
                WebPlayer(sources, onBack = { sources = emptyList() })
            } else {
                HomeScreen(
                    browse = browse,
                    onWatch = { item, season, episode ->
                        if (!busy) {
                            busy = true
                            error = null
                            scope.launch {
                                try {
                                    val r = resolveSources(PW_URL, PW_KEY, item.kind, item.id, season, episode)
                                    if (r.isEmpty()) error = "No source for ${item.title}"
                                    else sources = r
                                } catch (e: Exception) {
                                    error = e.message ?: "resolve failed"
                                }
                                busy = false
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
                )
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
