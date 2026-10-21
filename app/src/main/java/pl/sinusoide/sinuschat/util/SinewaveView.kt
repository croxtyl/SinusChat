package pl.sinusoide.sinuschat.util

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

class SinewaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = resolveWaveColor(context)
        style  = Paint.Style.STROKE
        strokeWidth = 4f * context.resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    companion object {
        fun resolveWaveColor(context: Context): Int {
            val nightMask = context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
            return if (nightMask == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                android.graphics.Color.parseColor("#8BBFED")
            } else {
                android.graphics.Color.parseColor("#5288C1")
            }
        }
    }

    private var phase = 0f

    private val animator = ValueAnimator.ofFloat(0f, (2f * Math.PI).toFloat()).apply {
        duration        = 900L
        repeatCount     = ValueAnimator.INFINITE
        interpolator    = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    fun startWave() {
        if (!animator.isRunning) animator.start()
    }

    fun stopWave() {
        animator.cancel()
        phase = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0) return

        val w         = width.toFloat()
        val cy        = height / 2f
        val amplitude = height * 0.38f
        val cycles    = 2.2f
        val path      = Path()

        var first = true
        val step = 1
        var px = 0
        while (px <= width) {
            val t = px.toFloat() / w
            val angle = (t * cycles * 2.0 * Math.PI - phase).toFloat()
            val y = cy + amplitude * sin(angle.toDouble()).toFloat()
            if (first) { path.moveTo(px.toFloat(), y); first = false }
            else path.lineTo(px.toFloat(), y)
            px += step
        }
        canvas.drawPath(path, wavePaint)
    }
}
