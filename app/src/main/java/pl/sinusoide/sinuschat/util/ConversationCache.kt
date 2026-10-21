package pl.sinusoide.sinuschat.util

import android.content.Context
import org.json.JSONArray

object ConversationCache {

    private const val PREFS = "sinuschat_conv_cache"
    private const val KEY = "last_conversations"

    fun save(context: Context, arr: JSONArray) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, arr.toString()).apply()
        } catch (_: Exception) {}
    }

    fun load(context: Context): JSONArray? = try {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?.let { JSONArray(it) }
    } catch (_: Exception) { null }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    fun remove(context: Context, conversationId: String) {
        try {
            val arr = load(context) ?: return
            val out = JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                if (o.optString("id") != conversationId) out.put(o)
            }
            save(context, out)
        } catch (_: Exception) {}
    }
}
