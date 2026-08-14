package dev.phantomwatch.core

import java.net.URI

internal object UpdateTrust {
    private const val MAX_APK_BYTES = 200L * 1024 * 1024

    fun validUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "http" && uri.host == "127.0.0.1" && uri.port in 1024..65535 &&
            uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            uri.rawPath in setOf("/app-debug.apk", "/app-release.apk")
    }.getOrDefault(false)

    fun validToken(value: String): Boolean = value.matches(Regex("[a-f0-9]{64}"))

    fun validSize(size: Long): Boolean = size in 1..MAX_APK_BYTES

    fun sameCertificate(actual: String, expected: String): Boolean {
        val normalized = actual.replace(":", "").lowercase()
        val trusted = expected.replace(":", "").lowercase()
        return trusted.matches(Regex("[a-f0-9]{64}")) && normalized == trusted
    }
}
