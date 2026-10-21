package pl.sinusoide.sinuschat.crypto

import android.content.ContentValues
import android.content.Context
import android.util.Base64
import android.util.Log
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.ECPrivateKey
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SignalProtocolStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import java.security.SecureRandom
import java.util.UUID

class SignalStore private constructor(
    private val app: Context,
    private val db: SQLiteDatabase
) : SignalProtocolStore {

    companion object {
        private const val TAG = "SignalStore"
        private const val DB_NAME = "signal_store.db"
        private const val KEY_PREFS = "signal_store_key"

        @Volatile private var instance: SignalStore? = null

        fun get(context: Context): SignalStore =
            instance ?: synchronized(this) { instance ?: open(context.applicationContext).also { instance = it } }

        @Synchronized
        fun wipe(context: Context) {
            val app = context.applicationContext
            synchronized(this) {
                try { instance?.db?.close() } catch (_: Exception) {}
                instance = null
            }
            app.deleteDatabase(DB_NAME)
            SecurePrefs.forget(app, KEY_PREFS)
        }

        private fun open(app: Context): SignalStore {
            System.loadLibrary("sqlcipher")
            val prefs = SecurePrefs.get(app, KEY_PREFS)
            var pass = prefs.getString("k", null)
            val file = app.getDatabasePath(DB_NAME)
            if (pass == null) {
                if (file.exists()) app.deleteDatabase(DB_NAME)
                pass = Base64.encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) }, Base64.NO_WRAP)
                prefs.edit().putString("k", pass).commit()
            }
            file.parentFile?.mkdirs()
            val db = try {
                openDb(file.path, pass!!)
            } catch (e: Exception) {
                Log.e(TAG, "Baza protokołu nie do otwarcia — zakładam nową", e)
                app.deleteDatabase(DB_NAME)
                openDb(file.path, pass!!)
            }
            db.execSQL("CREATE TABLE IF NOT EXISTS kv (ns TEXT NOT NULL, k TEXT NOT NULL, v BLOB NOT NULL, PRIMARY KEY (ns, k))")
            return SignalStore(app, db)
        }

        private fun openDb(path: String, pass: String): SQLiteDatabase =
            SQLiteDatabase.openDatabase(path, pass.toByteArray(Charsets.UTF_8), null,
                SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY, null, null)

        private fun addr(a: SignalProtocolAddress) = "${a.name}.${a.deviceId}"
    }

    @Synchronized
    fun getRaw(ns: String, k: String): ByteArray? =
        db.rawQuery("SELECT v FROM kv WHERE ns=? AND k=?", arrayOf(ns, k)).use { c ->
            if (c.moveToFirst()) c.getBlob(0) else null
        }

    @Synchronized
    fun putRaw(ns: String, k: String, v: ByteArray) {
        db.insertWithOnConflict("kv", null, ContentValues().apply {
            put("ns", ns); put("k", k); put("v", v)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    fun delRaw(ns: String, k: String) { db.delete("kv", "ns=? AND k=?", arrayOf(ns, k)) }

    @Synchronized
    fun listRaw(ns: String): List<Pair<String, ByteArray>> =
        db.rawQuery("SELECT k, v FROM kv WHERE ns=?", arrayOf(ns)).use { c ->
            val out = ArrayList<Pair<String, ByteArray>>()
            while (c.moveToNext()) out.add(c.getString(0) to c.getBlob(1))
            out
        }

    @Synchronized
    fun clearAll() { db.delete("kv", null, null) }

    fun getString(ns: String, k: String): String? = getRaw(ns, k)?.toString(Charsets.UTF_8)
    fun putString(ns: String, k: String, v: String) = putRaw(ns, k, v.toByteArray(Charsets.UTF_8))
    fun getInt(ns: String, k: String): Int? = getString(ns, k)?.toIntOrNull()
    fun putInt(ns: String, k: String, v: Int) = putString(ns, k, v.toString())
    fun getLong(ns: String, k: String): Long? = getString(ns, k)?.toLongOrNull()
    fun putLong(ns: String, k: String, v: Long) = putString(ns, k, v.toString())

    @Synchronized
    fun renamePeer(oldName: String, newName: String) {
        for (ns in listOf("session", "identity")) {
            getRaw(ns, "$oldName.1")?.let { putRaw(ns, "$newName.1", it); delRaw(ns, "$oldName.1") }
        }
        for ((k, v) in listRaw("senderkey")) {
            if (k.startsWith("$oldName.1|")) { putRaw("senderkey", "$newName.1|" + k.substringAfter('|'), v); delRaw("senderkey", k) }
        }
        for ((k, v) in listRaw("shared")) {
            val s = v.toString(Charsets.UTF_8)
            if (s.contains("\"$oldName\"")) putRaw("shared", k, s.replace(oldName, newName).toByteArray(Charsets.UTF_8))
        }
    }

    @Volatile private var identityCache: IdentityKeyPair? = null

    fun identityOrNull(): IdentityKeyPair? {
        identityCache?.let { return it }
        val pub = E2EManager.getMyPublicKey(app) ?: return null
        val priv = E2EManager.myPrivateKeyBytes(app) ?: return null
        return try {
            IdentityKeyPair(IdentityKey(ECPublicKey(Base64.decode(pub, Base64.NO_WRAP))), ECPrivateKey(priv))
                .also { identityCache = it }
        } catch (e: Exception) { Log.e(TAG, "Zły klucz tożsamości", e); null }
    }

    fun forgetIdentityCache() { identityCache = null }

    override fun getIdentityKeyPair(): IdentityKeyPair =
        identityOrNull() ?: throw IllegalStateException("Brak klucza tożsamości")

    override fun getLocalRegistrationId(): Int = getInt("meta", "reg_id") ?: 0

    override fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey): IdentityKeyStore.IdentityChange {
        val k = addr(address)
        val existing = getRaw("identity", k)
        val bytes = identityKey.serialize()
        putRaw("identity", k, bytes)
        pl.sinusoide.sinuschat.util.PeerKeyCache.saveKey(app, address.name,
            Base64.encodeToString(bytes, Base64.NO_WRAP))
        return if (existing == null || existing.contentEquals(bytes)) {
            IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED
        } else {
            SignalProtocol.forgetSharedWith(app, address.name)
            IdentityKeyStore.IdentityChange.REPLACED_EXISTING
        }
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress, identityKey: IdentityKey, direction: IdentityKeyStore.Direction
    ): Boolean = true

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? =
        getRaw("identity", addr(address))?.let { try { IdentityKey(it) } catch (_: Exception) { null } }

    override fun loadSession(address: SignalProtocolAddress): SessionRecord? =
        getRaw("session", addr(address))?.let { try { SessionRecord(it) } catch (_: Exception) { null } }

    override fun loadExistingSessions(addresses: List<SignalProtocolAddress>): List<SessionRecord> =
        addresses.map { a ->
            loadSession(a) ?: throw org.signal.libsignal.protocol.NoSessionException(a, "brak sesji")
        }

    override fun getSubDeviceSessions(name: String): List<Int> = emptyList()

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) =
        putRaw("session", addr(address), record.serialize())

    override fun containsSession(address: SignalProtocolAddress): Boolean =
        loadSession(address)?.let { it.hasSenderChain() } ?: false

    override fun deleteSession(address: SignalProtocolAddress) = delRaw("session", addr(address))

    override fun deleteAllSessions(name: String) {
        for ((k, _) in listRaw("session")) if (k.substringBeforeLast('.') == name) delRaw("session", k)
    }

    override fun loadPreKey(preKeyId: Int): PreKeyRecord =
        getRaw("prekey", preKeyId.toString())?.let { PreKeyRecord(it) }
            ?: throw InvalidKeyIdException("Brak klucza wstępnego $preKeyId")

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) =
        putRaw("prekey", preKeyId.toString(), record.serialize())

    override fun containsPreKey(preKeyId: Int): Boolean = getRaw("prekey", preKeyId.toString()) != null

    override fun removePreKey(preKeyId: Int) = delRaw("prekey", preKeyId.toString())

    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord =
        getRaw("signed", signedPreKeyId.toString())?.let { SignedPreKeyRecord(it) }
            ?: throw InvalidKeyIdException("Brak podpisanego klucza $signedPreKeyId")

    override fun loadSignedPreKeys(): List<SignedPreKeyRecord> =
        listRaw("signed").mapNotNull { (_, v) -> try { SignedPreKeyRecord(v) } catch (_: Exception) { null } }

    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) =
        putRaw("signed", signedPreKeyId.toString(), record.serialize())

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean = getRaw("signed", signedPreKeyId.toString()) != null

    override fun removeSignedPreKey(signedPreKeyId: Int) = delRaw("signed", signedPreKeyId.toString())

    override fun loadKyberPreKey(kyberPreKeyId: Int): KyberPreKeyRecord =
        getRaw("kyber", kyberPreKeyId.toString())?.let { KyberPreKeyRecord(it) }
            ?: throw InvalidKeyIdException("Brak klucza Kyber $kyberPreKeyId")

    override fun loadKyberPreKeys(): List<KyberPreKeyRecord> =
        listRaw("kyber").mapNotNull { (_, v) -> try { KyberPreKeyRecord(v) } catch (_: Exception) { null } }

    override fun storeKyberPreKey(kyberPreKeyId: Int, record: KyberPreKeyRecord) =
        putRaw("kyber", kyberPreKeyId.toString(), record.serialize())

    override fun containsKyberPreKey(kyberPreKeyId: Int): Boolean = getRaw("kyber", kyberPreKeyId.toString()) != null

    override fun markKyberPreKeyUsed(kyberPreKeyId: Int, signedPreKeyId: Int, baseKey: ECPublicKey) {
        val seen = "$kyberPreKeyId|$signedPreKeyId|" + Base64.encodeToString(baseKey.serialize(), Base64.NO_WRAP)
        if (getRaw("kyber_seen", seen) != null) throw org.signal.libsignal.protocol.ReusedBaseKeyException()
        putRaw("kyber_seen", seen, byteArrayOf(1))
    }

    override fun storeSenderKey(sender: SignalProtocolAddress, distributionId: UUID, record: SenderKeyRecord) =
        putRaw("senderkey", "${addr(sender)}|$distributionId", record.serialize())

    override fun loadSenderKey(sender: SignalProtocolAddress, distributionId: UUID): SenderKeyRecord? =
        getRaw("senderkey", "${addr(sender)}|$distributionId")?.let {
            try { SenderKeyRecord(it) } catch (_: Exception) { null }
        }
}
