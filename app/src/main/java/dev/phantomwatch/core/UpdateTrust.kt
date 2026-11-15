package dev.phantomwatch.core

import java.net.URI

internal object UpdateTrust {
    private const val MAX_APK_BYTES = 200L * 1024 * 1024
    private val RELEASE_HOSTS = setOf(
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
    )

    // scripts/install.sh serves a build on loopback, only a debug app takes that
    // path and a release app answers false here whatever it is handed
    fun localEntry(url: String, token: String, allowLocal: Boolean): Boolean =
        allowLocal && token.matches(Regex("[a-f0-9]{64}")) && runCatching {
            val uri = URI(url)
            uri.scheme == "http" && uri.host == "127.0.0.1" && uri.port in 1024..65535 &&
                uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
                uri.rawPath in setOf("/app-debug.apk", "/app-release.apk")
        }.getOrDefault(false)

    fun releaseEntry(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && uri.host == "github.com" && uri.rawUserInfo == null &&
            uri.rawQuery == null && uri.rawFragment == null &&
            uri.path.startsWith("/ejjays/phantom-watch/releases/download/") &&
            uri.path.endsWith(".apk")
    }.getOrDefault(false)

    // redirects land on signed cdn url with no file name in path, only host checked
    fun releaseHop(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && uri.host in RELEASE_HOSTS && uri.rawUserInfo == null
    }.getOrDefault(false)

    fun validSize(size: Long): Boolean = size in 1..MAX_APK_BYTES

    fun sameCertificate(actual: String, expected: String): Boolean {
        val normalized = actual.replace(":", "").lowercase()
        val trusted = expected.replace(":", "").lowercase()
        return trusted.matches(Regex("[a-f0-9]{64}")) && normalized == trusted
    }
}
