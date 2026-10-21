package pl.sinusoide.sinuschat.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

class VoicePlayer {

    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var progressCallback: ((Int, Int) -> Unit)? = null
    private var onCompleteCallback: (() -> Unit)? = null
    private var onInterruptCallback: (() -> Unit)? = null
    
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    val isPlaying: Boolean get() = player?.isPlaying ?: false
    @Volatile private var prepared = false
    val canResume: Boolean get() = player != null && prepared
    val currentPositionMs: Int get() = try { player?.currentPosition ?: 0 } catch (_: Exception) { 0 }
    val durationMs: Int get() = try { player?.duration ?: 0 } catch (_: Exception) { 0 }

    private var anchorPos = 0
    private var anchorAt = 0L

    private fun smoothPosition(): Int {
        val cur = currentPositionMs
        val now = android.os.SystemClock.uptimeMillis()
        if (anchorAt == 0L) { anchorPos = cur; anchorAt = now; return cur }
        val est = anchorPos + (now - anchorAt).toInt()
        if (kotlin.math.abs(cur - est) > 300) { anchorPos = cur; anchorAt = now; return cur }
        val dur = durationMs
        return if (dur > 0) est.coerceIn(0, dur) else est
    }

    private val frameCallback = object : android.view.Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (isPlaying) {
                progressCallback?.invoke(smoothPosition(), durationMs)
                android.view.Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    private fun cancelFrames() {
        if (Looper.myLooper() == Looper.getMainLooper()) android.view.Choreographer.getInstance().removeFrameCallback(frameCallback)
        else handler.post { android.view.Choreographer.getInstance().removeFrameCallback(frameCallback) }
    }

    private val progressRunnable = Runnable {
        anchorAt = 0L
        android.view.Choreographer.getInstance().removeFrameCallback(frameCallback)
        android.view.Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun isPlayableSource(pathOrUrl: String): Boolean {
        val s = pathOrUrl.trim()
        if (!s.contains("://")) return s.isNotEmpty()
        return try {
            val u = java.net.URI(s)
            u.scheme.equals("https", ignoreCase = true) && u.host.equals(apiHost(), ignoreCase = true)
        } catch (_: Exception) { false }
    }

    companion object {
        private const val TAG = "VoicePlayer"

        private fun apiHost(): String? =
            try { java.net.URI(pl.sinusoide.sinuschat.AppConfig.API_BASE).host } catch (_: Exception) { null }

        @JvmStatic
        fun normalizeSource(pathOrUrl: String): String = pathOrUrl.trim()

        private var active: VoicePlayer? = null

        fun pauseActive() { active?.interrupt() }
    }

    private fun claim() {
        val previous = active
        active = this
        if (previous != null && previous !== this) previous.interrupt()
    }

    private fun interrupt() {
        pause()
        abandonAudioFocus()
        onInterruptCallback?.invoke()
    }

    fun play(
        context: Context,
        pathOrUrl: String,
        onProgress: (Int, Int) -> Unit,
        onComplete: () -> Unit,
        onInterrupt: () -> Unit = {}
    ) {
        stop()
        claim()

        val source = normalizeSource(pathOrUrl)
        if (!isPlayableSource(source)) {
            Log.e(TAG, "Odrzucono źródło spoza własnego API")
            onComplete()
            return
        }

        progressCallback = onProgress
        onCompleteCallback = onComplete
        onInterruptCallback = onInterrupt

        requestAudioFocus(context)

        try {
            player = MediaPlayer().apply {
                setDataSource(source)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setOnCompletionListener {
                    handler.removeCallbacks(progressRunnable)
                    abandonAudioFocus()
                    if (active === this@VoicePlayer) active = null
                    onCompleteCallback?.invoke()
                }
                setOnErrorListener { _, _, _ ->
                    Log.e(TAG, "MediaPlayer error")
                    stop()
                    true
                }
                setOnPreparedListener { mp ->
                    prepared = true
                    if (active === this@VoicePlayer) {
                        mp.start()
                        handler.post(progressRunnable)
                    }
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Play error: ${e.message}")
            abandonAudioFocus()
        }
    }

    private fun requestAudioFocus(context: Context) {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS || 
                        focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        pause()
                    }
                }
                .build()
            audioManager?.requestAudioFocus(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS || 
                        focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        pause()
                    }
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
        focusRequest = null
    }

    fun pause() {
        try {
            if (player?.isPlaying == true) {
                player?.pause()
            }
        } catch (_: Exception) {}
        handler.removeCallbacks(progressRunnable)
        cancelFrames()
        anchorAt = 0L
    }

    fun resume() {
        claim()
        try {
            player?.start()
            handler.post(progressRunnable)
        } catch (_: Exception) {}
    }

    fun seekTo(positionMs: Int) {
        try {
            player?.seekTo(positionMs.toLong(), MediaPlayer.SEEK_CLOSEST)
            anchorAt = 0L
            progressCallback?.invoke(currentPositionMs, durationMs)
        } catch (_: Exception) {}
    }

    fun stop() {
        prepared = false
        if (active === this) active = null
        handler.removeCallbacks(progressRunnable)
        cancelFrames()
        anchorAt = 0L
        abandonAudioFocus()
        try {
            player?.stop()
        } catch (_: Exception) {}
        player?.release()
        player = null
    }
}
