package dev.phantomwatch.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrentsTest {
    @Test
    fun bestPrefersSeeded() {
        val risky = TorrentRelease("risky", "1 GB", 1, "hashA")
        val solid = TorrentRelease("solid", "1 GB", 9, "hashB")
        assertEquals(solid, Torrents.best(listOf(risky, solid)))
    }

    @Test
    fun bestSkipsDeadReleases() {
        val dead = TorrentRelease("dead", "1 GB", 20, "hashC")
        Torrents.DeadReleases.hashes.add("hashC")
        try {
            assertNull(Torrents.best(listOf(dead)))
        } finally {
            Torrents.DeadReleases.hashes.remove("hashC")
        }
    }

    @Test
    fun deadHostsForgiveClearsBan() {
        Torrents.DeadHosts.mark("https://forgive.example/a.mp4")
        assertTrue(Torrents.DeadHosts.blocked("https://forgive.example/b.mp4"))
        Torrents.DeadHosts.forgive("https://forgive.example/b.mp4")
        assertTrue(!Torrents.DeadHosts.blocked("https://forgive.example/a.mp4"))
    }
}
