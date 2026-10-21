package pl.sinusoide.sinuschat.crypto

import android.content.Context
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import pl.sinusoide.sinuschat.messenger.GroupRepository
import java.security.SecureRandom

object GroupKeyManager {

    private const val TAG = "GroupKeyManager"
    private const val PREFS_NAME = "group_sender_keys"
    private const val KEY_SIZE = 32

    private fun getPrefs(context: Context) =
        SecurePrefs.get(context, PREFS_NAME, listOf("my_", "member_", "prev_", "rotate_"))

    fun getOrCreateMyKey(context: Context, groupId: String): ByteArray {
        val prefs = getPrefs(context)
        val existing = prefs.getString("my_$groupId", null)
        if (existing != null) {
            val bytes = Base64.decode(existing, Base64.NO_WRAP)
            if (bytes.size == KEY_SIZE) return bytes
        }
        val key = ByteArray(KEY_SIZE)
        SecureRandom().nextBytes(key)
        prefs.edit().putString("my_$groupId", Base64.encodeToString(key, Base64.NO_WRAP)).apply()
        Log.d(TAG, "Wygenerowano nowy klucz nadawcy dla grupy ${groupId.take(8)}")
        return key
    }

    fun rotateMyKey(context: Context, groupId: String): ByteArray {
        getPrefs(context).edit().remove("my_$groupId").apply()
        return getOrCreateMyKey(context, groupId)
    }

    fun saveMemberKey(context: Context, groupId: String, memberHash: String, key: ByteArray) {
        if (key.size != KEY_SIZE) { Log.w(TAG, "Zły rozmiar klucza członka"); return }
        val prefs = getPrefs(context)
        val name = "member_${groupId}_$memberHash"
        val old = prefs.getString(name, null)
        val new = Base64.encodeToString(key, Base64.NO_WRAP)
        val edit = prefs.edit().putString(name, new)
        if (old != null && old != new) edit.putString("prev_${groupId}_$memberHash", old)
        edit.apply()
    }

    fun getMemberKey(context: Context, groupId: String, memberHash: String): ByteArray? {
        val stored = getPrefs(context).getString("member_${groupId}_$memberHash", null) ?: return null
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        return if (bytes.size == KEY_SIZE) bytes else null
    }

    fun encryptForGroup(context: Context, groupId: String, plaintext: String): String? {
        ensureFreshKey(context, groupId)
        val myKey = getOrCreateMyKey(context, groupId)
        return E2EManager.encrypt(plaintext, myKey)
    }

    fun markRotationNeeded(context: Context, groupId: String) {
        getPrefs(context).edit().putBoolean("rotate_$groupId", true).apply()
        SignalProtocol.markGroupRotation(context, groupId)
    }

    fun sealForGroup(context: Context, groupId: String, plaintext: String, field: String, reply: String? = null): org.json.JSONObject? {
        val access = pl.sinusoide.sinuschat.messenger.GroupRepository.sendAccessBlocking(context, groupId)
        if (access != null && access.allSignal && SignalProtocol.isPublished(context)) {
            val skm = SignalProtocol.groupEncrypt(context, groupId, access.memberHashes,
                plaintext.toByteArray(Charsets.UTF_8)) ?: return null
            val out = JSONObject().put("v", 2).put("skm", skm)
            reply?.let { SignalProtocol.groupEncrypt(context, groupId, access.memberHashes, it.toByteArray(Charsets.UTF_8)) }
                ?.let { out.put(REPLY_SKM, it) }
            return out
        }
        val enc = encryptForGroup(context, groupId, plaintext) ?: return null
        val out = org.json.JSONObject().put(field, enc)
        reply?.let { encryptForGroup(context, groupId, it) }?.let { out.put(REPLY_ENC, it) }
        return out
    }

    const val REPLY_SKM = "rskm"
    const val REPLY_ENC = "encReply"

    fun openReply(context: Context, groupId: String, senderHash: String, payload: JSONObject): String? {
        payload.optString(REPLY_SKM).takeIf { it.isNotEmpty() }?.let { skm ->
            return (SignalProtocol.groupDecrypt(context, senderHash, skm) as? SignalProtocol.GroupOpened.Ok)
                ?.bytes?.toString(Charsets.UTF_8)
        }
        val enc = payload.optString(REPLY_ENC).takeIf { it.isNotEmpty() } ?: return null
        return decryptFromMember(context, groupId, senderHash, enc)
    }

