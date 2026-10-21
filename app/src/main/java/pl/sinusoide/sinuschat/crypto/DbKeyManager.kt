package pl.sinusoide.sinuschat.crypto

import android.content.Context
import android.content.Intent
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.util.Base64
import android.util.Log
import pl.sinusoide.sinuschat.util.RecoveryKey
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

object DbKeyManager {

    private const val TAG = "DbKeyManager"
    private const val PREFS = "db_key_store"
    private const val KS_ALIAS = "sinus_db_wrap_key"
    private const val KS_ALIAS_BIO = "sinus_db_bio_key"
    private const val KS_ALIAS_BIO_RSA = "sinus_db_bio_rsa"
    private const val ANDROID_KS = "AndroidKeyStore"

    private const val K_SALT = "salt"
    private const val K_KS_IV = "ks_iv"
    private const val K_BLOB = "wrapped"
    private const val K_BLOB_BIO = "wrapped_bio"
    private const val K_BLOB_BIO_RSA = "wrapped_bio_rsa"
    private const val K_BIO_TAG = "bio_tag"
    private const val K_BLOB_RK = "wrapped_rk"
    private const val K_RK_SALT = "rk_salt"
    private const val K_RK_ID = "rk_id"
    private const val K_RK_TAG = "rk_tag"
    private const val RK_LABEL = "SinusChat db unlock v1"
    private const val K_STRONGBOX = "strongbox"
    private const val K_MIGRATION_ERROR = "migration_error"
    private const val K_IS_MIGRATING = "is_migrating"
    private const val K_IS_TRANSPARENT = "is_transparent"
    private const val K_FAST_NO_PIN = "no_pin_fast"

