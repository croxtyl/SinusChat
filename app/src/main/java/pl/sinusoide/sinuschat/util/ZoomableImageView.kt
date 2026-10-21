package pl.sinusoide.sinuschat.util

import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

class ZoomableImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    var onScaleChanged: ((Float) -> Unit)? = null

    var onClick: (() -> Unit)? = null

    private val matrixValues = FloatArray(9)
    private var minScale = 1f
    private val maxScale = 5f
    private var baseScale = 1f
    private val imgMatrix = Matrix()

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                var factor = d.scaleFactor
                val current = currentScale()
                if (current * factor > maxScale) factor = maxScale / current
                if (current * factor < minScale * 0.9f) factor = (minScale * 0.9f) / current
                imgMatrix.postScale(factor, factor, d.focusX - paddingLeft, d.focusY - paddingTop)
                fixTranslation()
                imageMatrix = imgMatrix
                onScaleChanged?.invoke(currentScale() / baseScale)
                return true
            }
        })

    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val target = if (currentScale() > baseScale * 1.3f) baseScale else baseScale * 2.5f
                animateScaleTo(target, e.x - paddingLeft, e.y - paddingTop)
                return true
            }
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                if (currentScale() > baseScale * 1.02f) {
                    imgMatrix.postTranslate(-dx, -dy)
                    fixTranslation()
                    imageMatrix = imgMatrix
                }
                return true
            }
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                onClick?.invoke()
                performClick()
                return true
            }
        })

    private var ready = false

    init {
        scaleType = ScaleType.MATRIX
        isClickable = true
        ready = true
    }

    override fun setImageDrawable(drawable: android.graphics.drawable.Drawable?) {
        super.setImageDrawable(drawable)
        if (!ready) return
        if (width > 0 && height > 0) fitCenter() else post { fitCenter() }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        fitCenter()
    }

    private var fitDw = -1
    private var fitDh = -1
    private var fitVw = -1
    private var fitVh = -1

    override fun onDraw(canvas: android.graphics.Canvas) {
        val d = drawable
        if (ready && d != null && width > 0 && height > 0) {
            val dw = d.intrinsicWidth
            val dh = d.intrinsicHeight
            if (dw > 0 && dh > 0) {
                if (dw != fitDw || dh != fitDh || width != fitVw || height != fitVh) {
                    fitCenter(keepZoom = dw == fitDw && dh == fitDh)
                } else if (imageMatrix != imgMatrix) {
                    imageMatrix = imgMatrix
                }
            }
        }
        super.onDraw(canvas)
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        val changed = left != paddingLeft || top != paddingTop || right != paddingRight || bottom != paddingBottom
        super.setPadding(left, top, right, bottom)
        if (changed && drawable != null) fitCenter(keepZoom = true)
    }

    private fun contentWidth(): Float = (width - paddingLeft - paddingRight).toFloat()

    private fun contentHeight(): Float = (height - paddingTop - paddingBottom).toFloat()

    private fun fitCenter(keepZoom: Boolean = false) {
        val d = drawable ?: return
        val vw = contentWidth(); val vh = contentHeight()
        if (vw <= 0f || vh <= 0f) return
        val dw = d.intrinsicWidth.toFloat(); val dh = d.intrinsicHeight.toFloat()
        if (dw == 0f || dh == 0f) return
        val scale = minOf(vw / dw, vh / dh)
        val zoomed = keepZoom && currentScale() > baseScale * 1.02f
        baseScale = scale
        minScale = scale
        fitDw = d.intrinsicWidth
        fitDh = d.intrinsicHeight
        fitVw = width
        fitVh = height
        if (zoomed) {
            fixTranslation()
            imageMatrix = imgMatrix
            return
        }
        imgMatrix.reset()
        imgMatrix.postScale(scale, scale)
        val extraX = (vw - dw * scale) / 2f
        val extraY = (vh - dh * scale) / 2f
        imgMatrix.postTranslate(extraX, extraY)
        imageMatrix = imgMatrix
        onScaleChanged?.invoke(1f)
    }

    private fun currentScale(): Float {
        imgMatrix.getValues(matrixValues)
        return matrixValues[Matrix.MSCALE_X]
    }

    private fun fixTranslation() {
        val d = drawable ?: return
        imgMatrix.getValues(matrixValues)
        val transX = matrixValues[Matrix.MTRANS_X]
        val transY = matrixValues[Matrix.MTRANS_Y]
        val scale = matrixValues[Matrix.MSCALE_X]
        val imgW = d.intrinsicWidth * scale
        val imgH = d.intrinsicHeight * scale
        val vw = contentWidth(); val vh = contentHeight()

        val newX = fixTrans(transX, vw, imgW)
        val newY = fixTrans(transY, vh, imgH)
        imgMatrix.postTranslate(newX - transX, newY - transY)
    }

    private fun fixTrans(trans: Float, viewSize: Float, imgSize: Float): Float {
        val minTrans: Float; val maxTrans: Float
        if (imgSize <= viewSize) {
            minTrans = (viewSize - imgSize) / 2f
            maxTrans = minTrans
        } else {
            minTrans = viewSize - imgSize
            maxTrans = 0f
        }
        return trans.coerceIn(minTrans, maxTrans)
    }

    private fun animateScaleTo(targetScale: Float, focusX: Float, focusY: Float) {
        val start = currentScale()
        val anim = android.animation.ValueAnimator.ofFloat(start, targetScale)
        anim.duration = 250
        anim.interpolator = android.view.animation.DecelerateInterpolator()
        var last = start
        anim.addUpdateListener {
            val now = it.animatedValue as Float
            val factor = now / last
            last = now
            imgMatrix.postScale(factor, factor, focusX, focusY)
            fixTranslation()
            imageMatrix = imgMatrix
            onScaleChanged?.invoke(currentScale() / baseScale)
        }
        anim.start()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        parent?.requestDisallowInterceptTouchEvent(currentScale() > baseScale * 1.05f)
        return true
    }
}
