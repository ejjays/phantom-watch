package dev.phantomwatch.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// LazyRow already handles touch and mouse drag
@Composable
actual fun Modifier.mouseDragScroll(state: LazyListState): Modifier = this
