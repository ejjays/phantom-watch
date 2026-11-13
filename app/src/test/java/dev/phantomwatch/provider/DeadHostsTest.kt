package dev.phantomwatch.provider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeadHostsTest {
    @Test
    fun theProbeDoesNotForgiveAHang() {
        DeadHosts.markHang("https://hung.example/a", 120)
        DeadHosts.forgive("https://hung.example/a")
        assertTrue(DeadHosts.blocked("https://hung.example/a"))
    }

    @Test
    fun theProbeForgivesAnErrorBan() {
        DeadHosts.mark("https://err.example/a", 2)
        DeadHosts.forgive("https://err.example/a")
        assertFalse(DeadHosts.blocked("https://err.example/a"))
    }

    @Test
    fun aRateLimitCooldownExpiresWithoutAProbe() {
        DeadHosts.cooldown("https://rate.example/a", 10)
        assertTrue(DeadHosts.blocked("https://rate.example/a"))
        DeadHosts.cooldown("https://rate.example/a", 0)
        assertFalse(DeadHosts.blocked("https://rate.example/a"))
    }
}
