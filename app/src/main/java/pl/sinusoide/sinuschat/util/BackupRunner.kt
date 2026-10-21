package pl.sinusoide.sinuschat.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import pl.sinusoide.sinuschat.BackupActivity
import pl.sinusoide.sinuschat.R
import java.io.File

object BackupRunner {

    private const val TAG = "BackupRunner"
    private const val CHANNEL = "backup_reminder"
    private const val NOTIF_ID = 8321
    private const val ASK_COOLDOWN_MS = 20 * 60 * 60 * 1000L
    private const val TMP_NAME = "backup-upload.tmp"

    sealed class Result {
        data class Ok(val name: String, val stats: BackupManager.Stats) : Result()
        object NoFolder : Result()
        object NoKey : Result()
        data class NeedsGoogle(val intent: IntentSender) : Result()
        object GoogleNotConfigured : Result()
        data class Failed(val message: String) : Result()
    }

    fun folderReady(ctx: Context): Boolean {
        val uri = BackupPrefs.folderUri(ctx) ?: return false
        return try {
            val dir = DocumentFile.fromTreeUri(ctx.applicationContext, Uri.parse(uri))
            dir != null && dir.exists() && dir.canWrite()
        } catch (_: Throwable) { false }
    }

    private fun targetReady(ctx: Context): Boolean =
        if (BackupPrefs.target(ctx) == BackupPrefs.TARGET_DRIVE)
            online(ctx) && DriveBackup.authorize(ctx, BackupPrefs.driveHidden(ctx)) is DriveBackup.Auth.Token
        else folderReady(ctx)

