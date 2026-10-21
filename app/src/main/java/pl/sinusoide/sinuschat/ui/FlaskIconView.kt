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
import android.view.animation.PathInterpolator
import pl.sinusoide.sinuschat.R
import kotlin.math.PI
import kotlin.math.sin

class FlaskIconView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val flask = Path()
    private val liquid = Path()

    private val level = Path()
    private val poured = Path()
    private val content = RectF()

    private var ink = Color.WHITE
    private var startNs = 0L
    private val iconToView = Matrix()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pour = PathInterpolator(0.3f, 0f, 0.25f, 1f)

    init {
        val res = R.drawable.ic_experimental
        for (p in IconDrawView.shapesInside(context, res, LIQUID)) liquid.addPath(p)
        for (p in IconDrawView.shapesInside(context, res, LIQUID, outside = true)) flask.addPath(p)
        flask.computeBounds(content, true)
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

        val k = pour.getInterpolation(((ms - START_MS) / FILL_MS).coerceIn(0f, 1f))
        val surface = BOTTOM - (BOTTOM - TOP) * k
        val calm = ((ms - START_MS - FILL_MS) / CALM_MS).coerceIn(0f, 1f)
        val wave = WAVE * (1f - calm) * (0.35f + 0.65f * k)
        val phase = ms / 170f

        paint.color = ink
        canvas.save()
        canvas.concat(iconToView)
        canvas.drawPath(flask, paint)

        if (k > 0f) {
            level.rewind()
            level.moveTo(LEFT, surface + wave * sin(phase))
            var x = LEFT
            while (x <= RIGHT) {
                level.lineTo(x, surface + wave * sin(phase + (x - LEFT) / (RIGHT - LEFT) * 3f * PI.toFloat()))
                x += STEP
            }
            level.lineTo(RIGHT, BOTTOM + 40f)
            level.lineTo(LEFT, BOTTOM + 40f)
            level.close()

            poured.set(liquid)
            poured.op(level, Path.Op.INTERSECT)
            canvas.drawPath(poured, paint)
        }
        canvas.restore()

        if (isShown && ms < START_MS + FILL_MS + CALM_MS) postInvalidateOnAnimation()
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
        val LIQUID = RectF(260f, 540f, 700f, 740f)

        const val TOP = 558f
        const val BOTTOM = 724f
        const val LEFT = 240f
        const val RIGHT = 720f
        const val STEP = 20f

        const val WAVE = 9f
        const val START_MS = 280f
        const val FILL_MS = 1150f
        const val CALM_MS = 520f
    }
}
