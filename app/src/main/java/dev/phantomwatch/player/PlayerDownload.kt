package dev.phantomwatch.player

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.phantomwatch.data.Stream
import dev.phantomwatch.data.Title
import dev.phantomwatch.ui.Cyan
import dev.phantomwatch.ui.DownloadIcon
import dev.phantomwatch.ui.WatchRed

@Composable
internal fun BoxScope.PlayerDownloadFab(
    stream: Stream,
    onOpen: () -> Unit,
) {
    if (!PhantomDownload.isHttpUrl(stream.url)) return
    SmallFloatingActionButton(
        onClick = onOpen,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 16.dp, bottom = 16.dp)
            .navigationBarsPadding(),
        containerColor = Color(0xE61E293B),
        contentColor = Cyan,
    ) {
        DownloadIcon(contentDescription = "Download", tint = Cyan, size = 20.dp)
    }
}

@Composable
internal fun PlayerDownloadDialog(
    media: Title,
    season: String,
    episode: String,
    stream: Stream,
    videoH: Int,
    durationMs: Long,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val installed = remember { PhantomDownload.isInstalled(context) }
    var installOnly by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val useInstall = installOnly || !installed
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download with Phantom") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(PhantomDownload.baseName(media.title, season, episode))
                Text(if (videoH > 0) "${videoH}p" else "Auto quality", color = Cyan)
                Text(
                    when {
                        PhantomDownload.englishSub(stream.subs) != null -> "English subtitle included."
                        stream.isHls -> "English subtitle added if the stream provides one."
                        else -> "No separate English subtitle found."
                    },
                )
                Text("Saves to Movies/PhantomWatch.")
                error?.let { Text(it, color = WatchRed) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (useInstall) {
                    PhantomDownload.openInstall(context)
                    onDismiss()
                    return@TextButton
                }
                val job = PhantomDownload.build(
                    media = media,
                    season = season,
                    episode = episode,
                    stream = stream,
                    headers = mediaHeaders(stream),
                    height = videoH,
                    durationMs = durationMs,
                )
                if (job == null || !PhantomDownload.send(context, job)) {
                    installOnly = true
                    error = "Phantom needs an update. Install the latest version and try again."
                } else {
                    onDismiss()
                    Toast.makeText(context, "Opening Phantom…", Toast.LENGTH_SHORT).show()
                }
            }) {
                Text(
                    if (useInstall) {
                        if (installed) "Update Phantom" else "Install Phantom"
                    } else {
                        "Open Phantom"
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
