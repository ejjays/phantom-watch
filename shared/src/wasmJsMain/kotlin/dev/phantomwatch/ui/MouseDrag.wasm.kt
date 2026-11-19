package dev.phantomwatch.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
actual fun Modifier.mouseDragScroll(state: LazyListState): Modifier {
    val scope = rememberCoroutineScope()
    return this.pointerInput(state, scope) {
        awaitEachGesture {
            val down = awaitPointerEvent()
            if (down.type != PointerEventType.Press) return@awaitEachGesture
            val first = down.changes.firstOrNull() ?: return@awaitEachGesture
            val id = first.id
            val startX = first.position.x
            val startIndex = state.firstVisibleItemIndex
            val startOffset = state.firstVisibleItemScrollOffset
            val itemSize = state.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.toFloat() ?: 240f
            val lastIndex = (state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
            var dragged = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == id } ?: break
                if (event.type == PointerEventType.Release || !change.pressed) {
                    // drag must not fall through to card clickable
                    if (dragged) change.consume()
                    break
                }
                if (event.type == PointerEventType.Move) {
                    // damping gives drag resistance instead of snapping rail one for one with cursor
                    val delta = (startX - change.position.x) * 0.6f
                    if (kotlin.math.abs(delta) > 6f) dragged = true
                    if (dragged) {
                        change.consume()
                        // normalize into item + offset so dragging both ways
                        // can cross item boundaries instead of sticking at 0
                        var index = startIndex
                        var offset = startOffset + delta
                        while (offset < 0f && index > 0) {
                            index--
                            offset += itemSize
                        }
                        while (offset > itemSize && index < lastIndex) {
                            index++
                            offset -= itemSize
                        }
                        // scrollToItem is restricted suspend call, run outside awaitEachGesture limited scope
                        scope.launch {
                            state.scrollToItem(index, offset.toInt().coerceAtLeast(0))
                        }
                    }
                }
            }
        }
    }
}
