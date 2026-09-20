package dev.phantomwatch.ui

import androidx.compose.runtime.Composable

// the two things Compose keeps on the platform side: hardware back and "became visible again"
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

@Composable
expect fun OnResumeEffect(block: () -> Unit)

expect fun initialPath(): String?

expect fun syncRoute(path: String)
