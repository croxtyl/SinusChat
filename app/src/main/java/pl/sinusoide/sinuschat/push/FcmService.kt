package pl.sinusoide.sinuschat.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import pl.sinusoide.sinuschat.messenger.MessageListenerService
import pl.sinusoide.sinuschat.network.WebSocketManager
import pl.sinusoide.sinuschat.util.NotificationPrefs

class FcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        PushManager.onNewToken(this, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (NotificationPrefs.pushMode(this) != NotificationPrefs.PushMode.FCM) return
        Log.d("FcmService", "FCM wake")
        MessageListenerService.startChecked(this)
        WebSocketManager.forceReconnect()
        MessageListenerService.pollNow(this)
    }
}
