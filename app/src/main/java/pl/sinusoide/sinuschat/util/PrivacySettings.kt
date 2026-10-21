package pl.sinusoide.sinuschat.util

import android.content.Context
import org.json.JSONObject
import pl.sinusoide.sinuschat.network.ApiClient

object PrivacySettings {

    private const val PREFS            = "sinuschat_privacy"
    private const val KEY_SHOW_PREVIEW = "show_message_preview"
    private const val KEY_SUPER        = "super_privacy_mode"
    private const val KEY_TOR          = "tor_routing_enabled"
    
    private const val KEY_DISCREET     = "privacy_discreet_notifications"
    private const val KEY_HIDE_LAST    = "privacy_hide_last_message"
    private const val KEY_QR_PROTECT   = "privacy_qr_protection"
    private const val KEY_CAPTURE_BLOCK = "privacy_capture_block"
    private const val KEY_EMAIL_BLOCK  = "privacy_email_block"
    private const val KEY_BLOCK_NEW    = "privacy_block_new_chats"
    
    private const val KEY_READ_STATUS    = "privacy_disable_read_status"
    private const val KEY_TYPING_STATUS  = "privacy_disable_typing_status"
    private const val KEY_ACTIVITY_STATUS = "privacy_disable_activity_status"
    private const val KEY_CALL_RELAY = "privacy_call_relay"
    private const val KEY_NOTIF_REPLY = "privacy_notification_reply"
    private const val KEY_INCOGNITO_KEYBOARD = "privacy_incognito_keyboard"

    fun isIncognitoKeyboard(context: Context): Boolean =
        prefs(context).getBoolean(KEY_INCOGNITO_KEYBOARD, false)

    fun setIncognitoKeyboard(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_INCOGNITO_KEYBOARD, enabled).apply()
    }

    fun applyIncognitoKeyboard(view: android.widget.EditText) {
        val flag = android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        val current = view.imeOptions
        val wanted = if (isIncognitoKeyboard(view.context)) current or flag else current and flag.inv()
        if (wanted == current) return
        view.imeOptions = wanted
        if (view.hasFocus()) view.context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)?.restartInput(view)
    }

    fun isNotificationReplyEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NOTIF_REPLY, true)

    fun setNotificationReplyEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_NOTIF_REPLY, enabled).apply()
    }

    fun isPreviewEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_PREVIEW, true)

    fun isSuperPrivacyEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SUPER, false)

    fun shouldHidePreview(context: Context): Boolean =
        isSuperPrivacyEnabled(context) || !isPreviewEnabled(context) || isHideLastMessageEnabled(context)

    fun isTorEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TOR, false)

    fun setTorEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_TOR, enabled).apply()
    }

    fun isCallRelayEnabled(context: Context): Boolean =
        isTorEnabled(context) || prefs(context).getBoolean(KEY_CALL_RELAY, false)

    fun isCallRelayChosen(context: Context): Boolean = prefs(context).getBoolean(KEY_CALL_RELAY, false)

    fun setCallRelayEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_CALL_RELAY, enabled).apply()
    }

    fun isDiscreetNotificationsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DISCREET, false)

    fun setDiscreetNotificationsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DISCREET, enabled).apply()
    }

    fun isHideLastMessageEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HIDE_LAST, false)

    fun setHideLastMessageEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_HIDE_LAST, enabled).apply()
    }

    fun isQrProtectionEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_QR_PROTECT, false)

    fun setQrProtectionEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_QR_PROTECT, enabled).apply()
    }

    fun isCaptureBlockEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CAPTURE_BLOCK, false)

    fun setCaptureBlockEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_CAPTURE_BLOCK, enabled).apply()
        syncPrivacyWithServer(context)
    }

    fun isEmailBlockEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_EMAIL_BLOCK, false)

    fun setEmailBlockEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_EMAIL_BLOCK, enabled).apply()
    }

    fun isBlockNewEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BLOCK_NEW, false)

    fun setBlockNewEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BLOCK_NEW, enabled).apply()
        syncPrivacyWithServer(context)
        if (enabled) {
            val app = context.applicationContext
            AppExecutor.io { pl.sinusoide.sinuschat.crypto.ProfileCrypto.rotate(app) }
        }
    }

    fun isReadStatusDisabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_READ_STATUS, false)

    fun setReadStatusDisabled(context: Context, disabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_READ_STATUS, disabled).apply()
    }

    fun isTypingStatusDisabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TYPING_STATUS, false)

    fun setTypingStatusDisabled(context: Context, disabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_TYPING_STATUS, disabled).apply()
    }

    fun isActivityStatusDisabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ACTIVITY_STATUS, false)

    fun setActivityStatusDisabled(context: Context, disabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ACTIVITY_STATUS, disabled).apply()
        syncPrivacyWithServer(context)
    }

    fun syncPrivacyWithServer(context: Context) {
        val activityDisabled = isActivityStatusDisabled(context)
        val captureBlock = isCaptureBlockEnabled(context)
        val emailBlock = isEmailBlockEnabled(context)
        val blockNew = isBlockNewEnabled(context)

        AppExecutor.io {
            val body = JSONObject().apply {
                put("activityHidden", activityDisabled)
                put("chatPrivacyMode", captureBlock)
                put("emailBlock", emailBlock)
                put("blockNew", blockNew)
            }
            ApiClient.post("/api/user/update", body, context)
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
