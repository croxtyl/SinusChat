package pl.sinusoide.sinuschat.crypto

import android.content.Context
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.signal.libsignal.protocol.DuplicateMessageException
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.NoSessionException
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.groups.GroupCipher
import org.signal.libsignal.protocol.groups.GroupSessionBuilder
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.kem.KEMPublicKey
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SenderKeyDistributionMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import pl.sinusoide.sinuschat.network.ApiClient
import pl.sinusoide.sinuschat.setup.SetupActivity
import java.security.SecureRandom
import java.util.UUID

object SignalProtocol {

    private const val TAG = "Signal"
    const val DEVICE_ID = 1

    private const val PREKEY_BATCH = 100
    private const val PREKEY_MIN = 25
    private const val SIGNED_ROTATE_MS = 7L * 24 * 3600_000
    private const val OLD_KEYS_KEEP_MS = 30L * 24 * 3600_000
    private const val CHECK_EVERY_MS = 6L * 3600_000
    private const val NO_BUNDLE_RETRY_MS = 10L * 60_000
    private const val PAD_BLOCK = 64

    private val lock = Any()
    private val uploadLock = Any()

    private val noBundle = HashMap<String, Long>()

    private fun addr(hash: String) = SignalProtocolAddress(hash, DEVICE_ID)
    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    private fun ready(ctx: Context): SignalStore? {
        val pub = E2EManager.getMyPublicKey(ctx) ?: return null
        val st = try { SignalStore.get(ctx) } catch (e: Throwable) {
            Log.e(TAG, "Magazyn protokołu niedostępny", e); return null
        }
        if (st.getString("meta", "identity_pub") != pub) {
            synchronized(lock) {
                if (st.getString("meta", "identity_pub") != pub) {
                    Log.w(TAG, "Nowa tożsamość — stan protokołu od nowa")
                    st.clearAll()
                    st.forgetIdentityCache()
                    val rnd = SecureRandom()
                    st.putString("meta", "identity_pub", pub)
                    st.putInt("meta", "reg_id", 1 + rnd.nextInt(16380))
                    st.putInt("meta", "next_prekey", 1 + rnd.nextInt(0x7FFFFF))
                    st.putInt("meta", "next_signed", 1 + rnd.nextInt(0x7FFFFF))
                }
            }
        }
        return if (st.identityOrNull() != null) st else null
    }

    private fun me(ctx: Context) = SetupActivity.getUserPhoneHash(ctx)

    fun isPublished(ctx: Context): Boolean = ready(ctx)?.getLong("meta", "uploaded_at") != null

    fun ensureKeysUploaded(ctx: Context, minIntervalMs: Long = CHECK_EVERY_MS) {
        if (!ApiClient.isAuthenticated(ctx) || me(ctx).isEmpty()) return
        val st = ready(ctx) ?: return
        synchronized(uploadLock) {
            val now = System.currentTimeMillis()
            val checked = st.getLong("meta", "checked_at") ?: 0L
            val published = st.getLong("meta", "uploaded_at") != null
            if (published && now - checked < minIntervalMs) return
            try { upload(ctx, st, now) } catch (e: Exception) { Log.w(TAG, "Klucze wstępne: ${e.message}") }
        }
    }

