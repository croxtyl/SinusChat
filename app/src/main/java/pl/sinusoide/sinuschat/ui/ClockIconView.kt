package pl.sinusoide.sinuschat.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import pl.sinusoide.sinuschat.R
import kotlin.math.floor

class ClockIconView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val dial = Path()
    private val hourHand = hand(HOUR_LEN)
    private val minuteHand = hand(MINUTE_LEN)

    private var ink = Color.WHITE
    private var startNs = 0L
    private val iconToView = Matrix()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)

    private val sweep = PathInterpolator(0.4f, 0f, 0.2f, 1f)
    private val tick = OvershootInterpolator(2.4f)

    private var shownHour = 0
    private var stepMs = -1f

    init {
        for (p in IconDrawView.shapesInside(context, R.drawable.ic_clock, HANDS, outside = true)) {
            dial.addPath(p)
        }
    }

    fun setColor(color: Int) {
        ink = color
        layoutGlow()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        val scale = minOf(w, h) * 0.6f / CONTENT.height()
        iconToView.setTranslate(-CONTENT.centerX(), -CONTENT.centerY())
        iconToView.postScale(scale, scale)
        iconToView.postTranslate(w / 2f, h / 2f)
        layoutGlow()
        if (startNs == 0L) startNs = System.nanoTime()
    }

    private fun layoutGlow() {
        if (width == 0) return
        glow.shader = RadialGradient(width / 2f, height / 2f, minOf(width, height) / 2f,
            intArrayOf(withAlpha(ink, 0.12f), withAlpha(ink, 0.035f), Color.TRANSPARENT),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0) return
        if (startNs == 0L) startNs = System.nanoTime()
        val ms = (System.nanoTime() - startNs) / 1_000_000f

        glow.alpha = (255 * (ms / 500f).coerceIn(0f, 1f)).toInt()
        canvas.drawCircle(width / 2f, height / 2f, minOf(width, height) / 2f, glow)

        val p = ((ms - START_MS) / SPIN_MS).coerceIn(0f, 1f)
        val laps = HOURS * sweep.getInterpolation(p)

        val reached = floor(laps).toInt().coerceIn(0, HOURS)
        if (reached != shownHour) {
            shownHour = reached
            stepMs = ms
        }
        val step = if (stepMs < 0f) 1f else ((ms - stepMs) / STEP_MS).coerceIn(0f, 1f)
        val from = (shownHour - 1).coerceAtLeast(0)
        val hour = from + (shownHour - from) * tick.getInterpolation(step)

        paint.color = ink
        canvas.save()
        canvas.concat(iconToView)
        canvas.drawPath(dial, paint)
        canvas.drawCircle(CX, CY, HALF, paint)
        canvas.save()
        canvas.rotate(30f * hour, CX, CY)
        canvas.drawPath(hourHand, paint)
        canvas.restore()
        canvas.rotate(360f * laps, CX, CY)
        canvas.drawPath(minuteHand, paint)
        canvas.restore()

        if (isShown && ms < START_MS + SPIN_MS + STEP_MS) postInvalidateOnAnimation()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) invalidate()
    }

    private fun withAlpha(color: Int, a: Float) =
        (color and 0x00FFFFFF) or ((255 * a).toInt() shl 24)

    private companion object {
        const val CX = 480f
        const val CY = 480f

        const val HALF = 40f
        const val HOUR_LEN = 180f
        const val MINUTE_LEN = 250f

        val HANDS = RectF(430f, 270f, 680f, 680f)

        val CONTENT = RectF(80f, 80f, 880f, 880f)

        const val HOURS = 4
        const val START_MS = 400f
        const val SPIN_MS = 3600f
        const val STEP_MS = 260f

        fun hand(len: Float) = Path().apply {
            addRect(CX - HALF, CY - len, CX + HALF, CY, Path.Direction.CW)
        }
    }
}
