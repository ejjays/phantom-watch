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