    @Synchronized
    fun ensureFreshKey(context: Context, groupId: String) {
        val prefs = getPrefs(context)
        if (!prefs.getBoolean("rotate_$groupId", false)) return
        val myHash = pl.sinusoide.sinuschat.setup.SetupActivity.getUserPhoneHash(context)
        val members = pl.sinusoide.sinuschat.messenger.GroupRepository
            .sendAccessBlocking(context, groupId)?.memberHashes ?: return
        rotateMyKey(context, groupId)
        distributeMyKey(context, groupId, myHash, members)
        prefs.edit().remove("rotate_$groupId").apply()
        Log.d(TAG, "Nowy klucz nadawcy po zmianie składu grupy ${groupId.take(8)}")
    }

    fun decryptFromMember(context: Context, groupId: String, senderHash: String, encrypted: String): String? {
        val key = getMemberKey(context, groupId, senderHash) ?: run {
            Log.w(TAG, "Brak klucza nadawcy członka ${senderHash.take(8)} — nie odczytam (KROK 2)")
            return null
        }
        E2EManager.decrypt(encrypted, key)?.let { return it }
        val prev = getPrefs(context).getString("prev_${groupId}_$senderHash", null) ?: return null
        val prevKey = try { Base64.decode(prev, Base64.NO_WRAP) } catch (_: Exception) { return null }
        return if (prevKey.size == KEY_SIZE) E2EManager.decrypt(encrypted, prevKey) else null
    }

    fun clearGroup(context: Context, groupId: String) {
        val prefs = getPrefs(context)
        val all = prefs.all.keys.filter { it.contains(groupId) }
        val edit = prefs.edit()
        for (k in all) edit.remove(k)
        edit.apply()
        SignalProtocol.clearGroup(context, groupId)
        GroupMeta.forget(context, groupId)
        pl.sinusoide.sinuschat.util.GroupRoster.forget(context, groupId)
        pl.sinusoide.sinuschat.util.GroupReceipts.forget(context, groupId)
        val pending = context.getSharedPreferences(PENDING_PREFS, Context.MODE_PRIVATE)
        val pe = pending.edit()
        for ((id, raw) in pending.all) {
            if ((raw as? String)?.contains("\"$groupId\"") == true) pe.remove(id)
        }
        pe.apply()
    }

    fun distributeMyKey(
        context: Context,
        groupId: String,
        myHash: String,
        memberHashes: List<String>
    ) {
        val myKey = getOrCreateMyKey(context, groupId)
        val myKeyB64 = Base64.encodeToString(myKey, Base64.NO_WRAP)

        for (memberHash in memberHashes) {
            if (memberHash == myHash) continue

            val theirPubKey = E2EManager.fetchAndCacheKey(context, memberHash)
            if (theirPubKey == null) {
                Log.w(TAG, "Brak klucza publicznego członka ${memberHash.take(8)} — pominę")
                continue
            }
            val secret = E2EManager.computeSharedSecret(context, theirPubKey)
            if (secret == null) {
                Log.w(TAG, "Nie policzę shared secret dla ${memberHash.take(8)}")
                continue
            }

            val encryptedKey = E2EManager.encrypt(myKeyB64, secret) ?: continue

            val payload = org.json.JSONObject()
                .put("groupId", groupId)
                .put("senderHash", myHash)
                .put("encryptedSenderKey", encryptedKey)
            GroupMeta.profileKey(context, groupId)?.let { pk ->
                E2EManager.encrypt(Base64.encodeToString(pk, Base64.NO_WRAP), secret)?.let { payload.put("pk1", it) }
            }
            if (!pl.sinusoide.sinuschat.util.BlockList.isBlocked(context, memberHash))
                E2EManager.encrypt(ProfileCrypto.myKeyB64(context), secret)?.let { payload.put("upk1", it) }
            sendGroupKeyEvent(context, groupId, memberHash, payload)
            Log.d(TAG, "Wysłano klucz nadawcy do ${memberHash.take(8)}")
        }
    }

