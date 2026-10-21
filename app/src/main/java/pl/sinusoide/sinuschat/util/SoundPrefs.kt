package pl.sinusoide.sinuschat.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import pl.sinusoide.sinuschat.R

object SoundPrefs {

    private const val PREFS = "sinuschat_sounds"
    private const val KEY_RING = "ringtone"
    private const val KEY_NOTIF = "notification"
    private const val KEY_OUTGOING = "outgoing"
    private const val KEY_RING_VOLUME = "ring_volume"
    private const val KEY_NOTIF_VOLUME = "notif_volume"
    const val DEFAULT_VOLUME = 50

    enum class Ring(val key: String, val raw: Int, val label: Int) {
        BIT("bit", R.raw.sc_ring_bit, R.string.sound_ring_bit),
        BIT_MELODY("bit_melodia", R.raw.sc_ring_bit_melodia, R.string.sound_ring_bit_melody),
        PULSE("puls", R.raw.sc_ring_puls, R.string.sound_ring_pulse),
        SYSTEM("system", 0, R.string.sound_system)
    }

    enum class Notif(val key: String, val raw: Int, val label: Int) {
        WAVE("fala", R.raw.sc_notif_fala, R.string.sound_notif_wave),
        DROP("kropla", R.raw.sc_notif_kropla, R.string.sound_notif_drop),
        SYSTEM("system", 0, R.string.sound_system)
    }

    enum class Outgoing(val key: String, val raw: Int, val label: Int, val desc: Int) {
        TONE("ton", R.raw.sc_ringback_puls, R.string.sound_outgoing_tone, R.string.sound_outgoing_tone_desc),
        VIBRATE("wibracja", 0, R.string.sound_outgoing_vibrate, R.string.sound_outgoing_vibrate_desc)
    }

    const val LEGACY_CHANNEL = "sinuschat_messages"
    const val MESSAGE_CHANNEL = "sinuschat_messages_v2"
    private val OLD_CHANNELS = listOf(LEGACY_CHANNEL, "sinuschat_msg_fala", "sinuschat_msg_kropla", "sinuschat_msg_system",
        "sinuschat_msg_app")

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun ringVolume(ctx: Context): Int = prefs(ctx).getInt(KEY_RING_VOLUME, DEFAULT_VOLUME).coerceIn(0, 100)

    fun setRingVolume(ctx: Context, percent: Int) =
        prefs(ctx).edit().putInt(KEY_RING_VOLUME, percent.coerceIn(0, 100)).apply()

    fun notifVolume(ctx: Context): Int = prefs(ctx).getInt(KEY_NOTIF_VOLUME, DEFAULT_VOLUME).coerceIn(0, 100)

    fun setNotifVolume(ctx: Context, percent: Int) =
        prefs(ctx).edit().putInt(KEY_NOTIF_VOLUME, percent.coerceIn(0, 100)).apply()

    fun lastVolume(ctx: Context, ring: Boolean): Int =
        prefs(ctx).getInt(if (ring) "ring_volume_last" else "notif_volume_last", DEFAULT_VOLUME).coerceIn(1, 100)

    fun setLastVolume(ctx: Context, ring: Boolean, percent: Int) {
        if (percent > 0) prefs(ctx).edit().putInt(if (ring) "ring_volume_last" else "notif_volume_last",
            percent.coerceIn(1, 100)).apply()
    }

    fun gain(percent: Int): Float {
        val f = percent.coerceIn(0, 100) / 100f
        return f * f
    }

    fun ring(ctx: Context): Ring {
        val k = prefs(ctx).getString(KEY_RING, null)
        return Ring.values().firstOrNull { it.key == k } ?: Ring.BIT
    }

    fun setRing(ctx: Context, value: Ring) = prefs(ctx).edit().putString(KEY_RING, value.key).apply()

    fun notif(ctx: Context): Notif {
        val k = prefs(ctx).getString(KEY_NOTIF, null)
        return Notif.values().firstOrNull { it.key == k } ?: Notif.WAVE
    }

    fun outgoing(ctx: Context): Outgoing {
        val k = prefs(ctx).getString(KEY_OUTGOING, null)
        return Outgoing.values().firstOrNull { it.key == k } ?: Outgoing.TONE
    }

    fun setOutgoing(ctx: Context, value: Outgoing) = prefs(ctx).edit().putString(KEY_OUTGOING, value.key).apply()

    fun rawUri(ctx: Context, raw: Int): Uri {
        val name = ctx.resources.getResourceEntryName(raw)
        return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + ctx.packageName + "/raw/" + name)
    }

    fun ringUri(ctx: Context): Uri {
        val r = ring(ctx)
        return if (r == Ring.SYSTEM) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE) else rawUri(ctx, r.raw)
    }

    @Volatile private var ensured = false

    fun messageChannel(ctx: Context): String {
        if (!ensured) {
            ensureMessageChannel(ctx)
            ensured = true
        }
        return MESSAGE_CHANNEL
    }

    fun setNotif(ctx: Context, value: Notif) = prefs(ctx).edit().putString(KEY_NOTIF, value.key).apply()

    @Synchronized
    private fun ensureMessageChannel(ctx: Context) {
        val mgr = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(MESSAGE_CHANNEL) == null) {
            val source = OLD_CHANNELS.firstNotNullOfOrNull { mgr.getNotificationChannel(it) }
            val ch = NotificationChannel(MESSAGE_CHANNEL, ctx.getString(R.string.new_messages),
                source?.importance ?: NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(false)
                source?.let {
                    lockscreenVisibility = it.lockscreenVisibility
                    setShowBadge(it.canShowBadge())
                }
                setSound(null, null)
            }
            mgr.createNotificationChannel(ch)
        }
        val moved = try {
            mgr.activeNotifications.any { it.notification.channelId in OLD_CHANNELS }
        } catch (_: Exception) { false }
        for (id in OLD_CHANNELS) {
            if (mgr.getNotificationChannel(id) != null) {
                try { mgr.deleteNotificationChannel(id) } catch (_: Exception) {}
            }
        }
        if (moved) MessageNotifier.restoreUnread(ctx, delayMs = 1_500L)
    }

    fun preview(ctx: Context, raw: Int, systemType: Int, ringtone: Boolean): MediaPlayer? = try {
        val uri = if (raw != 0) rawUri(ctx, raw) else RingtoneManager.getDefaultUri(systemType)
        MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder()
                .setUsage(if (ringtone) AudioAttributes.USAGE_NOTIFICATION_RINGTONE else AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            setDataSource(ctx, uri)
            val g = gain(if (ringtone) ringVolume(ctx) else notifVolume(ctx))
            setVolume(g, g)
            setOnPreparedListener { it.start() }
            prepareAsync()
        }
    } catch (_: Exception) { null }
}
