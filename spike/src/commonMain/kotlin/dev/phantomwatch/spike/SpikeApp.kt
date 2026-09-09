package dev.phantomwatch.spike

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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

private const val IMG = "https://image.tmdb.org/t/p/w342"

private val demo = listOf(
    Title("1", "movie", "Dune: Part Two", "$IMG/6izwz7rsy95ARzTR3poZ8H6c5pp.jpg"),
    Title("2", "tv", "Severance", "$IMG/pPHpeI2X1qEd1CS1SeyrdhZ4qnT.jpg"),
    Title("3", "movie", "Oppenheimer", "$IMG/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg"),
    Title("4", "tv", "The Bear", "$IMG/eKfVzzEazSIjJMrw9ADa2x8ksLz.jpg"),
)

@Composable
fun SpikeApp() {
    MaterialTheme {
        BoxWithConstraints(Modifier.fillMaxSize().background(Bg)) {
            val gap = 12.dp
            val cardW = (maxWidth - 48.dp - gap * 2) / 3
            val cardH = cardW * 1.5f
            Column(Modifier.padding(24.dp)) {
                Text("Phantom Watch", color = WatchRed, fontSize = 26.sp, fontFamily = Rubik, fontWeight = FontWeight.Bold)
                Text(
                    "PosterCard + TopTenCard, shared from :shared",
                    color = Slate400, fontSize = 12.sp, fontFamily = Rubik,
                    modifier = Modifier.padding(top = 2.dp, bottom = 20.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    demo.take(3).forEach { t ->
                        PosterCard(item = t, width = cardW, height = cardH, onOpen = {})
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    TopTenCard(rank = 1, item = demo[0], width = cardW, height = cardH, onOpen = {})
                    TopTenCard(rank = 10, item = demo[3], width = cardW, height = cardH, onOpen = {})
                }
            }
        }
    }
}
