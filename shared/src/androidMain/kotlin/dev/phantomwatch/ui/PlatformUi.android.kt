package dev.phantomwatch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.LifecycleResumeEffect

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled, onBack)
}

@Composable
actual fun OnResumeEffect(block: () -> Unit) {
    LifecycleResumeEffect(Unit) {
        block()
        onPauseOrDispose { }
    }
}
