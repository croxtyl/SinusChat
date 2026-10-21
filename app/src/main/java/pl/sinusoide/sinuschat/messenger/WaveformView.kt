package pl.sinusoide.sinuschat.messenger

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.MotionEvent
import android.view.animation.LinearInterpolator
import kotlin.math.sin
import kotlin.random.Random

class WaveformView(context: Context) : View(context) {

    private val playedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * context.resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }
    private val unplayedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * context.resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }

    private var bars: FloatArray = generateBars(46, 0)
    private var animPhase = 0f
    private var phaseOffset = 0f
    private val phase get() = animPhase + phaseOffset

    private val animator = ValueAnimator.ofFloat(0f, (2f * Math.PI).toFloat()).apply {
        duration = 1200L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            animPhase = it.animatedValue as Float
            invalidate()
        }
    }

    fun currentPhase(): Float = phase

    fun setPhase(value: Float) {
        phaseOffset = value - animPhase
        invalidate()
    }

    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var onSeek: ((Float) -> Unit)? = null
    var onScrub: ((Boolean) -> Unit)? = null

    fun setColors(played: Int, unplayed: Int) {
        playedPaint.color = played
        unplayedPaint.color = unplayed
        invalidate()
    }

    fun setSeed(seed: Int, count: Int = 46) {
        bars = generateBars(count, seed)
        invalidate()
    }

    fun setAmplitudes(values: FloatArray) {
        if (values.isNotEmpty()) {
            bars = values
            invalidate()
        }
    }

    fun startAnimation() {
        if (!animator.isRunning) animator.start()
    }

    fun stopAnimation() {
        animator.cancel()
        animPhase = 0f
        phaseOffset = 0f
        invalidate()
    }

    fun pauseAnimation() {
        if (animator.isRunning && !animator.isPaused) animator.pause()
    }

    fun resumeAnimation() {
        if (animator.isPaused) animator.resume() else if (!animator.isRunning) animator.start()
    }

    private fun generateBars(count: Int, seed: Int): FloatArray {
        val rnd = Random(seed.toLong())
        return FloatArray(count) { i ->
            val base = (sin(i * 0.4) * 0.4 + 0.6).toFloat()
            val jitter = rnd.nextFloat() * 0.4f
            (base * 0.6f + jitter * 0.4f).coerceIn(0.2f, 1f)
        }
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                performClick()
                parent?.requestDisallowInterceptTouchEvent(true)
                onScrub?.invoke(true)
                val p = (event.x / width).coerceIn(0f, 1f)
                onSeek?.invoke(p)
                progress = p
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                onScrub?.invoke(false)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val p = (event.x / width).coerceIn(0f, 1f)
                onSeek?.invoke(p)
                progress = p
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private val wavePath = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 10 || h <= 10) return

        val cy = h / 2f
        val amplitude = h * 0.32f
        val cycles = 6.0f

        wavePath.reset()
        wavePath.moveTo(0f, cy)
        for (x in 0..width step 2) {
            val t = x.toFloat() / w
            val angle = (t * cycles * 2.0 * Math.PI - phase).toFloat()
            val y = cy + amplitude * sin(angle.toDouble()).toFloat()
            wavePath.lineTo(x.toFloat(), y)
        }

        val splitX = w * progress

        if (progress <= 0f) {
            canvas.drawPath(wavePath, if (isRecordingMode) playedPaint else unplayedPaint)
        } else {
            canvas.save()
            canvas.clipRect(0f, 0f, splitX, h)
            canvas.drawPath(wavePath, playedPaint)
            canvas.restore()

            canvas.save()
            canvas.clipRect(splitX, 0f, w, h)
            canvas.drawLine(splitX, cy, w, cy, unplayedPaint)
            canvas.restore()

            canvas.drawLine(splitX, cy - amplitude * 1.15f, splitX, cy + amplitude * 1.15f, playedPaint)
        }
    }

    var isRecordingMode: Boolean = false
        set(value) { field = value; invalidate() }
}
