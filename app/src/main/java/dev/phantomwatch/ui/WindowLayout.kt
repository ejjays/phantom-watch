package dev.phantomwatch.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val ExpandedMin = 840.dp
private val DetailPaneMax = 480.dp
private val PosterCellMin = 120.dp

internal fun isExpanded(width: Dp): Boolean = width >= ExpandedMin

internal fun detailPaneWidth(width: Dp): Dp = minOf(DetailPaneMax, width * 0.42f)

internal fun gridColumns(width: Dp): Int =
    (width / PosterCellMin).toInt().coerceIn(3, 6)

internal fun gridCell(width: Dp, columns: Int): Dp =
    ((width - 32.dp - 12.dp * (columns - 1)) / columns).coerceAtLeast(1.dp)
