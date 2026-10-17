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

    @Test
    fun aRateLimitCooldownExpiresWithoutAProbe() {
        Torrents.DeadHosts.cooldown("https://rate.example/a", 10)
        assertTrue(Torrents.DeadHosts.blocked("https://rate.example/a"))
        Torrents.DeadHosts.cooldown("https://rate.example/a", 0)
        assertFalse(Torrents.DeadHosts.blocked("https://rate.example/a"))
    }
}
