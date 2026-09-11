package dev.phantomwatch.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

@Composable
actual fun OnResumeEffect(block: () -> Unit) {
    LaunchedEffect(Unit) { block() }
}
