package dev.phantomwatch

import android.app.Application
import dev.phantomwatch.core.AppLog
import dev.phantomwatch.core.CrashReport
import dev.phantomwatch.core.Log
import dev.phantomwatch.core.RemoteConfig
import dev.phantomwatch.data.Settings
import dev.phantomwatch.data.TmdbHost
import dev.phantomwatch.player.TrailerBridge
import dev.phantomwatch.provider.JsProviders
import dev.phantomwatch.social.Social

class PhantomApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReport.install(this)
        // app-wide, not per-activity: an AppFunction call wakes the service with no activity at all
        RemoteConfig.load(this)
        Log.sink = AppLog::d
        TmdbHost.key = RemoteConfig.tmdbKey()
        TmdbHost.base = RemoteConfig.tmdbBase()
        TmdbHost.image = RemoteConfig.tmdbImage()
        JsProviders.init(this)
        TrailerBridge.init(this)
        Social.init(this)
        if (Settings.on(this, Settings.AUTO_REFRESH, true)) {
            Thread { RemoteConfig.refresh(this) }.start()
        }
    }
}
