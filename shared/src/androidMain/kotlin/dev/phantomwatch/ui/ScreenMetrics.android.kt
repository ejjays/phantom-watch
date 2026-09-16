package dev.phantomwatch.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp

@Composable
actual fun screenWidthDp(): Dp =
    with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }

@Composable
actual fun screenHeightDp(): Dp =
    with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
