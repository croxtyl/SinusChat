package pl.sinusoide.sinuschat.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object NetworkSettings {

    private const val PREFS = "sinuschat_network"
    private const val KEY_WIFI_ONLY = "download_wifi_only"
    private const val KEY_MOBILE_BYTES = "mobile_bytes_used"
    private const val KEY_MOBILE_SENT = "mobile_bytes_sent"
    private const val KEY_COUNTER_SINCE = "mobile_counter_since"

    enum class Connection { WIFI, MOBILE, OTHER, NONE }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isWifiOnly(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WIFI_ONLY, true)

    fun setWifiOnly(context: Context, wifiOnly: Boolean) {
        prefs(context).edit().putBoolean(KEY_WIFI_ONLY, wifiOnly).apply()
    }

    fun mobileBytesUsed(context: Context): Long =
        prefs(context).getLong(KEY_MOBILE_BYTES, 0L)

    fun mobileBytesSent(context: Context): Long =
        prefs(context).getLong(KEY_MOBILE_SENT, 0L)

    fun counterSince(context: Context): Long =
        prefs(context).getLong(KEY_COUNTER_SINCE, 0L)

    fun resetMobileCounter(context: Context) {
        prefs(context).edit()
            .putLong(KEY_MOBILE_BYTES, 0L)
            .putLong(KEY_MOBILE_SENT, 0L)
            .putLong(KEY_COUNTER_SINCE, System.currentTimeMillis())
            .apply()
    }

    fun recordReceived(context: Context, bytes: Long) = record(context, KEY_MOBILE_BYTES, bytes)
    fun recordSent(context: Context, bytes: Long) = record(context, KEY_MOBILE_SENT, bytes)

    @Synchronized
    private fun record(context: Context, key: String, bytes: Long) {
        if (bytes <= 0L) return
        try {
            if (!isMetered(context)) return
            val p = prefs(context)
            val ed = p.edit().putLong(key, p.getLong(key, 0L) + bytes)
            if (!p.contains(KEY_COUNTER_SINCE)) ed.putLong(KEY_COUNTER_SINCE, System.currentTimeMillis())
            ed.apply()
        } catch (_: Exception) {}
    }

    fun canDownloadNow(context: Context): Boolean {
        if (!isWifiOnly(context)) return true
        return isOnWifi(context)
    }

    fun isOnWifi(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    fun isMetered(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return false
        if (cm.activeNetwork == null) return false
        return cm.isActiveNetworkMetered
    }

    fun connection(context: Context): Connection {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return Connection.NONE
        val net = cm.activeNetwork ?: return Connection.NONE
        val caps = cm.getNetworkCapabilities(net) ?: return Connection.NONE
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Connection.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Connection.MOBILE
            else -> Connection.OTHER
        }
    }
}
