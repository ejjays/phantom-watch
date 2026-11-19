package dev.phantomwatch.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.shapes.Capsule
import dev.phantomwatch.R
import dev.phantomwatch.ui.WorkSans

@Composable
internal fun PlayerPill(
    text: String,
    modifier: Modifier = Modifier,
    icon: Int = R.drawable.ic_skip_next_filled,
    iconBefore: Boolean = false,
    iconSize: androidx.compose.ui.unit.Dp = 12.dp,
    onClick: () -> Unit,
) {
    Row(
        modifier.height(32.dp).clip(Capsule())
            .background(Color.Black.copy(alpha = 0.6f))
            .focusRing(Capsule())
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(start = 12.dp, end = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val label = @Composable {
            Text(
                text, color = Color.White, fontSize = 12.sp,
                fontFamily = WorkSans, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp,
            )
        }
        val arrow = @Composable {
            Icon(
                painterResource(icon),
                contentDescription = null, tint = Color.White, modifier = Modifier.size(iconSize),
            )
        }
        if (iconBefore) {
            arrow()
            Spacer(Modifier.width(6.dp))
            label()
        } else {
            label()
            Spacer(Modifier.width(6.dp))
            arrow()
        }
    }
}
