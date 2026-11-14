package dev.phantomwatch

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import dev.phantomwatch.core.UpdateTrust
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

// the app downloads and commits the session itself: the installer can't read a content:// apk
class UpdateInstallActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        alive = WeakReference(this)
        log("start appop=${packageManager.canRequestPackageInstalls()}")
        val url = intent.getStringExtra("url").orEmpty()
        if (!UpdateTrust.releaseEntry(url)) return finishWith("rejected release url")
        if (!packageManager.canRequestPackageInstalls()) {
            Toast.makeText(this, getString(R.string.update_allow_install), Toast.LENGTH_LONG).show()
            runCatching { startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))) }
            return finishWith("no install appop")
        }
        window.decorView.postDelayed({ finish() }, 300_000)
        Thread {
            runCatching { commit(url) }.onFailure {
                result(it.message ?: it.javaClass.simpleName)
            }
        }.start()
    }

    override fun onDestroy() {
        if (alive?.get() === this) alive = null
        super.onDestroy()
    }

    private fun commit(url: String) {
        check(UpdateTrust.releaseEntry(url)) { "invalid release url" }
        val conn = open(url)
        try {
            check(conn.responseCode == 200) { "server said ${conn.responseCode}" }
            val expectedSize = conn.contentLengthLong
            check(UpdateTrust.validSize(expectedSize)) { "invalid apk size" }
            val declared = intent.getLongExtra("size", 0L)
            check(declared <= 0 || declared == expectedSize) { "apk size does not match release" }
            val cache = File(cacheDir, "update.apk")
            var total = 0L
            conn.inputStream.use { input ->
                FileOutputStream(cache).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        check(UpdateTrust.validSize(total)) { "apk exceeds size limit" }
                        output.write(buffer, 0, n)
                    }
                }
            }
            check(total == expectedSize) { "incomplete apk download" }
            verifyApk(cache)
            log("verified update ${cache.length()} bytes for $packageName")
            val installer = packageManager.packageInstaller
            // A session left waiting for a tap blocks the next one once the system cap is hit
            runCatching { installer.mySessions.forEach { runCatching { installer.abandonSession(it.sessionId) } } }
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setSize(cache.length())
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                cache.inputStream().use { input ->
                    session.openWrite("base.apk", 0, cache.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val callback = Intent(this, UpdateResultReceiver::class.java)
                session.commit(
                    PendingIntent.getBroadcast(
                        this, id, callback, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ).intentSender,
                )
            }
            log("committed verified session $id")
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection {
        var current = url
        repeat(4) {
            val conn = URL(current).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 5000
            conn.readTimeout = 120000
            if (conn.responseCode !in 300..399) return conn
            val next = conn.getHeaderField("Location") ?: error("redirect without location")
            conn.disconnect()
            check(UpdateTrust.releaseHop(next)) { "redirect left github" }
            current = next
        }
        error("too many redirects")
    }

    private fun verifyApk(apk: File): String {
        check(UpdateTrust.validSize(apk.length())) { "invalid apk size" }
        val flags = if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val update = packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: error("invalid apk")
        check(update.packageName == packageName) { "wrong package" }
        val installed = packageManager.getPackageInfo(packageName, flags)
        check(versionCode(update) >= versionCode(installed)) { "update is older than installed app" }
        val updateSigners = signers(update) ?: error("update has no signer")
        val installedSigners = signers(installed) ?: error("installed app has no signer")
        check(updateSigners.isNotEmpty() && updateSigners == installedSigners) {
            "update signer does not match installed app"
        }
        if (!BuildConfig.DEBUG) {
            val fingerprint = updateSigners.singleOrNull()?.let(::fingerprint).orEmpty()
            check(BuildConfig.UPDATE_CERT_SHA256.isNotBlank() &&
                UpdateTrust.sameCertificate(fingerprint, BuildConfig.UPDATE_CERT_SHA256)
            ) {
                "update signer is not the pinned production certificate"
            }
        }
        val appInfo = update.applicationInfo ?: error("update has no application info")
        appInfo.sourceDir = apk.absolutePath
        appInfo.publicSourceDir = apk.absolutePath
        val label = appInfo.loadLabel(packageManager).toString().trim()
        check(label.isNotEmpty()) { "update has no app label" }
        return label
    }

    @Suppress("DEPRECATION")
    private fun versionCode(info: android.content.pm.PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun signers(info: android.content.pm.PackageInfo): Set<android.content.pm.Signature>? =
        if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners?.toSet() else info.signatures?.toSet()

    private fun fingerprint(signature: android.content.pm.Signature): String =
        MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun result(message: String) {
        log("failed: $message")
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun finishWith(reason: String) {
        log(reason)
        finish()
    }

    companion object {
        private var alive: WeakReference<UpdateInstallActivity>? = null
        private var appContext: Context? = null
        private var logUri: Uri? = null
        private val logLines = StringBuilder()

        fun log(text: String) {
            synchronized(logLines) {
                logLines.append(System.currentTimeMillis()).append(' ').append(text).append('\n')
                if (Build.VERSION.SDK_INT < 29) return
                val context = alive?.get()?.applicationContext?.also { appContext = it } ?: appContext ?: return
                runCatching {
                    val uri = logUri ?: context.contentResolver.insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, "pw-update.log")
                            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        }
                    )?.also { logUri = it } ?: return
                    context.contentResolver.openOutputStream(uri)?.use { it.write(logLines.toString().toByteArray()) }
                }
            }
        }

        fun finishAlive() {
            alive?.get()?.finish()
        }
    }
}

class UpdateResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            UpdateInstallActivity.log("pending user action")
            @Suppress("DEPRECATION")
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            if (confirm != null) runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return
        }
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "status $status"
        UpdateInstallActivity.log("result $status $message")
        if (status != PackageInstaller.STATUS_SUCCESS) Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        UpdateInstallActivity.finishAlive()
    }
}
