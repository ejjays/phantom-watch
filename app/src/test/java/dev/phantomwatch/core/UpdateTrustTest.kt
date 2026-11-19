package dev.phantomwatch.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateTrustTest {
    @Test
    fun acceptsOnlyReleaseApkUrls() {
        assertTrue(UpdateTrust.releaseEntry("https://github.com/ejjays/phantom-watch/releases/download/v1/app-release.apk"))
        assertFalse(UpdateTrust.releaseEntry("http://github.com/ejjays/phantom-watch/releases/download/v1/app-release.apk"))
        assertFalse(UpdateTrust.releaseEntry("https://github.com/other/repo/releases/download/v1/app-release.apk"))
        assertFalse(UpdateTrust.releaseEntry("https://github.com/ejjays/phantom-watch/releases/download/v1/notes.txt"))
        assertFalse(UpdateTrust.releaseEntry("https://github.com/ejjays/phantom-watch/releases/download/v1/app-release.apk?x=1"))
        assertTrue(UpdateTrust.releaseHop("https://release-assets.githubusercontent.com/github-production-release-asset/x"))
        assertFalse(UpdateTrust.releaseHop("https://evil.test/github-production-release-asset/x"))
    }

    @Test
    fun acceptsPhantomDownloaderReleaseApkUrls() {
        assertTrue(UpdateTrust.releaseEntry("https://github.com/ejjays/phantom/releases/download/v1.2.23/app-release.apk"))
        assertFalse(UpdateTrust.releaseEntry("https://github.com/ejjays/phantom/releases/download/v1.2.23/notes.txt"))
        assertFalse(UpdateTrust.releaseEntry("https://github.com/ejjays/phantom/releases/download/v1.2.23/app-release.apk?x=1"))
    }

    @Test
    fun acceptsLoopbackOnlyForDebugBuilds() {
        val url = "http://127.0.0.1:8123/app-debug.apk"
        val token = "a".repeat(64)
        assertTrue(UpdateTrust.localEntry(url, token, allowLocal = true))
        assertFalse(UpdateTrust.localEntry(url, token, allowLocal = false))
        assertFalse(UpdateTrust.localEntry(url, "short", allowLocal = true))
        assertFalse(UpdateTrust.localEntry("http://127.0.0.1:80/app-debug.apk", token, allowLocal = true))
        assertFalse(UpdateTrust.localEntry("http://10.0.0.2:8123/app-debug.apk", token, allowLocal = true))
        assertFalse(UpdateTrust.localEntry("http://127.0.0.1:8123/other.apk", token, allowLocal = true))
        assertFalse(UpdateTrust.localEntry("http://127.0.0.1:8123/app-debug.apk?x=1", token, allowLocal = true))
        assertFalse(UpdateTrust.localEntry("https://github.com/ejjays/phantom-watch/releases/download/v1/app-debug.apk", token, allowLocal = true))
    }

    @Test
    fun enforcesPositiveApkSizeWithinLimit() {
        assertFalse(UpdateTrust.validSize(0))
        assertTrue(UpdateTrust.validSize(1))
        assertTrue(UpdateTrust.validSize(200L * 1024 * 1024))
        assertFalse(UpdateTrust.validSize(200L * 1024 * 1024 + 1))
    }

    @Test
    fun comparesOnlyWellFormedPinnedCertificateFingerprints() {
        val fingerprint = "0123456789abcdef".repeat(4)
        assertTrue(UpdateTrust.sameCertificate(fingerprint, fingerprint.uppercase()))
        assertTrue(UpdateTrust.sameCertificate(fingerprint, fingerprint.chunked(2).joinToString(":")))
        assertFalse(UpdateTrust.sameCertificate(fingerprint, ""))
        assertFalse(UpdateTrust.sameCertificate(fingerprint, "f".repeat(64)))
        assertFalse(UpdateTrust.sameCertificate("z".repeat(64), fingerprint))
    }
}
