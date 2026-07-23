package dev.phantomwatch.torrent

import com.frostwire.jlibtorrent.TorrentHandle

class DownloadControl(val handle: TorrentHandle) {
    @Volatile var active = true
    var tries = 0
    var fileIndex = -1
    var fileSize = 0L

    fun stop() {
        active = false
        try {
            handle.pause()
        } catch (_: Exception) {
        }
    }
}
