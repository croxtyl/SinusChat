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

class CloudIconView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val icon = Path()
    private val trace = Path()
    private val inked = Path()
    private val measure = PathMeasure()
    private var length = 0f

    private val content = RectF()
    private var ink = Color.WHITE
    private var startNs = 0L
    private val iconToView = Matrix()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nib = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = PEN
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val pen = PathInterpolator(0.35f, 0f, 0.25f, 1f)

    init {
        IconDrawView.vectorPath(context, R.drawable.ic_backup)?.let { icon.set(it) }
        icon.computeBounds(content, true)
        measure.setPath(icon, false)
        length = measure.length
    }

    fun setColor(color: Int) {
        ink = color
        layoutGlow()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        val scale = minOf(w, h) * 0.6f / maxOf(content.width(), content.height())
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
        if (width == 0 || length <= 0f) return
        if (startNs == 0L) startNs = System.nanoTime()
        val ms = (System.nanoTime() - startNs) / 1_000_000f

        val k = pen.getInterpolation(((ms - START_MS) / DRAW_MS).coerceIn(0f, 1f))
        glow.alpha = (255 * ((ms - START_MS - DRAW_MS + 400f) / 600f).coerceIn(0f, 1f)).toInt()
        canvas.drawCircle(width / 2f, height / 2f, minOf(width, height) / 2f, glow)

        paint.color = ink
        canvas.save()
        canvas.concat(iconToView)
        if (k <= 0f) {
        } else if (k >= 1f) {
            canvas.drawPath(icon, paint)
        } else {
            trace.rewind()
            measure.setPath(icon, false)
            measure.getSegment(0f, length * k, trace, true)
            inked.rewind()
            nib.getFillPath(trace, inked)
            inked.op(icon, Path.Op.INTERSECT)
            canvas.drawPath(inked, paint)
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

    private fun withAlpha(color: Int, a: Float) =
        (color and 0x00FFFFFF) or ((255 * a).toInt() shl 24)

    private companion object {
        const val PEN = 210f

        const val START_MS = 200f
        const val DRAW_MS = 1450f
    }
}
