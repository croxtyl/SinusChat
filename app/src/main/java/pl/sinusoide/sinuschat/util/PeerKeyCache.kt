package pl.sinusoide.sinuschat.util

import android.content.Context

object PeerKeyCache {

    private const val PREFS = "sinuschat_peer_keys"

    const val ACTION_CHANGED = "pl.sinusoide.sinuschat.PEER_KEY_CHANGED"

    fun getKey(context: Context, hash: String): String? {
        if (hash.isEmpty()) return null
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("k_$hash", null)
    }

    fun saveKey(context: Context, hash: String, key: String?): Boolean {
        if (hash.isEmpty() || key.isNullOrEmpty()) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getString("k_$hash", null)

        val changed = previous != null && previous != key

        val edit = prefs.edit()
            .putString("k_$hash", key)
            .putLong("t_$hash", System.currentTimeMillis())
        if (changed) {
            edit.putBoolean("changed_$hash", true)
            edit.putString("prevkey_$hash", previous)
        }
        edit.apply()
        if (changed || previous == null) {
            try {
                context.sendBroadcast(android.content.Intent(ACTION_CHANGED)
                    .setPackage(context.packageName).putExtra("hash", hash))
            } catch (_: Exception) {}
        }
        if (changed) {
            try {
                for (gid in GroupRoster.groupsOf(context, hash)) GroupSystemMessages.keyChanged(context, gid, hash, key.hashCode())
            } catch (_: Exception) {}
        }
        return changed
    }

    fun acknowledgeKeyChange(context: Context, hash: String) {
        if (hash.isEmpty()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove("changed_$hash").remove("prevkey_$hash").apply()
    }

    fun clear(context: Context, hash: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove("k_$hash").remove("t_$hash").apply()
    }
}
