package dev.phantomwatch.core

import android.content.Context

fun isWifi(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        ?: return true
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
}
