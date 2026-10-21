package pl.sinusoide.sinuschat.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

object GlassBead {
    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val depth = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val caustic = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val spec = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arc = RectF()

    private fun a(c: Int, alpha: Float) =
        Color.argb((Color.alpha(c) * alpha).toInt().coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))

    fun draw(canvas: Canvas, cx: Float, cy: Float, r: Float, accent: Int, density: Float,
             alpha: Float = 1f, stretch: Float = 0f, angle: Float = 0f) {
        if (r <= 0.5f || alpha <= 0.01f) return
        val light = LockPalette.blend(accent, Color.WHITE, 0.55f)
        val deep = LockPalette.blend(accent, Color.BLACK, 0.3f)

        canvas.save()
        if (stretch > 0.001f) {
            canvas.rotate(angle, cx, cy)
            canvas.scale(1f + stretch, 1f - stretch * 0.42f, cx, cy)
            canvas.rotate(-angle, cx, cy)
        }

        body.shader = RadialGradient(cx - r * 0.22f, cy - r * 0.3f, r * 1.3f,
            intArrayOf(a((light and 0x00FFFFFF) or 0xE6000000.toInt(), alpha), a((accent and 0x00FFFFFF) or 0xF2000000.toInt(), alpha),
                a((deep and 0x00FFFFFF) or 0xF7000000.toInt(), alpha)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, body)

        depth.shader = RadialGradient(cx + r * 0.18f, cy + r * 0.28f, r * 1.05f,
            intArrayOf(0x00000000, 0x00000000, a((deep and 0x00FFFFFF) or 0x33000000, alpha)),
            floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, depth)

        val cr = r * 0.7f
        arc.set(cx - cr, cy - cr, cx + cr, cy + cr)
        caustic.strokeWidth = r * 0.16f
        caustic.shader = LinearGradient(cx - cr, 0f, cx + cr, 0f,
            intArrayOf(0x00FFFFFF, a(light or 0xFF000000.toInt(), 0.3f * alpha), 0x00FFFFFF),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawArc(arc, 35f, 110f, false, caustic)

        rim.strokeWidth = 1.2f * density
        rim.shader = LinearGradient(0f, cy - r, 0f, cy + r,
            a(0x99FFFFFF.toInt(), alpha), a((light and 0x00FFFFFF) or 0x33000000, alpha), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r - rim.strokeWidth / 2f, rim)
        canvas.restore()

        val hx = cx - r * 0.34f; val hy = cy - r * 0.42f
        spec.shader = RadialGradient(hx, hy, r * 0.36f,
            intArrayOf(a(0x8CFFFFFF.toInt(), alpha), a(0x00FFFFFF, alpha)), null, Shader.TileMode.CLAMP)
        canvas.save()
        canvas.scale(1.5f, 0.78f, hx, hy)
        canvas.drawCircle(hx, hy, r * 0.36f, spec)
        canvas.restore()
        spec.shader = null
        spec.color = a(0xFFFFFFFF.toInt(), 0.45f * alpha)
        canvas.drawCircle(cx - r * 0.12f, cy - r * 0.58f, r * 0.075f, spec)
    }
}

class GlassCheckDrawable(ctx: Context, val accent: Int, selected: Boolean) : Drawable() {

    private val d = ctx.resources.displayMetrics.density

    var selected = selected
        internal set

    private var fill = if (selected) 1f else 0f
    private var enterSide = -1f
    private var entering = true
    private var ripple = -1f
    private var anim: ValueAnimator? = null
    private var rippleAnim: ValueAnimator? = null

    private val ringFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val wave = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val arcRect = RectF()
    private val clip = Path()

    fun beadRadius(): Float = min(bounds.width(), bounds.height()) / 2f - 3.2f * d

    fun setSelectedNow(value: Boolean) {
        anim?.cancel(); rippleAnim?.cancel()
        selected = value
        fill = if (value) 1f else 0f
        ripple = -1f
        invalidateSelf()
    }

    fun showEmpty() {
        anim?.cancel()
        fill = 0f
        invalidateSelf()
    }

    fun land() {
        anim?.cancel()
        fill = 1f
        startRipple(0)
        invalidateSelf()
    }

    fun animateTo(value: Boolean) {
        selected = value
        anim?.cancel()
        entering = value
        anim = ValueAnimator.ofFloat(fill, if (value) 1f else 0f).apply {
            duration = if (value) 460 else 320
            interpolator = PathInterpolator(0.3f, 0f, 0.15f, 1f)
            addUpdateListener { fill = it.animatedValue as Float; invalidateSelf() }
            start()
        }
        if (value) startRipple(300)
    }

    private fun startRipple(delay: Long) {
        rippleAnim?.cancel()
        rippleAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 480; startDelay = delay
            interpolator = DecelerateInterpolator(1.4f)
            addUpdateListener { ripple = it.animatedValue as Float; invalidateSelf() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) { ripple = -1f; invalidateSelf() }
            })
            start()
        }
    }

    override fun draw(canvas: Canvas) {
        val b: Rect = bounds
        if (b.isEmpty) return
        val cx = b.exactCenterX(); val cy = b.exactCenterY()
        val r = min(b.width(), b.height()) / 2f - 1f * d
        val on = fill.coerceIn(0f, 1f)

        ringFill.color = Color.argb((0x12 + 0x0C * on).toInt(), 255, 255, 255)
        canvas.drawCircle(cx, cy, r, ringFill)
        ring.strokeWidth = 1.5f * d
        ring.shader = LinearGradient(0f, cy - r, 0f, cy + r,
            Color.argb((0xB3 + 0x33 * on).toInt(), 255, 255, 255),
            Color.argb(0x40, 255, 255, 255), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r - ring.strokeWidth / 2f, ring)
        ring.shader = null
        if (on < 0.99f) {
            ring.strokeWidth = 1.2f * d
            ring.color = Color.argb((0x80 * (1f - on)).toInt(), 255, 255, 255)
            val ir = r * 0.62f
            arcRect.set(cx - ir, cy - ir, cx + ir, cy + ir)
            canvas.drawArc(arcRect, 200f, 50f, false, ring)
        }

        if (on > 0.001f) {
            val br = beadRadius()
            val travel = (1f - on) * r * 2.1f
            val side = if (entering) enterSide else -enterSide
            val bx = cx + side * travel
            val s = 0.38f * sin(PI.toFloat() * on)
            canvas.save()
            clip.reset(); clip.addCircle(cx, cy, r - 1.5f * d, Path.Direction.CW)
            canvas.clipPath(clip)
            GlassBead.draw(canvas, bx, cy, br, accent, d, stretch = s, angle = 0f)
            canvas.restore()
        }

        if (ripple in 0f..1f) {
            val wr = r * (0.5f + 0.5f * ripple)
            wave.strokeWidth = (2.2f * (1f - ripple) + 0.4f) * d
            wave.color = Color.argb((0xD9 * (1f - ripple)).toInt(), 255, 255, 255)
            canvas.drawCircle(cx, cy, wr, wave)
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

class LiquidBeadDrawable(
    private val density: Float,
    private val accent: Int,
    private val fromX: Float, private val fromY: Float,
    private val toX: Float, private val toY: Float,
    private val radius: Float
) : Drawable() {

    var progress = 0f
        set(value) { field = value; invalidateSelf() }

    private val angle = Math.toDegrees(atan2((toY - fromY).toDouble(), (toX - fromX).toDouble())).toFloat()
    private val distance = hypot(toX - fromX, toY - fromY)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun at(t: Float) = floatArrayOf(fromX + (toX - fromX) * t, fromY + (toY - fromY) * t)

    override fun draw(canvas: Canvas) {
        val t = progress.coerceIn(0f, 1f)
        val bell = sin(PI.toFloat() * t)
        val stretch = bell * min(0.75f, 0.18f + distance / (radius * 16f))

        for (k in 2 downTo 1) {
            val tt = (t - 0.07f * k).coerceAtLeast(0f)
            val p = at(tt)
            GlassBead.draw(canvas, p[0], p[1], radius * (1f - 0.18f * k), accent, density,
                alpha = 0.22f * bell / k, stretch = stretch * 0.6f, angle = angle)
        }

        val p = at(t)
        glow.shader = RadialGradient(p[0], p[1], radius * 2.1f,
            intArrayOf((accent and 0x00FFFFFF) or ((0x55 * bell).toInt() shl 24), 0x00000000), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(p[0], p[1], radius * 2.1f, glow)
        GlassBead.draw(canvas, p[0], p[1], radius, accent, density, stretch = stretch, angle = angle)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

class FlowSheetDrawable(ctx: Context) : Drawable() {

    private val d = ctx.resources.displayMetrics.density
    private val accent = ThemeUi.accent(ctx)
    private val corner = 28f * d
    private val base = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blobA = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blobB = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.2f * d }
    private val path = Path()
    private val rimPath = Path()
    private val rect = RectF()

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        val w = bounds.width().toFloat(); val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return
        val l = bounds.left.toFloat(); val t = bounds.top.toFloat()
        rect.set(l, t, l + w, t + h)
        val radii = floatArrayOf(corner, corner, corner, corner, 0f, 0f, 0f, 0f)
        path.reset(); path.addRoundRect(rect, radii, Path.Direction.CW)
        val half = rim.strokeWidth / 2f
        rimPath.reset()
        rimPath.addRoundRect(RectF(l + half, t + half, l + w - half, t + h + corner), radii, Path.Direction.CW)

        base.shader = LinearGradient(0f, t, 0f, t + h, 0xF2172238.toInt(), 0xFA0A0F1B.toInt(), Shader.TileMode.CLAMP)
        val long = maxOf(w, h)
        blobA.shader = RadialGradient(l + w * 0.12f, t + h * 0.05f, long * 0.75f,
            intArrayOf((accent and 0x00FFFFFF) or 0x59000000, 0x00000000), null, Shader.TileMode.CLAMP)
        blobB.shader = RadialGradient(l + w * 0.95f, t + h * 0.55f, long * 0.7f,
            intArrayOf(0x407C5CFF, 0x00000000), null, Shader.TileMode.CLAMP)
        sheen.shader = LinearGradient(0f, t, 0f, t + 90f * d, 0x1FFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        rim.shader = LinearGradient(0f, t, 0f, t + 70f * d, 0x80FFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP)
    }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        canvas.save()
        canvas.clipPath(path)
        canvas.drawRect(rect, base)
        canvas.drawRect(rect, blobA)
        canvas.drawRect(rect, blobB)
        canvas.drawRect(rect, sheen)
        canvas.restore()
        canvas.drawPath(rimPath, rim)
    }

    override fun getOutline(outline: android.graphics.Outline) {
        outline.setRoundRect(bounds.left, bounds.top, bounds.right, bounds.bottom + corner.toInt(), corner)
        outline.alpha = 0f
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
