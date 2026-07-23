package dev.phantomwatch

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.common.api.Status
import com.google.android.gms.media.effect.enhancement.Enhancement
import com.google.android.gms.media.effect.enhancement.EnhancementCallback
import com.google.android.gms.media.effect.enhancement.EnhancementClient
import com.google.android.gms.media.effect.enhancement.EnhancementMode
import com.google.android.gms.media.effect.enhancement.EnhancementOptions
import com.google.android.gms.media.effect.enhancement.EnhancementSession
import com.google.android.gms.media.effect.enhancement.EnhancementSessionCallback
import dev.phantomwatch.core.AppLog

object Enhance {
    fun options(w: Int, h: Int) =
        EnhancementOptions(w, h, EnhancementMode.SURFACE, true, false, true, false, true)

    fun probe(ctx: Context, done: (Boolean) -> Unit) {
        try {
            val c = Enhancement.getClient(ctx)
            c.isDeviceSupported().addOnSuccessListener { ok ->
                if (ok != true) {
                    AppLog.d("enh unsupported device")
                    done(false)
                    return@addOnSuccessListener
                }
                c.isModuleInstalled().addOnSuccessListener { inst ->
                    if (inst == true) {
                        AppLog.d("enh module ready")
                        done(true)
                    } else {
                        AppLog.d("enh downloading models")
                        c.installModule(installLog).addOnSuccessListener { done(it == true) }
                            .addOnFailureListener { AppLog.d("enh install fail $it"); done(false) }
                    }
                }.addOnFailureListener { done(false) }
            }.addOnFailureListener { done(false) }
        } catch (e: Exception) {
            AppLog.d("enh probe fail ${e.message}")
            done(false)
        }
    }

    fun open(ctx: Context, w: Int, h: Int, done: (EnhancementSession?) -> Unit) {
        try {
            Enhancement.getClient(ctx).createSession(options(w, h), object : EnhancementSessionCallback {
                override fun onSessionCreated(s: EnhancementSession) {
                    AppLog.d("enh session ${w}x$h")
                    done(s)
                }

                override fun onSessionCreationFailed(s: Status) {
                    AppLog.d("enh create fail $s")
                    done(null)
                }

                override fun onSessionDisconnected(s: Status) {
                    AppLog.d("enh disconnected $s")
                }

                override fun onSessionDestroyed() {
                    AppLog.d("enh destroyed")
                }
            }).addOnFailureListener {
                AppLog.d("enh task fail $it")
                done(null)
            }
        } catch (e: Exception) {
            AppLog.d("enh open fail ${e.message}")
            done(null)
        }
    }

    fun frames(): EnhancementCallback = object : EnhancementCallback {
        var n = 0

        override fun onSurfaceProcessed(timestamp: Long) {
            if (n++ == 0) AppLog.d("enh flowing")
        }

        override fun onBitmapProcessed(bitmap: Bitmap) {}

        override fun onError(code: Int) {
            AppLog.d("enh frame err $code")
        }

        override fun onCancelled(code: Int) {
            AppLog.d("enh cancelled $code")
        }
    }

    private val installLog = object : EnhancementClient.InstallStatusCallback {
        override fun onError(e: String) {
            AppLog.d("enh dl err $e")
        }

        override fun onCancelled() {
            AppLog.d("enh dl cancelled")
        }

        override fun onDownloadProgressUpdate(p: Int) {
            AppLog.d("enh dl $p%")
        }

        override fun onDownloadPending() {
            AppLog.d("enh dl pending")
        }

        override fun onDownloadStart() {
            AppLog.d("enh dl start")
        }

        override fun onDownloadPaused() {
            AppLog.d("enh dl paused")
        }

        override fun onDownloadComplete() {
            AppLog.d("enh dl done")
        }

        override fun onInstalled() {
            AppLog.d("enh installed")
        }
    }
}
