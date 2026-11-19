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

// phone gets 128dp cards (2 full plus half next), tablet scales wider
fun featuredCell(width: Dp): Dp =
    if (isExpanded(width)) (gridCell(width, 6) * 1.12f).coerceIn(162.dp, 280.dp) else 128.dp

// library shelves denser than home showcase, cards are reference thumbnails not hero so more fit in row
fun libraryCell(width: Dp): Dp =
    if (isExpanded(width)) (gridCell(width, 8) * 1.05f).coerceIn(110.dp, 190.dp) else 92.dp

// chart rail landscape, one poster per swipe, 74% width leaves neighbour numeral and poster sliver in gutters, tablet cell capped so share set separately
fun landscapeCell(width: Dp): Dp =
    if (isExpanded(width)) (width * 0.52f).coerceIn(340.dp, 660.dp) else width * 0.74f

// numeral hangs in left gutter so phone centres poster, capped tablet cell leaves gutter so centring pulls row off 16dp margin other rails use
fun chartStart(width: Dp, cell: Dp, lead: Dp): Dp =
    if (isExpanded(width)) 16.dp else (((width - cell) / 2).coerceAtLeast(16.dp) - lead).coerceAtLeast(8.dp)

fun chartEnd(width: Dp, cell: Dp): Dp =
    if (isExpanded(width)) 16.dp else ((width - cell) / 2).coerceAtLeast(16.dp)

// one step denser than featured rails
fun exploreColumns(width: Dp): Int =
    (width / (featuredCell(width) * 0.72f + 12.dp)).toInt().coerceIn(3, 10)
