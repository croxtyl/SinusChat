package pl.sinusoide.sinuschat.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

class ThinkingDotsView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val d      = resources.displayMetrics.density
    private val paint  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow   = Paint(Paint.ANTI_ALIAS_FLAG)

    private val r0     = 3.6f * d
    private val step   = 2 * r0 + 7.5f * d
    private val orbitR = 6.5f * d
    private val lift   = 5f * d

    private val count = 3
    private val x = FloatArray(count); private val y = FloatArray(count)
    private val r = FloatArray(count); private val a = FloatArray(count)
    private val nx = FloatArray(count); private val ny = FloatArray(count)
    private val nr = FloatArray(count); private val na = FloatArray(count)

    private val sceneCount = 4
    private val sceneTime  = 1.8f
    private val blendTime  = 0.6f

    private var time = 0f
    private var anim: ValueAnimator? = null

    var dotColor: Int = Color.WHITE
        set(value) { field = value; invalidate() }

    var highlightColor: Int = Color.WHITE
        set(value) { field = value; invalidate() }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = (2 * (step + r0 * 1.6f) + paddingLeft + paddingRight).toInt()
        val h = (2 * (orbitR * 0.8f + r0 * 1.6f) + lift + paddingTop + paddingBottom).toInt()
        setMeasuredDimension(resolveSize(w, widthSpec), resolveSize(h, heightSpec))
    }

    private fun bump(t: Float): Float {
        val p = ((t % 1f) + 1f) % 1f
        return max(0f, sin(p * Math.PI.toFloat())).pow(2.2f)
    }

    private fun scene(
        index: Int, t: Float, cx: Float, cy: Float,
        ox: FloatArray, oy: FloatArray, or_: FloatArray, oa: FloatArray
    ) {
        when (index) {
            0 -> for (i in 0 until count) {
                val k = bump(t * 0.85f - i * 0.18f)
                ox[i] = cx + (i - 1) * step
                oy[i] = cy - lift * k
                or_[i] = r0 * (0.85f + 0.4f * k)
                oa[i] = 0.42f + 0.58f * k
            }
            1 -> for (i in 0 until count) {
                val ang = t * 2.1f + i * (2f * Math.PI.toFloat() / count)
                val s = 0.5f + 0.5f * sin(ang)
                ox[i] = cx + cos(ang) * orbitR
                oy[i] = cy + sin(ang) * orbitR * 0.72f
                or_[i] = r0 * (0.8f + 0.35f * s)
                oa[i] = 0.5f + 0.5f * s
            }
            2 -> {
                val s = (0.5f + 0.5f * sin(t * 2.3f)).pow(1.4f)
                for (i in 0 until count) {
                    ox[i] = cx + (i - 1) * step * s
                    oy[i] = cy
                    or_[i] = r0 * (1.45f - 0.45f * s)
                    oa[i] = 0.55f + 0.45f * s
                }
            }
            else -> for (i in 0 until count) {
                val k = bump(t * 1.15f - i * 0.22f)
                ox[i] = cx + (i - 1) * step
                oy[i] = cy
                or_[i] = r0 * (0.55f + 0.95f * k)
                oa[i] = 0.4f + 0.6f * k
            }
        }
    }

    private fun smooth(t: Float): Float {
        val p = t.coerceIn(0f, 1f)
        return p * p * (3f - 2f * p)
    }

    private fun lerpColor(from: Int, to: Int, f: Float): Int {
        val g = f.coerceIn(0f, 1f)
        fun mix(shift: Int): Int {
            val s = from shr shift and 0xFF
            val e = to shr shift and 0xFF
            return (s + (e - s) * g).toInt()
        }
        return Color.argb(255, mix(16), mix(8), mix(0))
    }

    override fun onDraw(canvas: Canvas) {
        val cx = (paddingLeft + width - paddingRight) / 2f
        val cy = (paddingTop + height - paddingBottom) / 2f + lift / 2f

        val stage = (time / sceneTime).toInt()
        val local = time - stage * sceneTime
        scene(stage % sceneCount, time, cx, cy, x, y, r, a)

        val blend = smooth((local - (sceneTime - blendTime)) / blendTime)
        if (blend > 0f) {
            scene((stage + 1) % sceneCount, time, cx, cy, nx, ny, nr, na)
            for (i in 0 until count) {
                x[i] += (nx[i] - x[i]) * blend
                y[i] += (ny[i] - y[i]) * blend
                r[i] += (nr[i] - r[i]) * blend
                a[i] += (na[i] - a[i]) * blend
            }
        }

        for (i in 0 until count) {
            val k = ((a[i] - 0.4f) / 0.6f).coerceIn(0f, 1f)
            if (k > 0.05f) {
                glow.color = highlightColor
                glow.alpha = (52 * k).toInt()
                canvas.drawCircle(x[i], y[i], r[i] * 2.2f, glow)
            }
            paint.color = lerpColor(dotColor, highlightColor, k)
            paint.alpha = (255 * a[i]).toInt().coerceIn(0, 255)
            canvas.drawCircle(x[i], y[i], r[i], paint)
        }
    }

    private fun syncAnimation() {
        val shouldRun = isShown && windowVisibility == VISIBLE
        if (shouldRun && anim == null) {
            val loop = sceneTime * sceneCount
            anim = ValueAnimator.ofFloat(0f, loop).apply {
                duration = (loop * 1000).toLong()
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { time = it.animatedValue as Float; invalidate() }
                start()
            }
        } else if (!shouldRun) {
            anim?.cancel(); anim = null
        }
    }

    override fun onAttachedToWindow()   { super.onAttachedToWindow(); syncAnimation() }
    override fun onDetachedFromWindow() { anim?.cancel(); anim = null; super.onDetachedFromWindow() }
    override fun onVisibilityChanged(changed: View, visibility: Int) {
        super.onVisibilityChanged(changed, visibility); syncAnimation()
    }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility); syncAnimation()
    }
}
