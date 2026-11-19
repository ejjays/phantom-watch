package dev.phantomwatch

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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phantomwatch.core.UpdateTrust
import dev.phantomwatch.player.PhantomDownload
import dev.phantomwatch.ui.Bg
import dev.phantomwatch.ui.Cyan
import dev.phantomwatch.ui.PhantomTypography
import dev.phantomwatch.ui.Slate100
import dev.phantomwatch.ui.Slate300
import dev.phantomwatch.ui.Slate600
import dev.phantomwatch.ui.SurfaceColor
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

// app downloads and commits session itself, installer cant read content:// apk
class UpdateInstallActivity : ComponentActivity() {
    private val progress = mutableFloatStateOf(0f)
    private val ready = mutableStateOf(false)
    private val installing = mutableStateOf(false)
    private var apk: File? = null
    private var expectedPackage = ""
    private var started = false
    private val permission: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (packageManager.canRequestPackageInstalls()) begin() else finishWith("install appop denied")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        expectedPackage = intent.getStringExtra("packageName")?.takeIf(String::isNotBlank) ?: packageName
        alive = WeakReference(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(), typography = PhantomTypography()) {
                UpdateProgress(
                    progress.floatValue, ready.value, installing.value,
                    expectedPackage == packageName, ::install,
                )
            }
        }
        log("start appop=${packageManager.canRequestPackageInstalls()}")
        begin()
    }

    private fun begin() {
        if (started) return
        val url = intent.getStringExtra("url").orEmpty()
        val token = intent.getStringExtra("token").orEmpty()
        val local = UpdateTrust.localEntry(url, token, BuildConfig.DEBUG)
        if (!local && !UpdateTrust.releaseEntry(url)) return finishWith("rejected update url")
        if (!packageManager.canRequestPackageInstalls()) {
            Toast.makeText(this, getString(R.string.update_allow_install), Toast.LENGTH_LONG).show()
            runCatching {
                permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            }.onFailure { finishWith("no installer settings") }
            return
        }
        started = true
        window.decorView.postDelayed({ finish() }, 300_000)
        Thread {
            runCatching { download(url, token, local) }
                .onSuccess { file ->
                    apk = file
                    runOnUiThread {
                        progress.floatValue = 1f
                        if (expectedPackage == packageName) ready.value = true else install()
                    }
                }
                .onFailure { result(it) }
        }.start()
    }

    override fun onDestroy() {
        if (alive?.get() === this) alive = null
        super.onDestroy()
    }

    private fun install() {
        val file = apk ?: return
        ready.value = false
        installing.value = true
        Thread {
            runCatching { commit(file) }.onFailure { result(it) }
        }.start()
    }

    private fun download(url: String, token: String, local: Boolean): File {
        check(if (local) UpdateTrust.localEntry(url, token, true) else UpdateTrust.releaseEntry(url)) {
            "invalid update url"
        }
        val conn = open(url, if (local) token else null)
        try {
            check(conn.responseCode == 200) { "server said ${conn.responseCode}" }
            val expectedSize = conn.contentLengthLong
            check(UpdateTrust.validSize(expectedSize)) { "invalid apk size" }
            val declared = intent.getLongExtra("size", 0L)
            check(declared <= 0 || declared == expectedSize) { "apk size does not match release" }
            val cache = File(cacheDir, "update.apk")
            var total = 0L
            var shown = -1
            conn.inputStream.use { input ->
                FileOutputStream(cache).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        check(UpdateTrust.validSize(total)) { "apk exceeds size limit" }
                        output.write(buffer, 0, n)
                        val pct = (total * 100 / expectedSize).toInt()
                        if (pct != shown) {
                            shown = pct
                            runOnUiThread { progress.floatValue = pct / 100f }
                        }
                    }
                }
            }
            check(total == expectedSize) { "incomplete apk download" }
            verifyApk(cache)
            log("verified update ${cache.length()} bytes for $expectedPackage")
            return cache
        } finally {
            conn.disconnect()
        }
    }

    private fun commit(cache: File) {
        val installer = packageManager.packageInstaller
        // open session from crashed attempt blocks next create, committed one may still have confirm dialog on screen so leave alone
        runCatching { installer.mySessions.filter { it.isActive }.forEach { runCatching { installer.abandonSession(it.sessionId) } } }
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
                .putExtra("packageName", expectedPackage)
                .putExtra("job", intent.getStringExtra("job"))
            session.commit(
                PendingIntent.getBroadcast(
                    this, id, callback, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ).intentSender,
            )
        }
        log("committed verified session $id")
    }

    private fun open(url: String, token: String?): HttpURLConnection {
        var current = url
        repeat(4) {
            val conn = URL(current).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 5000
            conn.readTimeout = 120000
            if (token != null) conn.setRequestProperty("X-PhantomWatch-Update", token)
            if (conn.responseCode !in 300..399) return conn
            val next = conn.getHeaderField("Location") ?: error("redirect without location")
            conn.disconnect()
            check(token == null) { "unexpected redirect" }
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
        check(update.packageName == expectedPackage) { "wrong package" }
        val installed = runCatching { packageManager.getPackageInfo(expectedPackage, flags) }.getOrNull()
        val updateSigners = signers(update) ?: error("update has no signer")
        if (installed != null) {
            check(versionCode(update) >= versionCode(installed)) { "update is older than installed app" }
            val installedSigners = signers(installed) ?: error("installed app has no signer")
            check(updateSigners.isNotEmpty() && updateSigners == installedSigners) {
                "update signer does not match installed app"
            }
        }
        if (expectedPackage == packageName && !BuildConfig.DEBUG) {
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

    private fun result(error: Throwable) {
        log("failed: ${error.message ?: error.javaClass.simpleName}")
        runOnUiThread {
            Toast.makeText(this, R.string.update_failed, Toast.LENGTH_LONG).show()
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
                if (!BuildConfig.DEBUG || Build.VERSION.SDK_INT < 29) return
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

@Composable
private fun UpdateProgress(progress: Float, ready: Boolean, installing: Boolean, selfUpdate: Boolean, onInstall: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = RoundedCornerShape(24.dp), color = SurfaceColor) {
            Column(Modifier.width(300.dp).padding(24.dp)) {
                Text(
                    stringResource(
                        when {
                            ready -> R.string.update_ready
                            selfUpdate -> R.string.update_progress_title
                            else -> R.string.phantom_progress_title
                        }
                    ),
                    color = Slate100,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(18.dp))
                when {
                    ready -> {
                        Text(
                            stringResource(R.string.update_will_close),
                            color = Slate300,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(18.dp))
                        Button(
                            onClick = onInstall,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Bg),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                        ) {
                            Text(
                                stringResource(R.string.update_install),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    installing -> {
                        Text(
                            stringResource(R.string.update_progress_installing),
                            color = Slate300,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = Cyan,
                            trackColor = Slate600,
                        )
                    }
                    else -> {
                        Text(
                            stringResource(R.string.update_progress_downloading, (progress * 100).toInt()),
                            color = Slate300,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = Cyan,
                            trackColor = Slate600,
                        )
                    }
                }
            }
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
        if (status != PackageInstaller.STATUS_SUCCESS) {
            Toast.makeText(context, context.getString(resultText(status)), Toast.LENGTH_LONG).show()
        } else if (intent.getStringExtra("packageName").orEmpty() != context.packageName) {
            val job = intent.getStringExtra("job")
            if (job.isNullOrBlank()) {
                Toast.makeText(context, R.string.phantom_downloader_installed, Toast.LENGTH_LONG).show()
            } else {
                PhantomDownload.installed(job)
            }
        }
        UpdateInstallActivity.finishAlive()
    }

    private fun resultText(status: Int): Int = when (status) {
        PackageInstaller.STATUS_FAILURE_ABORTED -> R.string.update_cancelled
        PackageInstaller.STATUS_FAILURE_STORAGE -> R.string.update_no_space
        else -> R.string.update_failed
    }
}
