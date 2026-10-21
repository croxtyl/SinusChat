package pl.sinusoide.sinuschat.crypto
import pl.sinusoide.sinuschat.util.optStringOrNull

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.ecc.ECPrivateKey
import org.signal.libsignal.protocol.ecc.ECPublicKey
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object E2EManager {

    private const val TAG = "E2EManager"
    private const val PREFS_NAME = "e2e_keys"
    private const val KEY_PRIVATE = "my_private_key"
    private const val KEY_PUBLIC = "my_public_key"
    private const val KEY_CREATED_AT = "key_created_at"

    private fun getPrefs(context: Context) =
        SecurePrefs.get(context, PREFS_NAME, listOf(KEY_PRIVATE, KEY_PUBLIC, KEY_CREATED_AT))

    fun hasKeys(context: Context): Boolean = try {
        getPrefs(context).getString(KEY_PRIVATE, null) != null
    } catch (e: Throwable) { false }

    enum class EncState { ENCRYPTED, NOT_ENCRYPTED, ERROR }
    class EncryptionStatus(val state: EncState, val error: String? = null)

    fun encryptionStatus(context: Context): EncryptionStatus {
        return try {
            val enc = EncryptedSharedPreferences.create(
                context, PREFS_NAME,
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            val hasKey = enc.getString(KEY_PRIVATE, null) != null
            if (hasKey) EncryptionStatus(EncState.ENCRYPTED) else EncryptionStatus(EncState.NOT_ENCRYPTED)
        } catch (e: Throwable) {
            Log.e(TAG, "encryptionStatus failed", e)
            EncryptionStatus(EncState.ERROR, e.message ?: e.javaClass.simpleName)
        }
    }

    fun getKeyCreatedAt(context: Context): Long = try {
        getPrefs(context).getLong(KEY_CREATED_AT, 0L)
    } catch (e: Throwable) { 0L }

    fun generateKeys(context: Context): ECKeyPair {
        val keyPair = ECKeyPair.generate()
        val now = System.currentTimeMillis()
        getPrefs(context).edit()
            .putString(KEY_PRIVATE, toBase64(keyPair.privateKey.serialize()))
            .putString(KEY_PUBLIC, toBase64(keyPair.publicKey.serialize()))
            .putLong(KEY_CREATED_AT, now)
            .apply()
        Log.d(TAG, "Keys generated at $now")
        return keyPair
    }

    fun getMyPublicKey(context: Context): String? = try {
        getPrefs(context).getString(KEY_PUBLIC, null)
    } catch (e: Throwable) { null }

    fun myPrivateKeyBytes(context: Context): ByteArray? = getMyPrivateKeyBytes(context)

    private fun getMyPrivateKeyBytes(context: Context): ByteArray? {
        val b64 = try { getPrefs(context).getString(KEY_PRIVATE, null) } catch (e: Throwable) { null }
        return if (b64 != null) fromBase64(b64) else null
    }

    fun generateAndUploadKeys(context: Context, phoneHash: String, onComplete: (Boolean) -> Unit) {
        try {
            val keyPair = generateKeys(context)
            val publicKeyBase64 = toBase64(keyPair.publicKey.serialize())
            pl.sinusoide.sinuschat.util.AppExecutor.io {
                val result = pl.sinusoide.sinuschat.network.ApiClient.post(
                    "/api/user/update",
                    org.json.JSONObject()
                        .put("e2ePublicKey", publicKeyBase64)
                        .put("keyUpdatedAt", getKeyCreatedAt(context)),
                    context
                )
                pl.sinusoide.sinuschat.util.AppExecutor.main { onComplete(result != null) }
            }
        } catch (e: Exception) { Log.e(TAG, "Key gen failed", e); onComplete(false) }
    }

    private val sharedSecretCache = mutableMapOf<String, ByteArray>()

    fun computeSharedSecret(context: Context, theirPublicKeyBase64: String): ByteArray? {
        sharedSecretCache[theirPublicKeyBase64]?.let { return it }
        return try {
            val myPrivateBytes = getMyPrivateKeyBytes(context) ?: return null
            val theirPublicBytes = fromBase64(theirPublicKeyBase64)
            val myPrivateKey = ECPrivateKey(myPrivateBytes)
            val theirPublicKey = ECPublicKey(theirPublicBytes)
            val rawSecret = myPrivateKey.calculateAgreement(theirPublicKey)
            val aesKey = hkdf(rawSecret, "SinusChat-E2E-v1".toByteArray(), 32)
            sharedSecretCache[theirPublicKeyBase64] = aesKey
            aesKey
        } catch (e: Exception) { Log.e(TAG, "Shared secret failed", e); null }
    }

    fun getSharedSecretCached(context: Context, otherPhoneHash: String): ByteArray? {
        val key = pl.sinusoide.sinuschat.util.PeerKeyCache.getKey(context, otherPhoneHash) ?: return null
        return computeSharedSecret(context, key)
    }

    fun getSharedSecretForUser(context: Context, otherPhoneHash: String, onComplete: (ByteArray?) -> Unit) {
        val cached = getSharedSecretCached(context, otherPhoneHash)
        if (cached != null) {
            pl.sinusoide.sinuschat.util.AppExecutor.main { onComplete(cached) }
            pl.sinusoide.sinuschat.util.AppExecutor.io { fetchAndCacheKey(context, otherPhoneHash) }
            return
        }
        fetchSharedSecret(context, otherPhoneHash, onComplete)
    }

    private fun fetchSharedSecret(context: Context, otherPhoneHash: String, onComplete: (ByteArray?) -> Unit) {
        pl.sinusoide.sinuschat.util.AppExecutor.io {
            val key = fetchAndCacheKey(context, otherPhoneHash)
            val secret = if (key != null) computeSharedSecret(context, key) else null
            if (secret == null) Log.w(TAG, "getSharedSecretForUser: no key for $otherPhoneHash")
            pl.sinusoide.sinuschat.util.AppExecutor.main { onComplete(secret) }
        }
    }

    fun fetchAndCacheKey(context: Context, otherPhoneHash: String): String? {
        val user = pl.sinusoide.sinuschat.network.ApiClient.getObject("/api/user/$otherPhoneHash", context)
        val key  = user?.optStringOrNull("e2e_public_key") ?: return null
        pl.sinusoide.sinuschat.util.PeerKeyCache.saveKey(context, otherPhoneHash, key)
        return key
    }

    fun encrypt(plaintext: String, sharedSecret: ByteArray): String? = try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(sharedSecret, "AES"))
        toBase64(cipher.iv + cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)))
    } catch (e: Exception) { Log.e(TAG, "Encrypt failed", e); null }

    fun decrypt(encryptedBase64: String, sharedSecret: ByteArray): String? = try {
        val combined = fromBase64(encryptedBase64)
        if (combined.size < 13) null
        else {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(sharedSecret, "AES"),
                GCMParameterSpec(128, combined.copyOfRange(0, 12)))
            String(cipher.doFinal(combined.copyOfRange(12, combined.size)), Charsets.UTF_8)
        }
    } catch (e: Exception) { Log.e(TAG, "Decrypt failed: ${e.message}"); null }

    private fun hkdf(inputKey: ByteArray, info: ByteArray, len: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(ByteArray(32), "HmacSHA256"))
        val prk = mac.doFinal(inputKey)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        return mac.doFinal(info + byteArrayOf(0x01)).copyOf(len)
    }

    fun sha256(input: String): String {
        val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun toBase64(data: ByteArray): String = Base64.encodeToString(data, Base64.NO_WRAP)
    private fun fromBase64(data: String): ByteArray = Base64.decode(data, Base64.NO_WRAP)
}
