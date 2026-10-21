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
import kotlin.math.cos

class LockIconView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val hollow = Path().apply { addRect(INNER, Path.Direction.CW) }

    private val body = Path().apply {
        addRoundRect(RectF(160f, 320f, 800f, 880f), 80f, 80f, Path.Direction.CW)
        addRect(INNER, Path.Direction.CCW)
        addCircle(480f, 600f, 80f, Path.Direction.CW)
    }

    private val shackle = Path().apply {
        moveTo(280f, FREE_END)
        lineTo(280f, 240f)
        arcTo(RectF(280f, 40f, 680f, 440f), 180f, 180f)
        lineTo(680f, PIVOT_END)
        lineTo(600f, PIVOT_END)
        lineTo(600f, 240f)
        arcTo(RectF(360f, 120f, 600f, 360f), 0f, -180f)
        lineTo(360f, FREE_END)
        close()
    }

    private var ink = Color.WHITE
    private var startNs = 0L
    private val iconToView = Matrix()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)

    private val swing = PathInterpolator(0.45f, 0f, 0.2f, 1f)
    private val snap = OvershootInterpolator(1.4f)

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

        val turn = ((ms - HOLD_MS) / SWING_MS).coerceIn(0f, 1f)
        val flip = cos(Math.PI * (1f - swing.getInterpolation(turn))).toFloat()
        val shut = ((ms - HOLD_MS - SWING_MS - PAUSE_MS) / DROP_MS).coerceIn(0f, 1f)
        val lift = LIFT * (1f - snap.getInterpolation(shut))

        paint.color = ink
        canvas.save()
        canvas.concat(iconToView)
        canvas.save()
        canvas.clipOutPath(hollow)
        canvas.translate(0f, -lift)
        canvas.scale(if (flip == 0f) 0.001f else flip, 1f, PIVOT_X, 0f)
        canvas.drawPath(shackle, paint)
        canvas.restore()
        canvas.drawPath(body, paint)
        canvas.restore()

        if (isShown && ms < HOLD_MS + SWING_MS + PAUSE_MS + DROP_MS) postInvalidateOnAnimation()
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
        val INNER = RectF(240f, 400f, 720f, 800f)

        const val FREE_END = 340f
        const val PIVOT_END = 520f

        const val PIVOT_X = 640f

        const val LIFT = 100f

        const val HOLD_MS = 450f
        const val SWING_MS = 620f
        const val PAUSE_MS = 80f
        const val DROP_MS = 300f

        val CONTENT = RectF(160f, 40f, 800f, 880f)
    }
}
