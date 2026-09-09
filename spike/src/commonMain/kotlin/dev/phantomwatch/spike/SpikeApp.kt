package dev.phantomwatch.spike

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phantomwatch.data.Title
import dev.phantomwatch.ui.Bg
import dev.phantomwatch.ui.PosterCard
import dev.phantomwatch.ui.Rubik
import dev.phantomwatch.ui.Slate400
import dev.phantomwatch.ui.TopTenCard
import dev.phantomwatch.ui.WatchRed

private val demo = listOf(
    Title("1", "movie", "Dune: Part Two", null),
    Title("2", "tv", "Severance", null),
    Title("3", "movie", "Oppenheimer", null),
    Title("4", "tv", "The Bear", null),
)

@Composable
fun SpikeApp() {
    MaterialTheme {
        Column(Modifier.fillMaxSize().background(Bg).padding(24.dp)) {
            Text("Phantom Watch", color = WatchRed, fontSize = 28.sp, fontFamily = Rubik, fontWeight = FontWeight.Bold)
            Text(
                "PosterCard + TopTenCard, shared from :shared",
                color = Slate400, fontSize = 13.sp, fontFamily = Rubik,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                demo.take(3).forEach { t ->
                    PosterCard(item = t, width = 130.dp, height = 195.dp, onOpen = {})
                }
                TopTenCard(rank = 10, item = demo[3], width = 130.dp, height = 195.dp, onOpen = {})
            }
        }
    }
}
