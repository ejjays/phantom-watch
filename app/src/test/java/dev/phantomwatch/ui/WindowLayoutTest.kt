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
    fun chartCardKeepsItsPhoneShare() {
        assertEquals(411f * 0.74f, landscapeCell(411.dp).value, 0.05f)
    }

    @Test
    fun chartCardGrowsOnTablets() {
        val cell = landscapeCell(1115.dp)
        assertTrue(cell.value > 520f)
        assertTrue(cell.value <= 660f)
        assertEquals(660f, landscapeCell(1400.dp).value, 0.01f)
    }

    @Test
    fun chartRowStartsAtTheContentMarginOnTablets() {
        val width = 1115.dp
        val lead = landscapeCell(width) * 9f / 16f * 0.26f
        assertEquals(16f, chartStart(width, landscapeCell(width), lead).value, 0.01f)
        assertEquals(16f, chartEnd(width, landscapeCell(width)).value, 0.01f)
    }

    @Test
    fun chartPosterStaysCentredOnPhones() {
        val width = 411.dp
        val cell = landscapeCell(width)
        val lead = cell * 9f / 16f * 0.26f
        assertEquals((width - cell).value / 2f, (chartStart(width, cell, lead) + lead).value, 0.5f)
    }
}
