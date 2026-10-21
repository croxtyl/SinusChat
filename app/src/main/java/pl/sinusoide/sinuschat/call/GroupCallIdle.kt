package pl.sinusoide.sinuschat.call

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import pl.sinusoide.sinuschat.R

object GroupCallIdle {

    private const val NOTIF_ID = 7310
    private const val LEFT_NOTIF_ID = 7311
    const val ACTION_ACTIVE = "pl.sinusoide.sinuschat.GCALL_ACTIVE"

    fun notice(ctx: Context, groupId: String, kind: String, title: String, windowMs: Long) {
        CallNotifier.createChannel(ctx)
        val active = PendingIntent.getBroadcast(ctx, NOTIF_ID,
            Intent(ctx, GroupCallIdleReceiver::class.java).setAction(ACTION_ACTIVE).setPackage(ctx.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(ctx, NOTIF_ID,
            GroupCallActivity.intent(ctx, groupId, kind, GroupCallActivity.MODE_RESUME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CallNotifier.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(title)
            .setContentText(ctx.getString(R.string.gcall_idle_prompt))
            .setStyle(NotificationCompat.BigTextStyle().bigText(ctx.getString(R.string.gcall_idle_prompt)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setColor(0xFF5288C1.toInt())
            .setAutoCancel(true)
            .setTimeoutAfter(windowMs)
            .setContentIntent(open)
            .addAction(R.drawable.ic_timer, ctx.getString(R.string.gcall_idle_button), active)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(NOTIF_ID, n) } catch (_: SecurityException) {}
    }

    fun cancelNotice(ctx: Context) {
        try { NotificationManagerCompat.from(ctx).cancel(NOTIF_ID) } catch (_: Exception) {}
    }

    fun left(ctx: Context, title: String, textRes: Int) {
        CallNotifier.createChannel(ctx)
        val n = NotificationCompat.Builder(ctx, CallNotifier.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(title)
            .setContentText(ctx.getString(textRes))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setColor(0xFF5288C1.toInt())
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(LEFT_NOTIF_ID, n) } catch (_: SecurityException) {}
    }
}

class GroupCallIdleReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != GroupCallIdle.ACTION_ACTIVE) return
        GroupCallIdle.cancelNotice(ctx)
        GroupCallSession.current?.confirmActive()
    }
}
