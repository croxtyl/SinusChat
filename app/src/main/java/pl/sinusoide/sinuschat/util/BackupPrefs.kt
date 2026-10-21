package pl.sinusoide.sinuschat.util

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object BackupPrefs {

    private const val PREFS = "sinuschat_backup"
    private const val SECRET_PREFS = "sinuschat_backup_secret"
    private const val KEY_PASS = "passphrase"
    private const val KEY_RECOVERY = "recovery_key"
    private const val KEY_RECOVERY_ID = "recovery_key_id"
    private const val KEY_RECOVERY_AT = "recovery_key_at"
    private const val KEY_RECOVERY_SEALED = "recovery_key_sealed"
    private const val SEAL_LABEL = "SinusChat recovery key at rest v1"

    private const val KEY_INTERVAL = "interval_sec"
    private const val KEY_MEDIA = "with_media"
    private const val KEY_FOLDER = "folder_uri"
    private const val KEY_FOLDER_NAME = "folder_name"
    private const val KEY_REMIND = "remind_mode"
    private const val KEY_LAST_MS = "last_backup_ms"
    private const val KEY_LAST_NAME = "last_backup_name"
    private const val KEY_ASKED_MS = "last_asked_ms"
    private const val KEY_TARGET = "target"
    private const val KEY_DRIVE_ACC = "drive_account"
    private const val KEY_DRIVE_HIDDEN = "drive_hidden"

    const val DAY = 86_400L
    const val WEEK = 7 * DAY
    const val MONTH = 30 * DAY
    const val QUARTER = 90 * DAY
    const val YEAR = 365 * DAY

    val PRESETS = longArrayOf(DAY, WEEK, MONTH, QUARTER, YEAR)

    const val TARGET_USB = 0
    const val TARGET_DRIVE = 1
    const val TARGET_LOCAL = 2

    const val REMIND_NOTIFICATION = 0
    const val REMIND_ON_OPEN = 1
    const val REMIND_BOTH = 2

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun secret(ctx: Context) = try {
        val app = ctx.applicationContext
        EncryptedSharedPreferences.create(
            app, SECRET_PREFS,
            MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Throwable) {
        Log.e("BackupPrefs", "magazyn hasła niedostępny", e)
        null
    }

    fun intervalSec(ctx: Context): Long = prefs(ctx).getLong(KEY_INTERVAL, 0L)

    fun setIntervalSec(ctx: Context, sec: Long) =
        prefs(ctx).edit().putLong(KEY_INTERVAL, sec).apply()

    fun withMedia(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_MEDIA, true)

    fun setWithMedia(ctx: Context, on: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_MEDIA, on).apply()

    fun remindMode(ctx: Context): Int = prefs(ctx).getInt(KEY_REMIND, REMIND_BOTH)

    fun setRemindMode(ctx: Context, mode: Int) =
        prefs(ctx).edit().putInt(KEY_REMIND, mode).apply()

    fun folderUri(ctx: Context): String? = prefs(ctx).getString(KEY_FOLDER, null)

    fun folderName(ctx: Context): String = prefs(ctx).getString(KEY_FOLDER_NAME, "") ?: ""

    fun setFolder(ctx: Context, uri: String?, name: String) =
        prefs(ctx).edit().putString(KEY_FOLDER, uri).putString(KEY_FOLDER_NAME, name).apply()

    fun target(ctx: Context): Int = prefs(ctx).getInt(KEY_TARGET, TARGET_USB)

    fun setTarget(ctx: Context, target: Int) =
        prefs(ctx).edit().putInt(KEY_TARGET, target).apply()

    fun driveHidden(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_DRIVE_HIDDEN, true)

    fun setDriveHidden(ctx: Context, hidden: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_DRIVE_HIDDEN, hidden).apply()

    fun driveAccount(ctx: Context): String = prefs(ctx).getString(KEY_DRIVE_ACC, "") ?: ""

    fun setDriveAccount(ctx: Context, email: String) =
        prefs(ctx).edit().putString(KEY_DRIVE_ACC, email).apply()

    fun lastBackupMs(ctx: Context): Long = prefs(ctx).getLong(KEY_LAST_MS, 0L)

    fun markDone(ctx: Context, name: String) = prefs(ctx).edit()
        .putLong(KEY_LAST_MS, System.currentTimeMillis())
        .putString(KEY_LAST_NAME, name)
        .apply()

    fun lastAskedMs(ctx: Context): Long = prefs(ctx).getLong(KEY_ASKED_MS, 0L)

    fun markAsked(ctx: Context) =
        prefs(ctx).edit().putLong(KEY_ASKED_MS, System.currentTimeMillis()).apply()

    fun nextDueMs(ctx: Context): Long {
        val every = intervalSec(ctx)
        if (every <= 0L) return 0L
        val last = lastBackupMs(ctx)
        return (if (last > 0L) last else System.currentTimeMillis()) + every * 1000L
    }

    fun isDue(ctx: Context): Boolean {
        val due = nextDueMs(ctx)
        return due > 0L && System.currentTimeMillis() >= due
    }

    fun recoveryKey(ctx: Context): RecoveryKey? {
        val p = prefs(ctx)
        p.getString(KEY_RECOVERY_SEALED, null)?.let { sealed ->
            if (pl.sinusoide.sinuschat.crypto.DbKeyManager.cachedKey() == null) return null
            val key = pl.sinusoide.sinuschat.crypto.DbKeyManager.unseal(SEAL_LABEL, sealed)
                ?.let { RecoveryKey.fromStorage(String(it, Charsets.US_ASCII)) }
            if (key == null) p.edit().remove(KEY_RECOVERY_SEALED).remove(KEY_RECOVERY_ID).remove(KEY_RECOVERY_AT).apply()
            return key
        }
        val legacy = try {
            secret(ctx)?.getString(KEY_RECOVERY, null)?.let { RecoveryKey.fromStorage(it) }
        } catch (_: Throwable) { null }
        if (legacy != null) {
            pl.sinusoide.sinuschat.crypto.DbKeyManager.seal(SEAL_LABEL, legacy.toStorage().toByteArray(Charsets.US_ASCII))
                ?.let { sealed ->
                    if (p.edit().putString(KEY_RECOVERY_SEALED, sealed).commit()) {
                        try { secret(ctx)?.edit()?.remove(KEY_RECOVERY)?.commit() } catch (_: Throwable) {}
                    }
                }
            return legacy
        }
        if (recoveryKeyId(ctx).isNotEmpty())
            p.edit().remove(KEY_RECOVERY_ID).remove(KEY_RECOVERY_AT).apply()
        return null
    }

    fun hasRecoveryKey(ctx: Context): Boolean = recoveryKeyId(ctx).isNotEmpty()

    fun recoveryKeyId(ctx: Context): String = prefs(ctx).getString(KEY_RECOVERY_ID, "") ?: ""

    fun recoveryKeyCreatedMs(ctx: Context): Long = prefs(ctx).getLong(KEY_RECOVERY_AT, 0L)

    fun setRecoveryKey(ctx: Context, key: RecoveryKey, createdMs: Long = System.currentTimeMillis()): Boolean {
        val sealed = pl.sinusoide.sinuschat.crypto.DbKeyManager.seal(SEAL_LABEL, key.toStorage().toByteArray(Charsets.US_ASCII))
        val ok = if (sealed != null) {
            val saved = prefs(ctx).edit().putString(KEY_RECOVERY_SEALED, sealed).commit()
            if (saved) try { secret(ctx)?.edit()?.remove(KEY_RECOVERY)?.remove(KEY_PASS)?.commit() } catch (_: Throwable) {}
            saved
        } else {
            val s = secret(ctx) ?: return false
            try { s.edit().putString(KEY_RECOVERY, key.toStorage()).remove(KEY_PASS).commit() } catch (_: Throwable) { false }
        }
        if (ok) {
            prefs(ctx).edit()
                .putString(KEY_RECOVERY_ID, key.shortId)
                .putLong(KEY_RECOVERY_AT, createdMs)
                .apply()
            pl.sinusoide.sinuschat.crypto.DbKeyManager.syncUnlockCopiesAsync(ctx)
        }
        return ok
    }

    fun exportForTransfer(ctx: Context): org.json.JSONObject = org.json.JSONObject()
        .put("interval", intervalSec(ctx))
        .put("media", withMedia(ctx))
        .put("remind", remindMode(ctx))
        .put("target", target(ctx))
        .put("driveHidden", driveHidden(ctx))
        .apply {
            recoveryKey(ctx)?.let {
                put("key", it.toStorage())
                put("keyAt", recoveryKeyCreatedMs(ctx))
            }
        }

    fun importFromTransfer(ctx: Context, o: org.json.JSONObject) {
        prefs(ctx).edit()
            .putLong(KEY_INTERVAL, o.optLong("interval", 0L))
            .putBoolean(KEY_MEDIA, o.optBoolean("media", true))
            .putInt(KEY_REMIND, o.optInt("remind", REMIND_BOTH))
            .putInt(KEY_TARGET, o.optInt("target", TARGET_USB))
            .putBoolean(KEY_DRIVE_HIDDEN, o.optBoolean("driveHidden", true))
            .apply()
        val key = RecoveryKey.fromStorage(o.optString("key")) ?: return
        if (!hasRecoveryKey(ctx)) setRecoveryKey(ctx, key, o.optLong("keyAt").takeIf { it > 0L } ?: System.currentTimeMillis())
    }
}