    private fun upload(ctx: Context, st: SignalStore, now: Long) {
        val identity = st.identityOrNull() ?: return
        val firstTime = st.getLong("meta", "uploaded_at") == null
        var full = firstTime

        var signedId = st.getInt("meta", "signed_id")
        if (signedId == null || now - (st.getLong("meta", "signed_at") ?: 0L) > SIGNED_ROTATE_MS) {
            signedId = rotateSigned(st, identity, now)
            full = true
        }

        var addPreKeys = firstTime
        if (!full) {
            val info = ApiClient.getObject("/api/signal/count", ctx) ?: return
            val serverIdentity = info.optString("identityKey")
            val serverSigned = info.optInt("signedPreKeyId", -1)
            if (serverIdentity != b64(identity.publicKey.serialize()) || serverSigned != signedId) full = true
            if (info.optInt("count", 0) < PREKEY_MIN) addPreKeys = true
        }
        if (!full && !addPreKeys) {
            st.putLong("meta", "checked_at", now)
            return
        }

        val signed = st.loadSignedPreKey(signedId)
        val kyber = st.loadKyberPreKey(st.getInt("meta", "kyber_id") ?: return)
        val body = JSONObject()
            .put("registrationId", st.localRegistrationId)
            .put("identityKey", b64(identity.publicKey.serialize()))
            .put("signedPreKey", JSONObject()
                .put("id", signed.id)
                .put("pub", b64(signed.keyPair.publicKey.serialize()))
                .put("sig", b64(signed.signature)))
            .put("kyberPreKey", JSONObject()
                .put("id", kyber.id)
                .put("pub", b64(kyber.keyPair.publicKey.serialize()))
                .put("sig", b64(kyber.signature)))
        if (addPreKeys) {
            val arr = JSONArray()
            var next = st.getInt("meta", "next_prekey") ?: 1
            repeat(PREKEY_BATCH) {
                val rec = PreKeyRecord(next, ECKeyPair.generate())
                st.storePreKey(next, rec)
                arr.put(JSONObject().put("id", next).put("pub", b64(rec.keyPair.publicKey.serialize())))
                next = if (next >= 0xFFFFFE) 1 else next + 1
            }
            st.putInt("meta", "next_prekey", next)
            body.put("preKeys", arr)
        }
        if (firstTime) body.put("replacePreKeys", true)

        val resp = ApiClient.post("/api/signal/keys", body, ctx)
        if (resp?.optBoolean("ok", false) == true) {
            st.putLong("meta", "uploaded_at", now)
            st.putLong("meta", "checked_at", now)
            Log.d(TAG, "Klucze wstępne na serwerze (pełne=$full, nowe jednorazowe=$addPreKeys)")
        }
    }

    private fun rotateSigned(st: SignalStore, identity: org.signal.libsignal.protocol.IdentityKeyPair, now: Long): Int {
        var id = st.getInt("meta", "next_signed") ?: 1
        val kp = ECKeyPair.generate()
        val sig = identity.privateKey.calculateSignature(kp.publicKey.serialize())
        st.storeSignedPreKey(id, SignedPreKeyRecord(id, now, kp, sig))
        val kem = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        val ksig = identity.privateKey.calculateSignature(kem.publicKey.serialize())
        st.storeKyberPreKey(id, KyberPreKeyRecord(id, now, kem, ksig))
        st.putInt("meta", "signed_id", id)
        st.putInt("meta", "kyber_id", id)
        st.putLong("meta", "signed_at", now)
        for (old in st.loadSignedPreKeys()) if (old.id != id && now - old.timestamp > OLD_KEYS_KEEP_MS) st.removeSignedPreKey(old.id)
        for (old in st.loadKyberPreKeys()) if (old.id != id && now - old.timestamp > OLD_KEYS_KEEP_MS) st.delRaw("kyber", old.id.toString())
        id = if (id >= 0xFFFFFE) 1 else id + 1
        st.putInt("meta", "next_signed", id)
        return st.getInt("meta", "signed_id")!!
    }

