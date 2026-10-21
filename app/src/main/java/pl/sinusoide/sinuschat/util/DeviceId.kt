package pl.sinusoide.sinuschat.util

import android.content.Context
import java.util.UUID

object DeviceId {

    private const val PREFS = "sinuschat_device"
    private const val KEY_DEVICE_ID = "device_id"

    fun get(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id.isNullOrEmpty()) {
            id = UUID.randomUUID().toString().replace("-", "").take(32)
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }
}
