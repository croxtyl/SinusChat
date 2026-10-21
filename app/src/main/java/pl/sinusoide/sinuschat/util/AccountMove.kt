package pl.sinusoide.sinuschat.util

import android.content.Context
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import org.signal.libsignal.protocol.ecc.ECPrivateKey
import org.signal.libsignal.protocol.ecc.ECPublicKey
import pl.sinusoide.sinuschat.crypto.E2EManager
import pl.sinusoide.sinuschat.crypto.ProfileCrypto
import pl.sinusoide.sinuschat.crypto.SignalStore
import pl.sinusoide.sinuschat.data.SinusChatDatabase
import pl.sinusoide.sinuschat.messenger.MessageRepository
import java.io.File

object AccountMove {

    private const val TAG = "AccountMove"
    const val KIND = "moved"

    private fun message(oldHash: String, newHash: String) = "SinusChat moved v1|$oldHash|$newHash".toByteArray()

    fun afterEmailChange(ctx: Context, oldHash: String, newHash: String) {
        val app = ctx.applicationContext
        val contacts = LocalChats.contacts(app, oldHash)
        val priv = E2EManager.myPrivateKeyBytes(app)
        val sig = priv?.let {
            try { Base64.encodeToString(ECPrivateKey(it).calculateSignature(message(oldHash, newHash)), Base64.NO_WRAP) }
            catch (e: Exception) { Log.w(TAG, "Podpis: ${e.message}"); null }
        }
        for (other in contacts) {
            val oldId = MessageRepository.conversationId(oldHash, other)
            val newId = MessageRepository.conversationId(newHash, other)
            renameConversation(app, oldId, newId, other)
            if (sig != null) MessageRepository.sendMovedBlocking(app, newId, newHash, other, oldHash, sig)
        }
        renameHashLocally(app, oldHash, newHash)
    }

    fun onMoved(ctx: Context, me: String, newHash: String, payload: JSONObject): Boolean {
        val oldHash = payload.optString("oldHash")
        val sig = payload.optString("sig")
        if (!Regex("[a-f0-9]{64}").matches(oldHash) || oldHash == newHash || sig.isEmpty()) return false
        val known = PeerKeyCache.getKey(ctx, oldHash) ?: return false
        val ok = try {
            ECPublicKey(Base64.decode(known, Base64.NO_WRAP))
                .verifySignature(message(oldHash, newHash), Base64.decode(sig, Base64.NO_WRAP))
        } catch (_: Exception) { false }
        if (!ok) { Log.w(TAG, "Podpis przeniesienia się nie zgadza — pomijam"); return false }
        val oldId = MessageRepository.conversationId(me, oldHash)
        val newId = MessageRepository.conversationId(me, newHash)
        renameConversation(ctx, oldId, newId, newHash)
        renameHashLocally(ctx, oldHash, newHash)
        PeerKeyCache.saveKey(ctx, newHash, known)
        SignalStore.get(ctx).renamePeer(oldHash, newHash)
        ProfileCrypto.renameContact(ctx, oldHash, newHash)
        val text = LocaleManager.wrap(ctx).getString(pl.sinusoide.sinuschat.R.string.sys_peer_email_changed)
        MessageRepository.insertSystemMessage(ctx, newId, text, "sys_email_changed")
        return true
    }

    private fun renameConversation(ctx: Context, oldId: String, newId: String, newOther: String) {
        if (oldId == newId) return
        try {
            val db = SinusChatDatabase.getInstance(ctx)
            db.messageDao().renameConversation(oldId, newId)
            db.callDao().renameConversation(oldId, newId)
        } catch (e: Exception) { Log.w(TAG, "Baza: ${e.message}") }
        LocalChats.rename(ctx, oldId, newId, newOther)
        renameMediaDir(ctx, oldId, newId)
        renamePrefKeys(ctx, oldId, newId)
        try { ConversationCache.remove(ctx, oldId) } catch (_: Exception) {}
    }

    private fun renameHashLocally(ctx: Context, oldHash: String, newHash: String) {
        try {
            val db = SinusChatDatabase.getInstance(ctx)
            db.messageDao().renameSender(oldHash, newHash)
            db.callDao().renameOther(oldHash, newHash)
        } catch (e: Exception) { Log.w(TAG, "Baza: ${e.message}") }
        renamePrefKeys(ctx, oldHash, newHash)
    }

    private fun renameMediaDir(ctx: Context, oldId: String, newId: String) {
        fun dir(id: String) = File(ctx.filesDir, "media/" + id.replace(Regex("[^A-Za-z0-9_-]"), "_"))
        val from = dir(oldId)
        if (from.exists() && !dir(newId).exists()) from.renameTo(dir(newId))
    }

    private fun renamePrefKeys(ctx: Context, old: String, new: String) {
        val dir = File(ctx.applicationInfo.dataDir, "shared_prefs")
        for (f in dir.listFiles() ?: return) {
            val name = f.name.removeSuffix(".xml")
            try {
                val p = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
                val hits = p.all.filterKeys { it.contains(old) }
                if (hits.isEmpty()) continue
                val e = p.edit()
                for ((k, v) in hits) {
                    val nk = k.replace(old, new)
                    when (v) {
                        is String -> e.putString(nk, v)
                        is Long -> e.putLong(nk, v)
                        is Int -> e.putInt(nk, v)
                        is Boolean -> e.putBoolean(nk, v)
                        is Float -> e.putFloat(nk, v)
                        is Set<*> -> e.putStringSet(nk, v.filterIsInstance<String>().toSet())
                    }
                    e.remove(k)
                }
                e.apply()
            } catch (_: Exception) {}
        }
    }
}