    private fun fetchBundle(ctx: Context, peer: String): PreKeyBundle? {
        synchronized(noBundle) {
            val at = noBundle[peer]
            if (at != null && System.currentTimeMillis() - at < NO_BUNDLE_RETRY_MS) return null
        }
        val j = ApiClient.getObject("/api/signal/bundle/$peer", ctx)
        if (j == null || !j.has("signedPreKey")) {
            synchronized(noBundle) { noBundle[peer] = System.currentTimeMillis() }
            return null
        }
        return try {
            val identity = j.getString("identityKey")
            pl.sinusoide.sinuschat.util.PeerKeyCache.saveKey(ctx, peer, identity)
            val sp = j.getJSONObject("signedPreKey")
            val kp = j.getJSONObject("kyberPreKey")
            val pk = j.optJSONObject("preKey")
            PreKeyBundle(
                j.getInt("registrationId"), DEVICE_ID,
                pk?.getInt("id") ?: PreKeyBundle.NULL_PRE_KEY_ID,
                pk?.let { ECPublicKey(unb64(it.getString("pub"))) },
                sp.getInt("id"), ECPublicKey(unb64(sp.getString("pub"))), unb64(sp.getString("sig")),
                IdentityKey(unb64(identity)),
                kp.getInt("id"), KEMPublicKey(unb64(kp.getString("pub"))), unb64(kp.getString("sig"))
            )
        } catch (e: Exception) { Log.w(TAG, "Zły zestaw kluczy od serwera: ${e.message}"); null }
    }

