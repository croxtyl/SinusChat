package pl.sinusoide.sinuschat

import android.content.Context
import android.content.SharedPreferences
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import pl.sinusoide.sinuschat.setup.SetupActivity
import java.security.MessageDigest

object LockManager {

    @Volatile var sessionUnlocked = false

    private const val KEY_RELOCK = "relock_after_ms"
    private const val RELOCK_DEFAULT = 30_000L

    val RELOCK_OPTIONS = longArrayOf(15_000L, 30_000L, 60_000L, 120_000L,
        180_000L, 300_000L, 600_000L, 1_800_000L)

    fun relockAfterMs(ctx: Context): Long =
        prefs(ctx).getLong(KEY_RELOCK, RELOCK_DEFAULT).takeIf { it in RELOCK_OPTIONS } ?: RELOCK_DEFAULT

    fun setRelockAfterMs(ctx: Context, ms: Long) {
        prefs(ctx).edit().putLong(KEY_RELOCK, ms).apply()
    }

    @Volatile private var backgroundSinceMs = 0L

    fun onAppBackground() {
        backgroundSinceMs = android.os.SystemClock.elapsedRealtime()
    }

    fun onAppForeground(ctx: Context) {
        expireIfIdle(ctx)
        backgroundSinceMs = 0L
    }

    fun exportForTransfer(ctx: Context): org.json.JSONObject = org.json.JSONObject()
        .put("autoConfirm", isAutoConfirmEnabled(ctx))
        .put("dots", isShowPinDots(ctx))
        .put("silent", isSilentUnlock(ctx))
        .put("shuffle", isShuffleKeypad(ctx))
        .put("relockMs", relockAfterMs(ctx))

    fun importFromTransfer(ctx: Context, o: org.json.JSONObject) {
        setAutoConfirm(ctx, o.optBoolean("autoConfirm", true))
        setShowPinDots(ctx, o.optBoolean("dots", true))
        setSilentUnlock(ctx, o.optBoolean("silent", false))
        setShuffleKeypad(ctx, o.optBoolean("shuffle", false))
        o.optLong("relockMs").takeIf { it in RELOCK_OPTIONS }?.let { setRelockAfterMs(ctx, it) }
    }

    fun onScreenOff() {
        sessionUnlocked = false
    }

    fun expireIfIdle(ctx: Context) {
        val since = backgroundSinceMs
        if (since != 0L && android.os.SystemClock.elapsedRealtime() - since >= relockAfterMs(ctx))
            sessionUnlocked = false
    }

    private const val PREFS_NAME  = "sinuschat_lock"
    private const val KEY_PIN     = "pin_hash"
    private const val KEY_ENABLED = "lock_enabled"
    private const val KEY_BIO     = "biometric_enabled"

    private const val KEY_DURESS_PIN     = "duress_pin_hash"
    private const val KEY_DURESS_ENABLED = "duress_enabled"

    private const val KEY_AUTO_CONFIRM   = "auto_confirm_pin"
    private const val KEY_WIPE_ENABLED  = "wipe_on_fail_enabled"
    private const val KEY_FAIL_COUNT    = "fail_count"
    private const val KEY_SILENT        = "silent_unlock"
    private const val KEY_SHUFFLE       = "shuffle_keypad"
    private const val KEY_PIN_LEN       = "pin_length"
    private const val KEY_DURESS_LEN    = "duress_length"
    private const val MAX_ATTEMPTS = 10

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isLockEnabled(ctx: Context): Boolean {
        return hasPin(ctx)
    }

