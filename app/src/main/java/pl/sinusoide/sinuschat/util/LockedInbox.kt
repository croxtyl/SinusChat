package pl.sinusoide.sinuschat.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray

object LockedInbox {

    private const val TAG   = "LockedInbox"
    private const val PREFS = "sinuschat_locked_inbox"
    private const val KEY   = "frames"
    private const val MAX   = 300

    private fun prefs(ctx: Context): SharedPreferences = try {
        pl.sinusoide.sinuschat.crypto.SecurePrefs.get(ctx, PREFS, listOf(KEY))
    } catch (e: Exception) {
        Log.w(TAG, "Szyfrowany magazyn niedostępny: ${e.message}")
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    @Synchronized
    fun add(ctx: Context, frameJson: String) {
        try {
            val p = prefs(ctx)
            val arr = read(p)
            arr.put(frameJson)
            val trimmed = if (arr.length() <= MAX) arr else JSONArray().also { out ->
                for (i in arr.length() - MAX until arr.length()) out.put(arr.getString(i))
            }
            p.edit().putString(KEY, trimmed.toString()).apply()
        } catch (e: Exception) { Log.w(TAG, "add: ${e.message}") }
    }

    @Synchronized
    fun drain(ctx: Context): List<String> {
        return try {
            val p = prefs(ctx)
            val arr = read(p)
            if (arr.length() == 0) return emptyList()
            p.edit().remove(KEY).apply()
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) { Log.w(TAG, "drain: ${e.message}"); emptyList() }
    }

    fun size(ctx: Context): Int = try { read(prefs(ctx)).length() } catch (_: Exception) { 0 }

    private const val KEY_NOTIFIED = "notified_ids"

    @Synchronized
    fun markNotified(ctx: Context, messageId: String) {
        try {
            val p = prefs(ctx)
            val arr = try { JSONArray(p.getString(KEY_NOTIFIED, "[]")) } catch (_: Exception) { JSONArray() }
            arr.put(messageId)
            val trimmed = if (arr.length() <= MAX) arr else JSONArray().also { out ->
                for (i in arr.length() - MAX until arr.length()) out.put(arr.getString(i))
            }
            p.edit().putString(KEY_NOTIFIED, trimmed.toString()).apply()
        } catch (_: Exception) {}
    }

    @Synchronized
    fun consumeNotified(ctx: Context, messageId: String): Boolean {
        return try {
            val p = prefs(ctx)
            val raw = p.getString(KEY_NOTIFIED, null) ?: return false
            val arr = JSONArray(raw)
            val out = JSONArray()
            var found = false
            for (i in 0 until arr.length()) {
                val id = arr.getString(i)
                if (!found && id == messageId) found = true else out.put(id)
            }
            if (found) p.edit().putString(KEY_NOTIFIED, out.toString()).apply()
            found
        } catch (_: Exception) { false }
    }

    private fun read(p: SharedPreferences): JSONArray =
        try { JSONArray(p.getString(KEY, "[]") ?: "[]") } catch (_: Exception) { JSONArray() }
}
