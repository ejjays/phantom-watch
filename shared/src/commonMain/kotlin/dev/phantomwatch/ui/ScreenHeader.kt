package dev.phantomwatch.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phantomwatch.res.*

// pushed views show a back arrow; on History back is the tab bar
@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)?, lilita: Boolean = false) {
    val screenW = screenWidthDp()
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                Icon(painterResource(Res.drawable.ic_back), contentDescription = stringResource(Res.string.common_back), tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(4.dp))
        }
        Text(
            title, color = Color.White, fontSize = if (isExpanded(screenW)) 32.sp else 28.sp,
            fontFamily = if (lilita) Lilita else WorkSans,
            fontWeight = when {
                lilita -> FontWeight.Normal
                isExpanded(screenW) -> FontWeight.Bold
                else -> FontWeight.SemiBold
            },
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
