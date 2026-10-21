package pl.sinusoide.sinuschat.push

import android.content.Context
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject
import pl.sinusoide.sinuschat.BuildConfig
import pl.sinusoide.sinuschat.network.ApiClient
import pl.sinusoide.sinuschat.util.AppExecutor
import pl.sinusoide.sinuschat.util.NotificationPrefs
import pl.sinusoide.sinuschat.util.NotificationPrefs.PushMode

object PushManager {

    private const val TAG = "PushManager"

    fun isFcmBuilt(): Boolean =
        BuildConfig.FCM_API_KEY.isNotEmpty() && BuildConfig.FCM_APP_ID.isNotEmpty() &&
            BuildConfig.FCM_PROJECT_ID.isNotEmpty() && BuildConfig.FCM_SENDER_ID.isNotEmpty()

    fun hasPlayServices(ctx: Context): Boolean = try {
        GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(ctx) == ConnectionResult.SUCCESS
    } catch (_: Throwable) { false }

    fun initIfFcm(ctx: Context) {
        if (NotificationPrefs.pushMode(ctx) == PushMode.FCM) ensureFirebase(ctx)
    }

    private fun ensureFirebase(ctx: Context): Boolean {
        if (!isFcmBuilt()) return false
        return try {
            if (FirebaseApp.getApps(ctx).isEmpty()) {
                FirebaseApp.initializeApp(ctx.applicationContext, FirebaseOptions.Builder()
                    .setApiKey(BuildConfig.FCM_API_KEY)
                    .setApplicationId(BuildConfig.FCM_APP_ID)
                    .setProjectId(BuildConfig.FCM_PROJECT_ID)
                    .setGcmSenderId(BuildConfig.FCM_SENDER_ID)
                    .build())
            }
            true
        } catch (e: Exception) { Log.w(TAG, "Firebase init: ${e.message}"); false }
    }

    fun serverSupportsFcm(ctx: Context): Boolean =
        ApiClient.getObject("/api/push/config", ctx)?.optBoolean("fcm", false) == true

    enum class Result { OK, NOT_BUILT, NO_PLAY_SERVICES, SERVER_UNAVAILABLE, FAILED }

    fun enableFcm(ctx: Context, done: (Result) -> Unit) {
        val app = ctx.applicationContext
        if (!isFcmBuilt()) { done(Result.NOT_BUILT); return }
        if (!hasPlayServices(app)) { done(Result.NO_PLAY_SERVICES); return }
        AppExecutor.io {
            if (!serverSupportsFcm(app)) { AppExecutor.main { done(Result.SERVER_UNAVAILABLE) }; return@io }
            AppExecutor.main {
                if (!ensureFirebase(app)) { done(Result.FAILED); return@main }
                val fm = FirebaseMessaging.getInstance()
                fm.isAutoInitEnabled = true
                fm.token.addOnCompleteListener { task ->
                    val token = if (task.isSuccessful) task.result else null
                    if (token.isNullOrEmpty()) {
                        Log.w(TAG, "Brak tokenu FCM", task.exception)
                        fm.isAutoInitEnabled = false
                        done(Result.FAILED); return@addOnCompleteListener
                    }
                    AppExecutor.io {
                        val ok = registerWithServer(app, PushMode.FCM, token)
                        AppExecutor.main {
                            if (ok) {
                                NotificationPrefs.setPushMode(app, PushMode.FCM)
                                done(Result.OK)
                            } else {
                                fm.isAutoInitEnabled = false
                                done(Result.FAILED)
                            }
                        }
                    }
                }
            }
        }
    }

    fun disableFcm(ctx: Context, done: (Boolean) -> Unit) {
        val app = ctx.applicationContext
        NotificationPrefs.setPushMode(app, PushMode.NO_GOOGLE)
        AppExecutor.io {
            val ok = registerWithServer(app, PushMode.NO_GOOGLE, null)
            AppExecutor.main {
                if (FirebaseApp.getApps(app).isNotEmpty()) {
                    try {
                        val fm = FirebaseMessaging.getInstance()
                        fm.isAutoInitEnabled = false
                        fm.deleteToken()
                    } catch (e: Exception) { Log.w(TAG, "deleteToken: ${e.message}") }
                }
                done(ok)
            }
        }
    }

    fun syncOnStart(ctx: Context) {
        val app = ctx.applicationContext
        if (!ApiClient.isAuthenticated(app)) return
        when (NotificationPrefs.pushMode(app)) {
            PushMode.NO_GOOGLE -> if (syncedKey(app) != "ntfy") AppExecutor.io { registerWithServer(app, PushMode.NO_GOOGLE, null) }
            PushMode.FCM -> AppExecutor.main {
                if (!ensureFirebase(app)) return@main
                FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                    if (!token.isNullOrEmpty() && syncedKey(app) != fcmKey(token))
                        AppExecutor.io { registerWithServer(app, PushMode.FCM, token) }
                }
            }
        }
    }

    fun onNewToken(ctx: Context, token: String) {
        val app = ctx.applicationContext
        if (NotificationPrefs.pushMode(app) != PushMode.FCM) return
        AppExecutor.io { registerWithServer(app, PushMode.FCM, token) }
    }

    private fun registerWithServer(ctx: Context, mode: PushMode, token: String?): Boolean {
        val body = JSONObject().put("mode", if (mode == PushMode.FCM) "fcm" else "ntfy")
        if (token != null) body.put("token", token)
        val ok = ApiClient.post("/api/user/push", body, ctx)?.optBoolean("ok", false) == true
        ctx.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE).edit()
            .putString("synced", if (ok) (if (token != null) fcmKey(token) else "ntfy") else "").apply()
        return ok
    }

    private const val SYNC_PREFS = "sinuschat_push_sync"
    private fun syncedKey(ctx: Context) = ctx.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE).getString("synced", "") ?: ""
    private fun fcmKey(token: String) = "fcm:" + token.hashCode()
}
