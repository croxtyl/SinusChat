package pl.sinusoide.sinuschat.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import pl.sinusoide.sinuschat.R
import pl.sinusoide.sinuschat.ReportProblemActivity

object ReportNotifier {

    private const val TAG = "ReportNotifier"
    const val ACTION_REPORT_UPDATED = "pl.sinusoide.sinuschat.REPORT_UPDATED"
    private const val NOTIF_BASE_ID = 7700

    fun onUpdate(context: Context, id: Int, status: String, replied: Boolean) {
        context.sendBroadcast(Intent(ACTION_REPORT_UPDATED).setPackage(context.packageName))
        if (!NotificationPrefs.reportUpdatesEnabled(context)) return

        val statusLabel = context.getString(when (status) {
            "confirmed" -> R.string.report_status_confirmed
            "rejected" -> R.string.report_status_rejected
            else -> R.string.report_status_pending
        })
        val title = if (replied) context.getString(R.string.report_notif_reply_title, id)
                    else context.getString(R.string.report_notif_status_title, id, statusLabel)

        MessageNotifier.createChannel(context)
        val pi = PendingIntent.getActivity(
            context, NOTIF_BASE_ID + id,
            Intent(context, ReportProblemActivity::class.java)
                .putExtra(ReportProblemActivity.EXTRA_FROM_NOTIFICATION, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, MessageNotifier.channelId(context))
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.report_notif_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setSilent(NotificationPrefs.silentMessage(context))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIF_BASE_ID + id, notif)
            NotificationSound.alert(context, false)
        } catch (e: SecurityException) { Log.w(TAG, "Notif denied", e) }
    }
}
