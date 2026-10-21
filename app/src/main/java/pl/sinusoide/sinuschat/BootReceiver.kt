package pl.sinusoide.sinuschat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import pl.sinusoide.sinuschat.messenger.MessageListenerService
import pl.sinusoide.sinuschat.setup.SetupActivity

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                    pl.sinusoide.sinuschat.util.UpdateChecker.removeLegacyNotification(ctx)
                    pl.sinusoide.sinuschat.util.MessageNotifier.restoreUnread(ctx, delayMs = 2_500L)
                }
                if (SetupActivity.isSetupDone(ctx) &&
                    SetupActivity.getUserPhoneHash(ctx).isNotEmpty() &&
                    !MessageListenerService.onDemand(ctx)
                ) {
                    MessageListenerService.start(ctx)
                }
            }
        }
    }
}
