package pl.sinusoide.sinuschat.call

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import pl.sinusoide.sinuschat.util.NotificationPrefs
import pl.sinusoide.sinuschat.util.SoundPrefs

object CallRinger {

    private const val MAX_RING_MS = 40_000L

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var active: String? = null

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    @Synchronized
    fun start(ctx: Context, conversationId: String, silent: Boolean) {
        if (active == conversationId) return
        stopLocked()
        active = conversationId
        val app = ctx.applicationContext
        if (silent && NotificationPrefs.quietVibrate(app)) return
        val audio = app.getSystemService(AudioManager::class.java)
        val mode = audio?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL

        val gain = SoundPrefs.gain(SoundPrefs.ringVolume(app))
        if (!silent && mode == AudioManager.RINGER_MODE_NORMAL && gain > 0f) {
            try {
                val choice = SoundPrefs.ring(app)
                if (choice == SoundPrefs.Ring.SYSTEM && Build.VERSION.SDK_INT >= 28) {
                    ringtone = RingtoneManager.getRingtone(app, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))?.apply {
                        audioAttributes = attrs
                        isLooping = true
                        volume = gain
                        play()
                    }
                } else {
                    player = MediaPlayer().apply {
                        setAudioAttributes(attrs)
                        setDataSource(app, SoundPrefs.ringUri(app))
                        setVolume(gain, gain)
                        isLooping = true
                        setOnPreparedListener { mp -> synchronized(this@CallRinger) { if (player === mp) mp.start() } }
                        prepareAsync()
                    }
                }
            } catch (_: Exception) {}
        }

        if (mode != AudioManager.RINGER_MODE_SILENT && NotificationPrefs.callVibrate(app)) {
            try {
                vibrator = app.getSystemService(Vibrator::class.java)
                val pattern = longArrayOf(0, 500, 500)
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } catch (_: Exception) {}
        }
        handler.postDelayed({ stop(conversationId) }, MAX_RING_MS)
    }

    @Synchronized
    fun stop(conversationId: String? = null) {
        if (conversationId != null && active != null && active != conversationId) return
        stopLocked()
    }

    private fun stopLocked() {
        handler.removeCallbacksAndMessages(null)
        player?.let { mp ->
            try { mp.stop() } catch (_: Exception) {}
            try { mp.release() } catch (_: Exception) {}
        }
        player = null
        try { ringtone?.stop() } catch (_: Exception) {}
        ringtone = null
        try { vibrator?.cancel() } catch (_: Exception) {}
        vibrator = null
        active = null
    }
}
