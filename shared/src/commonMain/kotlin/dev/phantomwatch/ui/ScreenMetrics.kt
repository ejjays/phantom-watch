package dev.phantomwatch.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import kotlin.math.abs
import kotlin.math.roundToInt

// Android's LocalConfiguration is not in common Compose; the window container is.
@Composable
fun screenWidthDp(): Dp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }

@Composable
fun screenHeightDp(): Dp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }

// String.format is JVM-only; ratings only ever need one decimal.
fun fmt1(x: Double): String {
    val scaled = (x * 10).roundToInt()
    return "${scaled / 10}.${abs(scaled % 10)}"
}
