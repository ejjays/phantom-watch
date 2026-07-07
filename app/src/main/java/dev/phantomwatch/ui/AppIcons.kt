package dev.phantomwatch.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import dev.phantomwatch.R

@Composable
internal fun ChevronRightIcon(contentDescription: String?, tint: Color, size: Dp) {
    Icon(
        painter = painterResource(R.drawable.ic_chevron_right),
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier.size(size),
    )
}

@Composable
internal fun CloseIcon(contentDescription: String?, tint: Color, size: Dp) {
    Icon(
        painter = painterResource(R.drawable.ic_close),
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier.size(size),
    )
}

@Composable
internal fun DownloadIcon(contentDescription: String?, tint: Color, size: Dp) {
    Icon(
        painter = painterResource(R.drawable.ic_download),
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier.size(size),
    )
}

@Composable
internal fun LikeIcon(contentDescription: String?, tint: Color, size: Dp) {
    Icon(
        painter = painterResource(R.drawable.ic_like),
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier.size(size),
    )
}

@Composable
internal fun DislikeIcon(contentDescription: String?, tint: Color, size: Dp) {
    Icon(
        painter = painterResource(R.drawable.ic_dislike),
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier.size(size).scale(-1f, 1f),
    )
}

@Composable
internal fun CommentIcon(contentDescription: String?, tint: Color, size: Dp) {
    Icon(
        painter = painterResource(R.drawable.ic_comment),
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier.size(size),
    )
}

@Composable
internal fun ShareIcon(contentDescription: String?, tint: Color, size: Dp) {
    Icon(
        painter = painterResource(R.drawable.ic_share),
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier.size(size),
    )
}
