package dev.phantomwatch.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubsSourceSheet(
    selected: String,
    metered: Boolean,
    ready: Boolean,
    dlPct: Int?,
    dlErr: Boolean,
    onSelect: (String) -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var armed by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(
                "Subtitle source", color = Color.White, fontSize = 16.sp,
                fontFamily = Rubik, fontWeight = FontWeight.Bold,
            )
            SourceRow(
                selected == "providers", "Providers (default)",
                "OpenSubtitles + embedded tracks",
            ) { onSelect("providers") }
            SourceRow(
                selected == "local", "Local AI · SenseVoice",
                "On-device transcription · English via Qwen",
            ) { onSelect("local") }
            when {
                ready -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "model ✓ · 228MB", color = Cyan, fontSize = 12.sp, fontFamily = Mono,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDelete) { Text("Delete") }
                }
                dlPct != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { dlPct / 100f },
                        modifier = Modifier.weight(1f),
                        color = Cyan,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("$dlPct%", color = Cyan, fontSize = 12.sp, fontFamily = Mono)
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
                dlErr -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "download failed", color = WatchRed, fontSize = 12.sp, fontFamily = Mono,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { armed = false; onDownload() }) { Text("Retry") }
                }
                else -> Button(
                    onClick = {
                        if (metered && !armed) armed = true else onDownload()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    Text(
                        when {
                            armed -> "Metered · tap again to download 228MB"
                            metered -> "Download · 228MB (metered)"
                            else -> "Download · 228MB"
                        },
                        fontFamily = Rubik, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Local AI transcribes speech on this device (SenseVoice int8, one-time ~228MB). " +
                    "English lines come from Qwen — text only, a few KB.",
                color = Slate500, fontSize = 11.sp, fontFamily = Mono, lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun SourceRow(selected: Boolean, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = Cyan),
        )
        Column(Modifier.weight(1f)) {
            Text(
                title, color = Color.White, fontSize = 14.sp,
                fontFamily = Rubik, fontWeight = FontWeight.Medium,
            )
            Text(sub, color = Slate500, fontSize = 11.sp, fontFamily = Mono)
        }
    }
}
