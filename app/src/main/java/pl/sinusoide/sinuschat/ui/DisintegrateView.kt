package pl.sinusoide.sinuschat.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import java.util.Random
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class DisintegrateView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val particlePaint = Paint()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val maskPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val bursts = mutableListOf<Burst>()

    private class Burst(
        val bitmap: Bitmap,
        val left: Float,
        val top: Float,
        val xs: FloatArray, val ys: FloatArray,
        val colors: IntArray,
        val vx: FloatArray, val vy: FloatArray,
        val wind: FloatArray, val lift: FloatArray,
        val size: FloatArray, val delay: FloatArray, val phase: FloatArray,
        val count: Int,
        val reverse: Boolean,
        val onFinish: () -> Unit,
        var progress: Float = 0f,
    )

    private val sweep = 0.55f
    private val jitter = 0.12f

    fun addBurst(bitmap: Bitmap, startX: Float, startY: Float, reverse: Boolean = false, duration: Long = 1000L, onFinish: () -> Unit) {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0 || bitmap.isRecycled) { onFinish(); return }

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val here = IntArray(2)
        getLocationOnScreen(here)
        val left = startX - here[0]
        val top = startY - here[1]

        val budget = (2200 / (1 + bursts.size)).coerceAtLeast(500)
        val step = max(3, ceil(sqrt(w.toDouble() * h / budget)).toInt())
        val cap = (w / step + 1) * (h / step + 1)
        val xs = FloatArray(cap); val ys = FloatArray(cap); val colors = IntArray(cap)
        val vx = FloatArray(cap); val vy = FloatArray(cap); val wind = FloatArray(cap); val lift = FloatArray(cap)
        val size = FloatArray(cap); val delay = FloatArray(cap); val phase = FloatArray(cap)
        val rnd = Random()
        val d = density
        var n = 0
        for (j in 0 until h step step) {
            for (i in 0 until w step step) {
                val c = pixels[j * w + i]
                val a = Color.alpha(c)
                if (a < 10) continue
                colors[n] = (c and 0x00FFFFFF) or (max(a, 170) shl 24)
                xs[n] = left + i + step / 2f
                ys[n] = top + j + step / 2f
                vx[n] = (-25f + rnd.nextFloat() * 80f) * d
                vy[n] = (-50f + rnd.nextFloat() * 60f) * d
                wind[n] = (40f + rnd.nextFloat() * 70f) * d
                lift[n] = (20f + rnd.nextFloat() * 50f) * d
                size[n] = step * 0.55f * (0.8f + rnd.nextFloat() * 0.4f)
                delay[n] = (i.toFloat() / w) * sweep + rnd.nextFloat() * jitter
                phase[n] = rnd.nextFloat() * 6.2832f
                n++
            }
        }

        val burst = Burst(bitmap, left, top, xs, ys, colors, vx, vy, wind, lift, size, delay, phase, n, reverse, onFinish)
        bursts.add(burst)
        visibility = VISIBLE

        ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = if (reverse) 1000L else 1250L
            interpolator = LinearInterpolator()
            addUpdateListener {
                burst.progress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    bursts.remove(burst)
                    if (bursts.isEmpty()) visibility = View.GONE
                    invalidate()
                    onFinish()
                }
            })
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bursts.isEmpty()) return
        val wobble = 6f * density

        for (b in bursts) {
            val p = if (b.reverse) 1f - b.progress else b.progress
            drawIntact(canvas, b, p)

            for (k in 0 until b.count) {
                val start = b.delay[k]
                if (p <= start) continue
                val t = ((p - start) / (1f - start)).coerceIn(0f, 1f)
                if (t >= 1f) continue
                val x = b.xs[k] + b.vx[k] * t + b.wind[k] * t * t
                val y = b.ys[k] + b.vy[k] * t - b.lift[k] * t * t + sin(b.phase[k] + t * 7f) * wobble * t
                val s = b.size[k] * (1f - 0.65f * t)
                particlePaint.color = b.colors[k]
                particlePaint.alpha = (Color.alpha(b.colors[k]) * (1f - t).pow(1.3f)).toInt()
                canvas.drawRect(x - s, y - s, x + s, y + s, particlePaint)
            }
        }
    }

    private fun drawIntact(canvas: Canvas, b: Burst, p: Float) {
        if (b.bitmap.isRecycled) return
        val w = b.bitmap.width.toFloat()
        val h = b.bitmap.height.toFloat()
        val solidFrom = b.left + (p / sweep) * w
        val goneTo = b.left + ((p - jitter) / sweep) * w
        if (goneTo >= b.left + w) return
        if (solidFrom <= b.left) {
            canvas.drawBitmap(b.bitmap, b.left, b.top, bitmapPaint)
            return
        }
        val save = canvas.saveLayer(b.left, b.top, b.left + w, b.top + h, null)
        canvas.drawBitmap(b.bitmap, b.left, b.top, bitmapPaint)
        maskPaint.shader = LinearGradient(goneTo, 0f, max(solidFrom, goneTo + 1f), 0f,
            0x00000000, Color.BLACK, Shader.TileMode.CLAMP)
        canvas.drawRect(b.left, b.top, b.left + w, b.top + h, maskPaint)
        canvas.restoreToCount(save)
    }
}
