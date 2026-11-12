package dev.phantomwatch.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.phantomwatch.R
import dev.phantomwatch.core.RemoteConfig
import dev.phantomwatch.data.DetailCache
import dev.phantomwatch.data.Episode
import dev.phantomwatch.data.SeasonInfo
import dev.phantomwatch.data.TitleDetail
import dev.phantomwatch.data.Tmdb
import dev.phantomwatch.ui.Bg
import dev.phantomwatch.ui.Cyan
import dev.phantomwatch.ui.WorkSans
import dev.phantomwatch.ui.Slate100
import dev.phantomwatch.ui.Slate400
import dev.phantomwatch.ui.Slate500
import dev.phantomwatch.ui.SurfaceColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun EpisodesSidebar(
    detail: TitleDetail?,
    season: String,
    episode: String,
    onClose: () -> Unit,
    onEpisode: (String, String) -> Unit,
) {
    val seasons = detail?.seasons ?: emptyList()
    val playing = season.toIntOrNull()
    var seasonIdx by remember(detail?.id, seasons.size) { mutableIntStateOf(firstSeason(seasons, playing)) }
    val seasonNo = seasons.getOrNull(seasonIdx)?.number ?: 1
    var episodes by remember(detail?.id, seasonNo) {
        mutableStateOf(detail?.let { DetailCache.episodes(it.kind, it.id, seasonNo) })
    }

    LaunchedEffect(detail?.id, seasonNo) {
        val d = detail ?: return@LaunchedEffect
        val cached = DetailCache.episodes(d.kind, d.id, seasonNo)
        if (cached != null) {
            episodes = cached
            return@LaunchedEffect
        }
        val list = withContext(Dispatchers.IO) {
            runCatching { Tmdb.season(RemoteConfig.tmdbKey(), d.id, seasonNo) }.getOrDefault(emptyList())
        }
        if (list.isNotEmpty()) DetailCache.putEpisodes(d.kind, d.id, seasonNo, list)
        episodes = list
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        SidebarHeader(onClose)
        SeasonPicker(seasons, seasonIdx, seasonNo) { seasonIdx = it }
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        SidebarList(episodes, seasonNo, playing, episode, onEpisode)
    }
}

private fun firstSeason(seasons: List<SeasonInfo>, playing: Int?): Int {
    val match = seasons.indexOfFirst { it.number == playing }
    return if (match >= 0) match else seasons.indexOfFirst { it.number > 0 }.coerceAtLeast(0)
}

@Composable
private fun SidebarHeader(onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.player_episodes),
            color = Slate100,
            fontSize = 18.sp,
            fontFamily = WorkSans,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.player_close),
                tint = Slate400,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SeasonPicker(seasons: List<SeasonInfo>, index: Int, seasonNo: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().clickable { open = true }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                seasons.getOrNull(index)?.name ?: stringResource(R.string.player_season, seasonNo),
                color = Cyan,
                fontSize = 14.sp,
                fontFamily = WorkSans,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                painterResource(R.drawable.ic_chevron_down),
                contentDescription = null,
                tint = Cyan,
                modifier = Modifier.size(16.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = SurfaceColor) {
            seasons.forEachIndexed { i, s ->
                DropdownMenuItem(
                    text = {
                        Text(
                            s.name,
                            color = if (i == index) Cyan else Slate100,
                            fontSize = 14.sp,
                            fontFamily = WorkSans,
                        )
                    },
                    onClick = {
                        onPick(i)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SidebarList(
    episodes: List<Episode>?,
    seasonNo: Int,
    playing: Int?,
    episode: String,
    onEpisode: (String, String) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(episodes, seasonNo) {
        if (seasonNo != playing) return@LaunchedEffect
        val index = episodes.orEmpty().indexOfFirst { it.n.toString() == episode }
        if (index >= 0) listState.scrollToItem(index)
    }
    when {
        episodes == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Cyan, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        }
        episodes.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.player_no_episodes),
                color = Slate500,
                fontSize = 14.sp,
                fontFamily = WorkSans,
            )
        }
        else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(episodes, key = { _, e -> e.n }) { _, e ->
                val current = seasonNo == playing && e.n.toString() == episode
                EpisodeRow(e, current) { onEpisode(seasonNo.toString(), e.n.toString()) }
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            }
        }
    }
}

@Composable
private fun EpisodeRow(e: Episode, current: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (current) Color.White.copy(alpha = 0.06f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(72.dp).aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            val thumb = e.still
            if (thumb != null) {
                AsyncImage(
                    model = thumb,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    painterResource(R.drawable.ic_play_outline),
                    contentDescription = null,
                    tint = Slate400,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                e.name,
                color = if (current) Cyan else Slate100,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontFamily = WorkSans,
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.player_episode_no, e.n),
                color = if (current) Cyan.copy(alpha = 0.75f) else Slate500,
                fontSize = 11.sp,
                fontFamily = WorkSans,
            )
        }
    }
}
