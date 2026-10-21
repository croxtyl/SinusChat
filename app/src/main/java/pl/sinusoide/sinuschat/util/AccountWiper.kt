package pl.sinusoide.sinuschat.util

import android.content.Context
import android.content.Intent
import pl.sinusoide.sinuschat.crypto.ProfileCrypto
import pl.sinusoide.sinuschat.crypto.SecurePrefs
import pl.sinusoide.sinuschat.crypto.SignalStore
import pl.sinusoide.sinuschat.setup.SetupActivity
import java.io.File

object AccountWiper {

    fun wipeAndRestart(context: Context) {
        val app = context.applicationContext
        pl.sinusoide.sinuschat.messenger.MessageListenerService.stop(app)
        pl.sinusoide.sinuschat.pclink.PcLinkServer.shutdown()
        ChatBubbles.clear(app)
        pl.sinusoide.sinuschat.network.WebSocketManager.reset()
        pl.sinusoide.sinuschat.network.ApiClient.resetSession()
        try {
            pl.sinusoide.sinuschat.data.SinusChatDatabase.reset()
            app.deleteDatabase("sinuschat_messages.db")
            SignalStore.wipe(app)

            val prefsDir = File(app.applicationInfo.dataDir, "shared_prefs")
            prefsDir.listFiles()?.forEach { f ->
                val name = f.name.removeSuffix(".xml")
                app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
                f.delete()
            }
            SecurePrefs.forgetAll()

            File(app.filesDir, "media").deleteRecursively()
            File(app.filesDir, "my_avatar.jpg").delete()
            ProfileCrypto.wipePlainAvatars(app)
            app.cacheDir?.deleteRecursively()

            SetupActivity.resetSetup(app)
        } catch (_: Exception) {}

        RestartActivity.restartInto(app, Intent(app, SetupActivity::class.java), wipeDb = true)
    }
}
