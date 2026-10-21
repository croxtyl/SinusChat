package pl.sinusoide.sinuschat.call

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import kotlin.math.ceil
import kotlin.math.min

class TileGridLayout(context: Context) : ViewGroup(context) {

    private val gap = 10 * resources.displayMetrics.density
    private val order = ArrayList<View>()
    private val current = HashMap<View, RectF>()
    private val from = HashMap<View, RectF>()
    private val fromAlpha = HashMap<View, Float>()
    private val leaving = HashSet<View>()
    private var progress = 1f
    private var animator: ValueAnimator? = null

    val tiles: List<View> get() = order

    fun update(views: List<View>) {
        if (views == order && leaving.none { it in views }) return
        snapshot()
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c !in views) leaving += c
        }
        for (v in views) {
            if (v.parent === this) leaving.remove(v)
            else {
                (v.parent as? ViewGroup)?.removeView(v)
                addView(v)
                from.remove(v)
                fromAlpha[v] = 0f
                v.alpha = 0f
            }
        }
        order.clear()
        order.addAll(views)
        start()
    }

    private fun snapshot() {
        from.clear()
        fromAlpha.clear()
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            current[c]?.let { from[c] = RectF(it) }
            fromAlpha[c] = c.alpha
        }
    }

    private fun start() {
        val old = animator
        animator = null
        old?.cancel()
        progress = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 380
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { progress = it.animatedValue as Float; requestLayout() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) {
                    if (animator !== a) return
                    animator = null
                    leaving.toList().forEach { v -> leaving.remove(v); current.remove(v); removeView(v) }
                    from.clear()
                    fromAlpha.clear()
                    progress = 1f
                    requestLayout()
                }
            })
            start()
        }
        requestLayout()
    }

    private fun columns(n: Int, w: Float, h: Float): Int {
        var best = 1
        var bestScore = -1f
        for (cols in 1..n) {
            val rows = ceil(n / cols.toFloat())
            val cw = (w - gap * (cols - 1)) / cols
            val ch = (h - gap * (rows - 1)) / rows
            val score = min(cw, ch)
            if (score > bestScore + 0.5f) { best = cols; bestScore = score }
        }
        return best
    }

    private fun targets(w: Int, h: Int): Map<View, RectF> {
        val out = HashMap<View, RectF>()
        val n = order.size
        if (n == 0 || w <= 0 || h <= 0) return out
        val cols = columns(n, w.toFloat(), h.toFloat())
        val rows = ceil(n / cols.toFloat()).toInt()
        val cellW = (w - gap * (cols - 1)) / cols
        val cellH = (h - gap * (rows - 1)) / rows
        order.forEachIndexed { i, v ->
            val r = i / cols
            val inRow = if (r == rows - 1) n - r * cols else cols
            val offset = (w - (inRow * cellW + (inRow - 1) * gap)) / 2f
            val left = offset + (i % cols) * (cellW + gap)
            val top = r * (cellH + gap)
            out[v] = RectF(left, top, left + cellW, top + cellH)
        }
        return out
    }

    private fun scaled(r: RectF, s: Float) = RectF(
        r.centerX() - r.width() * s / 2, r.centerY() - r.height() * s / 2,
        r.centerX() + r.width() * s / 2, r.centerY() + r.height() * s / 2)

    private fun frameFor(v: View, target: RectF?): RectF {
        val t = progress
        if (v in leaving) {
            val f = from[v] ?: current[v] ?: RectF()
            return scaled(f, 1f - 0.15f * t)
        }
        val to = target ?: return RectF()
        val f = from[v] ?: scaled(to, 0.7f)
        return RectF(f.left + (to.left - f.left) * t, f.top + (to.top - f.top) * t,
            f.right + (to.right - f.right) * t, f.bottom + (to.bottom - f.bottom) * t)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val t = targets(w, h)
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            val r = frameFor(c, t[c])
            c.measure(MeasureSpec.makeMeasureSpec(r.width().toInt().coerceAtLeast(0), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(r.height().toInt().coerceAtLeast(0), MeasureSpec.EXACTLY))
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val targets = targets(r - l, b - t)
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            val f = frameFor(c, targets[c])
            c.layout(f.left.toInt(), f.top.toInt(), f.right.toInt(), f.bottom.toInt())
            val a0 = fromAlpha[c] ?: 1f
            c.alpha = if (c in leaving) a0 * (1f - progress) else a0 + (1f - a0) * progress
            current[c] = f
        }
    }
}
