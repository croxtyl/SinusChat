package pl.sinusoide.sinuschat.util

import android.content.Context
import android.content.Intent
import org.json.JSONObject
import pl.sinusoide.sinuschat.R
import pl.sinusoide.sinuschat.messenger.MessageListenerService
import pl.sinusoide.sinuschat.network.ApiClient
import pl.sinusoide.sinuschat.setup.SetupActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AutoDelete {

    private const val PREFS = "sinuschat_auto_delete"
    private const val KEY_AFTER = "after_sec"
    private const val KEY_LAST_USE = "last_use_ms"

    const val SECOND = 1L
    const val MINUTE = 60L
    const val HOUR = 3600L
    const val DAY = 86_400L
    const val MONTH = 30 * DAY
    const val YEAR = 365 * DAY
    const val YEAR_AND_HALF = 548 * DAY
    const val FIVE_YEARS = 5 * YEAR

    val PRESETS = longArrayOf(MONTH, 3 * MONTH, 9 * MONTH, YEAR, YEAR_AND_HALF, FIVE_YEARS)

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun seconds(ctx: Context): Long = prefs(ctx).getLong(KEY_AFTER, 0L)

    fun isOn(ctx: Context): Boolean = seconds(ctx) > 0L

    fun setSeconds(ctx: Context, requested: Long) {
        val app = ctx.applicationContext
        val sec = requested.coerceAtLeast(0L)
        prefs(app).edit().putLong(KEY_AFTER, sec).putLong(KEY_LAST_USE, System.currentTimeMillis()).apply()
        AppExecutor.io {
            try {
                ApiClient.post("/api/user/update",
                    JSONObject().put("autoDeleteAfterSec", sec), app)
            } catch (_: Exception) {}
        }
    }

    fun adopt(ctx: Context, sec: Long) {
        prefs(ctx).edit().putLong(KEY_AFTER, sec.coerceAtLeast(0L))
            .putLong(KEY_LAST_USE, System.currentTimeMillis()).apply()
    }

    fun touch(ctx: Context) {
        prefs(ctx).edit().putLong(KEY_LAST_USE, System.currentTimeMillis()).apply()
    }

    fun lastUse(ctx: Context): Long {
        val p = prefs(ctx)
        val saved = p.getLong(KEY_LAST_USE, 0L)
        if (saved > 0L) return saved
        val now = System.currentTimeMillis()
        p.edit().putLong(KEY_LAST_USE, now).apply()
        return now
    }

    fun deadline(ctx: Context): Long {
        val sec = seconds(ctx)
        return if (sec <= 0L) 0L else lastUse(ctx) + sec * 1000L
    }

    fun deadlineText(ctx: Context): String {
        val at = deadline(ctx)
        if (at == 0L) return ""
        val fmt = if (seconds(ctx) < DAY) SimpleDateFormat("d MMMM, HH:mm", Locale.getDefault())
        else SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
        return fmt.format(Date(at))
    }

    fun isExpired(ctx: Context): Boolean {
        val app = ctx.applicationContext
        val sec = seconds(app)
        if (sec <= 0L || !SetupActivity.isSetupDone(app)) return false
        val last = lastUse(app)
        val now = System.currentTimeMillis()
        if (now < last) { touch(app); return false }
        return now - last >= sec * 1000L
    }

    fun check(ctx: Context) {
        val app = ctx.applicationContext
        if (!isExpired(app)) return

        val resp = try {
            ApiClient.post("/api/user/delete-account", JSONObject(), app)
        } catch (_: Exception) { null }
        if (resp?.optBoolean("ok", false) != true) return

        try {
            app.stopService(Intent(app, MessageListenerService::class.java))
        } catch (_: Exception) {}
        AccountWiper.wipeAndRestart(app)
    }

    fun label(ctx: Context, sec: Long): String {
        if (sec <= 0L) return ctx.getString(R.string.auto_delete_off)
        if (sec == YEAR_AND_HALF) return ctx.getString(R.string.dur_year_and_half)
        val r = ctx.resources
        return when {
            sec % YEAR == 0L -> r.getQuantityString(R.plurals.dur_years, count(sec / YEAR), sec / YEAR)
            sec % MONTH == 0L -> r.getQuantityString(R.plurals.dur_months, count(sec / MONTH), sec / MONTH)
            sec % DAY == 0L -> r.getQuantityString(R.plurals.dur_days, count(sec / DAY), sec / DAY)
            sec % HOUR == 0L -> r.getQuantityString(R.plurals.dur_hours, count(sec / HOUR), sec / HOUR)
            sec % MINUTE == 0L -> r.getQuantityString(R.plurals.dur_minutes, count(sec / MINUTE), sec / MINUTE)
            else -> r.getQuantityString(R.plurals.dur_seconds, count(sec), sec)
        }
    }

    private fun count(v: Long): Int = if (v > Int.MAX_VALUE) Int.MAX_VALUE else v.toInt()
}
