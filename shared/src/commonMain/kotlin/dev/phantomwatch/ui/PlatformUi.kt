package dev.phantomwatch.ui

import androidx.compose.runtime.Composable

// The two things Compose still keeps on the platform side: hardware back and
// "screen became visible again". Web has neither in the same shape.
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

@Composable
expect fun OnResumeEffect(block: () -> Unit)
