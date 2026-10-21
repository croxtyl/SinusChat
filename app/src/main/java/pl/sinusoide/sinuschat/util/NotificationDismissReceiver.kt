package pl.sinusoide.sinuschat.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MessageNotifier.ACTION_DISMISSED) return
        MessageNotifier.markDismissed(context, intent.getStringExtra("conversationId").orEmpty())
    }
}
