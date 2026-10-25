package pl.sinusoide.sinuschat.util

import android.content.Context
import android.content.SharedPreferences
import pl.sinusoide.sinuschat.crypto.SecurePrefs

object Drafts {

    private const val PREFS = "sinuschat_drafts"
    private const val MAX_LEN = 20_000

    private fun prefs(ctx: Context): SharedPreferences? =
        try { SecurePrefs.get(ctx, PREFS) } catch (_: Exception) { null }

    fun get(ctx: Context, conversationId: String): String? {
        if (conversationId.isEmpty()) return null
        return prefs(ctx)?.getString(conversationId, null)?.takeIf { it.isNotBlank() }
    }

    fun save(ctx: Context, conversationId: String, text: String) {
        if (conversationId.isEmpty()) return
        val p = prefs(ctx) ?: return
        if (text.isBlank()) {
            if (p.contains(conversationId)) p.edit().remove(conversationId).apply()
            return
        }
        val value = text.take(MAX_LEN)
        if (p.getString(conversationId, null) != value) p.edit().putString(conversationId, value).apply()
    }

    fun clear(ctx: Context, conversationId: String) = save(ctx, conversationId, "")

    fun wipe(ctx: Context) {
        prefs(ctx)?.edit()?.clear()?.apply()
    }
}
