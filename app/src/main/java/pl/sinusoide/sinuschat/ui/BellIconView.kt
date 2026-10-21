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
import pl.sinusoide.sinuschat.R
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class BellIconView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val bell = Path()
    private val clapper = Path()
    private val content = RectF()

    private var ink = Color.WHITE
    private var startNs = 0L
    private val iconToView = Matrix()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        val res = R.drawable.ic_notifications
        for (p in IconDrawView.shapesInside(context, res, CLAPPER)) clapper.addPath(p)
        for (p in IconDrawView.shapesInside(context, res, CLAPPER, outside = true)) bell.addPath(p)
        bell.computeBounds(content, true)
        content.union(RectF().also { clapper.computeBounds(it, true) })
    }

    fun setColor(color: Int) {
        ink = color
        layoutGlow()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        val scale = minOf(w, h) * 0.6f / content.height()
        iconToView.setTranslate(-content.centerX(), -content.centerY())
        iconToView.postScale(scale, scale)
        iconToView.postTranslate(w / 2f, h / 2f)
        layoutGlow()
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

        paint.color = ink
        canvas.save()
        canvas.concat(iconToView)

        canvas.save()
        canvas.rotate(swing(ms), PIVOT_X, PIVOT_Y)
        canvas.drawPath(bell, paint)
        canvas.restore()

        canvas.save()
        canvas.rotate(swing(ms - LAG_MS) * 1.45f, PIVOT_X, PIVOT_Y)
        canvas.drawPath(clapper, paint)
        canvas.restore()

        canvas.restore()

        if (isShown && ms < START_MS + RING_S * 1000f + LAG_MS) postInvalidateOnAnimation()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) {
            startNs = 0L
            invalidate()
        }
    }

    private fun swing(ms: Float): Float {
        val t = (ms - START_MS) / 1000f
        if (t <= 0f || t > RING_S) return 0f
        return AMPLITUDE * exp(-DAMPING * t) * sin(2f * PI.toFloat() * HZ * t)
    }

    private fun withAlpha(color: Int, a: Float) =
        (color and 0x00FFFFFF) or ((255 * a).toInt() shl 24)

    private companion object {
        val CLAPPER = RectF(380f, 780f, 580f, 900f)

        const val PIVOT_X = 480f
        const val PIVOT_Y = 110f

        const val AMPLITUDE = 14f
        const val HZ = 4.2f
        const val DAMPING = 2.1f
        const val RING_S = 1.35f
        const val LAG_MS = 45f
        const val START_MS = 350f
    }
}
