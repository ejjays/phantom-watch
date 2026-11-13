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
// neighbour's numeral and a sliver of its poster in the gutters. the tablet cell is capped, so
// its share is set separately
fun landscapeCell(width: Dp): Dp =
    if (isExpanded(width)) (width * 0.52f).coerceIn(340.dp, 660.dp) else width * 0.74f

// the numeral hangs in the left gutter, so a phone centres the poster and lets the numeral sit in
// the margin; a capped tablet cell leaves enough gutter that centring pulls the row off the 16dp
// margin every other rail uses
fun chartStart(width: Dp, cell: Dp, lead: Dp): Dp =
    if (isExpanded(width)) 16.dp else (((width - cell) / 2).coerceAtLeast(16.dp) - lead).coerceAtLeast(8.dp)

fun chartEnd(width: Dp, cell: Dp): Dp =
    if (isExpanded(width)) 16.dp else ((width - cell) / 2).coerceAtLeast(16.dp)

// one step denser than the featured rails
fun exploreColumns(width: Dp): Int =
    (width / (featuredCell(width) * 0.72f + 12.dp)).toInt().coerceIn(3, 10)
