package dev.phantomwatch

import android.app.Application

class PhantomApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReport.install(this)
    }
}
