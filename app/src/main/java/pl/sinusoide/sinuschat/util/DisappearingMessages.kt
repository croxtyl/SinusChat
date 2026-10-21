package pl.sinusoide.sinuschat.util

import android.content.Context
import pl.sinusoide.sinuschat.R
import pl.sinusoide.sinuschat.data.SinusChatDatabase
import pl.sinusoide.sinuschat.util.MediaManager

object DisappearingMessages {

    private const val PREFS = "sinuschat_disappearing"

    const val TRIGGER_READ = "read"

    fun setTtl(context: Context, conversationId: String, ttlMs: Long, trigger: String = TRIGGER_READ) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val e = p.edit()
            .putLong("ttl_$conversationId", ttlMs)
            .putString("trigger_$conversationId", trigger)
        when {
            ttlMs <= 0L -> e.remove("since_$conversationId")
            p.getLong("ttl_$conversationId", 0L) <= 0L ->
                e.putLong("since_$conversationId", System.currentTimeMillis())
        }
        e.apply()
    }

    fun getEnabledSince(context: Context, conversationId: String): Long {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getLong("ttl_$conversationId", 0L) <= 0L) return 0L
        val since = p.getLong("since_$conversationId", 0L)
        if (since > 0L) return since
        val now = System.currentTimeMillis()
        p.edit().putLong("since_$conversationId", now).apply()
        return now
    }

    fun getTtl(context: Context, conversationId: String): Long {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong("ttl_$conversationId", 0L)
    }

    fun getTrigger(context: Context, conversationId: String): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("trigger_$conversationId", TRIGGER_READ) ?: TRIGGER_READ
    }

    fun isEnabled(context: Context, conversationId: String): Boolean =
        getTtl(context, conversationId) > 0L

    fun setPending(context: Context, conversationId: String, ttlMs: Long, trigger: String, fromName: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("pttl_$conversationId", ttlMs)
            .putString("ptrigger_$conversationId", trigger)
            .putString("pfrom_$conversationId", fromName)
            .apply()
    }

    fun getPending(context: Context, conversationId: String): Triple<Long, String, String>? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains("pttl_$conversationId")) return null
        return Triple(
            p.getLong("pttl_$conversationId", 0L),
            p.getString("ptrigger_$conversationId", TRIGGER_READ) ?: TRIGGER_READ,
            p.getString("pfrom_$conversationId", "") ?: ""
        )
    }

    fun clearPending(context: Context, conversationId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove("pttl_$conversationId")
            .remove("ptrigger_$conversationId")
            .remove("pfrom_$conversationId")
            .apply()
    }

    fun setAwaitingConsent(context: Context, conversationId: String, ttlMs: Long, trigger: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("attl_$conversationId", ttlMs)
            .putString("atrigger_$conversationId", trigger)
            .apply()
    }

    fun getAwaitingConsent(context: Context, conversationId: String): Pair<Long, String>? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains("attl_$conversationId")) return null
        return Pair(
            p.getLong("attl_$conversationId", 0L),
            p.getString("atrigger_$conversationId", TRIGGER_READ) ?: TRIGGER_READ
        )
    }

    fun clearAwaitingConsent(context: Context, conversationId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove("attl_$conversationId")
            .remove("atrigger_$conversationId")
            .apply()
    }

    fun fullLabel(context: Context, ttlMs: Long, trigger: String): String = label(context, ttlMs)

    fun label(context: android.content.Context, ttlMs: Long): String = when (ttlMs) {
        0L -> context.getString(pl.sinusoide.sinuschat.R.string.time_off)
        else -> {
            val sec = ttlMs / 1000L
            when {
                sec < 60 -> context.getString(pl.sinusoide.sinuschat.R.string.time_sec, sec.toInt())
                sec < 3600 -> context.getString(pl.sinusoide.sinuschat.R.string.time_min, (sec / 60).toInt())
                sec < 86400 -> context.getString(pl.sinusoide.sinuschat.R.string.time_hour, (sec / 3600).toInt())
                else -> context.getString(pl.sinusoide.sinuschat.R.string.time_day, (sec / 86400).toInt())
            }
        }
    }

    fun showCustomTimePicker(context: Context, onPicked: (Long) -> Unit) {
        val units = arrayOf("sekundy", "minuty", "godziny", "dni")
        val unitMs = longArrayOf(1000L, 60_000L, 3600_000L, 86400_000L)

        val row = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            val p = (16 * context.resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
            gravity = android.view.Gravity.CENTER
        }
        val numberPicker = android.widget.NumberPicker(context).apply {
            minValue = 1; maxValue = 999; value = 5
            wrapSelectorWheel = false
        }
        val unitPicker = android.widget.NumberPicker(context).apply {
            minValue = 0; maxValue = units.size - 1
            displayedValues = units
            value = 1
            wrapSelectorWheel = false
        }
        val lp = android.widget.LinearLayout.LayoutParams(
            (110 * context.resources.displayMetrics.density).toInt(),
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
        row.addView(numberPicker, lp)
        row.addView(unitPicker, lp)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(context, pl.sinusoide.sinuschat.R.style.Theme_SinusChat_Dialog)
            .setTitle(context.getString(pl.sinusoide.sinuschat.R.string.custom_time))
            .setView(row)
            .setPositiveButton("Ustaw") { _, _ ->
                val ttl = numberPicker.value.toLong() * unitMs[unitPicker.value]
                if (ttl > 0L) onPicked(ttl)
            }
            .setNegativeButton(context.getString(pl.sinusoide.sinuschat.R.string.cancel), null)
            .show()
    }

    fun sweepExpired(context: Context): Int {
        val dao = SinusChatDatabase.getInstance(context).messageDao()
        val now = System.currentTimeMillis()
        val expired = try { dao.getExpiredMessages(now) } catch (_: Exception) { emptyList() }
        for (m in expired) {
            try { MediaManager.deleteMessageMedia(context, m.conversationId, m.id) } catch (_: Exception) {}
            try { MediaManager.voiceCacheFile(context, m.id).delete() } catch (_: Exception) {}
        }
        val deleted = try { dao.deleteExpiredMessages(now) } catch (_: Exception) { 0 }
        if (deleted > 0) try { MediaManager.purgeOrphans(context) } catch (_: Exception) {}
        return deleted
    }

    fun applyOnRead(context: Context, conversationId: String, myHash: String) {
        val ttl = getTtl(context, conversationId)
        if (ttl <= 0L || myHash.isEmpty()) return
        val since = getEnabledSince(context, conversationId)
        val expireAt = System.currentTimeMillis() + ttl
        SinusChatDatabase.getInstance(context).messageDao()
            .setExpiryForIncoming(conversationId, myHash, since, expireAt)
    }

    fun applyOnReadAck(context: Context, conversationId: String, myHash: String, uptoMs: Long) {
        val ttl = getTtl(context, conversationId)
        if (ttl <= 0L || myHash.isEmpty() || uptoMs <= 0L) return
        val since = getEnabledSince(context, conversationId)
        val expireAt = System.currentTimeMillis() + ttl
        SinusChatDatabase.getInstance(context).messageDao()
            .setExpiryForReadOutgoing(conversationId, myHash, since, uptoMs, expireAt)
    }
}
