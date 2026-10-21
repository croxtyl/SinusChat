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

class SetupIllustration @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    enum class Scene {
        MARK, LANGUAGE, MAIL, CODE, PROFILE, THEME, ACCOUNT, LOCK,
        PRIVACY, NOTIFY, INTERNET, EXPERIMENT, BACKUP, TRANSFER, DONE
    }

    private val d = resources.displayMetrics.density
    private var scene = Scene.MARK
    private var startNs = 0L
    private var ink = Color.WHITE
    private var accent = ThemeUi.accent(context)

    private val duration = 1150f
    private val ease = PathInterpolator(0.17f, 0.84f, 0.26f, 1f)

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val solid = Paint(Paint.ANTI_ALIAS_FLAG)
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dst = Path()
    private val measure = PathMeasure()

    private sealed class Piece(val from: Float, val to: Float, val color: Int?) {
        class Stroke(val path: Path, from: Float, to: Float, val width: Float, color: Int?)
            : Piece(from, to, color)
        class Solid(val path: Path, from: Float, to: Float, color: Int?) : Piece(from, to, color)
        class Dot(val cx: Float, val cy: Float, val r: Float, from: Float, to: Float, color: Int?)
            : Piece(from, to, color)
    }

    private val pieces = ArrayList<Piece>()

    fun setScene(next: Scene) {
        scene = next
        rebuild()
        startNs = System.nanoTime()
        invalidate()
    }

    fun setColors(inkColor: Int, accentColor: Int) {
        ink = inkColor
        accent = accentColor
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        rebuild()
        if (startNs == 0L) startNs = System.nanoTime()
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        if (startNs == 0L) startNs = System.nanoTime()
        val raw = ((System.nanoTime() - startNs) / 1_000_000f) / duration
        val t = ease.getInterpolation(raw.coerceIn(0f, 1f))

        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f

        canvas.save()
        canvas.translate(0f, (1f - t) * 10f * d)
        canvas.scale(0.94f + 0.06f * t, 0.94f + 0.06f * t, cx, cy)

        val ha = span(raw, 0f, 0.35f)
        if (ha > 0f) {
            halo.shader = RadialGradient(cx, cy, r * 0.95f,
                intArrayOf(alpha(accent, 0.22f * ha), alpha(accent, 0.07f * ha), Color.TRANSPARENT),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, r * 0.95f, halo)
        }

        for (p in pieces) {
            val k = span(t, p.from, p.to)
            if (k <= 0f) continue
            when (p) {
                is Piece.Stroke -> {
                    line.strokeWidth = p.width
                    line.color = alpha(p.color ?: ink, 1f)
                    if (k >= 1f) canvas.drawPath(p.path, line)
                    else {
                        measure.setPath(p.path, false)
                        dst.reset()
                        measure.getSegment(0f, measure.length * k, dst, true)
                        canvas.drawPath(dst, line)
                    }
                }
                is Piece.Solid -> {
                    solid.color = alpha(p.color ?: accent, k)
                    canvas.drawPath(p.path, solid)
                }
                is Piece.Dot -> {
                    val s = if (k >= 1f) 1f else 1.18f * k - 0.18f * k * k
                    solid.color = alpha(p.color ?: accent, minOf(1f, k * 1.4f))
                    canvas.drawCircle(p.cx, p.cy, p.r * s, solid)
                }
            }
        }

        canvas.restore()
        if (raw < 1f) postInvalidateOnAnimation()
    }

    private fun span(t: Float, from: Float, to: Float): Float =
        ((t - from) / (to - from)).coerceIn(0f, 1f)

    private fun alpha(color: Int, a: Float): Int =
        (color and 0x00FFFFFF) or (((255 * a.coerceIn(0f, 1f)).toInt()) shl 24)

    private fun rebuild() {
        pieces.clear()
        if (width == 0 || height == 0) return
        val cx = width / 2f
        val cy = height / 2f
        val u = minOf(width, height) / 2f * 0.62f
        val w = 2.6f * d
        val thin = 2.0f * d

        fun path(build: Path.() -> Unit) = Path().apply(build)
        fun stroke(from: Float, to: Float, width: Float = w, color: Int? = null, build: Path.() -> Unit) {
            pieces.add(Piece.Stroke(path(build), from, to, width, color))
        }
        fun solidP(from: Float, to: Float, color: Int? = null, build: Path.() -> Unit) {
            pieces.add(Piece.Solid(path(build), from, to, color))
        }
        fun dot(x: Float, y: Float, rad: Float, from: Float, to: Float, color: Int? = null) {
            pieces.add(Piece.Dot(x, y, rad, from, to, color))
        }
        fun oval(l: Float, t: Float, rr: Float, b: Float) = RectF(l, t, rr, b)

        when (scene) {
            Scene.MARK -> {
                stroke(0.05f, 0.8f) {
                    addRoundRect(oval(cx - u, cy - u * 0.85f, cx + u, cy + u * 0.35f),
                        u * 0.34f, u * 0.34f, Path.Direction.CW)
                }
                stroke(0.55f, 0.9f) {
                    moveTo(cx - u * 0.3f, cy + u * 0.33f)
                    lineTo(cx - u * 0.06f, cy + u * 0.78f)
                    lineTo(cx + u * 0.2f, cy + u * 0.33f)
                }
                for (i in -1..1) dot(cx + i * u * 0.36f, cy - u * 0.24f, u * 0.09f,
                    0.72f + (i + 1) * 0.07f, 0.92f + (i + 1) * 0.03f, accent)
            }

            Scene.LANGUAGE -> {
                stroke(0.05f, 0.62f) { addCircle(cx, cy, u, Path.Direction.CW) }
                stroke(0.42f, 0.82f, thin) { addOval(oval(cx - u * 0.42f, cy - u, cx + u * 0.42f, cy + u), Path.Direction.CW) }
                stroke(0.55f, 0.95f, thin) { moveTo(cx - u, cy); lineTo(cx + u, cy) }
                dot(cx + u * 0.52f, cy - u * 0.42f, u * 0.1f, 0.85f, 1f, accent)
            }

            Scene.MAIL -> {
                stroke(0.05f, 0.7f) {
                    addRoundRect(oval(cx - u, cy - u * 0.66f, cx + u, cy + u * 0.66f),
                        u * 0.16f, u * 0.16f, Path.Direction.CW)
                }
                stroke(0.5f, 0.92f) {
                    moveTo(cx - u * 0.92f, cy - u * 0.5f)
                    lineTo(cx, cy + u * 0.12f)
                    lineTo(cx + u * 0.92f, cy - u * 0.5f)
                }
                dot(cx + u * 0.78f, cy - u * 0.78f, u * 0.14f, 0.85f, 1f, accent)
            }

            Scene.CODE -> {
                val bw = u * 0.3f
                val gap = u * 0.12f
                val total = 4 * bw + 3 * gap
                var x = cx - total / 2f
                for (i in 0 until 4) {
                    val left = x
                    stroke(0.05f + i * 0.09f, 0.45f + i * 0.09f, thin) {
                        addRoundRect(oval(left, cy - bw * 0.8f, left + bw, cy + bw * 0.8f),
                            bw * 0.3f, bw * 0.3f, Path.Direction.CW)
                    }
                    if (i < 3) dot(left + bw / 2f, cy, bw * 0.22f, 0.6f + i * 0.1f, 0.8f + i * 0.1f, accent)
                    x += bw + gap
                }
            }

            Scene.PROFILE -> {
                stroke(0.05f, 0.55f) { addCircle(cx, cy - u * 0.36f, u * 0.36f, Path.Direction.CW) }
                stroke(0.45f, 0.9f) {
                    addArc(oval(cx - u * 0.72f, cy + u * 0.02f, cx + u * 0.72f, cy + u * 1.3f), 200f, 140f)
                }
                dot(cx + u * 0.62f, cy - u * 0.62f, u * 0.13f, 0.86f, 1f, accent)
            }

            Scene.THEME -> {
                stroke(0.05f, 0.62f) { addCircle(cx, cy, u, Path.Direction.CW) }
                solidP(0.5f, 0.95f, accent) {
                    addArc(oval(cx - u, cy - u, cx + u, cy + u), 90f, 180f)
                    close()
                }
            }

            Scene.ACCOUNT -> {
                stroke(0.05f, 0.62f) { addCircle(cx, cy, u, Path.Direction.CW) }
                stroke(0.5f, 0.8f) { moveTo(cx, cy); lineTo(cx, cy - u * 0.56f) }
                stroke(0.68f, 0.95f, w, accent) { moveTo(cx, cy); lineTo(cx + u * 0.42f, cy + u * 0.2f) }
                dot(cx, cy, u * 0.08f, 0.9f, 1f, accent)
            }

            Scene.LOCK -> {
                stroke(0.05f, 0.5f) {
                    addArc(oval(cx - u * 0.42f, cy - u * 0.95f, cx + u * 0.42f, cy - u * 0.1f), 180f, 180f)
                }
                stroke(0.38f, 0.85f) {
                    addRoundRect(oval(cx - u * 0.7f, cy - u * 0.16f, cx + u * 0.7f, cy + u * 0.8f),
                        u * 0.18f, u * 0.18f, Path.Direction.CW)
                }
                dot(cx, cy + u * 0.32f, u * 0.11f, 0.86f, 1f, accent)
            }

            Scene.PRIVACY -> {
                stroke(0.05f, 0.68f) {
                    moveTo(cx, cy - u)
                    lineTo(cx + u * 0.82f, cy - u * 0.55f)
                    lineTo(cx + u * 0.82f, cy + u * 0.16f)
                    quadTo(cx + u * 0.78f, cy + u * 0.82f, cx, cy + u)
                    quadTo(cx - u * 0.78f, cy + u * 0.82f, cx - u * 0.82f, cy + u * 0.16f)
                    lineTo(cx - u * 0.82f, cy - u * 0.55f)
                    close()
                }
                stroke(0.6f, 0.95f, w, accent) {
                    moveTo(cx - u * 0.3f, cy + u * 0.02f)
                    lineTo(cx - u * 0.06f, cy + u * 0.28f)
                    lineTo(cx + u * 0.36f, cy - u * 0.3f)
                }
            }

            Scene.NOTIFY -> {
                stroke(0.05f, 0.7f) {
                    moveTo(cx - u * 0.62f, cy + u * 0.35f)
                    quadTo(cx - u * 0.54f, cy - u * 0.48f, cx, cy - u * 0.72f)
                    quadTo(cx + u * 0.54f, cy - u * 0.48f, cx + u * 0.62f, cy + u * 0.35f)
                    close()
                }
                stroke(0.55f, 0.85f, thin) { moveTo(cx - u * 0.78f, cy + u * 0.4f); lineTo(cx + u * 0.78f, cy + u * 0.4f) }
                stroke(0.7f, 0.95f, thin, accent) {
                    addArc(oval(cx - u * 0.22f, cy + u * 0.42f, cx + u * 0.22f, cy + u * 0.8f), 0f, 180f)
                }
                dot(cx + u * 0.68f, cy - u * 0.66f, u * 0.12f, 0.88f, 1f, accent)
            }

            Scene.INTERNET -> {
                dot(cx, cy + u * 0.62f, u * 0.13f, 0.05f, 0.3f, accent)
                for (i in 0 until 3) {
                    val rad = u * (0.4f + i * 0.3f)
                    val from = 0.2f + i * 0.2f
                    stroke(from, from + 0.35f, if (i == 2) w else thin,
                        if (i == 2) accent else null) {
                        addArc(oval(cx - rad, cy + u * 0.62f - rad, cx + rad, cy + u * 0.62f + rad),
                            210f, 120f)
                    }
                }
            }

            Scene.EXPERIMENT -> {
                stroke(0.05f, 0.72f) {
                    moveTo(cx - u * 0.24f, cy - u * 0.85f)
                    lineTo(cx - u * 0.24f, cy - u * 0.18f)
                    lineTo(cx - u * 0.74f, cy + u * 0.78f)
                    lineTo(cx + u * 0.74f, cy + u * 0.78f)
                    lineTo(cx + u * 0.24f, cy - u * 0.18f)
                    lineTo(cx + u * 0.24f, cy - u * 0.85f)
                }
                solidP(0.62f, 0.95f, accent) {
                    moveTo(cx - u * 0.52f, cy + u * 0.42f)
                    lineTo(cx + u * 0.52f, cy + u * 0.42f)
                    lineTo(cx + u * 0.68f, cy + u * 0.72f)
                    lineTo(cx - u * 0.68f, cy + u * 0.72f)
                    close()
                }
                dot(cx + u * 0.02f, cy + u * 0.2f, u * 0.08f, 0.9f, 1f, accent)
            }

            Scene.BACKUP -> {
                stroke(0.05f, 0.72f) {
                    moveTo(cx - u * 0.52f, cy + u * 0.34f)
                    arcTo(oval(cx - u * 0.86f, cy - u * 0.02f, cx - u * 0.18f, cy + u * 0.66f), 90f, 180f, false)
                    arcTo(oval(cx - u * 0.52f, cy - u * 0.66f, cx + u * 0.3f, cy + u * 0.16f), 170f, -170f, false)
                    arcTo(oval(cx + u * 0.18f, cy - u * 0.2f, cx + u * 0.86f, cy + u * 0.48f), 270f, 180f, false)
                    close()
                }
                stroke(0.62f, 0.9f, w, accent) { moveTo(cx, cy + u * 0.9f); lineTo(cx, cy + u * 0.16f) }
                stroke(0.78f, 1f, w, accent) {
                    moveTo(cx - u * 0.2f, cy + u * 0.36f)
                    lineTo(cx, cy + u * 0.14f)
                    lineTo(cx + u * 0.2f, cy + u * 0.36f)
                }
            }

            Scene.TRANSFER -> {
                stroke(0.05f, 0.5f) {
                    addRoundRect(oval(cx - u * 1.0f, cy - u * 0.66f, cx - u * 0.4f, cy + u * 0.66f),
                        u * 0.13f, u * 0.13f, Path.Direction.CW)
                }
                stroke(0.3f, 0.75f) {
                    addRoundRect(oval(cx + u * 0.4f, cy - u * 0.66f, cx + u * 1.0f, cy + u * 0.66f),
                        u * 0.13f, u * 0.13f, Path.Direction.CW)
                }
                stroke(0.66f, 0.95f, w, accent) { moveTo(cx - u * 0.28f, cy); lineTo(cx + u * 0.24f, cy) }
                stroke(0.82f, 1f, w, accent) {
                    moveTo(cx + u * 0.06f, cy - u * 0.16f)
                    lineTo(cx + u * 0.26f, cy)
                    lineTo(cx + u * 0.06f, cy + u * 0.16f)
                }
            }

            Scene.DONE -> {
                stroke(0.05f, 0.62f) { addCircle(cx, cy, u, Path.Direction.CW) }
                stroke(0.45f, 0.95f, 3.4f * d, accent) {
                    moveTo(cx - u * 0.42f, cy + u * 0.02f)
                    lineTo(cx - u * 0.1f, cy + u * 0.36f)
                    lineTo(cx + u * 0.46f, cy - u * 0.34f)
                }
            }
        }
    }
}
