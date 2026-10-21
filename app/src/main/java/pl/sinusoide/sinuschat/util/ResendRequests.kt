package pl.sinusoide.sinuschat.util

import android.content.Context
import java.security.MessageDigest

object ResendRequests {

    private const val PREFS = "sinuschat_resend_requests"
    private const val TTL_MS = 3 * 24 * 3600_000L

    @Synchronized
    fun remember(ctx: Context, msgId: String, convId: String, senderHash: String): Boolean {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = h(msgId)
        if (prefs.contains(key)) return false
        val now = System.currentTimeMillis()
        val edit = prefs.edit()
        for ((k, v) in prefs.all) {
            val at = (v as? String)?.substringAfter('|', "")?.toLongOrNull() ?: 0L
            if (now - at > TTL_MS) edit.remove(k)
        }
        edit.putString(key, h("$convId|$senderHash") + "|" + now).apply()
        return true
    }

    @Synchronized
    fun take(ctx: Context, origId: String, convId: String, senderHash: String): Boolean {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = h(origId)
        val v = prefs.getString(key, null) ?: return false
        if (v.substringBefore('|') != h("$convId|$senderHash")) return false
        prefs.edit().remove(key).apply()
        return true
    }

    private fun h(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(32)
}