    private fun sendGroupKeyEvent(
        context: Context, groupId: String, recipientHash: String, payload: org.json.JSONObject
    ) {
        val payloadB64 = Base64.encodeToString(
            payload.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val body = org.json.JSONObject().apply {
            put("conversationId", groupId)
            put("messageId", java.util.UUID.randomUUID().toString())
            put("kind", "group_key")
            put("payloadB64", payloadB64)
            put("recipientHash", recipientHash)
        }
        pl.sinusoide.sinuschat.network.ApiClient.post("/api/message", body, context)
    }

    fun receiveMemberKey(
        context: Context, groupId: String, senderHash: String, payload: org.json.JSONObject
    ): Boolean {
        val encryptedSenderKey = payload.optString("encryptedSenderKey").ifEmpty { return false }
        val theirPubKey = E2EManager.fetchAndCacheKey(context, senderHash) ?: return false
        val secret = E2EManager.computeSharedSecret(context, theirPubKey) ?: return false
        payload.optString("pk1").takeIf { it.isNotEmpty() }?.let { enc ->
            E2EManager.decrypt(enc, secret)?.let { runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull() }
                ?.let { GroupMeta.receiveProfileKey(context, groupId, it) }
        }
        payload.optString("upk1").takeIf { it.isNotEmpty() }?.let { enc ->
            ProfileCrypto.receiveKey(context, senderHash, E2EManager.decrypt(enc, secret))
        }
        val keyB64 = E2EManager.decrypt(encryptedSenderKey, secret) ?: return false
        val key = try { Base64.decode(keyB64, Base64.NO_WRAP) } catch (e: Exception) { return false }
        if (key.size != KEY_SIZE) return false
        saveMemberKey(context, groupId, senderHash, key)
        Log.d(TAG, "Odebrano i zapisano klucz nadawcy od ${senderHash.take(8)}")
        return true
    }

    private const val PENDING_PREFS = "group_pending_msgs"

    fun queuePendingMessage(
        context: Context, groupId: String, msgId: String, senderHash: String,
        encrypted: String, timestamp: Long, v2: Boolean = false, reply: String? = null
    ) {
        val prefs = context.getSharedPreferences(PENDING_PREFS, Context.MODE_PRIVATE)
        val record = org.json.JSONObject()
            .put("groupId", groupId).put("senderHash", senderHash)
            .put("enc", encrypted).put("ts", timestamp).put("v2", v2)
            .apply { if (!reply.isNullOrEmpty()) put("renc", reply) }
            .toString()
        prefs.edit().putString(msgId, record).apply()
        Log.d(TAG, "Zakolejkowano wiadomość $msgId (brak klucza od ${senderHash.take(8)})")
    }

    class Drained(val id: String, val text: String, val reply: String?, val timestamp: Long)

    fun drainPendingFor(
        context: Context, groupId: String, senderHash: String
    ): List<Drained> {
        val prefs = context.getSharedPreferences(PENDING_PREFS, Context.MODE_PRIVATE)
        val result = ArrayList<Drained>()
        val toRemove = ArrayList<String>()
        for ((msgId, raw) in prefs.all) {
            val rec = try { org.json.JSONObject(raw as String) } catch (e: Exception) { continue }
            if (rec.optString("groupId") != groupId) continue
            if (rec.optString("senderHash") != senderHash) continue
            val v2 = rec.optBoolean("v2")
            fun open(enc: String): String? = if (v2) {
                (SignalProtocol.groupDecrypt(context, senderHash, enc)
                    as? SignalProtocol.GroupOpened.Ok)?.bytes?.toString(Charsets.UTF_8)
            } else decryptFromMember(context, groupId, senderHash, enc)
            val dec = open(rec.optString("enc"))
            if (dec != null) {
                val reply = rec.optString("renc").takeIf { it.isNotEmpty() }?.let { open(it) }
                result.add(Drained(msgId, dec, reply, rec.optLong("ts", 0L).takeIf { it > 0L } ?: System.currentTimeMillis()))
                toRemove.add(msgId)
            }
        }
        if (toRemove.isNotEmpty()) {
            val edit = prefs.edit()
            for (id in toRemove) edit.remove(id)
            edit.apply()
            Log.d(TAG, "Odszyfrowano ${toRemove.size} oczekujących wiadomości od ${senderHash.take(8)}")
        }
        return result
    }

    fun requestKeyFrom(context: Context, groupId: String, myHash: String, senderHash: String, v2: Boolean = false) {
        val payload = org.json.JSONObject()
            .put("groupId", groupId)
            .put("requesterHash", myHash)
            .put("v2", v2)
        val payloadB64 = Base64.encodeToString(
            payload.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val body = org.json.JSONObject().apply {
            put("conversationId", groupId)
            put("messageId", java.util.UUID.randomUUID().toString())
            put("kind", "group_key_request")
            put("payloadB64", payloadB64)
            put("recipientHash", senderHash)
        }
        pl.sinusoide.sinuschat.network.ApiClient.post("/api/message", body, context)
        Log.d(TAG, "Poproszono ${senderHash.take(8)} o klucz nadawcy")
    }

    fun respondToKeyRequest(context: Context, groupId: String, myHash: String, requesterHash: String, v2: Boolean = false) {
        if (v2) SignalProtocol.shareMyGroupKey(context, groupId, requesterHash)
        else distributeMyKey(context, groupId, myHash, listOf(requesterHash))
        Log.d(TAG, "Odesłano klucz nadawcy do ${requesterHash.take(8)} na prośbę")
    }
}
