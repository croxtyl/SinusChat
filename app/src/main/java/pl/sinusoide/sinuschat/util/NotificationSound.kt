package pl.sinusoide.sinuschat.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator

object NotificationSound {

    private const val MIN_GAP_MS = 1500L
    private const val MAX_PLAY_MS = 6000L
    private val PATTERN = longArrayOf(0, 250, 250, 250)

    private val handler = Handler(Looper.getMainLooper())
    private var lastMs = 0L
    private var player: MediaPlayer? = null
    private var ringtone: Ringtone? = null
    private var audio: AudioManager? = null
    private var focus: AudioFocusRequest? = null
    private val stopper = Runnable { stop() }

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun alert(ctx: Context, silent: Boolean) {
        if (silent) return
        val app = ctx.applicationContext
        handler.post { play(app) }
    }

    fun previewVibration(ctx: Context) = vibrate(ctx.applicationContext)

    private fun play(app: Context) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastMs < MIN_GAP_MS || !NotificationPrefs.soundEnabled(app)) return
        val am = app.getSystemService(AudioManager::class.java) ?: return
        val ch = allowedChannel(app) ?: return
        val inCall = am.mode == AudioManager.MODE_IN_CALL || am.mode == AudioManager.MODE_IN_COMMUNICATION
        if (inCall) return
        val quiet = NotificationPrefs.isQuietNow(app)
        val gain = SoundPrefs.gain(SoundPrefs.notifVolume(app))
        val sound = !quiet && gain > 0f && ch.sound == null && am.ringerMode == AudioManager.RINGER_MODE_NORMAL
        val buzz = NotificationPrefs.notifVibrate(app) && !(quiet && NotificationPrefs.quietVibrate(app)) &&
            !ch.shouldVibrate() && am.ringerMode != AudioManager.RINGER_MODE_SILENT
        if (!sound && !buzz) return
        lastMs = now
        if (buzz) vibrate(app)
        if (sound) startSound(app, am, gain)
    }

    private fun allowedChannel(app: Context): NotificationChannel? = try {
        val mgr = app.getSystemService(NotificationManager::class.java)
        val ch = mgr?.getNotificationChannel(SoundPrefs.messageChannel(app))
        val filter = mgr?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL
        ch?.takeIf {
            mgr.areNotificationsEnabled() &&
                it.importance >= NotificationManager.IMPORTANCE_DEFAULT &&
                (filter == NotificationManager.INTERRUPTION_FILTER_ALL ||
                    filter == NotificationManager.INTERRUPTION_FILTER_UNKNOWN || it.canBypassDnd())
        }
    } catch (_: Exception) {
        null
    }

    private fun vibrate(app: Context) {
        try {
            val v = app.getSystemService(Vibrator::class.java) ?: return
            if (!v.hasVibrator()) return
            val effect = VibrationEffect.createWaveform(PATTERN, -1)
            if (Build.VERSION.SDK_INT >= 33) {
                v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_NOTIFICATION))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(effect, attrs)
            }
        } catch (_: Exception) {}
    }

    private fun startSound(app: Context, am: AudioManager, gain: Float) {
        stop()
        audio = am
        try {
            focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs)
                .build()
                .also { am.requestAudioFocus(it) }
        } catch (_: Exception) {}
        try {
            val choice = SoundPrefs.notif(app)
            if (choice == SoundPrefs.Notif.SYSTEM) {
                ringtone = RingtoneManager.getRingtone(app, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.apply {
                    audioAttributes = attrs
                    if (Build.VERSION.SDK_INT >= 28) volume = gain
                    play()
                }
            } else {
                player = MediaPlayer().apply {
                    setAudioAttributes(attrs)
                    setDataSource(app, SoundPrefs.rawUri(app, choice.raw))
                    setVolume(gain, gain)
                    setOnPreparedListener { mp -> if (player === mp) mp.start() }
                    setOnCompletionListener { mp -> if (player === mp) stop() }
                    setOnErrorListener { mp, _, _ -> if (player === mp) stop(); true }
                    prepareAsync()
                }
            }
        } catch (_: Exception) {
            stop()
            return
        }
        handler.postDelayed(stopper, MAX_PLAY_MS)
    }

    private fun stop() {
        handler.removeCallbacks(stopper)
        player?.let { mp ->
            try { mp.stop() } catch (_: Exception) {}
            try { mp.release() } catch (_: Exception) {}
        }
        player = null
        try { ringtone?.stop() } catch (_: Exception) {}
        ringtone = null
        focus?.let { f -> try { audio?.abandonAudioFocusRequest(f) } catch (_: Exception) {} }
        focus = null
    }
}
