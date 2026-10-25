package pl.sinusoide.sinuschat.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

class RetroCheckDrawable(private val density: Float) : Drawable() {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = BORDER
    }
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.SQUARE
        strokeJoin = Paint.Join.MITER
        color = CHECK
    }
    private val box = RectF()
    private val path = Path()
    private var checked = false
    private var enabled = true
    private var pressed = false
    private var alphaValue = 255

    override fun isStateful() = true

    override fun onStateChange(state: IntArray): Boolean {
        val c = state.contains(android.R.attr.state_checked)
        val e = state.contains(android.R.attr.state_enabled)
        val p = state.contains(android.R.attr.state_pressed)
        if (c == checked && e == enabled && p == pressed) return false
        checked = c; enabled = e; pressed = p
        invalidateSelf()
        return true
    }

    override fun getIntrinsicWidth() = (SIZE * density).toInt()
    override fun getIntrinsicHeight() = (SIZE * density).toInt()

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val side = minOf(b.width(), b.height(), (SIZE * density).toInt()).toFloat()
        val left = b.exactCenterX() - side / 2f
        val top = b.exactCenterY() - side / 2f
        val sw = density.coerceAtLeast(1f)
        box.set(left + sw / 2f, top + sw / 2f, left + side - sw / 2f, top + side - sw / 2f)
        val from = if (pressed) 0xFFB9CBE6.toInt() else 0xFFDCE4EE.toInt()
        fill.shader = LinearGradient(box.left, box.top, box.right, box.bottom,
            from, 0xFFFFFFFF.toInt(), Shader.TileMode.CLAMP)
        val a = if (enabled) alphaValue else alphaValue / 2
        fill.alpha = a; border.alpha = a; tick.alpha = a
        border.strokeWidth = sw
        canvas.drawRect(box, fill)
        canvas.drawRect(box, border)
        if (!checked) return
        val u = side / 13f
        tick.strokeWidth = 2f * u
        path.reset()
        path.moveTo(left + 3.5f * u, top + 6.5f * u)
        path.lineTo(left + 5.5f * u, top + 8.5f * u)
        path.lineTo(left + 9.5f * u, top + 4.5f * u)
        canvas.drawPath(path, tick)
    }

    override fun setAlpha(alpha: Int) { alphaValue = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        const val SIZE = 20f
        const val BORDER = 0xFF1C5180.toInt()
        const val CHECK = 0xFF21A121.toInt()
    }
}
