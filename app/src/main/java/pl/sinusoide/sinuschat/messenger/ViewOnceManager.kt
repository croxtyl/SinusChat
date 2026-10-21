package pl.sinusoide.sinuschat.messenger

import android.content.Context
import android.util.Log
import pl.sinusoide.sinuschat.data.SinusChatDatabase
import pl.sinusoide.sinuschat.setup.SetupActivity
import pl.sinusoide.sinuschat.util.MediaManager
import org.json.JSONObject

object ViewOnceManager {

    private const val TAG = "ViewOnce"

    const val KIND_OPENED = "view_once_opened"

    fun markOpenedLocally(context: Context, conversationId: String, messageId: String) {
        if (conversationId.isEmpty() || messageId.isEmpty()) return
        try {
            val dao = SinusChatDatabase.getInstance(context).messageDao()
            val msg = dao.getById(messageId) ?: return
            if (!msg.viewed) dao.update(msg.copy(viewed = true))
            MediaManager.deleteMessageMedia(context, conversationId, messageId)
        } catch (e: Exception) {
            Log.e(TAG, "markOpenedLocally: ${e.message}")
        }
    }

    fun notifySenderOpened(context: Context, conversationId: String, messageId: String) {
        try {
            val dao = SinusChatDatabase.getInstance(context).messageDao()
            val msg = dao.getById(messageId) ?: return
            val myHash = SetupActivity.getUserPhoneHash(context)
            if (myHash.isEmpty() || msg.senderHash == myHash) return

            val payload = JSONObject()
                .put("senderHash", myHash)
                .put("targetMessageId", messageId)
                .put("timestamp", System.currentTimeMillis())

            val body = JSONObject().apply {
                put("conversationId", conversationId)
                put("messageId", java.util.UUID.randomUUID().toString())
                put("kind", KIND_OPENED)
                put("recipientHash", msg.senderHash)
                put("payloadB64", android.util.Base64.encodeToString(
                    payload.toString().toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP))
            }
            pl.sinusoide.sinuschat.crypto.SealedSender.post(context, body)
        } catch (e: Exception) {
            Log.e(TAG, "notifySenderOpened: ${e.message}")
        }
    }
}
