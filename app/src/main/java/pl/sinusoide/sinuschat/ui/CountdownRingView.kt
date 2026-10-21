package pl.sinusoide.sinuschat.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

class CountdownRingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val density = resources.displayMetrics.density
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        strokeCap = Paint.Cap.ROUND
        color = 0xFF2196F3.toInt() 
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = 0x22888888
    }

    private var sweep = 360f
    private val rect = RectF()
    private var animator: ValueAnimator? = null

    fun start(durationMs: Long, onFinish: () -> Unit) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(360f, 0f).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            addUpdateListener { sweep = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    if (sweep <= 0.5f) onFinish()
                }
            })
            start()
        }
    }

    fun cancel() {
        animator?.cancel()
        animator = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = ringPaint.strokeWidth
        rect.set(pad, pad, width - pad, height - pad)
        canvas.drawOval(rect, trackPaint)
        canvas.drawArc(rect, -90f, sweep, false, ringPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancel()
    }
}
