package pl.sinusoide.sinuschat.util

import android.content.Context
import java.util.Calendar

object NotificationPrefs {

    private const val PREFS = "sinuschat_notifications"

    enum class PushMode(val key: String) {
        NO_GOOGLE("no_google"),
        FCM("fcm");

        companion object {
            fun from(key: String?) = entries.firstOrNull { it.key == key } ?: NO_GOOGLE
        }
    }

    enum class Preview(val key: String) {
        FULL("full"),
        NAME("name"),
        HIDDEN("hidden");

        companion object {
            fun from(key: String?) = entries.firstOrNull { it.key == key } ?: FULL
        }
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun bool(ctx: Context, key: String, def: Boolean) = prefs(ctx).getBoolean(key, def)
    private fun setBool(ctx: Context, key: String, v: Boolean) = prefs(ctx).edit().putBoolean(key, v).apply()

    fun pushMode(ctx: Context) = PushMode.from(prefs(ctx).getString("push_mode", null))
    fun setPushMode(ctx: Context, mode: PushMode) {
        prefs(ctx).edit().putString("push_mode", mode.key).commit()
    }

    fun messagesEnabled(ctx: Context) = bool(ctx, "msg_enabled", true)
    fun setMessagesEnabled(ctx: Context, v: Boolean) = setBool(ctx, "msg_enabled", v)

    fun groupsEnabled(ctx: Context) = bool(ctx, "group_enabled", true)
    fun setGroupsEnabled(ctx: Context, v: Boolean) = setBool(ctx, "group_enabled", v)

    fun preview(ctx: Context): Preview =
        if (PrivacySettings.isDiscreetNotificationsEnabled(ctx)) Preview.HIDDEN
        else Preview.from(prefs(ctx).getString("preview", null)).let { if (it == Preview.HIDDEN) Preview.FULL else it }

    fun setPreview(ctx: Context, p: Preview) {
        PrivacySettings.setDiscreetNotificationsEnabled(ctx, p == Preview.HIDDEN)
        if (p != Preview.HIDDEN) prefs(ctx).edit().putString("preview", p.key).apply()
    }

    fun soundEnabled(ctx: Context) = bool(ctx, "sound", true)
    fun setSoundEnabled(ctx: Context, v: Boolean) = setBool(ctx, "sound", v)

    fun ignoreRequests(ctx: Context) = bool(ctx, "ignore_requests", false)
    fun setIgnoreRequests(ctx: Context, v: Boolean) = setBool(ctx, "ignore_requests", v)

    fun inAppEnabled(ctx: Context) = bool(ctx, "in_app", true)
    fun setInAppEnabled(ctx: Context, v: Boolean) = setBool(ctx, "in_app", v)

    fun notifVibrate(ctx: Context) = bool(ctx, "notif_vibrate", true)
    fun setNotifVibrate(ctx: Context, v: Boolean) = setBool(ctx, "notif_vibrate", v)

    fun callVibrate(ctx: Context) = bool(ctx, "call_vibrate", true)
    fun setCallVibrate(ctx: Context, v: Boolean) = setBool(ctx, "call_vibrate", v)

    fun missedCallsEnabled(ctx: Context) = bool(ctx, "missed_calls", true)
    fun safetyPrompts(ctx: Context) = bool(ctx, "safety_prompts", true)
    fun setSafetyPrompts(ctx: Context, v: Boolean) = setBool(ctx, "safety_prompts", v)
    fun updateNotices(ctx: Context) = bool(ctx, "update_notices", true)
    fun setUpdateNotices(ctx: Context, v: Boolean) = setBool(ctx, "update_notices", v)
    fun setMissedCallsEnabled(ctx: Context, v: Boolean) = setBool(ctx, "missed_calls", v)

    fun groupAddedEnabled(ctx: Context) = bool(ctx, "group_added", true)
    fun setGroupAddedEnabled(ctx: Context, v: Boolean) = setBool(ctx, "group_added", v)

    fun undeliveredNotice(ctx: Context) = bool(ctx, "undelivered_notice", true)
    fun setUndeliveredNotice(ctx: Context, v: Boolean) = setBool(ctx, "undelivered_notice", v)

    fun reportUpdatesEnabled(ctx: Context) = bool(ctx, "report_updates", true)
    fun setReportUpdatesEnabled(ctx: Context, v: Boolean) = setBool(ctx, "report_updates", v)

    fun quietEnabled(ctx: Context) = bool(ctx, "quiet_enabled", false)
    fun setQuietEnabled(ctx: Context, v: Boolean) = setBool(ctx, "quiet_enabled", v)

    fun quietFrom(ctx: Context) = prefs(ctx).getInt("quiet_from", 22 * 60)
    fun quietTo(ctx: Context) = prefs(ctx).getInt("quiet_to", 7 * 60)
    fun setQuietRange(ctx: Context, from: Int, to: Int) {
        prefs(ctx).edit().putInt("quiet_from", from).putInt("quiet_to", to).apply()
    }

    fun quietAllowCalls(ctx: Context) = bool(ctx, "quiet_calls", true)
    fun setQuietAllowCalls(ctx: Context, v: Boolean) = setBool(ctx, "quiet_calls", v)

    fun quietVibrate(ctx: Context) = bool(ctx, "quiet_vibrate", true)
    fun setQuietVibrate(ctx: Context, v: Boolean) = setBool(ctx, "quiet_vibrate", v)

    fun quietAvailable(ctx: Context) = notifVibrate(ctx) || callVibrate(ctx) ||
        SoundPrefs.ringVolume(ctx) > 0 || SoundPrefs.notifVolume(ctx) > 0

    fun isQuietNow(ctx: Context): Boolean {
        if (!quietEnabled(ctx) || !quietAvailable(ctx)) return false
        val c = Calendar.getInstance()
        val now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val from = quietFrom(ctx); val to = quietTo(ctx)
        return when {
            from == to -> false
            from < to -> now in from until to
            else -> now >= from || now < to
        }
    }

    fun formatMinutes(minutes: Int): String = String.format("%02d:%02d", minutes / 60, minutes % 60)

    fun allowMessage(ctx: Context, conversationId: String): Boolean {
        if (conversationId.startsWith("g_")) { if (!groupsEnabled(ctx)) return false }
        else if (!messagesEnabled(ctx)) return false
        if (!inAppEnabled(ctx) && pl.sinusoide.sinuschat.SinusApp.isForeground) return false
        return true
    }

    fun silentMessage(ctx: Context) = !soundEnabled(ctx) || isQuietNow(ctx)

    fun silentCall(ctx: Context) = isQuietNow(ctx) && !quietAllowCalls(ctx)
}
