package dev.phantomwatch.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.phantomwatch.res.*

@Composable
fun ExitDialog(onCancel: () -> Unit, onExit: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    val icon = 64.dp
    val overhang = icon * 0.714f
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.width(minOf(maxWidth * 0.78f, 380.dp)).padding(top = overhang)) {
            Column(
                Modifier.fillMaxWidth().clip(shape).background(SurfaceColor)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
                    .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(Res.string.exit_title), color = Slate100, fontFamily = WorkSans,
                    fontWeight = FontWeight.Bold, fontSize = 19.sp, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(Res.string.exit_body),
                    color = Slate400, fontFamily = WorkSans, fontSize = 14.sp, lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(22.dp))
                val cancel = stringResource(Res.string.common_cancel)
                val exit = stringResource(Res.string.exit_confirm)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill(cancel, Modifier.weight(1f), Color.White.copy(alpha = 0.10f), Color.White, FontWeight.SemiBold, onCancel)
                    Pill(exit, Modifier.weight(1f), Color.White, Color.Black, FontWeight.Bold, onExit)
                }
            }
            Image(
                painterResource(Res.drawable.phantom_logo), contentDescription = null,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = -overhang).size(icon),
            )
            }
        }
    }
}

@Composable
private fun Pill(text: String, modifier: Modifier, bg: Color, fg: Color, weight: FontWeight, onClick: () -> Unit) {
    Box(
        modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(bg).pressClick(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontFamily = WorkSans, fontWeight = weight, fontSize = 15.sp)
    }
}
