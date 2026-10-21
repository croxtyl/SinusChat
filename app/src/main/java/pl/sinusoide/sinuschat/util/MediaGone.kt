package pl.sinusoide.sinuschat.util

import android.content.Context

object MediaGone {

    private const val PREFS = "sinuschat_media_gone"
    private const val KEY = "ids"
    private const val MAX = 3000

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isGone(context: Context, messageId: String): Boolean =
        messageId.isNotEmpty() && prefs(context).getStringSet(KEY, null)?.contains(messageId) == true

    fun mark(context: Context, messageId: String) {
        if (messageId.isEmpty()) return
        val p = prefs(context)
        val set = HashSet(p.getStringSet(KEY, null).orEmpty())
        if (!set.add(messageId)) return
        if (set.size > MAX) set.clear().also { set.add(messageId) }
        p.edit().putStringSet(KEY, set).apply()
    }
}
