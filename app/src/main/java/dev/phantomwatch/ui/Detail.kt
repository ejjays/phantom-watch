package dev.phantomwatch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import dev.phantomwatch.BuildConfig
import dev.phantomwatch.R
import dev.phantomwatch.core.AppLog
import dev.phantomwatch.core.RemoteConfig
import dev.phantomwatch.core.isWifi
import dev.phantomwatch.data.DetailCache
import dev.phantomwatch.data.Episode
import dev.phantomwatch.data.Person
import dev.phantomwatch.data.Search
import dev.phantomwatch.data.Title
import dev.phantomwatch.data.TitleDetail
import dev.phantomwatch.data.Tmdb
import dev.phantomwatch.provider.Resolve
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun ShimmerBox(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f)),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    item: Title,
    onClose: () -> Unit,
    onWatch: (Title, String, String) -> Unit,
    startEpisodes: Boolean = false,
    onEpisodesExit: (() -> Unit)? = null,
) {
    val key = RemoteConfig.tmdbKey()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var detail by remember(item.id) { mutableStateOf(DetailCache.detail(item.kind, item.id)) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    var episodesOpen by remember(startEpisodes) { mutableStateOf(startEpisodes) }
    var checking by remember { mutableStateOf(false) }
    var photosReady by remember { mutableStateOf(detail != null) }
    BackHandler(onBack = onClose)

    val t0 = remember(item.id, item.kind) { System.nanoTime() }
    fun ms() = (System.nanoTime() - t0) / 1000000
    LaunchedEffect(item.id, item.kind) {
        AppLog.d("detail open ${item.kind}/${item.id} ${item.title}")
    }
    LaunchedEffect(item.id, item.kind) {
        if (item.kind != "movie") return@LaunchedEffect
        delay(1200)
        if (!isWifi(context)) {
            AppLog.d("prefetch skipped ${item.title} (metered)")
            return@LaunchedEffect
        }
        Resolve.prefetch(item, "", "")
    }
    LaunchedEffect(item.id, item.kind) {
        delay(600)
        photosReady = true
        AppLog.d("detail photos ready")
    }
    LaunchedEffect(item.id, item.kind) {
        if (!BuildConfig.DEBUG) return@LaunchedEffect
        var last = 0L
        var slow = 0
        while (slow < 25) {
            val frame = withFrameNanos { it }
            if (last != 0L) {
                val dt = (frame - last) / 1000000
                if (dt > 50) {
                    slow++
                    AppLog.d("detail jank +${ms()}ms frame ${dt}ms")
                }
            }
            last = frame
        }
    }
    // shortcut: debug watchdog, remove once the open hitch is fixed
    LaunchedEffect(item.id, item.kind) {
        if (!BuildConfig.DEBUG) return@LaunchedEffect
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        var quits = false
        var logged = 0
        val t = Thread {
            while (!quits && logged < 15) {
                try {
                    Thread.sleep(20)
                } catch (_: InterruptedException) {
                    break
                }
                val sent = System.nanoTime()
                val done = java.util.concurrent.CountDownLatch(1)
                main.post { done.countDown() }
                try {
                    done.await(500, java.util.concurrent.TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    break
                }
                val cost = (System.nanoTime() - sent) / 1000000
                if (cost > 50 && !quits && logged < 15) {
                    logged++
                    AppLog.d("detail main blocked +${(sent - t0) / 1000000}ms for ${cost}ms")
                }
            }
        }
        t.isDaemon = true
        t.start()
        try {
            delay(Long.MAX_VALUE)
        } finally {
            quits = true
            t.interrupt()
        }
    }

    LaunchedEffect(item.id, item.kind, attempt) {
        if (detail != null) return@LaunchedEffect
        failed = false
        val start = System.nanoTime()
        AppLog.d("detail fetch start ${item.kind}/${item.id}")
        var result: TitleDetail? = null
        try {
            result = withContext(Dispatchers.IO) { Tmdb.detail(key, item.kind, item.id) }
        } catch (_: Exception) {
        }
        val elapsed = (System.nanoTime() - start) / 1000000
        val dd = result
        AppLog.d("detail fetch end +${elapsed}ms null=${dd == null} genres=${dd?.genres?.size ?: 0} cast=${dd?.cast?.size ?: 0} ov=${dd?.overview?.length ?: 0} heroSame=${(dd?.backdrop ?: dd?.poster) == (item.backdrop ?: item.poster)}")
        val precache = ((dd?.cast.orEmpty()) + listOfNotNull(dd?.director)).mapNotNull { it.photo }.take(9)
        if (precache.isNotEmpty()) {
            val loader = SingletonImageLoader.get(context)
            precache.forEach { url ->
                loader.enqueue(ImageRequest.Builder(context).data(url).build())
            }
            AppLog.d("detail precache ${precache.size}")
        }
        val wait = 400 - elapsed
        if (wait > 0) delay(wait)
        detail = result
        failed = result == null
        if (result != null) DetailCache.put(result)
        AppLog.d("detail applied +${ms()}ms")
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        val d = detail ?: TitleDetail(
            id = item.id, kind = item.kind, title = item.title, overview = "",
            poster = item.poster, backdrop = item.backdrop, year = item.year,
            runtime = 0, rating = item.rating ?: 0.0, votes = 0,
            genres = emptyList(), cert = null, director = null,
            cast = emptyList(), seasons = emptyList(),
        )
        val pending = detail == null
        // returning from the player lands on episodes, so skip the detail body entirely
        if (!(startEpisodes && episodesOpen)) when {
            failed && detail == null -> Column(
                Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Title unavailable", color = Slate100, fontSize = 15.sp, fontFamily = Mono, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("Phantom could not load this title", color = Slate400, fontSize = 12.sp, fontFamily = Mono)
                Text(
                    "Retry", color = Cyan, fontSize = 13.sp, fontFamily = Rubik,
                    modifier = Modifier.padding(top = 10.dp).clickable { attempt++ },
                )
            }
            else -> {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Box(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(bottomStart = 55.dp, bottomEnd = 55.dp))
                            .background(Color.White.copy(alpha = 0.05f)),
                    ) {
                        val hero = (item.backdrop ?: item.poster) ?: (d.backdrop ?: d.poster)
                        if (hero != null) {
                            AsyncImage(model = hero, contentDescription = null, modifier = Modifier.fillMaxWidth().height(413.dp), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, onSuccess = { AppLog.d("detail hero ok +${ms()}ms") }, onError = { AppLog.d("detail hero err +${ms()}ms") })
                        } else {
                            Spacer(Modifier.fillMaxWidth().height(120.dp))
                        }
                    }
                    Column(
                        Modifier.padding(horizontal = 16.dp).offset(y = (-120).dp)
                            .clip(RoundedCornerShape(32.dp)).background(SurfaceColor).padding(20.dp),
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            if ((item.poster ?: d.poster) != null) {
                                AsyncImage(model = item.poster ?: d.poster, contentDescription = null, modifier = Modifier.size(120.dp, 176.dp).clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop, onSuccess = { AppLog.d("detail poster ok +${ms()}ms") }, onError = { AppLog.d("detail poster err +${ms()}ms") })
                                Spacer(Modifier.width(12.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(d.title, color = Color.White, fontSize = 20.sp, lineHeight = 28.sp, fontFamily = Rubik, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (d.year != null) Text(d.year, color = Slate400, fontSize = 12.sp, fontFamily = Mono)
                                    Spacer(Modifier.width(6.dp))
                                    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.1f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                        Text(if (d.kind == "movie") "Movie" else "TV Show", color = Slate300, fontSize = 10.sp, fontFamily = Mono)
                                    }
                                    if (d.cert != null) {
                                        Spacer(Modifier.width(6.dp))
                                        Box(Modifier.clip(RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                            Text(d.cert, color = Slate300, fontSize = 10.sp, fontFamily = Mono)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(painterResource(R.drawable.ic_star), contentDescription = null, tint = StarYellow, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    if (d.rating > 0) {
                                        Text(String.format("%.1f", d.rating), color = Color.White, fontSize = 13.sp, fontFamily = Mono, fontWeight = FontWeight.SemiBold)
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Text(if (d.votes > 0) "${d.votes} votes" else "···", color = Slate500, fontSize = 11.sp, fontFamily = Mono)
                                }
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Column {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetaCell("Rating", if (d.rating > 0) String.format("%.1f", d.rating) else "—")
                            MetaCell("Runtime", if (d.runtime > 0) "${d.runtime} min" else "—")
                            MetaCell("Released", d.year ?: "—")
                        }
                        if (d.genres.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                d.genres.take(6).forEach { g ->
                                    Box(Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                                        Text(g, color = Slate100, fontSize = 12.sp, fontFamily = Rubik, fontWeight = FontWeight.Medium, maxLines = 1)
                                    }
                                }
                            }
                        } else if (pending) {
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ShimmerBox(Modifier.size(72.dp, 30.dp))
                                ShimmerBox(Modifier.size(90.dp, 30.dp))
                                ShimmerBox(Modifier.size(64.dp, 30.dp))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Story Plot", color = Color.White, fontSize = 16.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        if (d.overview.isNotEmpty()) {
                            Text(d.overview, color = Slate300, fontSize = 14.sp, lineHeight = 24.sp, fontFamily = Rubik)
                        } else if (pending) {
                            ShimmerBox(Modifier.fillMaxWidth().height(14.dp))
                            Spacer(Modifier.height(8.dp))
                            ShimmerBox(Modifier.fillMaxWidth(0.9f).height(14.dp))
                            Spacer(Modifier.height(8.dp))
                            ShimmerBox(Modifier.fillMaxWidth(0.8f).height(14.dp))
                            Spacer(Modifier.height(8.dp))
                            ShimmerBox(Modifier.fillMaxWidth(0.7f).height(14.dp))
                            Spacer(Modifier.height(8.dp))
                            ShimmerBox(Modifier.fillMaxWidth(0.5f).height(14.dp))
                        }
                        if (d.kind != "movie") {
                            Spacer(Modifier.height(12.dp))
                            Text("Episodes", color = Color.White, fontSize = 16.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            if (d.seasons.isNotEmpty()) {
                                SeasonPreview(
                                    detail = d,
                                    start = photosReady,
                                    onEpisode = { s, e -> onWatch(item, s, e) },
                                    onViewAll = { episodesOpen = true },
                                )
                            } else if (pending) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ShimmerBox(Modifier.size(90.dp, 36.dp))
                                    ShimmerBox(Modifier.size(90.dp, 36.dp))
                                }
                                Spacer(Modifier.height(8.dp))
                                repeat(3) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        ShimmerBox(Modifier.size(120.dp, 68.dp))
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            ShimmerBox(Modifier.fillMaxWidth(0.3f).height(12.dp))
                                            Spacer(Modifier.height(6.dp))
                                            ShimmerBox(Modifier.fillMaxWidth(0.8f).height(14.dp))
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                        if (d.director != null) {
                            Spacer(Modifier.height(12.dp))
                            Text("Director", color = Color.White, fontSize = 16.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(person = d.director, size = 44.dp, loadPhoto = photosReady)
                                Spacer(Modifier.width(10.dp))
                                Text(d.director.name, color = Slate300, fontSize = 14.sp, fontFamily = Rubik)
                            }
                        } else if (pending) {
                            Spacer(Modifier.height(12.dp))
                            Text("Director", color = Color.White, fontSize = 16.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ShimmerBox(Modifier.size(44.dp))
                                Spacer(Modifier.width(10.dp))
                                ShimmerBox(Modifier.fillMaxWidth(0.4f).height(14.dp))
                            }
                        }
                        if (photosReady && d.cast.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text("Casts", color = Color.White, fontSize = 16.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(d.cast.take(8), key = { it.name }) { p ->
                                    Column(Modifier.width(80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Avatar(person = p, size = 60.dp, loadPhoto = photosReady)
                                        Spacer(Modifier.height(6.dp))
                                        Text(p.name, color = Slate200, fontSize = 11.sp, lineHeight = 14.sp, fontFamily = Rubik, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.heightIn(min = 28.dp))
                                    }
                                }
                            }
                        } else if (pending || !photosReady) {
                            Spacer(Modifier.height(12.dp))
                            Text("Casts", color = Color.White, fontSize = 16.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                repeat(8) {
                                    Column(Modifier.width(80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        ShimmerBox(Modifier.size(60.dp))
                                        Spacer(Modifier.height(6.dp))
                                        ShimmerBox(Modifier.fillMaxWidth(0.8f).height(10.dp))
                                    }
                                }
                            }
                        }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    Spacer(Modifier.height(24.dp))
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.statusBarsPadding().padding(start = 16.dp, top = 8.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)),
                ) {
                    Icon(painterResource(R.drawable.ic_back), contentDescription = "Back", tint = Color.White, modifier = Modifier.size(22.dp))
                }
                WatchBar(
                    isTv = d.kind != "movie",
                    checking = checking,
                    onWatch = {
                        scope.launch {
                            checking = true
                            try {
                                if (d.kind == "movie") onWatch(item, "", "")
                                else episodesOpen = true
                            } finally {
                                checking = false
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
        EpisodesGate(isOpen = { episodesOpen }) {
            if (episodesOpen) {
                EpisodesScreen(
                    detail = detail,
                    // back from a post-player return skips the detail view
                    onClose = { if (startEpisodes && onEpisodesExit != null) onEpisodesExit() else episodesOpen = false },
                    onEpisode = { s, e ->
                        episodesOpen = false
                        onWatch(item, s, e)
                    },
                )
            }
        }
    }
}

@Composable
private fun EpisodesGate(isOpen: () -> Boolean, content: @Composable AnimatedVisibilityScope.() -> Unit) {
    AnimatedVisibility(
        visible = isOpen(),
        enter = slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it },
        exit = slideOutVertically(tween(280, easing = FastOutSlowInEasing)) { it },
    ) {
        content()
    }
}

@Composable
private fun MetaCell(label: String, value: String) {
    Column {
        Text(label, color = Slate500, fontSize = 11.sp, fontFamily = Mono)
        Text(value, color = Slate100, fontSize = 15.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Avatar(person: Person, size: androidx.compose.ui.unit.Dp, loadPhoto: Boolean = true) {
    if (person.photo != null && loadPhoto) {
        AsyncImage(model = person.photo, contentDescription = person.name, modifier = Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop, onSuccess = { AppLog.d("detail img ok ${person.name}") }, onError = { AppLog.d("detail img err ${person.name}") })
    } else {
        val initials = person.name.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString("")
        Box(Modifier.size(size).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Text(initials, color = Slate200, fontSize = 14.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SeasonPreview(detail: TitleDetail, start: Boolean, onEpisode: (String, String) -> Unit, onViewAll: () -> Unit) {
    val key = RemoteConfig.tmdbKey()
    val seasons = detail.seasons
    var seasonIdx by remember(detail.id) { mutableStateOf(seasons.indexOfFirst { it.number > 0 }.takeIf { it >= 0 } ?: 0) }
    var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    LaunchedEffect(detail.id, seasonIdx, start) {
        if (!start) return@LaunchedEffect
        AppLog.d("detail season start")
        val n = seasons.getOrNull(seasonIdx)?.number ?: 1
        try {
            val all = withContext(Dispatchers.IO) { Tmdb.season(key, detail.id, n) }
            if (all.isNotEmpty()) {
                DetailCache.putSeason(detail.kind, detail.id, n)
                DetailCache.putEpisodes(detail.kind, detail.id, n, all)
            }
            episodes = all.take(3)
        } catch (_: Exception) {
        }
        AppLog.d("detail season end ${episodes.size}")
    }
    if (seasons.size > 1) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(seasons.size) { i ->
                val active = i == seasonIdx
                Box(
                    Modifier.clip(CircleShape)
                        .background(if (active) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
                        .clickable { seasonIdx = i }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(seasons[i].name, color = if (active) Color.White else Slate400, fontSize = 13.sp, fontFamily = Rubik)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    episodes.forEach { ep ->
        EpisodeRow(ep = ep, fallbackStill = detail.backdrop ?: detail.poster, onClick = {
            onEpisode((seasons.getOrNull(seasonIdx)?.number ?: 1).toString(), ep.n.toString())
        })
    }
    val total = seasons.sumOf { it.count }
    Row(
        Modifier.fillMaxWidth().clip(CircleShape).background(Color.White.copy(alpha = 0.05f))
            .clickable(onClick = onViewAll).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("View all $total episodes", color = Cyan, fontSize = 13.sp, fontFamily = Rubik)
        ChevronRightIcon(contentDescription = null, tint = Cyan, size = 15.dp)
    }
}

internal fun airLabel(iso: String): String {
    val p = iso.split("-")
    if (p.size != 3) return iso
    val m = listOf("Jan.", "Feb.", "Mar.", "Apr.", "May.", "Jun.", "Jul.", "Aug.", "Sep.", "Oct.", "Nov.", "Dec.")
        .getOrNull((p[1].toIntOrNull() ?: 0) - 1) ?: return iso
    val d = p[2].toIntOrNull() ?: return iso
    return "$m $d, ${p[0]}"
}

@Composable
internal fun EpisodeRow(ep: Episode, fallbackStill: String?, playing: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val thumb = ep.still ?: fallbackStill
        if (thumb != null) {
            AsyncImage(model = thumb, contentDescription = null, modifier = Modifier.size(120.dp, 68.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.size(120.dp, 68.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.05f)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_play_outline), contentDescription = null, tint = Slate200, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "E${ep.n}" + (ep.air?.let { " · ${airLabel(it)}" } ?: ""),
                color = if (playing) Cyan else Slate500, fontSize = 11.sp, fontFamily = Mono,
            )
            Text(ep.name, color = Slate100, fontSize = 14.sp, fontFamily = Rubik, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (playing) {
            Box(
                Modifier.clip(CircleShape).background(Cyan.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text("PLAYING", color = Cyan, fontSize = 9.sp, fontFamily = Mono, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Icon(painterResource(R.drawable.ic_play_outline), contentDescription = null, tint = Slate200, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun WatchBar(isTv: Boolean, checking: Boolean, onWatch: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(CircleShape)
                .background(SurfaceColor).border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).height(56.dp).clip(CircleShape).background(WatchRed)
                    .clickable(onClick = onWatch).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (checking) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                } else {
                    Icon(painterResource(R.drawable.ic_play_filled), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isTv) "Episodes" else "Watch Now", color = Color.White, fontSize = 15.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
internal fun EpisodesScreen(
    detail: TitleDetail?,
    topInset: Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
    onClose: () -> Unit,
    onEpisode: (String, String) -> Unit,
) {
    val key = RemoteConfig.tmdbKey()
    val seasons = detail?.seasons.orEmpty()
    var seasonIdx by remember(detail?.id) {
        val def = seasons.indexOfFirst { it.number > 0 }.takeIf { it >= 0 } ?: 0
        val last = detail?.let { DetailCache.season(it.kind, it.id) } ?: -1
        mutableStateOf(seasons.indexOfFirst { it.number == last }.takeIf { it >= 0 } ?: def)
    }
    val initial = detail?.let { DetailCache.episodes(it.kind, it.id, seasons.getOrNull(seasonIdx)?.number ?: 1) }
    var episodes by remember { mutableStateOf(initial.orEmpty()) }
    var loading by remember { mutableStateOf(initial == null) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val context = LocalContext.current
    BackHandler(onBack = onClose)

    val t0 = remember(detail?.id) { System.nanoTime() }
    fun ms() = (System.nanoTime() - t0) / 1000000
    LaunchedEffect(detail?.id) {
        detail?.let { AppLog.d("ep open ${it.kind}/${it.id} ${it.title}") }
    }
    LaunchedEffect(detail?.id) {
        if (!BuildConfig.DEBUG) return@LaunchedEffect
        var last = 0L
        var slow = 0
        while (slow < 25) {
            val frame = withFrameNanos { it }
            if (last != 0L) {
                val dt = (frame - last) / 1000000
                if (dt > 50) {
                    slow++
                    AppLog.d("ep jank +${ms()}ms frame ${dt}ms")
                }
            }
            last = frame
        }
    }
    // shortcut: debug watchdog, remove once the open hitch is fixed
    LaunchedEffect(detail?.id) {
        if (!BuildConfig.DEBUG) return@LaunchedEffect
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        var quits = false
        var logged = 0
        val t = Thread {
            while (!quits && logged < 15) {
                try {
                    Thread.sleep(20)
                } catch (_: InterruptedException) {
                    break
                }
                val sent = System.nanoTime()
                val done = java.util.concurrent.CountDownLatch(1)
                main.post { done.countDown() }
                try {
                    done.await(500, java.util.concurrent.TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    break
                }
                val cost = (System.nanoTime() - sent) / 1000000
                if (cost > 50 && !quits && logged < 15) {
                    logged++
                    AppLog.d("ep main blocked +${(sent - t0) / 1000000}ms for ${cost}ms")
                }
            }
        }
        t.isDaemon = true
        t.start()
        try {
            delay(Long.MAX_VALUE)
        } finally {
            quits = true
            t.interrupt()
        }
    }

    LaunchedEffect(detail?.id, seasonIdx) {
        val d = detail ?: return@LaunchedEffect
        val n = seasons.getOrNull(seasonIdx)?.number ?: 1
        DetailCache.putSeason(d.kind, d.id, n)
        val cached = DetailCache.episodes(d.kind, d.id, n)
        if (cached != null) {
            episodes = cached
            loading = false
            return@LaunchedEffect
        }
        loading = true
        val start = System.nanoTime()
        val first = episodes.isEmpty()
        AppLog.d("ep fetch start ${d.id} s$seasonIdx")
        var result = emptyList<Episode>()
        try {
            result = withContext(Dispatchers.IO) {
                Tmdb.season(key, d.id, n)
            }
        } catch (_: Exception) {
            result = emptyList()
        }
        val elapsed = (System.nanoTime() - start) / 1000000
        AppLog.d("ep fetch end +${elapsed}ms n=${result.size}")
        val stills = result.mapNotNull { it.still }.take(12)
        if (stills.isNotEmpty()) {
            val loader = SingletonImageLoader.get(context)
            stills.forEach { url ->
                loader.enqueue(ImageRequest.Builder(context).data(url).build())
            }
            AppLog.d("ep precache ${stills.size}")
        }
        if (first) {
            val wait = 400 - elapsed
            if (wait > 0) delay(wait)
        }
        episodes = result
        loading = false
        if (result.isNotEmpty()) DetailCache.putEpisodes(d.kind, d.id, n, result)
        AppLog.d("ep applied +${ms()}ms n=${result.size}")
    }

    val shown = remember(query, episodes) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) episodes
        else episodes.filter {
            it.name.lowercase().contains(q) || ("e${it.n}" == q) || (it.n.toString() == q)
        }
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(top = topInset).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(38.dp)) {
                    Icon(painterResource(R.drawable.ic_back), contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(6.dp))
                if (searching) {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(8.dp)).background(SurfaceColor).padding(horizontal = 12.dp),
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp, fontFamily = Rubik),
                        singleLine = true,
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (query.isEmpty()) Text("Search E2 or title…", color = Slate500, fontSize = 15.sp, fontFamily = Rubik)
                                inner()
                            }
                        },
                    )
                    IconButton(onClick = { searching = false; query = "" }) {
                        CloseIcon(contentDescription = "Close", tint = Slate200, size = 20.dp)
                    }
                } else {
                    Text("Episodes", color = Color.White, fontSize = 18.sp, fontFamily = Rubik, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { searching = true }) {
                        Icon(painterResource(R.drawable.ic_search), contentDescription = "Search", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }
            if (seasons.size > 1 && !searching) {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(seasons.size) { i ->
                        val active = i == seasonIdx
                        Box(
                            Modifier.height(36.dp).clip(CircleShape)
                                .background(if (active) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
                                .clickable { seasonIdx = i }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(seasons[i].name, color = if (active) Color.White else Slate400, fontSize = 13.sp, fontFamily = Rubik, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            when {
                loading -> LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)) {
                    items(6) {                                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.Top) {
                            ShimmerBox(Modifier.fillMaxWidth(0.46f).aspectRatio(16f / 9f))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                ShimmerBox(Modifier.fillMaxWidth(0.95f).height(16.dp))
                                Spacer(Modifier.height(6.dp))
                                ShimmerBox(Modifier.fillMaxWidth(0.7f).height(16.dp))
                                Spacer(Modifier.height(8.dp))
                                ShimmerBox(Modifier.fillMaxWidth(0.4f).height(12.dp))
                            }
                        }
                    }
                }
                shown.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (query.isNotEmpty()) "No matches for \"$query\"" else "No episodes found", color = Slate200, fontSize = 15.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold)
                }
                else -> {
                    if (query.isNotEmpty()) {
                        Text("${shown.size} matches", color = Slate500, fontSize = 11.sp, fontFamily = Mono, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                    }
                    LazyColumn(modifier = Modifier.imePadding(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)) {
                        items(shown, key = { it.n }) { ep ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                    onEpisode((seasons.getOrNull(seasonIdx)?.number ?: 1).toString(), ep.n.toString())
                                }.padding(vertical = 14.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                val thumb = ep.still ?: (detail?.backdrop ?: detail?.poster)
                                Box(Modifier.fillMaxWidth(0.46f).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.05f))) {
                                    if (thumb != null) {
                                        AsyncImage(model = thumb, contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop, onSuccess = { AppLog.d("ep still ok E${ep.n}") }, onError = { AppLog.d("ep still err E${ep.n}") })
                                    } else {
                                        Icon(painterResource(R.drawable.ic_play_outline), contentDescription = null, tint = Slate200, modifier = Modifier.align(Alignment.Center).size(18.dp))
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(ep.name, color = Color.White, fontSize = 16.sp, lineHeight = 21.sp, fontFamily = Rubik, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(4.dp))
                                    Text("E${ep.n}" + (ep.air?.let { " · ${airLabel(it)}" } ?: ""), color = Slate400, fontSize = 13.sp, fontFamily = Mono)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
