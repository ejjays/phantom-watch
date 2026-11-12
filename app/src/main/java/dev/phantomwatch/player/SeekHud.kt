package dev.phantomwatch.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phantomwatch.R
import dev.phantomwatch.ui.WorkSans
import kotlin.math.roundToInt

// 0 -> 2 -> 0 over 500ms, replayed per tap
@Composable
internal fun SeekHud(back: Boolean, seconds: Int) {
    val nudge = remember { Animatable(0f) }
    val step = with(LocalDensity.current) { 2.dp.toPx() }
    LaunchedEffect(seconds) {
        nudge.animateTo(0f, keyframes {
            durationMillis = 500
            step at 200
        })
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(R.drawable.ic_seek_chevron),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(30.dp)
                .scale(scaleX = if (back) -1f else 1f, scaleY = 1f)
                .offset { IntOffset((if (back) -nudge.value else nudge.value).roundToInt(), 0) },
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "$seconds", color = Color.White, fontSize = 20.sp,
            fontFamily = WorkSans, fontWeight = FontWeight.SemiBold,
        )
    }
}

// recap wins over opening when the two overlap; an outro never shares the screen with either
internal data class StageAt(
    val intro: LongRange?,
    val recap: LongRange?,
    val outro: LongRange?,
    val duration: Long,
    val position: Long,
) {
    private fun has(range: LongRange?) = range != null && duration > 0 && position in range && !Pip.inPip
    val inRecap: Boolean get() = has(recap)
    val inIntro: Boolean get() = !inRecap && has(intro)
    val inOutro: Boolean get() = !inIntro && has(outro)
}

internal fun pillText(recap: Boolean, intro: Boolean): Int = when {
    recap -> R.string.player_skip_recap
    intro -> R.string.player_skip_intro
    else -> R.string.player_next_episode
}
