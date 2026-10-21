package pl.sinusoide.sinuschat.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.PathInterpolator
import pl.sinusoide.sinuschat.R
import kotlin.math.abs
import kotlin.math.atan2

class FingerprintIconView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private class Line(val path: Path, val head: Float, val tail: Float)

    private val lines: List<Line>
    private val whole = Path()
    private val content = RectF()

    private var ink = Color.WHITE
    private var startNs = 0L
    private val iconToView = Matrix()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pen = PathInterpolator(0.35f, 0f, 0.25f, 1f)
    private val wedge = Path()

    init {
        val shapes = IconDrawView.shapesInside(context, R.drawable.ic_fingerprint, ALL)
        lines = shapes.mapIndexed { i, p ->
            whole.addPath(p)
            val (from, to) = arc(p)
            if (i % 2 == 0) Line(p, from, to) else Line(p, to, from)
        }
        whole.computeBounds(content, true)
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

        glow.alpha = (255 * (ms / 600f).coerceIn(0f, 1f)).toInt()
        canvas.drawCircle(width / 2f, height / 2f, minOf(width, height) / 2f, glow)

        val t = pen.getInterpolation(((ms - START_MS) / DRAW_MS).coerceIn(0f, 1f))
        paint.color = ink

        canvas.save()
        canvas.concat(iconToView)
        if (t >= 1f) {
            canvas.drawPath(whole, paint)
        } else for (line in lines) {
            if (t <= 0f) break
            val end = line.head + (line.tail - line.head) * t
            wedge.rewind()
            wedge.moveTo(CX, CY)
            wedge.arcTo(SWEEP, minOf(line.head, end), abs(end - line.head))
            wedge.close()
            canvas.save()
            canvas.clipPath(wedge)
            canvas.drawPath(line.path, paint)
            canvas.restore()
        }
        canvas.restore()

        if (isShown && ms < START_MS + DRAW_MS) postInvalidateOnAnimation()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) {
            startNs = 0L
            invalidate()
        }
    }

    private fun arc(p: Path): Pair<Float, Float> {
        val measure = PathMeasure(p, false)
        val pos = FloatArray(2)
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        var previous = Float.NaN
        var turns = 0f
        do {
            var d = 0f
            val len = measure.length
            while (d <= len) {
                measure.getPosTan(d, pos, null)
                val raw = Math.toDegrees(atan2((pos[1] - CY).toDouble(), (pos[0] - CX).toDouble())).toFloat()
                if (!previous.isNaN()) {
                    if (raw - previous > 180f) turns -= 360f
                    if (raw - previous < -180f) turns += 360f
                }
                previous = raw
                val a = raw + turns
                if (a < min) min = a
                if (a > max) max = a
                d += STEP
            }
        } while (measure.nextContour())
        return if (min > max) 0f to 0f else min to max
    }

    private fun withAlpha(color: Int, a: Float) =
        (color and 0x00FFFFFF) or ((255 * a).toInt() shl 24)

    private companion object {
        const val CX = 481f
        const val CY = 586f

        val SWEEP = RectF(CX - 1400f, CY - 1400f, CX + 1400f, CY + 1400f)

        val ALL = RectF(0f, 0f, 960f, 960f)

        const val STEP = 6f
        const val START_MS = 220f
        const val DRAW_MS = 1150f
    }
}
