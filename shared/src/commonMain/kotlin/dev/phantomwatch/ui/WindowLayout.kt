package dev.phantomwatch.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val ExpandedMin = 840.dp
private val PosterCellMin = 120.dp

fun isExpanded(width: Dp): Boolean = width >= ExpandedMin

fun gridColumns(width: Dp): Int =
    (width / PosterCellMin).toInt().coerceIn(3, 6)

fun gridCell(width: Dp, columns: Int): Dp =
    ((width - 32.dp - 12.dp * (columns - 1)) / columns).coerceAtLeast(1.dp)

// phone gets 150dp cards (~2.5 visible); tablet scales wider
fun featuredCell(width: Dp): Dp =
    if (isExpanded(width)) (gridCell(width, 6) * 1.12f).coerceIn(162.dp, 280.dp) else 150.dp

// one step denser than the featured rails
fun exploreColumns(width: Dp): Int =
    (width / (featuredCell(width) * 0.72f + 12.dp)).toInt().coerceIn(3, 10)
