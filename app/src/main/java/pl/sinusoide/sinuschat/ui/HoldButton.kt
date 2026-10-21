package pl.sinusoide.sinuschat.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatButton

class HoldButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : AppCompatButton(context, attrs, defStyle) {

    var holdMs: Long = 300L
    var onHoldComplete: (() -> Unit)? = null
    var fillColor: Int = Color.parseColor("#33E74C3C")

    private var progress = 0f
    private var animator: ValueAnimator? = null
    private var completed = false
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    init {
        gravity = android.view.Gravity.CENTER
        isAllCaps = false
        includeFontPadding = false
        setPadding(0, 0, 0, 0)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> { startHold(); return true }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!completed) cancelHold()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun startHold() {
        completed = false
        animator?.cancel()
        animator = ValueAnimator.ofFloat(progress, 1f).apply {
            duration = ((1f - progress) * holdMs).toLong()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
                if (progress >= 1f && !completed) {
                    completed = true
                    onHoldComplete?.invoke()
                }
            }
            start()
        }
    }

    private fun cancelHold() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(progress, 0f).apply {
            duration = 150
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    fun reset() {
        completed = false
        progress = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (progress > 0f) {
            fillPaint.color = fillColor
            rect.set(0f, 0f, width * progress, height.toFloat())
            val r = height / 4f
            canvas.drawRoundRect(rect, r, r, fillPaint)
        }
        super.onDraw(canvas)
    }
}