    private const val PBKDF2_ITERS = 210_000
    private const val GCM_TAG_BITS = 128
    private const val DB_KEY_LEN = 32
    private const val SALT_LEN = 16
    private const val GCM_IV_LEN = 12

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context): Boolean = prefs(ctx).contains(K_BLOB)

    fun isTransparent(ctx: Context): Boolean = isEnabled(ctx) && prefs(ctx).getBoolean(K_IS_TRANSPARENT, false)

    fun needsPinUnlock(ctx: Context): Boolean = isEnabled(ctx) && cachedKey() == null && !isTransparent(ctx)

    fun getMigrationError(ctx: Context): String? = prefs(ctx).getString(K_MIGRATION_ERROR, null)

    fun setMigrationError(ctx: Context, error: String?) {
        prefs(ctx).edit().putString(K_MIGRATION_ERROR, error).apply()
    }

    fun isMigrating(ctx: Context): Boolean = prefs(ctx).getBoolean(K_IS_MIGRATING, false)

    fun setMigrating(ctx: Context, migrating: Boolean) {
        prefs(ctx).edit().putBoolean(K_IS_MIGRATING, migrating).apply()
        ctx.sendBroadcast(Intent("pl.sinusoide.sinuschat.DB_MIGRATION_STATUS_CHANGED"))
    }

    fun initTransparent(ctx: Context): ByteArray? {
        if (isEnabled(ctx)) return if (isTransparent(ctx)) unlockTransparent(ctx) else null
        return try {
            val dbKey = ByteArray(DB_KEY_LEN).also { SecureRandom().nextBytes(it) }
            val dbFile = ctx.getDatabasePath("sinuschat_messages.db")
            if (dbFile.exists() && DbCrypto.isDbEncrypted(ctx)) {
                Log.w(TAG, "initTransparent: zaszyfrowana baza bez klucza — usuwam")
                for (f in listOf(dbFile, java.io.File(dbFile.path + "-wal"), java.io.File(dbFile.path + "-shm"))) {
                    if (f.exists()) f.delete()
                }
            }
            if (dbFile.exists() && !DbCrypto.isDbEncrypted(ctx)) {
                if (!DbCrypto.encryptExisting(ctx, dbKey)) {
                    Log.e(TAG, "initTransparent: nie udało się zaszyfrować istniejącej bazy — ponowię przy następnym starcie")
                    dbKey.fill(0)
                    return null
                }
            }
            val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
            storeWrapped(ctx, dbKey, "", salt)
            sessionKey = dbKey
            syncUnlockCopiesAsync(ctx)
            dbKey
        } catch (e: Throwable) {
            Log.e(TAG, "initTransparent failed", e); null
        }
    }

    private fun unlockTransparent(ctx: Context): ByteArray? {
        val key = unlock(ctx, "")
        if (key != null) {
            sessionKey = key
            syncUnlockCopiesAsync(ctx)
        }
        return key
    }

    private val OAEP = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
    private const val RSA_OAEP = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"

    fun hasBiometricKey(ctx: Context): Boolean = prefs(ctx).contains(K_BLOB_BIO_RSA)

    private fun setupBiometricKey(ctx: Context, dbKey: ByteArray) {
        try {
            val ks = KeyStore.getInstance(ANDROID_KS).apply { load(null) }
            if (!ks.containsAlias(KS_ALIAS_BIO_RSA)) {
                val spec = KeyGenParameterSpec.Builder(KS_ALIAS_BIO_RSA, KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(2048)
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                    .setUserAuthenticationRequired(true)
                    .setInvalidatedByBiometricEnrollment(true)
                    .apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                            setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                        else @Suppress("DEPRECATION") setUserAuthenticationValidityDurationSeconds(-1)
                    }
                    .build()
                KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KS)
                    .apply { initialize(spec) }.generateKeyPair()
            }
            val sealedPub = ks.getCertificate(KS_ALIAS_BIO_RSA)?.publicKey ?: return
            val pub = KeyFactory.getInstance(sealedPub.algorithm).generatePublic(X509EncodedKeySpec(sealedPub.encoded))
            val cipher = Cipher.getInstance(RSA_OAEP).apply { init(Cipher.ENCRYPT_MODE, pub, OAEP) }
            prefs(ctx).edit()
                .putString(K_BLOB_BIO_RSA, toBase64(cipher.doFinal(dbKey)))
                .putString(K_BIO_TAG, keyTag(dbKey))
                .apply()
        } catch (e: Throwable) {
            Log.w(TAG, "setupBiometricKey: ${e.message}")
            removeBiometricKey(ctx)
        }
    }

    fun removeBiometricKey(ctx: Context) {
        prefs(ctx).edit().remove(K_BLOB_BIO_RSA).remove(K_BIO_TAG).apply()
        try {
            val ks = KeyStore.getInstance(ANDROID_KS).apply { load(null) }
            if (ks.containsAlias(KS_ALIAS_BIO_RSA)) ks.deleteEntry(KS_ALIAS_BIO_RSA)
        } catch (_: Throwable) {}
    }

    fun dropLegacyBio(ctx: Context) {
        if (prefs(ctx).contains(K_BLOB_BIO)) prefs(ctx).edit().remove(K_BLOB_BIO).apply()
        try {
            val ks = KeyStore.getInstance(ANDROID_KS).apply { load(null) }
            if (ks.containsAlias(KS_ALIAS_BIO)) ks.deleteEntry(KS_ALIAS_BIO)
        } catch (_: Throwable) {}
    }

    fun biometricCipher(ctx: Context): Cipher? {
        if (!hasBiometricKey(ctx)) return null
        return try {
            val ks = KeyStore.getInstance(ANDROID_KS).apply { load(null) }
            val priv = ks.getKey(KS_ALIAS_BIO_RSA, null) as? PrivateKey ?: run { removeBiometricKey(ctx); return null }
            Cipher.getInstance(RSA_OAEP).apply { init(Cipher.DECRYPT_MODE, priv, OAEP) }
        } catch (e: KeyPermanentlyInvalidatedException) {
            Log.w(TAG, "odcisk palca w telefonie się zmienił — kopia do odtworzenia po PIN-ie")
            removeBiometricKey(ctx)
            null
        } catch (e: Throwable) {
            Log.e(TAG, "biometricCipher", e); null
        }
    }

    fun unlockWithBiometricCipher(ctx: Context, cipher: Cipher): Boolean {
        val blob = prefs(ctx).getString(K_BLOB_BIO_RSA, null) ?: return false
        return try {
            sessionKey = cipher.doFinal(fromBase64(blob))
            syncUnlockCopiesAsync(ctx)
            true
        } catch (e: Throwable) {
            Log.e(TAG, "unlockWithBiometricCipher", e); false
        }
    }

    fun hasRecoveryUnlock(ctx: Context): Boolean = prefs(ctx).contains(K_BLOB_RK)

    fun recoveryUnlockId(ctx: Context): ByteArray? = prefs(ctx).getString(K_RK_ID, null)?.let { fromBase64(it) }

    private fun setupRecoveryUnlock(ctx: Context, dbKey: ByteArray, rk: RecoveryKey) {
        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(GCM_IV_LEN).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(rk.proof(RK_LABEL, salt), "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        prefs(ctx).edit()
            .putString(K_BLOB_RK, toBase64(iv + cipher.doFinal(dbKey)))
            .putString(K_RK_SALT, toBase64(salt))
            .putString(K_RK_ID, toBase64(rk.id))
            .putString(K_RK_TAG, keyTag(dbKey))
            .apply()
    }

    fun unlockWithRecoveryKey(ctx: Context, rk: RecoveryKey): Boolean {
        return try {
            val p = prefs(ctx)
            val blob = fromBase64(p.getString(K_BLOB_RK, null) ?: return false)
            val salt = fromBase64(p.getString(K_RK_SALT, null) ?: return false)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(rk.proof(RK_LABEL, salt), "AES"),
                GCMParameterSpec(GCM_TAG_BITS, blob.copyOfRange(0, GCM_IV_LEN)))
            sessionKey = cipher.doFinal(blob.copyOfRange(GCM_IV_LEN, blob.size))
            syncUnlockCopiesAsync(ctx)
            true
        } catch (e: AEADBadTagException) { false }
        catch (e: Throwable) { Log.e(TAG, "unlockWithRecoveryKey", e); false }
    }

    fun seal(label: String, plain: ByteArray): String? {
        val key = sessionKey ?: return null
        return try {
            val iv = ByteArray(GCM_IV_LEN).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(hmac(key, label), "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            toBase64(iv + cipher.doFinal(plain))
        } catch (e: Throwable) { Log.e(TAG, "seal", e); null }
    }

    fun unseal(label: String, sealed: String): ByteArray? {
        val key = sessionKey ?: return null
        return try {
            val raw = fromBase64(sealed)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(hmac(key, label), "AES"),
                GCMParameterSpec(GCM_TAG_BITS, raw.copyOfRange(0, GCM_IV_LEN)))
            cipher.doFinal(raw.copyOfRange(GCM_IV_LEN, raw.size))
        } catch (e: Throwable) { null }
    }

    @Synchronized
    fun syncUnlockCopies(ctx: Context) {
        val key = sessionKey ?: return
        try {
            dropLegacyBio(ctx)
            val tag = keyTag(key)
            val p = prefs(ctx)
            val wantBio = pl.sinusoide.sinuschat.LockManager.hasPin(ctx) &&
                pl.sinusoide.sinuschat.LockManager.isBiometricEnabled(ctx)
            if (!wantBio) {
                if (hasBiometricKey(ctx)) removeBiometricKey(ctx)
            } else if (!hasBiometricKey(ctx) || p.getString(K_BIO_TAG, null) != tag) {
                setupBiometricKey(ctx, key)
            }
            val rk = pl.sinusoide.sinuschat.util.BackupPrefs.recoveryKey(ctx)
            if (rk == null) {
                if (hasRecoveryUnlock(ctx) && !pl.sinusoide.sinuschat.util.BackupPrefs.hasRecoveryKey(ctx)) {
                    p.edit().remove(K_BLOB_RK).remove(K_RK_SALT).remove(K_RK_ID).remove(K_RK_TAG).apply()
                }
            } else if (!hasRecoveryUnlock(ctx) || p.getString(K_RK_TAG, null) != tag ||
                recoveryUnlockId(ctx)?.let { rk.matches(it) } != true) {
                setupRecoveryUnlock(ctx, key, rk)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "syncUnlockCopies", e)
        }
    }

    fun syncUnlockCopiesAsync(ctx: Context) {
        val app = ctx.applicationContext
        pl.sinusoide.sinuschat.util.AppExecutor.io { syncUnlockCopies(app) }
    }

    private fun keyTag(key: ByteArray): String = toBase64(hmac(key, "SinusChat key tag").copyOf(8))

    private fun hmac(key: ByteArray, label: String): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(label.toByteArray(Charsets.UTF_8))
        }

    private fun toBase64(x: ByteArray) = Base64.encodeToString(x, Base64.NO_WRAP)
    private fun fromBase64(x: String) = Base64.decode(x, Base64.NO_WRAP)
    private fun b64(x: ByteArray) = toBase64(x)
    private fun b64(x: String) = fromBase64(x)

    fun setUp(ctx: Context, pin: String): ByteArray? {
        return try {
            val dbKey = ByteArray(DB_KEY_LEN).also { SecureRandom().nextBytes(it) }
            val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
            storeWrapped(ctx, dbKey, pin, salt)
            dbKey
        } catch (e: Throwable) {
            Log.e(TAG, "setUp failed", e); null
        }
    }

    fun unlock(ctx: Context, pin: String): ByteArray? {
        return try {
            val p = prefs(ctx)
            val salt = b64(p.getString(K_SALT, null) ?: return null)
            val ksIv = b64(p.getString(K_KS_IV, null) ?: return null)
            val blob = b64(p.getString(K_BLOB, null) ?: return null)

            val ksKey = loadKsKey() ?: return null
            val ksCipher = Cipher.getInstance("AES/GCM/NoPadding")
            ksCipher.init(Cipher.DECRYPT_MODE, ksKey, GCMParameterSpec(GCM_TAG_BITS, ksIv))
            val pinBlob = ksCipher.doFinal(blob)

            val pinIv = pinBlob.copyOfRange(0, GCM_IV_LEN)
            val pinCt = pinBlob.copyOfRange(GCM_IV_LEN, pinBlob.size)
            fun open(kp: SecretKey): ByteArray {
                val pinCipher = Cipher.getInstance("AES/GCM/NoPadding")
                pinCipher.init(Cipher.DECRYPT_MODE, kp, GCMParameterSpec(GCM_TAG_BITS, pinIv))
                return pinCipher.doFinal(pinCt)
            }
            if (pin.isEmpty() && p.getBoolean(K_FAST_NO_PIN, false)) {
                try { return open(noPinKey(salt)) } catch (_: AEADBadTagException) {}
            }
            val key = try {
                open(deriveKp(pin, salt))
            } catch (e: AEADBadTagException) {
                if (pin.isEmpty()) open(deriveRaw("", salt)) else throw e
            }
            if (pin.isEmpty() && p.getBoolean(K_IS_TRANSPARENT, false)) {
                try { storeWrapped(ctx, key, "", ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }) }
                catch (e: Throwable) { Log.w(TAG, "Szybkie odblokowanie bez PIN-u nie zapisane", e) }
            }
            key
        } catch (e: AEADBadTagException) { null }
        catch (e: Throwable) { Log.e(TAG, "unlock failed", e); null }
    }

    fun rewrapPin(ctx: Context, oldPin: String, newPin: String): Boolean {
        val dbKey = unlock(ctx, oldPin) ?: return false
        return try {
            val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
            storeWrapped(ctx, dbKey, newPin, salt)
            true
        } catch (e: Throwable) { Log.e(TAG, "rewrapPin failed", e); false }
        finally { dbKey.fill(0) }
    }

    fun wipe(ctx: Context) {
        try {
            val ks = KeyStore.getInstance(ANDROID_KS).apply { load(null) }
            if (ks.containsAlias(KS_ALIAS)) ks.deleteEntry(KS_ALIAS)
            if (ks.containsAlias(KS_ALIAS_BIO)) ks.deleteEntry(KS_ALIAS_BIO)
            if (ks.containsAlias(KS_ALIAS_BIO_RSA)) ks.deleteEntry(KS_ALIAS_BIO_RSA)
        } catch (e: Throwable) { Log.e(TAG, "wipe ks failed", e) }
        prefs(ctx).edit().clear().apply()
    }

    private fun storeWrapped(ctx: Context, dbKey: ByteArray, pin: String, salt: ByteArray) {
        val kp = if (pin.isEmpty()) noPinKey(salt) else deriveKp(pin, salt)
        val pinIv = ByteArray(GCM_IV_LEN).also { SecureRandom().nextBytes(it) }
        val pinCipher = Cipher.getInstance("AES/GCM/NoPadding")
        pinCipher.init(Cipher.ENCRYPT_MODE, kp, GCMParameterSpec(GCM_TAG_BITS, pinIv))
        val pinCt = pinCipher.doFinal(dbKey)
        val pinBlob = pinIv + pinCt

        val strongBox = ensureKsKey()
        val ksKey = loadKsKey() ?: throw IllegalStateException("brak klucza Keystore")
        val ksCipher = Cipher.getInstance("AES/GCM/NoPadding")
        ksCipher.init(Cipher.ENCRYPT_MODE, ksKey)
        val ksIv = ksCipher.iv
        val blob = ksCipher.doFinal(pinBlob)

        prefs(ctx).edit()
            .putString(K_SALT, b64(salt))
            .putString(K_KS_IV, b64(ksIv))
            .putString(K_BLOB, b64(blob))
            .putBoolean(K_STRONGBOX, strongBox)
            .putBoolean(K_IS_TRANSPARENT, pin.isEmpty())
            .putBoolean(K_FAST_NO_PIN, pin.isEmpty())
            .commit()
    }

    private const val NO_PIN_SECRET = "sinuschat:no-pin"

    private fun noPinKey(salt: ByteArray): SecretKey {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        digest.update(NO_PIN_SECRET.toByteArray(Charsets.UTF_8))
        digest.update(salt)
        return SecretKeySpec(digest.digest(), "AES")
    }

    private fun deriveKp(pin: String, salt: ByteArray): SecretKey =
        deriveRaw(if (pin.isEmpty()) NO_PIN_SECRET else pin, salt)

    private fun deriveRaw(pin: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private fun ensureKsKey(): Boolean {
        val ks = KeyStore.getInstance(ANDROID_KS).apply { load(null) }
        if (ks.containsAlias(KS_ALIAS)) return isStrongBoxEntry()
        val useStrongBox = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        if (useStrongBox) {
            try { generateKsKey(true); return true }
            catch (e: Throwable) { Log.w(TAG, "StrongBox niedostępny", e) }
        }
        generateKsKey(false); return false
    }

    private fun generateKsKey(strongBox: Boolean) {
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KS)
        val spec = KeyGenParameterSpec.Builder(KS_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .apply { if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setIsStrongBoxBacked(true) }
            .build()
        kg.init(spec); kg.generateKey()
    }

    private fun loadKsKey(): SecretKey? {
        val ks = KeyStore.getInstance(ANDROID_KS).apply { load(null) }
        return (ks.getEntry(KS_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    private fun isStrongBoxEntry(): Boolean = false

    @Volatile private var sessionKey: ByteArray? = null

    fun unlockSession(ctx: Context, pin: String): Boolean {
        val k = unlock(ctx, pin) ?: return false
        sessionKey = k
        syncUnlockCopiesAsync(ctx)
        return true
    }

    fun cacheKey(key: ByteArray) { sessionKey = key.copyOf() }
    fun cachedKey(): ByteArray? = sessionKey
    fun passphrase(key: ByteArray): String = Base64.encodeToString(key, Base64.NO_WRAP)
    fun sessionPassphraseBytes(): ByteArray? = sessionKey?.let { passphrase(it).toByteArray(Charsets.UTF_8) }

    fun rewrapWithCachedKey(ctx: Context, newPin: String): Boolean {
        val k = sessionKey ?: return false
        return try {
            val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
            storeWrapped(ctx, k, newPin, salt)
            syncUnlockCopies(ctx)
            true
        } catch (e: Throwable) { Log.e(TAG, "rewrapWithCachedKey failed", e); false }
    }
}
