package dev.phantomwatch.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val ExpandedMin = 840.dp
private val PosterCellMin = 120.dp

internal fun isExpanded(width: Dp): Boolean = width >= ExpandedMin

internal fun gridColumns(width: Dp): Int =
    (width / PosterCellMin).toInt().coerceIn(3, 6)

internal fun gridCell(width: Dp, columns: Int): Dp =
    ((width - 32.dp - 12.dp * (columns - 1)) / columns).coerceAtLeast(1.dp)

/** Featured rails: a sixth card is always sliced by the right edge so the row reads as scrollable. */
internal fun featuredCell(width: Dp): Dp = (gridCell(width, 6) * 1.12f).coerceIn(162.dp, 280.dp)

/** Explore grid runs one step denser than the featured rails. */
internal fun exploreColumns(width: Dp): Int =
    (width / (featuredCell(width) * 0.72f + 12.dp)).toInt().coerceIn(3, 10)
