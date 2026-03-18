package dev.phantomwatch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.phantomwatch.BuildConfig
import dev.phantomwatch.R
import dev.phantomwatch.Episode
import dev.phantomwatch.Person
import dev.phantomwatch.Title
import dev.phantomwatch.TitleDetail
import dev.phantomwatch.VidLove
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MovieDetail(item: Title, onClose: () -> Unit, onWatch: (Title, String, String) -> Unit) {
    val key = BuildConfig.TMDB_API_KEY
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<TitleDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var episodesOpen by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    BackHandler(onBack = onClose)

    LaunchedEffect(item.id, item.kind) {
        loading = true
        failed = false
        try {
            detail = withContext(Dispatchers.IO) { VidLove.detail(key, item.kind, item.id) }
            failed = detail == null
        } catch (_: Exception) {
            failed = true
        } finally {
            loading = false
        }
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Cyan)
            }
            failed || detail == null -> Column(
                Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Title unavailable", color = Slate100, fontSize = 15.sp, fontFamily = Mono, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("Phantom could not load this title", color = Slate400, fontSize = 12.sp, fontFamily = Mono)
            }
            else -> {
                val d = detail!!
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Box(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(bottomStart = 55.dp, bottomEnd = 55.dp))
                            .background(Color.White.copy(alpha = 0.05f)),
                    ) {
                        val hero = d.backdrop ?: d.poster
                        if (hero != null) {
                            AsyncImage(model = hero, contentDescription = null, modifier = Modifier.fillMaxWidth().height(413.dp), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
                        } else {
                            Spacer(Modifier.fillMaxWidth().height(120.dp))
                        }
                    }
                    Column(
                        Modifier.padding(horizontal = 16.dp).offset(y = (-120).dp)
                            .clip(RoundedCornerShape(32.dp)).background(Surface).padding(20.dp),
                    ) {
                        Row {
                            if (d.poster != null) {
                                AsyncImage(model = d.poster, contentDescription = null, modifier = Modifier.size(120.dp, 176.dp).clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop)
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
                                    Icon(Icons.Filled.Star, contentDescription = null, tint = StarYellow, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    if (d.rating > 0) {
                                        Text(String.format("%.1f", d.rating), color = Color.White, fontSize = 13.sp, fontFamily = Mono, fontWeight = FontWeight.SemiBold)
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Text("${d.votes} votes", color = Slate500, fontSize = 11.sp, fontFamily = Mono)
                                }
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetaCell("Rating", if (d.rating > 0) String.format("%.1f", d.rating) else "—")
                            MetaCell("Runtime", if (d.runtime > 0) "${d.runtime} min" else "—")
                            MetaCell("Released", d.year ?: "—")
                        }
                        if (d.genres.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                d.genres.take(4).forEach { g ->
                                    Box(Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                                        Text(g, color = Slate100, fontSize = 12.sp, fontFamily = Rubik, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                        if (d.overview.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text("Story Plot", color = Color.White, fontSize = 16.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(d.overview, color = Slate300, fontSize = 14.sp, lineHeight = 24.sp, fontFamily = Rubik)
                        }
                        if (d.kind != "movie" && d.seasons.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text("Episodes", color = Color.White, fontSize = 16.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            SeasonPreview(
                                detail = d,
                                onEpisode = { s, e -> onWatch(item, s, e) },
                                onViewAll = { episodesOpen = true },
                            )
                        }
                        if (d.director != null) {
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(person = d.director, size = 44.dp)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("Director", color = Slate500, fontSize = 11.sp, fontFamily = Mono)
                                    Text(d.director.name, color = Slate300, fontSize = 14.sp, fontFamily = Rubik)
                                }
                            }
                        }
                        if (d.cast.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(d.cast.take(8), key = { it.name }) { p ->
                                    Column(Modifier.width(68.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Avatar(person = p, size = 56.dp)
                                        Spacer(Modifier.height(4.dp))
                                        Text(p.name, color = Slate300, fontSize = 10.sp, fontFamily = Rubik, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(40.dp))
                    }
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.statusBarsPadding().padding(start = 16.dp, top = 8.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)),
                ) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Box(
                    Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(end = 16.dp, top = 16.dp)
                        .clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(if (d.kind == "movie") "MOVIE" else "TV SHOW", color = Slate200, fontSize = 11.sp, fontFamily = Mono)
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
        if (episodesOpen && detail != null) {
            EpisodesScreen(
                detail = detail!!,
                onClose = { episodesOpen = false },
                onEpisode = { s, e ->
                    episodesOpen = false
                    onWatch(item, s, e)
                },
            )
        }
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
private fun Avatar(person: Person, size: androidx.compose.ui.unit.Dp) {
    if (person.photo != null) {
        AsyncImage(model = person.photo, contentDescription = person.name, modifier = Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
    } else {
        val initials = person.name.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString("")
        Box(Modifier.size(size).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Text(initials, color = Slate200, fontSize = 14.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SeasonPreview(detail: TitleDetail, onEpisode: (String, String) -> Unit, onViewAll: () -> Unit) {
    val key = BuildConfig.TMDB_API_KEY
    var seasonIdx by remember { mutableStateOf(0) }
    var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    val seasons = detail.seasons
    LaunchedEffect(detail.id, seasonIdx) {
        try {
            episodes = withContext(Dispatchers.IO) {
                VidLove.season(key, detail.id, seasons.getOrNull(seasonIdx)?.number ?: 1).take(3)
            }
        } catch (_: Exception) {
        }
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
        EpisodeRow(ep = ep, onClick = {
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
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Cyan, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun EpisodeRow(ep: Episode, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ep.still != null) {
            AsyncImage(model = ep.still, contentDescription = null, modifier = Modifier.size(120.dp, 68.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.size(120.dp, 68.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.05f)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_play3), contentDescription = null, tint = Slate200, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("E${ep.n}", color = Slate500, fontSize = 11.sp, fontFamily = Mono)
            Text(ep.name, color = Slate100, fontSize = 14.sp, fontFamily = Rubik, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(painterResource(R.drawable.ic_play3), contentDescription = null, tint = Slate200, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun WatchBar(isTv: Boolean, checking: Boolean, onWatch: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(CircleShape)
                .background(Surface).padding(12.dp),
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
                    Icon(painterResource(R.drawable.ic_play3_filled), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isTv) "Episodes" else "Watch Now", color = Color.White, fontSize = 15.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun EpisodesScreen(detail: TitleDetail, onClose: () -> Unit, onEpisode: (String, String) -> Unit) {
    val key = BuildConfig.TMDB_API_KEY
    var seasonIdx by remember { mutableStateOf(0) }
    var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val seasons = detail.seasons
    BackHandler(onBack = onClose)

    LaunchedEffect(detail.id, seasonIdx) {
        loading = true
        try {
            episodes = withContext(Dispatchers.IO) {
                VidLove.season(key, detail.id, seasons.getOrNull(seasonIdx)?.number ?: 1)
            }
        } catch (_: Exception) {
            episodes = emptyList()
        } finally {
            loading = false
        }
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
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 32.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose, modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.05f))) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                if (searching) {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(8.dp)).background(Surface).padding(horizontal = 12.dp),
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
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Slate200, modifier = Modifier.size(20.dp))
                    }
                } else {
                    Column(Modifier.weight(1f)) {
                        Text("Episodes", color = Color.White, fontSize = 18.sp, fontFamily = Rubik, fontWeight = FontWeight.Bold)
                        Text(detail.title, color = Slate400, fontSize = 12.sp, fontFamily = Mono, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { searching = true }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search", tint = Slate200, modifier = Modifier.size(20.dp))
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
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Cyan)
                }
                shown.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (query.isNotEmpty()) "No matches for \"$query\"" else "No episodes found", color = Slate200, fontSize = 15.sp, fontFamily = Rubik, fontWeight = FontWeight.SemiBold)
                }
                else -> {
                    if (query.isNotEmpty()) {
                        Text("${shown.size} matches", color = Slate500, fontSize = 11.sp, fontFamily = Mono, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                    }
                    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                        items(shown, key = { it.n }) { ep ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {
                                    onEpisode((seasons.getOrNull(seasonIdx)?.number ?: 1).toString(), ep.n.toString())
                                }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (ep.still != null) {
                                    AsyncImage(model = ep.still, contentDescription = null, modifier = Modifier.size(120.dp, 68.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                                } else {
                                    Box(Modifier.size(120.dp, 68.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.05f)), contentAlignment = Alignment.Center) {
                                        Icon(painterResource(R.drawable.ic_play3), contentDescription = null, tint = Slate200, modifier = Modifier.size(18.dp))
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("E${ep.n}", color = Slate500, fontSize = 11.sp, fontFamily = Mono)
                                    Text(ep.name, color = Slate100, fontSize = 14.sp, fontFamily = Rubik, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (ep.overview.isNotEmpty()) {
                                        Text(ep.overview, color = Slate400, fontSize = 12.sp, lineHeight = 16.sp, fontFamily = Rubik, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
