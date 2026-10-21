package pl.sinusoide.sinuschat.util

import android.content.Context
import android.util.Log
import pl.sinusoide.sinuschat.network.ApiClient
import pl.sinusoide.sinuschat.setup.SetupActivity

object ProfileSync {

    private const val TAG = "ProfileSync"

    fun pullMissing(context: Context, onDone: (changed: Boolean) -> Unit = {}) {
        val app = context.applicationContext
        val hash = SetupActivity.getUserPhoneHash(app)
        if (hash.isEmpty()) {
            onDone(false)
            return
        }
        val nameMissing = SetupActivity.getUserDisplayName(app).isEmpty()
        val avatarMissing = AvatarHelper.getLocalAvatarPath(app) == null
        if (!nameMissing && !avatarMissing) {
            onDone(false)
            return
        }

        AppExecutor.io {
            var changed = false
            try {
                val user = ApiClient.getObject("/api/user/$hash", app)
                if (user != null) {
                    if (nameMissing) {
                        val name = pl.sinusoide.sinuschat.crypto.ProfileCrypto.openOwnName(app, user.optString("display_name")).orEmpty()
                        if (name.isNotEmpty()) {
                            SetupActivity.setUserDisplayName(app, name)
                            changed = true
                        }
                    }
                    val color = user.optString("avatar_color").orEmpty()
                    if (color.startsWith("#") && !AvatarHelper.hasMyColor(app)) {
                        AvatarHelper.setMyColor(app, color)
                        changed = true
                    }

                    if (avatarMissing) {
                        val url = user.optString("avatar_url").orEmpty()
                        if (url.isNotEmpty() && url != "null") {
                            val path = AvatarHelper.downloadAvatar(app, url)
                            if (path != null) {
                                SetupActivity.setUserAvatarUri(app, path)
                                ContactCache.saveAvatarUrl(app, hash, url)
                                changed = true
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "nie udało się pobrać profilu", e)
            }
            val result = changed
            AppExecutor.main { onDone(result) }
        }
    }
}
