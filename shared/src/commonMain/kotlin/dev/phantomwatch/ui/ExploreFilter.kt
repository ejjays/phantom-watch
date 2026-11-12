package dev.phantomwatch.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phantomwatch.core.ioDispatcher
import dev.phantomwatch.data.Title
import dev.phantomwatch.data.Tmdb
import dev.phantomwatch.res.Res
import dev.phantomwatch.res.ic_chevron_right
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource

data class ExploreGenre(val label: String, val id: Int? = null)

private val ExploreGenres = listOf(
    ExploreGenre("General"),
    ExploreGenre("Action", 28),
    ExploreGenre("Adventure", 12),
    ExploreGenre("Animation", 16),
    ExploreGenre("Comedy", 35),
    ExploreGenre("Crime", 80),
    ExploreGenre("Documentary", 99),
    ExploreGenre("Drama", 18),
    ExploreGenre("Family", 10751),
    ExploreGenre("Fantasy", 14),
    ExploreGenre("History", 36),
    ExploreGenre("Horror", 27),
    ExploreGenre("Music", 10402),
    ExploreGenre("Mystery", 9648),
    ExploreGenre("Romance", 10749),
    ExploreGenre("Science Fiction", 878),
    ExploreGenre("Thriller", 53),
    ExploreGenre("War", 10752),
    ExploreGenre("Western", 37),
)

private fun explorePath(genre: ExploreGenre): String =
    genre.id?.let { "discover/movie?with_genres=$it&sort_by=popularity.desc" } ?: "movie/popular"

class ExploreFeed {
    var genre by mutableStateOf(ExploreGenres.first())
        private set
    var items by mutableStateOf<List<Title>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set

    private var page = 0
    private var total = 1
    private var gen = 0

    fun reset() {
        gen++
        items = emptyList()
        page = 0
        total = 1
        loading = false
    }

    fun loadMore(apiKey: String, rails: List<RailState>, scope: CoroutineScope) {
        if (loading || page >= total) return
        loading = true
        val mine = gen
        val path = explorePath(genre)
        scope.launch {
            try {
                // both grow with every page, so the dedupe set and page walk stay off the main thread
                val (next, pages, fresh) = withContext(ioDispatcher) {
                    val seen = (rails.flatMap { it.items } + items).mapTo(HashSet()) { "${it.kind}-${it.id}" }
                    var p = page
                    var t = total
                    var found = emptyList<Title>()
                    var skipped = 0
                    while (p < t && skipped < 3) {
                        val r = Tmdb.rail(apiKey, path, p + 1) ?: break
                        p += 1
                        skipped += 1
                        t = r.totalPages
                        found = r.items.filter { "${it.kind}-${it.id}" !in seen }
                        if (found.isNotEmpty()) break
                    }
                    Triple(p, t, found)
                }
                if (mine != gen) return@launch
                if (fresh.isNotEmpty()) items = items + fresh
                page = next
                total = pages
            } catch (_: Exception) {
            } finally {
                if (mine == gen) loading = false
            }
        }
    }

    fun select(next: ExploreGenre, apiKey: String, scope: CoroutineScope) {
        if (next == genre) return
        val prev = genre
        genre = next
        gen++
        val mine = gen
        page = 0
        total = 1
        loading = true
        scope.launch {
            try {
                val r = withContext(ioDispatcher) { Tmdb.rail(apiKey, explorePath(next), 1) }
                if (mine != gen) return@launch
                if (r == null) {
                    // keep the picker on the list whose rows are still on screen
                    genre = prev
                    return@launch
                }
                items = r.items
                page = 1
                total = r.totalPages
            } catch (_: Exception) {
                if (mine == gen) genre = prev
            } finally {
                if (mine == gen) loading = false
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExploreGenrePicker(selected: ExploreGenre, loading: Boolean, onSelect: (ExploreGenre) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var pillWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val menuWidth = 300.dp
    Box {
        Row(
            Modifier.clip(CircleShape).background(SurfaceColor)
                .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape)
                .clickable(role = Role.Button) { open = true }
                .onSizeChanged { pillWidth = it.width }
                .padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(selected.label, color = Slate200, fontFamily = WorkSans, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (loading) {
                CircularProgressIndicator(color = Cyan, strokeWidth = 1.5.dp, modifier = Modifier.size(16.dp))
            } else {
                Icon(
                    painterResource(Res.drawable.ic_chevron_right), contentDescription = null,
                    tint = Slate400, modifier = Modifier.size(16.dp).rotate(90f),
                )
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.width(menuWidth),
            offset = DpOffset(with(density) { ((pillWidth - menuWidth.toPx()) / 2f).toDp() }, 0.dp),
            shape = RoundedCornerShape(20.dp),
            containerColor = SurfaceColor,
            tonalElevation = 0.dp,
            shadowElevation = 16.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        ) {
            FlowRow(
                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ExploreGenres.forEach { g ->
                    val on = g == selected
                    Box(
                        Modifier.clip(CircleShape)
                            .background(if (on) Cyan.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f))
                            .clickable(role = Role.Button) {
                                open = false
                                onSelect(g)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            g.label, color = if (on) Cyan else Slate200,
                            fontFamily = WorkSans, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
