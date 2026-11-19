package dev.phantomwatch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.phantomwatch.data.Downloads

// phantom sends this after download finishes with MediaStore content:// uri, manifest registered so it arrives in background
class DownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val id = intent.getStringExtra("id").orEmpty()
        val uri = intent.getStringExtra("uri").orEmpty()
        if (id.isBlank() || uri.isBlank() || !uri.startsWith("content://")) return

        runCatching {
            context.contentResolver.takePersistableUriPermission(
                Uri.parse(uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }

        val season = intent.getIntExtra("season", -1)
        val episode = intent.getIntExtra("episode", -1)
        Downloads.add(
            ctx = context,
            id = id,
            title = intent.getStringExtra("title").orEmpty().ifBlank { "Download" },
            poster = intent.getStringExtra("poster")?.takeIf { it.isNotBlank() && it.startsWith("http") },
            season = season,
            episode = episode,
            uri = uri,
        )
    }

    companion object {
        const val ACTION = "dev.phantomwatch.action.DOWNLOAD_DONE"
    }
}
