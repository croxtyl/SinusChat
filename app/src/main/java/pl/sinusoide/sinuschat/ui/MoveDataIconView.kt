package pl.sinusoide.sinuschat.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import pl.sinusoide.sinuschat.R
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class MoveDataIconView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    enum class Mode { INTRO, MOVING, DONE, ERROR }

    private val phone = Path()
    private val arrow = Path()
    private val check = Path()
    private val bar = Path()
    private val dot = Path()

    private val wing = Path().apply {
        addRect(PIVOT_X - WING_LEN, 440f, PIVOT_X, 520f, Path.Direction.CW)
    }
    private val checkStroke = Path().apply {
        moveTo(358f, 460f); lineTo(442f, 544f); lineTo(612f, 374f)
    }
    private val checkMeasure = PathMeasure(checkStroke, false)

    private val band = Path()
    private val core = Path()
    private val turned = Path()
    private val shape = Path()
    private val piece = Path()
    private val nibOut = Path()
    private val spin = Matrix()
    private val content = RectF()

    private var ink = Color.WHITE
    private var startNs = 0L
    private var mode = Mode.INTRO
    private var modeNs = 0L
    private var exitFrom = 0f
    private val iconToView = Matrix()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nib = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 96f
        strokeCap = Paint.Cap.BUTT
        strokeJoin = Paint.Join.MITER
    }
    private val pop = OvershootInterpolator(2.2f)
    private val pen = PathInterpolator(0.4f, 0f, 0.2f, 1f)
    private val swing = OvershootInterpolator(1.3f)
    private val dotPop = OvershootInterpolator(3f)

    init {
        val res = R.drawable.ic_move_data
        for (p in IconDrawView.shapesInside(context, res, ARROW)) arrow.addPath(p)
        for (p in IconDrawView.shapesInside(context, res, ARROW, outside = true)) phone.addPath(p)
        for (p in IconDrawView.shapesInside(context, R.drawable.ic_moving_data_done, CHECK)) check.addPath(p)
        for (p in IconDrawView.shapesInside(context, R.drawable.ic_moving_data_error, BAR)) bar.addPath(p)
        for (p in IconDrawView.shapesInside(context, R.drawable.ic_moving_data_error, DOT)) dot.addPath(p)
        phone.computeBounds(content, true)
    }

    fun setColor(color: Int) {
        ink = color
        layoutGlow()
        invalidate()
    }

    fun setMode(next: Mode) {
        if (next == mode) return
        exitFrom = if (mode == Mode.MOVING) movingOffset(elapsedMs(modeNs)) else 0f
        mode = next
        modeNs = System.nanoTime()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        val scale = minOf(w, h) * 0.6f / content.height()
        iconToView.setTranslate(-content.centerX(), -content.centerY())
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

    private fun elapsedMs(fromNs: Long) = (System.nanoTime() - fromNs) / 1_000_000f

    override fun onDraw(canvas: Canvas) {
        if (width == 0) return
        if (startNs == 0L) startNs = System.nanoTime()
        val ms = elapsedMs(startNs)

        glow.alpha = (255 * ((ms - 1100f) / 600f).coerceIn(0f, 1f)).toInt()
        canvas.drawCircle(width / 2f, height / 2f, minOf(width, height) / 2f, glow)

        val enter = (ms / POP_MS).coerceIn(0f, 1f)
        paint.color = ink
        paint.alpha = (255 * (enter * 2.5f).coerceAtMost(1f)).toInt()

        canvas.save()
        canvas.concat(iconToView)
        val more = when (mode) {
            Mode.INTRO -> drawIntro(canvas, ms, enter)
            Mode.MOVING -> { drawMoving(canvas, elapsedMs(modeNs)); true }
            Mode.DONE -> drawDone(canvas, elapsedMs(modeNs))
            Mode.ERROR -> drawError(canvas, elapsedMs(modeNs))
        }
        canvas.restore()

        if (isShown && more) postInvalidateOnAnimation()
    }

    private fun drawIntro(canvas: Canvas, ms: Float, enter: Float): Boolean {
        val scale = 0.86f + 0.14f * pop.getInterpolation(enter)
        val done = ms >= LOWER_FROM + WING_MS

        if (!done) {
            val k = pen.getInterpolation(((ms - SHAFT_FROM) / SHAFT_MS).coerceIn(0f, 1f))
            shape.rewind()
            if (k > 0f) {
                band.rewind()
                band.addRect(LEFT, 440f, LEFT + (RIGHT - LEFT) * k, 520f, Path.Direction.CW)
                core.set(arrow)
                core.op(band, Path.Op.INTERSECT)
                shape.set(core)
            }
            addWing(ms, UPPER_FROM, 45f)
            addWing(ms, LOWER_FROM, -45f)
        }

        canvas.scale(scale, scale, content.centerX(), content.centerY())
        canvas.drawPath(phone, paint)
        canvas.drawPath(if (done) arrow else shape, paint)
        return !done
    }

    private fun addWing(ms: Float, from: Float, angle: Float) {
        val k = ((ms - from) / WING_MS).coerceIn(0f, 1f)
        if (k <= 0f) return
        spin.setRotate(angle * swing.getInterpolation(k), PIVOT_X, PIVOT_Y)
        wing.transform(spin, turned)
        shape.op(turned, Path.Op.UNION)
    }

    private fun movingOffset(ms: Float): Float = (ms % CYCLE_MS) / CYCLE_MS * SHIFT

    private fun drawMoving(canvas: Canvas, ms: Float) {
        canvas.drawPath(phone, paint)
        drawArrows(canvas, movingOffset(ms), 255)
    }

    private fun drawArrows(canvas: Canvas, dx: Float, alpha: Int) {
        if (alpha <= 0) return
        val before = paint.alpha
        paint.alpha = alpha * before / 255
        canvas.save()
        canvas.clipRect(SCREEN)
        canvas.translate(dx, 0f)
        canvas.drawPath(arrow, paint)
        canvas.translate(-SHIFT, 0f)
        canvas.drawPath(arrow, paint)
        canvas.restore()
        paint.alpha = before
    }

    private fun drawExit(canvas: Canvas, ms: Float) {
        val k = (ms / EXIT_MS).coerceIn(0f, 1f)
        if (k >= 1f) return
        drawArrows(canvas, exitFrom + ms / CYCLE_MS * SHIFT * 1.6f, (255 * (1f - k)).toInt())
    }

    private fun drawDone(canvas: Canvas, ms: Float): Boolean {
        val b = ((ms - BUMP_FROM) / BUMP_MS).coerceIn(0f, 1f)
        val bump = 1f + 0.06f * sin(PI.toFloat() * b)
        canvas.scale(bump, bump, content.centerX(), content.centerY())
        canvas.drawPath(phone, paint)
        drawExit(canvas, ms)

        val k = pen.getInterpolation(((ms - CHECK_FROM) / CHECK_MS).coerceIn(0f, 1f))
        if (k >= 1f) canvas.drawPath(check, paint)
        else if (k > 0f) {
            piece.rewind()
            checkMeasure.getSegment(0f, checkMeasure.length * k, piece, true)
            nibOut.rewind()
            nib.getFillPath(piece, nibOut)
            nibOut.op(check, Path.Op.INTERSECT)
            canvas.drawPath(nibOut, paint)
        }
        return b < 1f
    }

    private fun drawError(canvas: Canvas, ms: Float): Boolean {
        val s = (ms / SHAKE_MS).coerceIn(0f, 1f)
        val shake = if (s < 1f) 22f * exp(-4f * s) * sin(2f * PI.toFloat() * 4.5f * s) else 0f
        canvas.translate(shake, 0f)
        canvas.drawPath(phone, paint)
        drawExit(canvas, ms)

        val k = pen.getInterpolation(((ms - BAR_FROM) / BAR_MS).coerceIn(0f, 1f))
        if (k > 0f) {
            canvas.save()
            canvas.clipRect(BAR.left, BAR.top, BAR.right, BAR_TOP + (BAR_BOTTOM - BAR_TOP) * k)
            canvas.drawPath(bar, paint)
            canvas.restore()
        }
        val d = ((ms - DOT_FROM) / DOT_MS).coerceIn(0f, 1f)
        if (d > 0f) {
            val sc = dotPop.getInterpolation(d)
            canvas.save()
            canvas.scale(sc, sc, 480f, 640f)
            canvas.drawPath(dot, paint)
            canvas.restore()
        }
        return d < 1f || s < 1f
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) {
            if (mode == Mode.INTRO) startNs = 0L
            invalidate()
        }
    }

    private fun withAlpha(color: Int, a: Float) =
        (color and 0x00FFFFFF) or ((255 * a).toInt() shl 24)

    private companion object {
        val ARROW = RectF(300f, 300f, 660f, 660f)
        val CHECK = RectF(300f, 300f, 680f, 640f)
        val BAR = RectF(400f, 260f, 560f, 540f)
        val DOT = RectF(400f, 580f, 560f, 700f)
        const val BAR_TOP = 280f
        const val BAR_BOTTOM = 520f
        val SCREEN = RectF(280f, 120f, 680f, 840f)
        const val SHIFT = 400f

        const val LEFT = 320f
        const val RIGHT = 660f

        const val PIVOT_X = 584f
        const val PIVOT_Y = 480f
        const val WING_LEN = 186.7f

        const val POP_MS = 420f
        const val SHAFT_FROM = 460f
        const val SHAFT_MS = 560f
        const val UPPER_FROM = 1060f
        const val LOWER_FROM = 1220f
        const val WING_MS = 440f

        const val CYCLE_MS = 1150f
        const val EXIT_MS = 280f
        const val CHECK_FROM = 160f
        const val CHECK_MS = 520f
        const val BUMP_FROM = 680f
        const val BUMP_MS = 320f
        const val SHAKE_MS = 620f
        const val BAR_FROM = 140f
        const val BAR_MS = 320f
        const val DOT_FROM = 420f
        const val DOT_MS = 300f
    }
}
