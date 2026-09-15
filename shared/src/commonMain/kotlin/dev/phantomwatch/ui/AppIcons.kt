package dev.phantomwatch.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import dev.phantomwatch.res.Res
import dev.phantomwatch.res.ic_chevron_right
import dev.phantomwatch.res.ic_close
import org.jetbrains.compose.resources.painterResource

@Composable
fun ChevronRightIcon(contentDescription: String?, tint: Color, size: Dp) {
    Icon(
        painter = painterResource(Res.drawable.ic_chevron_right),
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier.size(size),
    )
}

@Composable
fun ChevronLeftIcon(contentDescription: String?, tint: Color, size: Dp) {
    Icon(
        painter = painterResource(Res.drawable.ic_chevron_right),
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier.size(size).rotate(180f),
    )
}

@Composable
fun CloseIcon(contentDescription: String?, tint: Color, size: Dp) {
    Icon(
        painter = painterResource(Res.drawable.ic_close),
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier.size(size),
    )
}
