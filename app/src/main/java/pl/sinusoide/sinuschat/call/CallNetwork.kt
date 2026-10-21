package pl.sinusoide.sinuschat.call

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object CallNetwork {

    const val OFFLINE = "offline"

    private fun caps(ctx: Context): NetworkCapabilities? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return null
        val net = cm.activeNetwork ?: return null
        return cm.getNetworkCapabilities(net)
    }

    fun online(ctx: Context): Boolean = caps(ctx)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

    fun onWifi(ctx: Context): Boolean = caps(ctx)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

    fun offlineLimitMs(ctx: Context): Long {
        val c = caps(ctx) ?: return 3_000L
        if (!c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return 3_000L
        return if (c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) 15_000L else 5_000L
    }
}
