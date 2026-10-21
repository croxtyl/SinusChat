package pl.sinusoide.sinuschat.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import pl.sinusoide.sinuschat.R
import kotlin.math.PI
import kotlin.math.sin

class SessionMoveView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val oldPhone = Path()
    private val newPhone = Path()
    private val fill = Path()
    private val box = RectF()

    private var ink = Color.WHITE
    private var accent = 0xFF2AE0F5.toInt()
    private var startNs = 0L

    private var u = 1f
    private var oldX = 0f
    private var newX = 0f
    private var midY = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pop = OvershootInterpolator(2.2f)
    private val ease = PathInterpolator(0.4f, 0f, 0.2f, 1f)
    private val spill = OvershootInterpolator(1.6f)

    init {
        val all = RectF(0f, 0f, 960f, 960f)
        for (p in IconDrawView.shapesInside(context, R.drawable.ic_old_phone, all)) oldPhone.addPath(p)
        for (p in IconDrawView.shapesInside(context, R.drawable.ic_new_phone, all)) newPhone.addPath(p)
        accent = ThemeUi.accent(context)
    }

    fun setColors(inkColor: Int, accentColor: Int) {
        ink = inkColor
        accent = accentColor
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        u = minOf(h * 0.70f / 880f, w * 0.30f / 600f)
        oldX = w * 0.24f
        newX = w * 0.76f
        midY = h * 0.60f
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0) return
        if (startNs == 0L) startNs = System.nanoTime()
        val ms = (System.nanoTime() - startNs) / 1_000_000f

        val oldIn = (ms / POP_MS).coerceIn(0f, 1f)
        val dim = ease.getInterpolation(((ms - DIM_FROM) / DIM_MS).coerceIn(0f, 1f))
        drawPhone(canvas, oldPhone, oldX, oldIn, 1f - 0.62f * dim, 1f)
        val lit = ((ms - FILL_FROM) / FILL_MS).coerceIn(0f, 1f)
        val shrink = ease.getInterpolation(((ms - SHRINK_FROM) / SHRINK_MS).coerceIn(0f, 1f))
        if (lit > 0f && shrink < 1f) drawSession(canvas, oldX, 1f - shrink, lit, OLD_SCREEN)

        val newIn = ((ms - NEW_DELAY) / POP_MS).coerceIn(0f, 1f)
        val b = ((ms - BUMP_FROM) / BUMP_MS).coerceIn(0f, 1f)
        val bump = 1f + 0.07f * sin(PI.toFloat() * b)
        drawPhone(canvas, newPhone, newX, newIn, 1f, bump)
        val grow = ((ms - GROW_FROM) / GROW_MS).coerceIn(0f, 1f)
        if (grow > 0f) drawSession(canvas, newX, spill.getInterpolation(grow), 1f, NEW_SCREEN, bump)

        val fly = ((ms - FLY_FROM) / FLY_MS).coerceIn(0f, 1f)
        if (fly > 0f && fly < 1f) {
            val fromY = midY + (OLD_SCREEN.centerY() - 480f) * u
            val toY = midY + (NEW_SCREEN.centerY() - 480f) * u
            for (i in 3 downTo 0) {
                val t = ease.getInterpolation((fly - i * 0.045f).coerceIn(0f, 1f))
                val x = oldX + (newX - oldX) * t
                val y = fromY + (toY - fromY) * t - 4f * t * (1f - t) * (height * 0.42f)
                glow.color = accent
                glow.alpha = if (i == 0) 255 else 70 / i
                canvas.drawCircle(x, y, DOT_R * u * (1f - i * 0.18f), glow)
            }
        }

        if (isShown && ms < END_MS) postInvalidateOnAnimation()
    }

    private fun drawPhone(canvas: Canvas, phone: Path, cx: Float, enter: Float, alpha: Float, bump: Float) {
        if (enter <= 0f) return
        val s = u * (0.8f + 0.2f * pop.getInterpolation(enter)) * bump
        paint.color = ink
        paint.alpha = (255 * alpha * (enter * 2.5f).coerceAtMost(1f)).toInt()
        canvas.save()
        canvas.translate(cx, midY)
        canvas.scale(s, s)
        canvas.translate(-480f, -480f)
        canvas.drawPath(phone, paint)
        canvas.restore()
    }

    private fun drawSession(canvas: Canvas, cx: Float, size: Float, alpha: Float, screen: RectF, bump: Float = 1f) {
        val k = size.coerceAtLeast(0f)
        val halfW = DOT_R + (screen.width() / 2f - DOT_R) * k.coerceAtMost(1.04f)
        val halfH = DOT_R + (screen.height() / 2f - DOT_R) * k.coerceAtMost(1.04f)
        val r = DOT_R + (SCREEN_R - DOT_R) * k.coerceIn(0f, 1f)
        box.set(screen.centerX() - halfW, screen.centerY() - halfH, screen.centerX() + halfW, screen.centerY() + halfH)
        fill.rewind()
        fill.addRoundRect(box, r, r, Path.Direction.CW)
        paint.color = accent
        paint.alpha = (230 * alpha).toInt()
        val s = u * bump
        canvas.save()
        canvas.translate(cx, midY)
        canvas.scale(s, s)
        canvas.translate(-480f, -480f)
        canvas.drawPath(fill, paint)
        canvas.restore()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) {
            startNs = 0L
            invalidate()
        }
    }

    private companion object {
        val OLD_SCREEN = RectF(310f, 150f, 650f, 680f)
        val NEW_SCREEN = RectF(310f, 280f, 650f, 810f)
        const val SCREEN_R = 30f
        const val DOT_R = 46f

        const val POP_MS = 420f
        const val NEW_DELAY = 140f
        const val FILL_FROM = 300f
        const val FILL_MS = 380f
        const val SHRINK_FROM = 1050f
        const val SHRINK_MS = 520f
        const val FLY_FROM = 1560f
        const val FLY_MS = 760f
        const val DIM_FROM = 1560f
        const val DIM_MS = 600f
        const val GROW_FROM = 2300f
        const val GROW_MS = 560f
        const val BUMP_FROM = 2300f
        const val BUMP_MS = 380f
        const val END_MS = 3000f
    }
}
