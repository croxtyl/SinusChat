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
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import pl.sinusoide.sinuschat.R

class ShieldIconView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val shield = Path()
    private val check = Path()

    private val stroke = Path().apply {
        moveTo(325.5f, 452.5f)
        lineTo(438f, 565f)
        lineTo(635.5f, 367.5f)
    }

    private val drawn = Path()
    private val cut = Path()
    private val shape = Path()
    private val measure = PathMeasure()
    private var length = 0f

    private var ink = Color.WHITE
    private var startNs = 0L
    private val iconToView = Matrix()
    private val content = RectF()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nib = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = PEN
        strokeCap = Paint.Cap.BUTT
        strokeJoin = Paint.Join.MITER
        strokeMiter = 4f
    }
    private val pop = OvershootInterpolator(2.2f)
    private val pen = PathInterpolator(0.4f, 0f, 0.2f, 1f)

    init {
        val res = R.drawable.ic_shield
        for (p in IconDrawView.shapesInside(context, res, CHECK)) check.addPath(p)
        for (p in IconDrawView.shapesInside(context, res, CHECK, outside = true)) shield.addPath(p)
        shield.computeBounds(content, true)
        measure.setPath(stroke, false)
        length = measure.length
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

        glow.alpha = (255 * ((ms - 600f) / 600f).coerceIn(0f, 1f)).toInt()
        canvas.drawCircle(width / 2f, height / 2f, minOf(width, height) / 2f, glow)

        val enter = ((ms - ENTER_MS) / POP_MS).coerceIn(0f, 1f)
        val scale = 0.86f + 0.14f * pop.getInterpolation(enter)
        val k = pen.getInterpolation(((ms - DRAW_FROM) / DRAW_MS).coerceIn(0f, 1f))

        shape.set(shield)
        if (k > 0f) {
            drawn.rewind()
            if (k >= 1f) drawn.set(stroke) else measure.getSegment(0f, length * k, drawn, true)
            cut.rewind()
            nib.getFillPath(drawn, cut)
            cut.op(check, Path.Op.INTERSECT)
            shape.op(cut, Path.Op.DIFFERENCE)
        }

        paint.color = ink
        paint.alpha = (255 * (enter * 2.5f).coerceAtMost(1f)).toInt()
        canvas.save()
        canvas.concat(iconToView)
        canvas.scale(scale, scale, content.centerX(), content.centerY())
        canvas.drawPath(shape, paint)
        canvas.restore()

        if (isShown && ms < DRAW_FROM + DRAW_MS) postInvalidateOnAnimation()
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
        val CHECK = RectF(280f, 320f, 680f, 640f)

        const val PEN = 92.6f

        const val ENTER_MS = 80f
        const val POP_MS = 420f
        const val DRAW_FROM = 520f
        const val DRAW_MS = 760f
    }
}
