package dev.phantomwatch.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateTrustTest {
    @Test
    fun onlyAcceptsLoopbackApkUrls() {
        assertTrue(UpdateTrust.validUrl("http://127.0.0.1:8123/app-debug.apk"))
        assertTrue(UpdateTrust.validUrl("http://127.0.0.1:8123/app-release.apk"))
        assertFalse(UpdateTrust.validUrl("http://localhost:8123/app-debug.apk"))
        assertFalse(UpdateTrust.validUrl("http://127.0.0.2:8123/app-debug.apk"))
        assertFalse(UpdateTrust.validUrl("https://127.0.0.1:8123/app-debug.apk"))
        assertFalse(UpdateTrust.validUrl("http://127.0.0.1:8123/other.apk"))
        assertFalse(UpdateTrust.validUrl("http://user@127.0.0.1:8123/app-debug.apk"))
        assertFalse(UpdateTrust.validUrl("http://127.0.0.1:8123/app-debug.apk?redirect=1"))
        assertFalse(UpdateTrust.validUrl("http://127.0.0.1:8123/app-debug.apk#fragment"))
    }

    @Test
    fun acceptsOnlyHexTokensOfExpectedLength() {
        assertTrue(UpdateTrust.validToken("a".repeat(64)))
        assertFalse(UpdateTrust.validToken("a".repeat(63)))
        assertFalse(UpdateTrust.validToken("g".repeat(64)))
        assertFalse(UpdateTrust.validToken("a".repeat(65)))
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
