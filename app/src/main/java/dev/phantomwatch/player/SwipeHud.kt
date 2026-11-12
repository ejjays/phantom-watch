package dev.phantomwatch.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phantomwatch.R
import dev.phantomwatch.ui.WorkSans
import kotlin.math.roundToInt

data class SwipeHud(val brightness: Boolean, val frac: Float)

// YouTube's swipe readout: one pill at the top of the picture, icon + track + value
@Composable
internal fun SwipeHudPill(hud: SwipeHud, modifier: Modifier = Modifier) {
    val frac = hud.frac.coerceIn(0f, 1f)
    Row(
        modifier.height(40.dp).clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(
                when {
                    hud.brightness -> R.drawable.ic_brightness
                    frac <= 0f -> R.drawable.ic_volume_off
                    else -> R.drawable.ic_volume_on
                },
            ),
            contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.3f)),
        ) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Color.White))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "${(frac * 100).roundToInt()}%",
            color = Color.White, fontSize = 12.sp, fontFamily = WorkSans, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End, maxLines = 1, softWrap = false,
            modifier = Modifier.widthIn(min = 34.dp),
        )
    }
}
