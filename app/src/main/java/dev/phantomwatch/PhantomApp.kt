package dev.phantomwatch

import android.app.Application
import dev.phantomwatch.core.AppLog
import dev.phantomwatch.core.CrashReport
import dev.phantomwatch.core.Log
import dev.phantomwatch.core.RemoteConfig
import dev.phantomwatch.data.Chart
import dev.phantomwatch.data.TmdbHost
import dev.phantomwatch.player.TrailerBridge
import dev.phantomwatch.provider.JsProviders
import dev.phantomwatch.social.Social

class PhantomApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReport.install(this)
        AppLog.init(this)
        // app wide not per activity, an appfunction call wakes service with no activity
        RemoteConfig.load(this)
        Log.sink = AppLog::d
        TmdbHost.key = RemoteConfig.tmdbKey()
        TmdbHost.base = RemoteConfig.tmdbBase()
        TmdbHost.image = RemoteConfig.tmdbImage()
        Chart.url = BuildConfig.TELEMETRY_URL.removeSuffix("/e")
        Chart.key = BuildConfig.TELEMETRY_KEY
        JsProviders.init(this)
        TrailerBridge.init(this)
        Social.init(this)
        // the signed config carries the provider legs, so a stale one means dead sources
        Thread { RemoteConfig.refresh(this) }.start()
    }
}
