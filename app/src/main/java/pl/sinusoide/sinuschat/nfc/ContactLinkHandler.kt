package pl.sinusoide.sinuschat.nfc

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import pl.sinusoide.sinuschat.messenger.ChatActivity
import pl.sinusoide.sinuschat.messenger.ContactQr
import pl.sinusoide.sinuschat.messenger.GroupInviteManager
import pl.sinusoide.sinuschat.messenger.InviteManager
import pl.sinusoide.sinuschat.messenger.QrLinkActivity
import pl.sinusoide.sinuschat.setup.SetupActivity

object ContactLinkHandler {

    enum class Result { OPENED, INVALID, OWN_CODE, EMPTY }

    fun extractToken(raw: String): String? = when {
        raw.contains("/add/") -> raw.substringAfter("/add/").substringBefore("?").substringBefore("/")
        raw.startsWith("sinuschat://add/") -> raw.removePrefix("sinuschat://add/")
        else -> null
    }

    fun handle(context: Context, raw: String, finishAfter: Activity? = null): Result {
        if (raw.isBlank()) return Result.EMPTY
        val token = extractToken(raw) ?: return Result.INVALID
        if (GroupInviteManager.isGroupInviteToken(token)) {
            context.startActivity(Intent(context, QrLinkActivity::class.java).apply {
                data = Uri.parse("sinuschat://add/$token")
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            finishAfter?.finish()
            return Result.OPENED
        }
        if (InviteManager.isInviteToken(token)) {
            context.startActivity(Intent(context, QrLinkActivity::class.java).apply {
                data = Uri.parse("sinuschat://add/$token")
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            finishAfter?.finish()
            return Result.OPENED
        }
        val decoded = ContactQr.decode(token) ?: return Result.INVALID
        val (hash, name) = decoded

        val myHash = SetupActivity.getUserPhoneHash(context)
        if (hash == myHash) return Result.OWN_CODE

        ContactQr.remember(context, token)
        val convId = if (myHash < hash) "${myHash}_${hash}" else "${hash}_${myHash}"
        val intent = Intent(context, ChatActivity::class.java).apply {
            putExtra("conversationId", convId)
            putExtra("otherUserHash", hash)
            putExtra("otherUserName", name)
            putExtra("weStarted", true)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        finishAfter?.finish()
        return Result.OPENED
    }
}
