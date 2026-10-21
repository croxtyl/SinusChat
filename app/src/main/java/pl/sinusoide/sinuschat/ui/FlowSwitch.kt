package pl.sinusoide.sinuschat.ui

import android.R
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

class GlassSwitchThumb(ctx: Context) : Drawable() {

    private val d = ctx.resources.displayMetrics.density
    private val size = (26 * d).toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val gloss = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private var lift = 0f
    private var liftAnim: ValueAnimator? = null
    private var pressed = false
    private var checked: Boolean? = null

    private var lastLeft = Int.MIN_VALUE
    private var lastMoveNs = 0L
    private var lastFrameNs = 0L
    private var stretchTarget = 0f
    private var stretch = 0f
    private var stretchVel = 0f

    override fun getIntrinsicWidth() = size
    override fun getIntrinsicHeight() = size

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        val now = SystemClock.elapsedRealtimeNanos()
        if (lastLeft != Int.MIN_VALUE && bounds.left != lastLeft) {
            val dt = ((now - lastMoveNs) / 1e9f).coerceIn(0.004f, 0.05f)
            val v = abs(bounds.left - lastLeft) / dt
            stretchTarget = (v / (900f * d)).coerceAtMost(0.5f)
            lastMoveNs = now
            invalidateSelf()
        }
        lastLeft = bounds.left
    }

    override fun isStateful() = true

    override fun onStateChange(state: IntArray): Boolean {
        val nowPressed = state.contains(R.attr.state_pressed)
        val nowChecked = state.contains(R.attr.state_checked)
        var changed = false
        if (nowPressed != pressed) {
            pressed = nowPressed
            animateLift(if (pressed) 1f else 0f, if (pressed) 160 else 420)
            changed = true
        }
        val was = checked
        checked = nowChecked
        if (was != null && was != nowChecked && !pressed) {
            liftAnim?.cancel()
            liftAnim = ValueAnimator.ofFloat(lift, 1f, 0f).apply {
                duration = 560
                interpolator = DecelerateInterpolator(1.2f)
                addUpdateListener { lift = it.animatedValue as Float; invalidateSelf() }
                start()
            }
            changed = true
        }
        return changed
    }

    private fun animateLift(to: Float, ms: Long) {
        liftAnim?.cancel()
        liftAnim = ValueAnimator.ofFloat(lift, to).apply {
            duration = ms
            interpolator = if (to > lift) DecelerateInterpolator() else OvershootInterpolator(1.6f)
            addUpdateListener { lift = (it.animatedValue as Float).coerceAtLeast(0f); invalidateSelf() }
            start()
        }
    }

    private fun stepSpring() {
        val now = SystemClock.elapsedRealtimeNanos()
        val dt = if (lastFrameNs == 0L) 1f / 60f else ((now - lastFrameNs) / 1e9f).coerceIn(0f, 1f / 30f)
        lastFrameNs = now
        if (now - lastMoveNs > 45_000_000L) stretchTarget = 0f
        val k = 320f
        val c = 2f * 0.5f * sqrt(k)
        repeat(3) {
            val h = dt / 3f
            stretchVel += ((stretchTarget - stretch) * k - c * stretchVel) * h
            stretch += stretchVel * h
        }
        if (abs(stretch) > 0.002f || abs(stretchVel) > 0.02f || stretchTarget > 0f) invalidateSelf()
        else { stretch = 0f; stretchVel = 0f }
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        stepSpring()
        val l = lift.coerceIn(0f, 1.2f)
        val cx = b.exactCenterX(); val cy = b.exactCenterY()
        val base = min(b.width(), b.height()) / 2f - 3f * d
        val r = base * (1f + 0.3f * l)
        val s = stretch.coerceIn(-0.2f, 0.6f)
        val rx = r * (1f + s)
        val ry = r * (1f - 0.28f * s)
        rect.set(cx - rx, cy - ry, cx + rx, cy + ry)
        val lens = l.coerceIn(0f, 1f)

        shadow.shader = RadialGradient(cx, cy + 1.2f * d, r * 1.2f,
            intArrayOf(Color.argb((0x2E * (1f - lens)).toInt(), 0, 0, 0), 0x00000000),
            floatArrayOf(0.75f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy + 1.2f * d, r * 1.2f, shadow)

        fill.shader = LinearGradient(0f, rect.top, 0f, rect.bottom,
            Color.argb((0x5C - 0x2E * lens).toInt(), 255, 255, 255),
            Color.argb((0x2E - 0x0C * lens).toInt(), 238, 242, 248), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, ry, ry, fill)

        rim.strokeWidth = (1.2f + 0.3f * lens) * d
        rim.shader = LinearGradient(0f, rect.top, 0f, rect.bottom,
            Color.argb((0xCC + 0x1A * lens).toInt(), 255, 255, 255),
            Color.argb((0x40 + 0x0C * lens).toInt(), 255, 255, 255), Shader.TileMode.CLAMP)
        val half = rim.strokeWidth / 2f
        rect.inset(half, half)
        canvas.drawRoundRect(rect, ry - half, ry - half, rim)

        val hx = cx - rx * 0.3f; val hy = cy - ry * 0.45f
        gloss.shader = RadialGradient(hx, hy, r * 0.45f,
            intArrayOf(Color.argb((0xA6 + 0x26 * lens).toInt(), 255, 255, 255), 0x00FFFFFF),
            null, Shader.TileMode.CLAMP)
        canvas.save()
        canvas.scale(1.5f, 0.75f, hx, hy)
        canvas.drawCircle(hx, hy, r * 0.45f, gloss)
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

class GlassSwitchTrack(ctx: Context, private val accent: Int) : Drawable() {

    private val d = ctx.resources.displayMetrics.density
    private val w = (52 * d).toInt()
    private val h = (32 * d).toInt()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val inner = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private var on = -1f
    private var anim: ValueAnimator? = null

    override fun getIntrinsicWidth() = w
    override fun getIntrinsicHeight() = h
    override fun isStateful() = true

    override fun onStateChange(state: IntArray): Boolean {
        val target = if (state.contains(R.attr.state_checked)) 1f else 0f
        if (on < 0f) { on = target; return true }
        if (target == on && anim == null) return false
        anim?.cancel()
        anim = ValueAnimator.ofFloat(on, target).apply {
            duration = 260
            interpolator = DecelerateInterpolator()
            addUpdateListener { on = it.animatedValue as Float; invalidateSelf() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { anim = null }
            })
            start()
        }
        return true
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val t = on.coerceIn(0f, 1f)
        val th = min(b.height().toFloat(), h.toFloat())
        rect.set(b.left + 1f * d, b.exactCenterY() - th / 2f + 1f * d, b.right - 1f * d, b.exactCenterY() + th / 2f - 1f * d)
        val r = rect.height() / 2f

        val off = 0x21FFFFFF
        val onColor = (accent and 0x00FFFFFF) or 0xCC000000.toInt()
        fill.color = LockPalette.blend(off, onColor, t)
        canvas.drawRoundRect(rect, r, r, fill)

        inner.shader = LinearGradient(0f, rect.top, 0f, rect.top + rect.height() * 0.5f,
            0x26000000, 0x00000000, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, r, r, inner)

        rim.strokeWidth = 1f * d
        rim.shader = LinearGradient(0f, rect.top, 0f, rect.bottom,
            Color.argb((0x47 + 0x20 * t).toInt(), 255, 255, 255), 0x14FFFFFF, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, r, r, rim)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