    private fun online(ctx: Context): Boolean = try {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (_: Throwable) { false }

    fun runNow(ctx: Context, onProgress: (Int, Int) -> Unit = { _, _ -> }): Result {
        val app = ctx.applicationContext
        val key = BackupPrefs.recoveryKey(app) ?: return Result.NoKey
        return if (BackupPrefs.target(app) == BackupPrefs.TARGET_DRIVE)
            toDrive(app, key, onProgress) else toFolder(app, key, onProgress)
    }

    const val BACKUP_DIR = "SinusChat"

    fun backupDir(root: DocumentFile): DocumentFile? = try {
        root.findFile(BACKUP_DIR)?.takeIf { it.isDirectory } ?: root.createDirectory(BACKUP_DIR)
    } catch (e: Throwable) {
        Log.e(TAG, "folder kopii", e)
        null
    }

    private fun toFolder(app: Context, key: RecoveryKey, onProgress: (Int, Int) -> Unit): Result {
        val folder = BackupPrefs.folderUri(app) ?: return Result.NoFolder
        val root = try { DocumentFile.fromTreeUri(app, Uri.parse(folder)) }
            catch (_: Throwable) { null } ?: return Result.NoFolder
        if (!root.exists() || !root.canWrite()) return Result.NoFolder
        val dir = backupDir(root) ?: return Result.NoFolder

        val name = BackupManager.fileName(System.currentTimeMillis())
        val doc = try { dir.createFile("application/octet-stream", name) }
            catch (e: Throwable) { Log.e(TAG, "tworzenie pliku", e); null } ?: return Result.NoFolder

        return try {
            val stats = app.contentResolver.openOutputStream(doc.uri, "w")!!.use { os ->
                BackupManager.create(app, os, key, BackupPrefs.withMedia(app), onProgress)
            }
            BackupPrefs.markDone(app, doc.name ?: name)
            cancelReminder(app)
            Result.Ok(doc.name ?: name, stats)
        } catch (e: Throwable) {
            Log.e(TAG, "kopia nie powiodła się", e)
            try { doc.delete() } catch (_: Throwable) {}
            Result.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun toDrive(app: Context, key: RecoveryKey, onProgress: (Int, Int) -> Unit): Result {
        if (!online(app)) return Result.Failed(app.getString(R.string.backup_drive_offline))
        val hidden = BackupPrefs.driveHidden(app)
        val token = when (val auth = DriveBackup.authorize(app, hidden)) {
            is DriveBackup.Auth.Token -> auth.value
            is DriveBackup.Auth.NeedsUser -> return Result.NeedsGoogle(auth.intent)
            is DriveBackup.Auth.Error ->
                return if (auth.message == "setup") Result.GoogleNotConfigured
                else Result.Failed(auth.message)
        }

        val tmp = File(app.cacheDir, TMP_NAME)
        val needed = BackupManager.mediaBytes(app) + 32L * 1024 * 1024
        if (app.cacheDir.usableSpace < needed)
            return Result.Failed(app.getString(R.string.backup_drive_no_space))

        return try {
            val stats = tmp.outputStream().use { os ->
                BackupManager.create(app, os, key, BackupPrefs.withMedia(app), onProgress)
            }
            val name = BackupManager.fileName(System.currentTimeMillis())
            val id = DriveBackup.upload(token, tmp, name, hidden)
            Log.d(TAG, "Dysk: wyslano " + name + " (" + tmp.length() + " B), ukryta=" + hidden +
                ", " + DriveBackup.describeFile(token, id))
            DriveBackup.prune(token, hidden, keepId = id)
            BackupPrefs.markDone(app, name)
            cancelReminder(app)
            Result.Ok(name, stats)
        } catch (e: Throwable) {
            Log.e(TAG, "wysyłka na Dysk nie powiodła się", e)
            Result.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            try { tmp.delete() } catch (_: Throwable) {}
        }
    }

    fun checkDue(ctx: Context) {
        val app = ctx.applicationContext
        if (!BackupPrefs.isDue(app)) return
        if (!BackupPrefs.hasRecoveryKey(app)) return
        if (BackupPrefs.target(app) != BackupPrefs.TARGET_DRIVE && BackupPrefs.folderUri(app) == null) return

        if (targetReady(app)) {
            when (val r = runNow(app)) {
                is Result.Ok -> Log.d(TAG, "kopia automatyczna: ${r.name}")
                else -> Log.w(TAG, "kopia automatyczna nieudana: $r")
            }
            return
        }
        val mode = BackupPrefs.remindMode(app)
        if (mode == BackupPrefs.REMIND_NOTIFICATION || mode == BackupPrefs.REMIND_BOTH) remind(app)
    }

    fun shouldAskOnOpen(ctx: Context): Boolean {
        val app = ctx.applicationContext
        if (!BackupPrefs.isDue(app)) return false
        if (!BackupPrefs.hasRecoveryKey(app)) return false
        if (BackupPrefs.target(app) != BackupPrefs.TARGET_DRIVE && BackupPrefs.folderUri(app) == null) return false
        val mode = BackupPrefs.remindMode(app)
        if (mode != BackupPrefs.REMIND_ON_OPEN && mode != BackupPrefs.REMIND_BOTH) return false
        return System.currentTimeMillis() - BackupPrefs.lastAskedMs(app) > ASK_COOLDOWN_MS
    }

    fun remind(ctx: Context) {
        val app = ctx.applicationContext
        val mgr = app.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL, app.getString(R.string.backup_channel),
                    NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = app.getString(R.string.backup_channel_desc)
                    setShowBadge(false)
                })
        }
        val open = PendingIntent.getActivity(app, 0,
            Intent(app, BackupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val text = when (BackupPrefs.target(app)) {
            BackupPrefs.TARGET_DRIVE -> app.getString(R.string.backup_notif_text_drive)
            BackupPrefs.TARGET_LOCAL -> app.getString(R.string.backup_notif_text_local)
            else -> app.getString(R.string.backup_notif_text)
        }

        mgr.notify(NOTIF_ID, NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(app.getString(R.string.backup_notif_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build())
        BackupPrefs.markAsked(app)
    }

    fun cancelReminder(ctx: Context) {
        try {
            (ctx.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                ?.cancel(NOTIF_ID)
        } catch (_: Throwable) {}
    }
}
