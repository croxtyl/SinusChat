package pl.sinusoide.sinuschat.util

import android.content.Context
import org.json.JSONObject
import pl.sinusoide.sinuschat.R
import pl.sinusoide.sinuschat.data.MessageEntity
import pl.sinusoide.sinuschat.data.SinusChatDatabase
import pl.sinusoide.sinuschat.messenger.GroupRepository

object DisappearingCoordinator {

    const val ACTION_PROPOSE = "propose"
    const val ACTION_ACCEPT  = "accept"
    const val ACTION_DECLINE = "decline"

    fun propose(
        context: Context,
        conversationId: String,
        otherUserHash: String,
        ttlMs: Long,
        trigger: String
    ): Boolean {
        if (conversationId.isEmpty()) return false

        if (ttlMs == DisappearingMessages.getTtl(context, conversationId) &&
            trigger == DisappearingMessages.getTrigger(context, conversationId)) {
            DisappearingMessages.clearAwaitingConsent(context, conversationId)
            return true
        }

        if (GroupRepository.isGroup(conversationId) || ttlMs <= 0L) {
            applyEffective(context, conversationId, otherUserHash, ttlMs, trigger, announce = true)
            return true
        }

        DisappearingMessages.setAwaitingConsent(context, conversationId, ttlMs, trigger)
        sendSignal(context, conversationId, otherUserHash, ACTION_PROPOSE, ttlMs, trigger)
        insertSystemMessage(context, conversationId, context.getString(
            R.string.disappearing_proposed_by_me_sys_msg,
            DisappearingMessages.fullLabel(context, ttlMs, trigger)))
        return false
    }

    fun applyEffective(
        context: Context,
        conversationId: String,
        otherUserHash: String,
        ttlMs: Long,
        trigger: String,
        announce: Boolean
    ) {
        if (conversationId.isEmpty()) return
        DisappearingMessages.setTtl(context, conversationId, ttlMs, trigger)
        DisappearingMessages.clearAwaitingConsent(context, conversationId)
        DisappearingMessages.clearPending(context, conversationId)

        insertSystemMessage(context, conversationId, context.getString(
            R.string.disappearing_update_sys_msg,
            DisappearingMessages.fullLabel(context, ttlMs, trigger)))

        if (announce) sendSignal(context, conversationId, otherUserHash, ACTION_ACCEPT, ttlMs, trigger)
    }

    fun sendSignal(
        context: Context,
        conversationId: String,
        otherUserHash: String,
        action: String,
        ttlMs: Long,
        trigger: String
    ) {
        if (GroupRepository.isGroup(conversationId) || otherUserHash.isEmpty()) return
        val app = context.applicationContext
        AppExecutor.io {
            val payload = JSONObject()
                .put("action", action)
                .put("ttlMs", ttlMs)
                .put("trigger", trigger)
            val body = JSONObject().apply {
                put("conversationId", conversationId)
                put("messageId", java.util.UUID.randomUUID().toString())
                put("kind", "disappearing_update")
                put("recipientHash", otherUserHash)
                put("payloadB64", android.util.Base64.encodeToString(
                    payload.toString().toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP))
            }
            pl.sinusoide.sinuschat.crypto.SealedSender.post(app, body)
        }
    }

    fun insertSystemMessage(context: Context, conversationId: String, text: String) {
        val app = context.applicationContext
        val entity = MessageEntity(
            id = "sys_disappearing_${System.currentTimeMillis()}",
            conversationId = conversationId, senderHash = "",
            text = text, timestampMs = System.currentTimeMillis(),
            type = "sys_disappearing_change",
            isSystemMessage = true, status = MessageEntity.STATUS_READ
        )
        AppExecutor.io { SinusChatDatabase.getInstance(app).messageDao().insert(entity) }
    }
}