    fun encrypt(ctx: Context, peer: String, plain: ByteArray): JSONObject? {
        val st = ready(ctx) ?: return null
        val me = me(ctx).ifEmpty { return null }
        if (peer.isEmpty() || peer == me) return null
        val remote = addr(peer)
        if (!synchronized(lock) { st.containsSession(remote) }) {
            val bundle = fetchBundle(ctx, peer) ?: return null
            try {
                synchronized(lock) {
                    if (!st.containsSession(remote)) SessionBuilder(st, remote, addr(me)).process(bundle)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Zestaw kluczy odrzucony: ${e.message}")
                return null
            }
        }
        return try {
            synchronized(lock) {
                val msg = SessionCipher(st, addr(me), remote).encrypt(pad(plain))
                JSONObject().put("v", 2).put("t", msg.type).put("c", b64(msg.serialize()))
            }
        } catch (e: Exception) { Log.e(TAG, "Szyfrowanie nieudane: ${e.message}"); null }
    }

    sealed class Opened {
        class Ok(val bytes: ByteArray) : Opened()
        object Duplicate : Opened()
        object Failed : Opened()
    }

    fun isV2(payload: JSONObject) = payload.optInt("v", 0) == 2 && payload.has("c")

    fun decrypt(ctx: Context, peer: String, v2: JSONObject): Opened {
        val st = ready(ctx) ?: return Opened.Failed
        val me = me(ctx).ifEmpty { return Opened.Failed }
        val bytes = try { unb64(v2.getString("c")) } catch (_: Exception) { return Opened.Failed }
        var usedPreKey = false
        val result = try {
            synchronized(lock) {
                val cipher = SessionCipher(st, addr(me), addr(peer))
                val out = when (v2.optInt("t")) {
                    CiphertextMessage.PREKEY_TYPE -> { usedPreKey = true; cipher.decrypt(PreKeySignalMessage(bytes)) }
                    CiphertextMessage.WHISPER_TYPE -> cipher.decrypt(SignalMessage(bytes))
                    else -> return Opened.Failed
                }
                Opened.Ok(unpad(out))
            }
        } catch (e: DuplicateMessageException) {
            Opened.Duplicate
        } catch (e: Exception) {
            Log.w(TAG, "Odszyfrowanie nieudane (${e.javaClass.simpleName}): ${e.message}")
            Opened.Failed
        }
        if (usedPreKey && result is Opened.Ok) {
            pl.sinusoide.sinuschat.util.AppExecutor.io { ensureKeysUploaded(ctx, 10 * 60_000L) }
        }
        return result
    }

    fun resetSession(ctx: Context, peer: String) {
        val st = ready(ctx) ?: return
        synchronized(lock) { st.deleteSession(addr(peer)) }
        synchronized(noBundle) { noBundle.remove(peer) }
    }

    fun onPeerIdentityChanged(ctx: Context, peer: String) {
        resetSession(ctx, peer)
        forgetSharedWith(ctx, peer)
    }

    fun markGroupRotation(ctx: Context, groupId: String) {
        val st = ready(ctx) ?: return
        st.putRaw("rotate", groupId, byteArrayOf(1))
    }

    fun clearGroup(ctx: Context, groupId: String) {
        val st = ready(ctx) ?: return
        synchronized(lock) {
            st.delRaw("mydist", groupId)
            st.delRaw("rotate", groupId)
            for ((k, _) in st.listRaw("shared")) if (k.startsWith("$groupId|")) st.delRaw("shared", k)
        }
    }

    private fun sharedWith(st: SignalStore, key: String): MutableSet<String> {
        val raw = st.getString("shared", key) ?: return mutableSetOf()
        return try { JSONArray(raw).let { a -> (0 until a.length()).map { a.getString(it) }.toMutableSet() } }
        catch (_: Exception) { mutableSetOf() }
    }

    private fun addShared(st: SignalStore, key: String, hash: String) = synchronized(lock) {
        val s = sharedWith(st, key)
        if (s.add(hash)) st.putString("shared", key, JSONArray(s.toList()).toString())
    }

    fun forgetSharedWith(ctx: Context, hash: String) {
        val st = try { SignalStore.get(ctx) } catch (_: Throwable) { return }
        synchronized(lock) {
            for ((k, v) in st.listRaw("shared")) {
                val arr = try { JSONArray(v.toString(Charsets.UTF_8)) } catch (_: Exception) { continue }
                val s = (0 until arr.length()).map { arr.getString(it) }.toMutableSet()
                if (s.remove(hash)) st.putString("shared", k, JSONArray(s.toList()).toString())
            }
        }
    }

    private fun myDistribution(st: SignalStore, groupId: String, me: String): UUID = synchronized(lock) {
        var d = st.getString("mydist", groupId)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (d == null || st.getRaw("rotate", groupId) != null) {
            d = UUID.randomUUID()
            st.putString("mydist", groupId, d.toString())
            st.delRaw("rotate", groupId)
        }
        if (st.loadSenderKey(addr(me), d) == null) GroupSessionBuilder(st).create(addr(me), d)
        d
    }

    fun groupEncrypt(ctx: Context, groupId: String, members: List<String>, plain: ByteArray): String? {
        val st = ready(ctx) ?: return null
        val me = me(ctx).ifEmpty { return null }
        return try {
            val dist = myDistribution(st, groupId, me)
            val key = "$groupId|$dist"
            val missing = members.filter { it != me && it !in sharedWith(st, key) }
            if (missing.isNotEmpty()) {
                val skdm = synchronized(lock) { GroupSessionBuilder(st).create(addr(me), dist).serialize() }
                for (m in missing) {
                    if (sendDistribution(ctx, groupId, me, m, skdm)) addShared(st, key, m)
                }
            }
            synchronized(lock) { b64(GroupCipher(st, addr(me)).encrypt(dist, pad(plain)).serialize()) }
        } catch (e: Exception) { Log.e(TAG, "Szyfrowanie grupowe nieudane: ${e.message}"); null }
    }

    fun shareMyGroupKey(ctx: Context, groupId: String, requester: String) {
        val st = ready(ctx) ?: return
        val me = me(ctx).ifEmpty { return }
        val dist = st.getString("mydist", groupId)?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return
        if (st.getRaw("rotate", groupId) != null) return
        val skdm = synchronized(lock) { GroupSessionBuilder(st).create(addr(me), dist).serialize() }
        if (sendDistribution(ctx, groupId, me, requester, skdm)) addShared(st, "$groupId|$dist", requester)
    }

    private fun sendDistribution(ctx: Context, groupId: String, me: String, member: String, skdm: ByteArray): Boolean {
        val inner = JSONObject().put("skdm", b64(skdm))
        if (!pl.sinusoide.sinuschat.util.BlockList.isBlocked(ctx, member)) inner.put("upk", ProfileCrypto.myKeyB64(ctx))
        GroupMeta.profileKey(ctx, groupId)?.let { inner.put("pk", b64(it)) }
        val plain = inner.toString().toByteArray(Charsets.UTF_8)
        val payload = JSONObject().put("groupId", groupId).put("senderHash", me)
        val v2 = encrypt(ctx, member, plain)
        if (v2 != null) {
            payload.put("skdm", v2)
        } else {
            val k = E2EManager.fetchAndCacheKey(ctx, member) ?: return false
            val secret = E2EManager.computeSharedSecret(ctx, k) ?: return false
            payload.put("skdm1", E2EManager.encrypt(inner.toString(), secret) ?: return false)
        }
        val body = JSONObject()
            .put("conversationId", groupId)
            .put("messageId", UUID.randomUUID().toString())
            .put("kind", "group_key")
            .put("payloadB64", b64(payload.toString().toByteArray(Charsets.UTF_8)))
            .put("recipientHash", member)
        return ApiClient.post("/api/message", body, ctx) != null
    }

    fun processDistribution(ctx: Context, groupId: String, sender: String, payload: JSONObject): Boolean {
        val st = ready(ctx) ?: return false
        val innerText: String = when {
            payload.has("skdm") -> (decrypt(ctx, sender, payload.getJSONObject("skdm")) as? Opened.Ok)
                ?.bytes?.toString(Charsets.UTF_8)
            payload.has("skdm1") -> {
                val k = E2EManager.fetchAndCacheKey(ctx, sender)
                val secret = k?.let { E2EManager.computeSharedSecret(ctx, it) }
                secret?.let { E2EManager.decrypt(payload.getString("skdm1"), it) }
            }
            else -> null
        } ?: return false
        val inner = runCatching { JSONObject(innerText) }.getOrNull() ?: return false
        inner.optString("pk").takeIf { it.isNotEmpty() }?.let {
            runCatching { GroupMeta.receiveProfileKey(ctx, groupId, unb64(it)) }
        }
        ProfileCrypto.receiveKey(ctx, sender, inner.optString("upk"))
        val skdm = runCatching { unb64(inner.getString("skdm")) }.getOrNull() ?: return false
        return try {
            synchronized(lock) {
                GroupSessionBuilder(st).process(addr(sender), SenderKeyDistributionMessage(skdm))
            }
            true
        } catch (e: Exception) { Log.w(TAG, "Klucz nadawcy odrzucony: ${e.message}"); false }
    }

    sealed class GroupOpened {
        class Ok(val bytes: ByteArray) : GroupOpened()
        object NoKey : GroupOpened()
        object Duplicate : GroupOpened()
        object Failed : GroupOpened()
    }

    fun groupDecrypt(ctx: Context, sender: String, skmB64: String): GroupOpened {
        val st = ready(ctx) ?: return GroupOpened.Failed
        val bytes = try { unb64(skmB64) } catch (_: Exception) { return GroupOpened.Failed }
        return try {
            synchronized(lock) { GroupOpened.Ok(unpad(GroupCipher(st, addr(sender)).decrypt(bytes))) }
        } catch (e: NoSessionException) {
            GroupOpened.NoKey
        } catch (e: DuplicateMessageException) {
            GroupOpened.Duplicate
        } catch (e: Exception) {
            Log.w(TAG, "Grupa: odszyfrowanie nieudane (${e.javaClass.simpleName}): ${e.message}")
            if (e.message?.contains("sender key", ignoreCase = true) == true) GroupOpened.NoKey else GroupOpened.Failed
        }
    }

    private fun pad(b: ByteArray): ByteArray {
        val len = ((b.size + 1 + PAD_BLOCK - 1) / PAD_BLOCK) * PAD_BLOCK
        val out = ByteArray(len)
        System.arraycopy(b, 0, out, 0, b.size)
        out[b.size] = 0x80.toByte()
        return out
    }

    private fun unpad(b: ByteArray): ByteArray {
        var i = b.size - 1
        while (i >= 0 && b[i] == 0.toByte()) i--
        return if (i >= 0 && b[i] == 0x80.toByte()) b.copyOf(i) else b
    }
}
