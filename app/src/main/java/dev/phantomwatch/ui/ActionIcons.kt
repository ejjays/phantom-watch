package dev.phantomwatch.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import dev.phantomwatch.R

@Composable
internal fun SendIcon(contentDescription: String?, tint: Color, size: Dp) {
    Icon(
        painter = painterResource(R.drawable.ic_send),
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier.size(size),
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

@Composable
internal fun AddIcon(contentDescription: String?, tint: Color, size: Dp) {
    Icon(
        painter = painterResource(R.drawable.ic_add),
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier.size(size),
    )
}
