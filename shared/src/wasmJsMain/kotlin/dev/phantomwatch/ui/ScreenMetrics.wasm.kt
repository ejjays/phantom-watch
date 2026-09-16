package dev.phantomwatch.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.browser.window

// the canvas runs at density 1, so one CSS pixel is one dp
@Composable
actual fun screenWidthDp(): Dp = window.innerWidth.toFloat().dp

@Composable
actual fun screenHeightDp(): Dp = window.innerHeight.toFloat().dp
