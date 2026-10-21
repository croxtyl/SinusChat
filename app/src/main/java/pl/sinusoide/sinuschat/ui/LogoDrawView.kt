package pl.sinusoide.sinuschat.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.PathInterpolator
import kotlin.math.PI
import kotlin.math.sin

class LogoDrawView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var startNs = 0L
    private var u = 1f
    private var cx = 0f
    private var cy = 0f

    private val ease = PathInterpolator(0.45f, 0f, 0.2f, 1f)
    private val pop = PathInterpolator(0.2f, 0.9f, 0.25f, 1f)

    private val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = CYAN
    }

    private val bubble = Path()
    private val wave = Path()
    private val piece = Path()
    private val measure = PathMeasure()
    private var bubbleLen = 0f
    private var waveLen = 0f

    @Suppress("UNUSED_PARAMETER")
    fun setColors(inkColor: Int, accentColor: Int) = Unit

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        cx = w / 2f
        cy = h / 2f
        u = minOf(w, h) / 2f * 0.8f / 144f
        build()
        glow.shader = RadialGradient(cx, cy, 144f * u * 1.25f,
            intArrayOf(withAlpha(CYAN, 0.22f), withAlpha(CYAN, 0.22f), withAlpha(CYAN, 0.07f), Color.TRANSPARENT),
            floatArrayOf(0f, 0.74f, 0.86f, 1f), Shader.TileMode.CLAMP)
        startNs = System.nanoTime()
    }

    private fun x(v: Float) = cx + (v - 216f) * u
    private fun y(v: Float) = cy + (v - 216f) * u
    private fun oval(l: Float, t: Float, r: Float, b: Float) = RectF(x(l), y(t), x(r), y(b))

    private fun build() {
        bubble.reset()
        bubble.moveTo(x(124f), y(302.2f))
        bubble.lineTo(x(124f), y(142.5f))
        bubble.arcTo(oval(124f, 123f, 163f, 162f), 180f, 90f)
        bubble.lineTo(x(286.5f), y(123f))
        bubble.arcTo(oval(267f, 123f, 306f, 162f), 270f, 90f)
        bubble.lineTo(x(306f), y(244.5f))
        bubble.arcTo(oval(267f, 225f, 306f, 264f), 0f, 90f)
        bubble.lineTo(x(175f), y(264f))
        bubble.lineTo(x(133.05f), y(305.95f))
        bubble.arcTo(oval(124f, 296.9f, 134.6f, 307.5f), 45f, 135f)
        bubble.close()
        measure.setPath(bubble, false)
        bubbleLen = measure.length

        wave.reset()
        val steps = 120
        for (i in 0..steps) {
            val px = 143f + (291f - 143f) * i / steps
            val py = 197.5f + 43.5f * sin(2.0 * PI * (px - 143.5f) / 93.0).toFloat()
            if (i == 0) wave.moveTo(x(px), y(py)) else wave.lineTo(x(px), y(py))
        }
        measure.setPath(wave, false)
        waveLen = measure.length
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        if (startNs == 0L) startNs = System.nanoTime()
        val ms = (System.nanoTime() - startNs) / 1_000_000f

        val g = span(ms, 1500f, 2200f)
        if (g > 0f) {
            glow.alpha = (255 * g).toInt()
            canvas.drawCircle(cx, cy, 144f * u * 1.25f, glow)
        }

        val d = pop.getInterpolation(span(ms, 0f, 520f))
        if (d > 0f) {
            disc.alpha = (255 * minOf(1f, d * 1.4f)).toInt()
            canvas.drawCircle(cx, cy, 144f * u * (0.84f + 0.16f * d), disc)
        }

        line.strokeWidth = 20.5f * u
        trim(canvas, bubble, bubbleLen, ease.getInterpolation(span(ms, 320f, 1400f)))

        line.strokeWidth = 8.5f * u
        trim(canvas, wave, waveLen, ease.getInterpolation(span(ms, 1150f, 1900f)))

        if (ms < 2200f) postInvalidateOnAnimation()
    }

    private fun trim(canvas: Canvas, path: Path, length: Float, k: Float) {
        if (k <= 0f) return
        if (k >= 1f) { canvas.drawPath(path, line); return }
        measure.setPath(path, false)
        piece.reset()
        measure.getSegment(0f, length * k, piece, true)
        canvas.drawPath(piece, line)
    }

    private fun span(ms: Float, from: Float, to: Float) = ((ms - from) / (to - from)).coerceIn(0f, 1f)

    private fun withAlpha(color: Int, a: Float) =
        (color and 0x00FFFFFF) or ((255 * a).toInt() shl 24)

    companion object {
        const val CYAN = 0xFF3BE6F9.toInt()

        const val DRAW_MS = 1900L
    }
}
