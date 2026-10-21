package pl.sinusoide.sinuschat.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import pl.sinusoide.sinuschat.R

class OngoingCallService : Service() {

    companion object {
        private const val TAG = "OngoingCall"
        private const val CHANNEL_ID = "sinuschat_ongoing_call"
        private const val NOTIF_ID = 7302
        private const val ACTION_HANG_UP = "pl.sinusoide.sinuschat.CALL_HANG_UP"
        private const val ACTION_MIC = "pl.sinusoide.sinuschat.CALL_MIC"
        private const val ACTION_SPEAKER = "pl.sinusoide.sinuschat.CALL_SPEAKER"

        @Volatile private var peerName = ""
        @Volatile private var video = false
        @Volatile private var connectedAt = 0L
        @Volatile private var wanted = false
        @Volatile private var running = false
        @Volatile private var projection = false
        @Volatile private var instance: OngoingCallService? = null
        @Volatile private var micOn = true
        @Volatile private var speakerOn = false

        @Volatile var onHangUp: (() -> Unit)? = null
        @Volatile var onToggleMic: (() -> Unit)? = null
        @Volatile var onToggleSpeaker: (() -> Unit)? = null

        fun start(ctx: Context, name: String, isVideo: Boolean) {
            peerName = name.ifBlank { ctx.getString(R.string.caller_unknown) }
            video = isVideo
            connectedAt = 0L
            projection = false
            micOn = true
            speakerOn = false
            wanted = true
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, OngoingCallService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "start: ${e.message}")
            }
        }

        fun update(ctx: Context, isVideo: Boolean, connectedAtMs: Long) {
            video = isVideo
            connectedAt = connectedAtMs
            if (!running) return
            try {
                NotificationManagerCompat.from(ctx).notify(NOTIF_ID, build(ctx))
            } catch (e: SecurityException) {
                Log.w(TAG, "update: ${e.message}")
            }
        }

        fun micIntent(ctx: Context): PendingIntent = PendingIntent.getService(ctx, 13,
            Intent(ctx, OngoingCallService::class.java).setAction(ACTION_MIC),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        fun hangUpIntent(ctx: Context): PendingIntent = PendingIntent.getService(ctx, 12,
            Intent(ctx, OngoingCallService::class.java).setAction(ACTION_HANG_UP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        fun setAudio(ctx: Context, mic: Boolean, speaker: Boolean) {
            if (micOn == mic && speakerOn == speaker) return
            micOn = mic
            speakerOn = speaker
            if (!running) return
            try {
                NotificationManagerCompat.from(ctx).notify(NOTIF_ID, build(ctx))
            } catch (e: SecurityException) {
                Log.w(TAG, "setAudio: ${e.message}")
            }
        }

        fun allowScreenCapture(): Boolean {
            val svc = instance ?: return false
            projection = true
            if (svc.goForeground()) return true
            projection = false
            return false
        }

        fun stop(ctx: Context) {
            onHangUp = null
            onToggleMic = null
            onToggleSpeaker = null
            wanted = false
            if (running) ctx.stopService(Intent(ctx, OngoingCallService::class.java))
        }

        private fun createChannel(ctx: Context) {
            val mgr = ctx.getSystemService(NotificationManager::class.java) ?: return
            if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
            mgr.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, ctx.getString(R.string.ongoing_call_channel), NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            })
        }

        private fun build(ctx: Context): Notification {
            val group = GroupCallSession.current
            val walkie = WalkieTalkie.session
            val target = when {
                group != null -> GroupCallActivity.intent(ctx, group.groupId, group.kind, GroupCallActivity.MODE_RESUME)
                walkie != null -> WalkieTalkieActivity.intent(ctx, walkie.groupId)
                else -> Intent(ctx, CallActivity::class.java).putExtra(CallActivity.EXTRA_RESUME, true)
            }
            val open = PendingIntent.getActivity(
                ctx, 11,
                target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val hangUp = hangUpIntent(ctx)
            val mic = NotificationCompat.Action.Builder(
                if (micOn) R.drawable.ic_mic else R.drawable.ic_mic_off,
                ctx.getString(if (micOn) R.string.call_action_mute else R.string.call_action_unmute),
                micIntent(ctx)
            ).build()
            val speaker = NotificationCompat.Action.Builder(
                if (speakerOn) R.drawable.ic_call_speaker_on else R.drawable.ic_call_speaker_off,
                ctx.getString(if (speakerOn) R.string.call_action_earpiece else R.string.call_action_speaker),
                PendingIntent.getService(ctx, 14,
                    Intent(ctx, OngoingCallService::class.java).setAction(ACTION_SPEAKER),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            ).build()
            val person = Person.Builder().setName(peerName).setImportant(true).build()
            val connected = connectedAt > 0L
            val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(if (video) R.drawable.ic_call_video_on else R.drawable.ic_call_answer)
                .setContentTitle(peerName)
                .setContentText(ctx.getString(if (connected) R.string.ongoing_call else R.string.calling))
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setColor(0xFF5288C1.toInt())
                .setContentIntent(open)
                .setStyle(NotificationCompat.CallStyle.forOngoingCall(person, hangUp))
                .addAction(mic)
                .addAction(speaker)
            if (connected) builder.setWhen(connectedAt).setShowWhen(true).setUsesChronometer(true)
            return builder.build()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HANG_UP) {
            val hangUp = onHangUp
            if (hangUp != null) hangUp() else stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_MIC) {
            onToggleMic?.invoke()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_SPEAKER) {
            onToggleSpeaker?.invoke()
            return START_NOT_STICKY
        }
        createChannel(this)
        if (!goForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        instance = this
        if (!wanted) stopSelf()
        return START_NOT_STICKY
    }

    private fun goForeground(): Boolean {
        var type = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        if (projection && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        return try {
            ServiceCompat.startForeground(this, NOTIF_ID, build(this), type)
            true
        } catch (e: Exception) {
            Log.w(TAG, "startForeground: ${e.message}")
            false
        }
    }

    override fun onDestroy() {
        running = false
        instance = null
        super.onDestroy()
    }
}
