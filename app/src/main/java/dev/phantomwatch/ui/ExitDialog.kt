package dev.phantomwatch.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.phantomwatch.R

@Composable
internal fun ExitDialog(onCancel: () -> Unit, onExit: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    // Most of the 56dp mark sits above the card; 16dp of it overlaps the edge.
    val overhang = 40.dp
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // The top padding is the icon's overhang, so the window does not clip its upper half.
        // fillMaxWidth must come after widthIn, or it forces the node past the 380dp cap and the
        // card sits off-centre inside a wider invisible box on tablets.
        Box(Modifier.padding(horizontal = 24.dp).widthIn(max = 380.dp).fillMaxWidth().padding(top = overhang)) {
            Column(
                Modifier.fillMaxWidth().clip(shape).background(SurfaceColor)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
                    .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Exit Phantom Watch?", color = Slate100, fontFamily = Rubik,
                    fontWeight = FontWeight.Bold, fontSize = 19.sp, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Your progress is saved, so you can pick up right where you left off.",
                    color = Slate400, fontFamily = Rubik, fontSize = 14.sp, lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill("Cancel", Modifier.weight(1f), Color.White.copy(alpha = 0.10f), Color.White, FontWeight.SemiBold, onCancel)
                    Pill("Exit", Modifier.weight(1f), Color.White, Color.Black, FontWeight.Bold, onExit)
                }
            }
            Image(
                painterResource(R.drawable.pirate), contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = -overhang)
                    .size(56.dp).clip(RoundedCornerShape(16.dp)),
            )
        }
    }
}

@Composable
private fun Pill(text: String, modifier: Modifier, bg: Color, fg: Color, weight: FontWeight, onClick: () -> Unit) {
    Box(
        modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(bg).pressClick(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontFamily = Rubik, fontWeight = weight, fontSize = 15.sp)
    }
}
