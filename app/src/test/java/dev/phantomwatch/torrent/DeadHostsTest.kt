package dev.phantomwatch.torrent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeadHostsTest {
    @Test
    fun theProbeDoesNotForgiveAHang() {
        Torrents.DeadHosts.markHang("https://hung.example/a", 2)
        Torrents.DeadHosts.forgive("https://hung.example/a")
        assertTrue(Torrents.DeadHosts.blocked("https://hung.example/a"))
    }

    @Test
    fun theProbeForgivesAnErrorBan() {
        Torrents.DeadHosts.mark("https://err.example/a", 2)
        Torrents.DeadHosts.forgive("https://err.example/a")
        assertFalse(Torrents.DeadHosts.blocked("https://err.example/a"))
    }
}
