package dev.phantomwatch.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import dev.phantomwatch.res.Res

@Composable
actual fun AnimatedPhantom(modifier: Modifier) {
    val json by produceState<String?>(null) {
        value = runCatching { Res.readBytes("files/animated_phantom.json").decodeToString() }.getOrNull()
    }
    val text = json ?: return
    val spec = remember(text) { LottieCompositionSpec.JsonString(text) }
    val composition by rememberLottieComposition(spec)
    LottieAnimation(
        composition = composition ?: return,
        modifier = modifier,
        iterations = 1,
    )
}
