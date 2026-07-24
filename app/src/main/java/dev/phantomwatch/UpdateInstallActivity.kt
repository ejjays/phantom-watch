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
import java.io.File
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL

/** Installs an update served by install.sh on loopback: the app writes the session itself. */
class UpdateInstallActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        alive = WeakReference(this)
        log("start appop=${packageManager.canRequestPackageInstalls()}")
        val url = intent.getStringExtra("url").orEmpty()
        if (!url.startsWith("http://127.0.0.1:")) return finishWith("rejected url")
        if (!packageManager.canRequestPackageInstalls()) {
            Toast.makeText(this, "Allow Phantom Watch to install apps, then run install.sh again", Toast.LENGTH_LONG).show()
            runCatching { startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))) }
            return finishWith("no install appop")
        }
        window.decorView.postDelayed({ finish() }, 300_000)
        Thread {
            runCatching { commit(url) }.onFailure { result(it.message ?: it.javaClass.simpleName) }
        }.start()
    }

    override fun onDestroy() {
        if (alive?.get() === this) alive = null
        super.onDestroy()
    }

    private fun commit(url: String) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 120000
        check(conn.responseCode == 200) { "server said ${conn.responseCode}" }
        val cache = File(cacheDir, "update.apk")
        conn.inputStream.use { input -> cache.outputStream().use { input.copyTo(it) } }
        check(sameSigner(cache)) { "downloaded apk is not signed with our key" }
        log("downloaded ${cache.length()} bytes")
        val installer = packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setSize(cache.length())
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            cache.inputStream().use { input ->
                session.openWrite("base.apk", 0, cache.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            session.commit(
                PendingIntent.getBroadcast(this, id, Intent(this, UpdateResultReceiver::class.java), PendingIntent.FLAG_MUTABLE)
                    .intentSender
            )
        }
        log("committed session $id")
    }

    private fun sameSigner(apk: File): Boolean {
        val theirs = signers(packageManager.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNATURES)) ?: return false
        val ours = signers(packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)) ?: return false
        return theirs.any { candidate -> ours.any { it == candidate } }
    }

    @Suppress("DEPRECATION")
    private fun signers(info: android.content.pm.PackageInfo?): Array<android.content.pm.Signature>? = info?.signatures

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
