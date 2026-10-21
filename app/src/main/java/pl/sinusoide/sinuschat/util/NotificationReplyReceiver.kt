package pl.sinusoide.sinuschat.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import pl.sinusoide.sinuschat.data.SinusChatDatabase
import pl.sinusoide.sinuschat.messenger.GroupRepository
import pl.sinusoide.sinuschat.messenger.MessageRepository
import pl.sinusoide.sinuschat.messenger.ReplyLabel
import pl.sinusoide.sinuschat.setup.SetupActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NotificationReplyReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_REPLY = "pl.sinusoide.sinuschat.NOTIFICATION_REPLY"
        private const val TAG = "NotificationReply"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REPLY) return
        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(MessageNotifier.KEY_REPLY_TEXT)?.toString()?.trim().orEmpty()
        val convId = intent.getStringExtra("conversationId").orEmpty()
        val otherHash = intent.getStringExtra("otherHash").orEmpty()
        val messageId = intent.getStringExtra("messageId").orEmpty()
        if (convId.isEmpty()) return
        val app = context.applicationContext
        if (text.isEmpty() || !PrivacySettings.isNotificationReplyEnabled(app)) {
            MessageNotifier.afterReply(app, convId)
            return
        }
        val pending = goAsync()
        AppExecutor.io {
            try {
                if (send(app, convId, otherHash, messageId, text)) MessageNotifier.afterReply(app, convId)
                else MessageNotifier.showReplyFailed(app, convId, otherHash)
            } catch (e: Exception) {
                Log.w(TAG, "Odpowiedź z powiadomienia: ${e.message}")
                MessageNotifier.showReplyFailed(app, convId, otherHash)
            } finally {
                pending.finish()
            }
        }
    }

    private fun send(app: Context, convId: String, otherHash: String, messageId: String, text: String): Boolean {
        val me = SetupActivity.getUserPhoneHash(app)
        if (me.isEmpty()) return false
        val dao = SinusChatDatabase.getInstance(app).messageDao()
        val original = messageId.takeIf { it.isNotEmpty() }?.let { dao.getById(it) }?.takeIf { it.conversationId == convId }
        val replyText = original?.let { ReplyLabel.of(app, it) }
        if (GroupRepository.isGroup(convId)) {
            val replyId = original?.id?.removeSuffix("_" + me.take(16))
            val ok = GroupRepository.sendTextBlocking(app, convId, me, text, replyId, replyText)
            if (ok) MessageRepository.publishRead(app, convId, me, original?.timestampMs ?: System.currentTimeMillis())
            return ok
        }
        if (otherHash.length != 64) return false
        val latch = CountDownLatch(1)
        var secret: ByteArray? = null
        pl.sinusoide.sinuschat.crypto.E2EManager.getSharedSecretForUser(app, otherHash) { s ->
            secret = s
            latch.countDown()
        }
        latch.await(20, TimeUnit.SECONDS)
        val key = secret ?: return false
        MessageRepository.sendText(app, convId, me, otherHash, key, text,
            replyToId = original?.id, replyToText = replyText)
        MessageRepository.publishRead(app, convId, me, original?.timestampMs ?: System.currentTimeMillis())
        return true
    }
}
