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

// phone gets 128dp cards (2 full plus half of the next); tablet scales wider
fun featuredCell(width: Dp): Dp =
    if (isExpanded(width)) (gridCell(width, 6) * 1.12f).coerceIn(162.dp, 280.dp) else 128.dp

// the chart rail runs landscape and snaps one poster at a time; 74% of the width leaves the
// neighbour's numeral and a sliver of its poster in the gutters
fun landscapeCell(width: Dp): Dp =
    if (isExpanded(width)) (width * 0.42f).coerceIn(320.dp, 560.dp) else width * 0.74f

// one step denser than the featured rails
fun exploreColumns(width: Dp): Int =
    (width / (featuredCell(width) * 0.72f + 12.dp)).toInt().coerceIn(3, 10)
