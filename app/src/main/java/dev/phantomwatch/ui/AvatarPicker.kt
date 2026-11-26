package dev.phantomwatch.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phantomwatch.R
import dev.phantomwatch.ui.settings.BackButton
import dev.phantomwatch.ui.settings.SettingsCanvas
import dev.phantomwatch.ui.settings.SettingsText
import dev.phantomwatch.ui.settings.SettingsText2

@Composable
fun AvatarPickerScreen(onClose: () -> Unit) {
    PlatformBackHandler(enabled = true, onBack = onClose)
    val context = LocalContext.current
    val avatars = remember {
        context.assets.list("avatars")?.sorted()?.mapNotNull { name ->
            context.assets.open("avatars/$name").use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }?.let { name to it }
        }.orEmpty()
    }
    val groups = remember(avatars) {
        val byName = avatars.toMap()
        fun group(label: Int, names: List<String>) =
            label to names.mapNotNull { byName["$it.png"]?.let { bitmap -> it to bitmap } }
        listOf(
            group(
                R.string.you_avatar_faces,
                listOf(
                    "anonymous", "blowing-kiss", "blue-face-smile", "magenta-sweat",
                    "smile-yellow-face", "sleeping", "wink-grin", "quote-smile",
                    "star-eyes-tear", "sweat-smile", "tan-heart-eyes", "teal-heart-eyes",
                ),
            ),
            group(R.string.you_avatar_bmo, listOf("bmo-blue", "bmo-pink", "bmo-pixel", "bmo-yellow")),
            group(
                R.string.you_avatar_robots,
                listOf("bot-blue-teeth", "bot-gold-visor", "green-robot", "mint-robot", "yellow-robot"),
            ),
            group(
                R.string.you_avatar_creatures,
                listOf(
                    "green-glasses", "monster", "mummy", "orange-mask", "pastel-cat",
                    "pixel-bunny", "pumpkin", "purple-mask", "split-face", "vampire",
                ),
            ),
        )
    }
    Box(
        Modifier.fillMaxSize().background(SettingsCanvas).windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.fillMaxSize().widthIn(max = 900.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 8.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackButton(onClose)
                Text(
                    stringResource(R.string.you_choose_avatar),
                    color = SettingsText, fontFamily = WorkSans,
                    fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                state = rememberLazyGridState(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize().weight(1f),
            ) {
                groups.forEach { (label, items) ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            stringResource(label), color = SettingsText2,
                            fontFamily = WorkSans, fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        )
                    }
                    items(items, key = { it.first }) { (name, bitmap) ->
                        Image(
                            bitmap,
                            contentDescription = name,
                            modifier = Modifier.size(100.dp).pressClick { },
                        )
                    }
                }
            }
        }
    }
}
