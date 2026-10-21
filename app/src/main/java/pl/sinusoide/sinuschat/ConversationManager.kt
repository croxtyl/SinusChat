package pl.sinusoide.sinuschat

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class Conversation(
    val id       : String,
    val title    : String,
    val timestamp: Long,
    val messages : List<ChatMessage>,
    val modelId  : String = ""
)

object ConversationManager {

    private const val PREFS_NAME  = "sinia_conversations"
    private const val KEY_LIST    = "conv_list"
    private const val MAX_CONVS   = 50

    fun saveConversation(ctx: Context, id: String, modelId: String, messages: List<ChatMessage>) {
        if (messages.isEmpty()) return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val title = messages.firstOrNull { it.role == ChatMessage.Role.USER }?.text
            ?.take(50) ?: "Rozmowa"

        val msgsJson = JSONArray()
        for (m in messages) {
            msgsJson.put(JSONObject().apply {
                put("role",    m.role.name)
                put("text",    m.text)
                put("emotion", m.emotion.name)
            })
        }

        val conv = JSONObject().apply {
            put("id",        id)
            put("model",     modelId)
            put("title",     title)
            put("timestamp", System.currentTimeMillis())
            put("messages",  msgsJson)
        }

        val list = loadRawList(ctx).toMutableList()
        val existing = list.indexOfFirst { it.getString("id") == id }
        if (existing >= 0) list[existing] = conv else list.add(0, conv)

        val trimmed = list.take(MAX_CONVS)
        val arr = JSONArray(); trimmed.forEach { arr.put(it) }
        prefs.edit().putString(KEY_LIST, arr.toString()).apply()
    }

    fun loadAll(ctx: Context, modelId: String? = null): List<Conversation> {
        return loadRawList(ctx)
            .filter { modelId == null || it.optString("model", "") == modelId }
            .map { obj ->
                val msgs = mutableListOf<ChatMessage>()
                val msgsArr = obj.optJSONArray("messages") ?: JSONArray()
                for (i in 0 until msgsArr.length()) {
                    val m = msgsArr.getJSONObject(i)
                    msgs.add(ChatMessage(
                        role    = try { ChatMessage.Role.valueOf(m.optString("role", "SINIA")) } catch (_: Exception) { ChatMessage.Role.SINIA },
                        text    = m.optString("text", ""),
                        emotion = try { ChatMessage.Emotion.valueOf(m.optString("emotion","NORMAL")) } catch (_: Exception) { ChatMessage.Emotion.NORMAL }
                    ))
                }
                Conversation(
                    id        = obj.optString("id", ""),
                    title     = obj.optString("title", "Rozmowa"),
                    timestamp = obj.optLong("timestamp", 0L),
                    messages  = msgs,
                    modelId   = obj.optString("model", "")
                )
            }
    }

    fun delete(ctx: Context, id: String) {
        save(ctx, loadRawList(ctx).filter { it.getString("id") != id })
    }

    fun deleteForModel(ctx: Context, modelId: String) {
        save(ctx, loadRawList(ctx).filter { it.optString("model", "") != modelId })
    }

    fun migrateLegacy(ctx: Context, modelId: String) {
        val list = loadRawList(ctx)
        val legacy = list.filter { it.optString("model", "").isEmpty() }
        if (legacy.isEmpty()) return
        legacy.forEach { it.put("model", modelId) }
        save(ctx, list)
    }

    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun save(ctx: Context, list: List<JSONObject>) {
        val arr = JSONArray(); list.forEach { arr.put(it) }
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LIST, arr.toString()).apply()
    }

    private fun loadRawList(ctx: Context): List<JSONObject> {
        val raw = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LIST, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (_: Exception) { emptyList() }
    }
}
