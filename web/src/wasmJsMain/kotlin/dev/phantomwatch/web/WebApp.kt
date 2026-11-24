package dev.phantomwatch.web

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.phantomwatch.core.pwLog
import dev.phantomwatch.data.Resume
import dev.phantomwatch.data.SettingsKeys
import dev.phantomwatch.data.Title
import dev.phantomwatch.ui.Route
import dev.phantomwatch.ui.BrowseState
import dev.phantomwatch.ui.DetailScreen
import dev.phantomwatch.ui.Geo
import dev.phantomwatch.ui.HomeLiquidTabs
import dev.phantomwatch.ui.HomeScreen
import dev.phantomwatch.ui.SurfaceColor
import dev.phantomwatch.ui.WebTrailer
import dev.phantomwatch.ui.WideDetail
import dev.phantomwatch.ui.countryChoiceCode
import dev.phantomwatch.ui.settings.SettingsAction
import dev.phantomwatch.ui.settings.SettingsData
import dev.phantomwatch.ui.settings.SettingsScreen
import kotlinx.browser.window

private const val REPO = "https://github.com/ejjays/phantom-watch"
private const val PHANTOM = "https://github.com/ejjays/phantom"

// iPhone draws page under status bar and home indicator, inset lands on app content not #app
// player positions stage from canvas bounds so moving canvas moves picture off chrome, zero on
// android and desktop
@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => { var d = document.createElement('div'); d.style.cssText = 'position:fixed;left:0;top:0;width:0;height:0;padding:env(safe-area-inset-top) env(safe-area-inset-right) env(safe-area-inset-bottom) env(safe-area-inset-left)'; document.body.appendChild(d); var c = getComputedStyle(d); var r = [c.paddingTop, c.paddingRight, c.paddingBottom, c.paddingLeft].map(function (x) { return parseFloat(x) || 0; }).join(','); d.remove(); return r; }")
private external fun safeAreaCss(): String

@Composable
fun WebApp() {
    Geo.overrideCode = countryChoiceCode(WebSettings.on(SettingsKeys.COUNTRY, 0))
    val browse = remember { BrowseState() }
    var playing by remember { mutableStateOf<Route.Player?>(null) }

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
        val inset = remember {
            val px = safeAreaCss().split(',').map { it.toFloatOrNull() ?: 0f }
            val d = if (px.size == 4) px else listOf(0f, 0f, 0f, 0f)
            d
        }
        val density = LocalDensity.current.density
        Box(
            Modifier.fillMaxSize().padding(
                start = (inset[3] / density).dp,
                top = (inset[0] / density).dp,
                end = (inset[1] / density).dp,
                bottom = (inset[2] / density).dp,
            ),
        ) {
            val now = playing
            if (now != null) {
                PlayerScreen(
                    media = now.media, season = now.season, episode = now.episode,
                    onBack = { playing = null },
                    onOpenTitle = { t ->
                        playing = null
                        browse.openTitle = t
                    },
                    // new episode keeps player open, same title, new sources
                    onEpisode = { s, e -> playing = now.copy(season = s, episode = e) },
                )
            } else {
                val tabBackdrop = rememberLayerBackdrop()
                Box(Modifier.fillMaxSize()) {
                    HomeScreen(
                        browse = browse,
                        onWatch = { item, season, episode ->
                            playing = Route.Player(item.title, null, item, season, episode)
                        },
                        // same two sources phone reads, one for library tab and one for continue
                        // rail above feed
                        loadRecent = { Resume.recent() },
                        continueWatching = WebSettings.on(SettingsKeys.CONTINUE_WATCHING) != 0,
                        recentRev = Resume.rev,
                        onRemoveRecent = { Resume.remove(it.kind, it.id, it.season, it.episode) },
                        onRestoreRecent = { Resume.restore(it) },
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
                                    settings = WebSettings.values,
                                    onValue = { key, value -> WebSettings.set(key, value) },
                                    onAction = { action ->
                                        when (action) {
                                            SettingsAction.ClearHistory -> {
                                                Resume.clear()
                                                browse.recent = emptyList()
                                            }
                                            SettingsAction.WhatsNew -> Unit
                                            SettingsAction.Updates -> window.open("$REPO/releases", "_blank")
                                            SettingsAction.Licences -> window.open("$REPO#licence", "_blank")
                                            SettingsAction.Privacy -> window.open("$REPO#privacy", "_blank")
                                            SettingsAction.ReportProblem -> window.open("$REPO/issues/new", "_blank")
                                            SettingsAction.GetPhantom -> window.open("$PHANTOM/releases/latest", "_blank")
                                        }
                                    },
                                ),
                            )
                        },
                    )
                }
                AnimatedVisibility(
                    visible = browse.openTitle == null,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(150)),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(150)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp)
                        // phone bar stops at 480dp, without it four tabs spread across whole
                        // window on desktop and read as nav strip, not pill
                        .widthIn(max = 480.dp)
                ) {
                    HomeLiquidTabs(
                        backdrop = tabBackdrop,
                        selectedTabIndex = { browse.tab },
                        onSelect = browse::selectTab,
                        // sampling page into offscreen layer per frame is what stalls safari, flat
                        // bar costs nothing and border keeps its edge
                        showGlass = false,
                        containerColor = SurfaceColor,
                        borderColor = Color.White.copy(alpha = 0.08f),
                    )
                }
            }
        }
    }
}