    fun isAutoConfirmEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_AUTO_CONFIRM, true)

    fun setAutoConfirm(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_AUTO_CONFIRM, enabled).apply()
    }

    fun isShowPinDots(ctx: Context): Boolean = prefs(ctx).getBoolean("show_pin_dots", true)

    fun setShowPinDots(ctx: Context, show: Boolean) {
        prefs(ctx).edit().putBoolean("show_pin_dots", show).apply()
    }

    fun isSilentUnlock(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_SILENT, false)

    fun setSilentUnlock(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_SILENT, enabled).apply()
    }

    fun isShuffleKeypad(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_SHUFFLE, false)

    fun setShuffleKeypad(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_SHUFFLE, enabled).apply()
    }

    fun pinLength(ctx: Context): Int = prefs(ctx).getInt(KEY_PIN_LEN, 0)
    fun duressLength(ctx: Context): Int = prefs(ctx).getInt(KEY_DURESS_LEN, 0)

    fun rememberPinLength(ctx: Context, length: Int) {
        if (pinLength(ctx) != length) prefs(ctx).edit().putInt(KEY_PIN_LEN, length).apply()
    }

    fun isBiometricEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_BIO, false)

    fun hasPin(ctx: Context): Boolean =
        prefs(ctx).getString(KEY_PIN, null) != null

    fun setPin(ctx: Context, pin: String) {
        prefs(ctx).edit()
            .putString(KEY_PIN, verifier(LABEL_PIN, pin))
            .putInt(KEY_PIN_LEN, pin.length)
            .putBoolean(KEY_ENABLED, true)
            .putInt(KEY_FAIL_COUNT, 0)
            .commit()
        clearLockout(ctx)
    }

    fun verifyPin(ctx: Context, pin: String): Boolean = checkVerifier(ctx, KEY_PIN, LABEL_PIN, pin)

    fun removePin(ctx: Context) {
        prefs(ctx).edit()
            .remove(KEY_PIN)
            .remove(KEY_DURESS_PIN)
            .remove(KEY_PIN_LEN)
            .remove(KEY_DURESS_LEN)
            .putBoolean(KEY_ENABLED, false)
            .putBoolean(KEY_BIO, false)
            .putBoolean(KEY_DURESS_ENABLED, false)
            .putBoolean(KEY_WIPE_ENABLED, false)
            .putBoolean(KEY_SHUFFLE, false)
            .putInt(KEY_FAIL_COUNT, 0)
            .commit()
        clearLockout(ctx)
        pl.sinusoide.sinuschat.crypto.DbKeyManager.syncUnlockCopiesAsync(ctx)
    }

    fun setBiometric(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_BIO, enabled).remove(KEY_BIO_REMOVED_SHOWN).commit()
        pl.sinusoide.sinuschat.crypto.DbKeyManager.syncUnlockCopiesAsync(ctx)
    }

    private const val KEY_BIO_REMOVED_SHOWN = "biometric_removed_notice_shown"

    fun shouldShowBiometricRemovedNotice(ctx: Context): Boolean {
        if (!hasPin(ctx) || !isBiometricEnabled(ctx)) return false
        val status = BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        val removed = status == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
        val p = prefs(ctx)
        if (!removed) {
            if (p.contains(KEY_BIO_REMOVED_SHOWN)) p.edit().remove(KEY_BIO_REMOVED_SHOWN).apply()
            return false
        }
        return !p.getBoolean(KEY_BIO_REMOVED_SHOWN, false)
    }

    fun markBiometricRemovedNoticeShown(ctx: Context) {
        prefs(ctx).edit().putBoolean(KEY_BIO_REMOVED_SHOWN, true).apply()
    }

    fun hasDuressPin(ctx: Context): Boolean =
        prefs(ctx).getString(KEY_DURESS_PIN, null) != null

    fun isDuressEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_DURESS_ENABLED, false) && hasDuressPin(ctx)

    fun setDuressPin(ctx: Context, pin: String) {
        prefs(ctx).edit()
            .putString(KEY_DURESS_PIN, verifier(LABEL_DURESS, pin))
            .putInt(KEY_DURESS_LEN, pin.length)
            .putBoolean(KEY_DURESS_ENABLED, true)
            .apply()
    }

    fun removeDuressPin(ctx: Context) {
        prefs(ctx).edit()
            .remove(KEY_DURESS_PIN)
            .remove(KEY_DURESS_LEN)
            .putBoolean(KEY_DURESS_ENABLED, false)
            .apply()
    }

    fun verifyDuressPin(ctx: Context, pin: String): Boolean {
        if (!isDuressEnabled(ctx)) return false
        return checkVerifier(ctx, KEY_DURESS_PIN, LABEL_DURESS, pin)
    }

    fun isWipeOnFailEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_WIPE_ENABLED, false)

    fun setWipeOnFail(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit()
            .putBoolean(KEY_WIPE_ENABLED, enabled)
            .putInt(KEY_FAIL_COUNT, 0)
            .apply()
    }

    fun getFailCount(ctx: Context): Int =
        prefs(ctx).getInt(KEY_FAIL_COUNT, 0)

    fun incrementFailCount(ctx: Context): Int {
        val newCount = getFailCount(ctx) + 1
        prefs(ctx).edit().putInt(KEY_FAIL_COUNT, newCount).apply()
        return newCount
    }

    fun resetFailCount(ctx: Context) {
        prefs(ctx).edit().putInt(KEY_FAIL_COUNT, 0).apply()
        clearLockout(ctx)
    }

    private const val LOCKOUT_AFTER = 5
    private const val LOCKOUT_STEP_MS = 5 * 60_000L
    private const val LOCKOUT_MAX_MS = 24 * 3600_000L
    private const val KEY_LOCK_DURATION = "lockout_duration"
    private const val KEY_LOCK_END_WALL = "lockout_end_wall"
    private const val KEY_LOCK_END_ELAPSED = "lockout_end_elapsed"
    private const val KEY_LOCK_BOOT = "lockout_boot"

    fun lockoutFor(attempts: Int): Long =
        if (attempts < LOCKOUT_AFTER) 0L
        else minOf((attempts - LOCKOUT_AFTER + 1) * LOCKOUT_STEP_MS, LOCKOUT_MAX_MS)

    fun startLockout(ctx: Context, durationMs: Long) {
        prefs(ctx).edit()
            .putLong(KEY_LOCK_DURATION, durationMs)
            .putLong(KEY_LOCK_END_WALL, System.currentTimeMillis() + durationMs)
            .putLong(KEY_LOCK_END_ELAPSED, android.os.SystemClock.elapsedRealtime() + durationMs)
            .putInt(KEY_LOCK_BOOT, bootCount(ctx))
            .commit()
    }

    fun lockoutRemainingMs(ctx: Context): Long {
        val p = prefs(ctx)
        val duration = p.getLong(KEY_LOCK_DURATION, 0L)
        if (duration <= 0L) return 0L
        val boot = bootCount(ctx)
        val nowElapsed = android.os.SystemClock.elapsedRealtime()
        val remaining = if (boot == p.getInt(KEY_LOCK_BOOT, Int.MIN_VALUE)) {
            p.getLong(KEY_LOCK_END_ELAPSED, 0L) - nowElapsed
        } else {
            val endWall = p.getLong(KEY_LOCK_END_WALL, 0L)
            val nowWall = System.currentTimeMillis()
            val r = (if (nowWall < endWall - duration) duration else endWall - nowWall).coerceIn(0L, duration)
            p.edit().putInt(KEY_LOCK_BOOT, boot).putLong(KEY_LOCK_END_ELAPSED, nowElapsed + r).apply()
            r
        }
        if (remaining <= 0L) {
            clearLockout(ctx)
            return 0L
        }
        return remaining.coerceAtMost(duration)
    }

    private fun clearLockout(ctx: Context) {
        val p = prefs(ctx)
        if (!p.contains(KEY_LOCK_DURATION)) return
        p.edit().remove(KEY_LOCK_DURATION).remove(KEY_LOCK_END_WALL)
            .remove(KEY_LOCK_END_ELAPSED).remove(KEY_LOCK_BOOT).apply()
    }

    private fun bootCount(ctx: Context): Int = try {
        android.provider.Settings.Global.getInt(ctx.contentResolver, android.provider.Settings.Global.BOOT_COUNT, -1)
    } catch (_: Throwable) { -1 }

    fun maxAttempts(): Int = MAX_ATTEMPTS

    fun wipeAllChats(ctx: Context) {
        pl.sinusoide.sinuschat.messenger.PendingLink.clear()
        pl.sinusoide.sinuschat.util.ChatBubbles.clear(ctx)
        try {
            SetupActivity.resetSetup(ctx)

            pl.sinusoide.sinuschat.util.AppExecutor.io {
                val db = pl.sinusoide.sinuschat.data.SinusChatDatabase.getInstance(ctx)
                db.runInTransaction {
                    db.openHelper.writableDatabase.execSQL("DELETE FROM messages")
                    db.openHelper.writableDatabase.execSQL("DELETE FROM conversations")
                }

                pl.sinusoide.sinuschat.network.ApiClient.post(
                    "/api/conversations/wipe",
                    org.json.JSONObject(),
                    ctx
                )
            }

            val mediaRoot = java.io.File(ctx.filesDir, "media")
            if (mediaRoot.exists()) mediaRoot.deleteRecursively()
            
            val avatarFile = java.io.File(ctx.filesDir, "my_avatar.jpg")
            if (avatarFile.exists()) avatarFile.delete()

        } catch (e: Exception) {
            android.util.Log.e("LockManager", "wipeAllChats failed", e)
        }
    }

    fun isBiometricAvailable(ctx: Context): Boolean {
        val bm = BiometricManager.from(ctx)
        return bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                BiometricManager.BIOMETRIC_SUCCESS
    }

    fun isHardwareDetected(ctx: Context): Boolean {
        val bm = BiometricManager.from(ctx)
        val result = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        return result != BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE &&
               result != BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE
    }

    fun showBiometricCryptoPrompt(
        activity: FragmentActivity,
        cipher: javax.crypto.Cipher,
        onSuccess: (javax.crypto.Cipher) -> Unit,
        onFailed: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val c = result.cryptoObject?.cipher
                    if (c != null) onSuccess(c) else onFailed()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onFailed()
                }
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.unlock_sinuschat))
            .setSubtitle(activity.getString(R.string.lock_use_fingerprint))
            .setNegativeButtonText(activity.getString(R.string.cancel))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    fun showBiometricPrompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFailed: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onFailed()
                }
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.unlock_sinuschat))
            .setSubtitle(activity.getString(R.string.lock_use_fingerprint))
            .setNegativeButtonText(activity.getString(R.string.cancel))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
        prompt.authenticate(info)
    }

    private const val KS_PIN_MAC = "sinus_pin_mac"
    private const val MAC_PREFIX = "m1:"
    private const val LABEL_PIN = "pin"
    private const val LABEL_DURESS = "duress"

    private fun pinMac(label: String, pin: String): String? = try {
        val ks = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias(KS_PIN_MAC)) {
            javax.crypto.KeyGenerator.getInstance(
                android.security.keystore.KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore"
            ).apply {
                init(android.security.keystore.KeyGenParameterSpec.Builder(
                    KS_PIN_MAC, android.security.keystore.KeyProperties.PURPOSE_SIGN).build())
            }.generateKey()
        }
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(ks.getKey(KS_PIN_MAC, null) as javax.crypto.SecretKey)
        MAC_PREFIX + hex(mac.doFinal("$label:$pin".toByteArray(Charsets.UTF_8)))
    } catch (e: Throwable) {
        android.util.Log.e("LockManager", "pinMac", e)
        null
    }

    private fun verifier(label: String, pin: String): String = pinMac(label, pin) ?: hash(pin)

    private fun checkVerifier(ctx: Context, key: String, label: String, pin: String): Boolean {
        val stored = prefs(ctx).getString(key, null) ?: return false
        if (stored.startsWith(MAC_PREFIX)) {
            val mac = pinMac(label, pin) ?: return false
            return MessageDigest.isEqual(mac.toByteArray(), stored.toByteArray())
        }
        val ok = MessageDigest.isEqual(hash(pin).toByteArray(), stored.toByteArray())
        if (ok) pinMac(label, pin)?.let { prefs(ctx).edit().putString(key, it).apply() }
        return ok
    }

    private fun hash(text: String): String =
        hex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray()))

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
