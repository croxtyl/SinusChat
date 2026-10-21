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

class DocumentIconView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val page = Path()
    private val shape = Path()
    private val cut = Path()
    private val content = RectF()

    private var ink = Color.WHITE
    private var startNs = 0L
    private val iconToView = Matrix()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pop = OvershootInterpolator(2.2f)
    private val pen = PathInterpolator(0.4f, 0f, 0.2f, 1f)

    init {
        for (p in IconDrawView.shapesInside(context, R.drawable.ic_document, LINES, outside = true)) {
            page.addPath(p)
        }
        page.computeBounds(content, true)
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

        glow.alpha = (255 * ((ms - 700f) / 600f).coerceIn(0f, 1f)).toInt()
        canvas.drawCircle(width / 2f, height / 2f, minOf(width, height) / 2f, glow)

        val enter = (ms / POP_MS).coerceIn(0f, 1f)
        val scale = 0.86f + 0.14f * pop.getInterpolation(enter)
        val top = pen.getInterpolation(((ms - LINE1_FROM) / LINE_MS).coerceIn(0f, 1f))
        val bottom = pen.getInterpolation(((ms - LINE2_FROM) / LINE_MS).coerceIn(0f, 1f))

        shape.set(page)
        if (top > 0f) {
            cut.rewind()
            cut.addRect(LEFT, 480f, LEFT + WIDTH * top, 560f, Path.Direction.CW)
            shape.op(cut, Path.Op.DIFFERENCE)
        }
        if (bottom > 0f) {
            cut.rewind()
            cut.addRect(LEFT, 640f, LEFT + WIDTH * bottom, 720f, Path.Direction.CW)
            shape.op(cut, Path.Op.DIFFERENCE)
        }

        paint.color = ink
        paint.alpha = (255 * (enter * 2.5f).coerceAtMost(1f)).toInt()
        canvas.save()
        canvas.concat(iconToView)
        canvas.scale(scale, scale, content.centerX(), content.centerY())
        canvas.drawPath(shape, paint)
        canvas.restore()

        if (isShown && ms < LINE2_FROM + LINE_MS) postInvalidateOnAnimation()
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
        val LINES = RectF(300f, 460f, 660f, 740f)

        const val LEFT = 320f
        const val WIDTH = 320f

        const val POP_MS = 420f
        const val LINE1_FROM = 480f
        const val LINE2_FROM = 860f
        const val LINE_MS = 520f
    }
}
