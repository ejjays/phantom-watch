package dev.phantomwatch

import android.app.Application
import dev.phantomwatch.core.CrashReport

class PhantomApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReport.install(this)
    }
}
