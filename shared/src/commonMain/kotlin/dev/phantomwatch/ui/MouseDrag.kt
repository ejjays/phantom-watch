package dev.phantomwatch.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// browser rails need explicit mouse drag support, touch handled by LazyRow
@Composable
expect fun Modifier.mouseDragScroll(state: LazyListState): Modifier
