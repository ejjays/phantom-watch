package dev.phantomwatch.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowLayoutTest {
    @Test
    fun phoneKeepsThreeColumns() {
        assertEquals(3, gridColumns(411.dp))
        assertEquals(3, gridColumns(320.dp))
    }

    @Test
    fun tabletCapsColumnsAtSix() {
        assertEquals(5, gridColumns(600.dp))
        assertEquals(6, gridColumns(840.dp))
        assertEquals(6, gridColumns(1280.dp))
    }

    @Test
    fun cellsExactlyFillTheWidth() {
        for (width in listOf(411.dp, 600.dp, 800.dp, 1280.dp)) {
            val columns = gridColumns(width)
            val used = gridCell(width, columns) * columns + 12.dp * (columns - 1) + 32.dp
            assertEquals(width.value, used.value, 0.01f)
        }
    }

    @Test
    fun twoPaneStartsAtExpanded() {
        assertFalse(isExpanded(839.dp))
        assertTrue(isExpanded(840.dp))
    }

    @Test
    fun detailPaneIsCapped() {
        assertEquals(352.8f, detailPaneWidth(840.dp).value, 0.01f)
        assertEquals(480f, detailPaneWidth(1280.dp).value, 0.01f)
    }
}
