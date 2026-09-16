package dev.phantomwatch.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import kotlin.math.abs
import kotlin.math.roundToInt

// LocalConfiguration is not in common Compose, and the wasm canvas never fills in
// LocalWindowInfo.containerSize, so each platform answers for itself
@Composable
expect fun screenWidthDp(): Dp

@Composable
expect fun screenHeightDp(): Dp

// String.format is JVM-only; ratings only ever need one decimal
fun fmt1(x: Double): String {
    val scaled = (x * 10).roundToInt()
    return "${scaled / 10}.${abs(scaled % 10)}"
}
