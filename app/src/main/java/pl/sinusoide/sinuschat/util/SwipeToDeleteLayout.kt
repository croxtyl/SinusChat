package pl.sinusoide.sinuschat.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

@SuppressLint("ViewConstructor")
class SwipeToDeleteLayout(
    context: Context,
    private val content: View,
    label: String,
    iconRes: Int,
    private val onDeleteClick: (SwipeToDeleteLayout) -> Unit
) : FrameLayout(context) {

    companion object {
        private var openRow: java.lang.ref.WeakReference<SwipeToDeleteLayout>? = null

        fun closeOpenRow() { openRow?.get()?.close() }
    }

    private val dp = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFling = ViewConfiguration.get(context).scaledMinimumFlingVelocity * 5
    private val buttonWidth = 92 * dp
    private val red = 0xFFE5484D.toInt()

    private val leftButton = deleteButton(label, iconRes, alignEnd = false)
    private val rightButton = deleteButton(label, iconRes, alignEnd = true)

    private var downX = 0f
    private var downY = 0f
    private var startTranslation = 0f
    private var dragging = false
    private var removed = false
    private var velocity: VelocityTracker? = null

    val isOpen: Boolean get() = content.translationX != 0f

    private var restBackground: android.graphics.drawable.Drawable? = null
    private var coverBackground: android.graphics.drawable.Drawable? = null

    init {
        addView(leftButton, LayoutParams(buttonWidth.toInt(), LayoutParams.MATCH_PARENT, Gravity.START))
        addView(rightButton, LayoutParams(buttonWidth.toInt(), LayoutParams.MATCH_PARENT, Gravity.END))
        val bgColor = (content.background as? android.graphics.drawable.ColorDrawable)?.color
        if (content.background == null || (bgColor != null && Color.alpha(bgColor) < 255)) {
            restBackground = content.background
            coverBackground = android.graphics.drawable.ColorDrawable(
                pl.sinusoide.sinuschat.ui.ThemeUi.backgroundSolid(context))
            if (content.background == null && !pl.sinusoide.sinuschat.ui.ThemeUi.isFlow(context)) {
                content.background = coverBackground
                restBackground = coverBackground
            }
        }
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        updateButtons(0f)
    }

    private fun deleteButton(label: String, iconRes: Int, alignEnd: Boolean) = FrameLayout(context).apply {
        val m = (8 * dp).toInt()
        val pill = pill(label, iconRes)
        addView(pill, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            setMargins(if (alignEnd) m / 2 else m, m / 2 + (2 * dp).toInt(),
                       if (alignEnd) m else m / 2, m / 2 + (2 * dp).toInt())
        })
    }

    private fun pill(label: String, iconRes: Int) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 14 * dp
            setColor(red)
        }
        isClickable = true
        contentDescription = label
        addView(ImageView(context).apply {
            setImageResource(iconRes); setColorFilter(Color.WHITE)
        }, LinearLayout.LayoutParams((22 * dp).toInt(), (22 * dp).toInt()).apply {
            bottomMargin = (4 * dp).toInt()
        })
        addView(TextView(context).apply {
            text = label; textSize = 13f; setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        setOnClickListener { if (!removed) onDeleteClick(this@SwipeToDeleteLayout) }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (removed) return true
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                openRow?.get()?.takeIf { it !== this }?.close()
                downX = ev.rawX; downY = ev.rawY
                startTranslation = content.translationX
                dragging = false
                velocity?.recycle(); velocity = VelocityTracker.obtain().also { it.addMovement(ev) }
            }
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(ev)
                val dx = ev.rawX - downX
                val dy = ev.rawY - downY
                if (!dragging && abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.5f) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isOpen && isOverContent(ev)) { close(); return true }
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (removed) return true
        velocity?.addMovement(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - downX
                if (!dragging && abs(dx) > touchSlop) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                if (dragging) setOffset(rubberBand(startTranslation + dx))
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    velocity?.computeCurrentVelocity(1000)
                    val vx = velocity?.xVelocity ?: 0f
                    val t = content.translationX
                    val target = when {
                        abs(vx) > minFling ->
                            if (vx < 0 && t < 0) -buttonWidth
                            else if (vx > 0 && t > 0) buttonWidth
                            else 0f
                        abs(t) > buttonWidth * 0.4f -> if (t < 0) -buttonWidth else buttonWidth
                        else -> 0f
                    }
                    animateTo(target)
                } else if (isOpen) {
                    close()
                }
                dragging = false
                velocity?.recycle(); velocity = null
                return true
            }
        }
        return super.onTouchEvent(ev)
    }

    private fun isOverContent(ev: MotionEvent): Boolean {
        val t = content.translationX
        return if (t < 0) ev.x < width + t else ev.x > t
    }

    private fun rubberBand(x: Float): Float = when {
        x > buttonWidth -> buttonWidth + (x - buttonWidth) * 0.25f
        x < -buttonWidth -> -buttonWidth + (x + buttonWidth) * 0.25f
        else -> x
    }

    private fun setOffset(x: Float) {
        content.translationX = x
        updateButtons(x)
    }

    private fun updateButtons(x: Float) {
        leftButton.visibility = if (x > 0) View.VISIBLE else View.INVISIBLE
        rightButton.visibility = if (x < 0) View.VISIBLE else View.INVISIBLE
        coverBackground?.let { cover ->
            val want = if (x != 0f) cover else restBackground
            if (content.background !== want) content.background = want
        }
        val p = (abs(x) / buttonWidth).coerceIn(0f, 1f)
        for (b in listOf(leftButton, rightButton)) {
            val pill = b.getChildAt(0) as? LinearLayout ?: continue
            for (i in 0 until pill.childCount) pill.getChildAt(i).alpha = p
        }
    }

    private fun animateTo(target: Float) {
        content.animate().cancel()
        content.animate().translationX(target)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator(1.6f))
            .setUpdateListener { updateButtons(content.translationX) }
            .withEndAction { updateButtons(target) }
            .start()
        openRow = if (target != 0f) java.lang.ref.WeakReference(this)
                  else openRow?.takeIf { it.get() !== this }
    }

    fun close() { if (isOpen && !removed) animateTo(0f) }

    fun removeAnimated(then: () -> Unit) {
        if (removed) return
        removed = true
        if (openRow?.get() === this) openRow = null
        val dir = if (content.translationX > 0) 1f else -1f
        content.animate().cancel()
        content.animate().translationX(dir * width).setDuration(180)
            .setUpdateListener { updateButtons(content.translationX) }
            .withEndAction {
                val start = height
                ValueAnimator.ofInt(start, 0).apply {
                    duration = 200
                    interpolator = DecelerateInterpolator()
                    addUpdateListener {
                        val h = it.animatedValue as Int
                        layoutParams = layoutParams.also { lp -> lp.height = h }
                        alpha = h.toFloat() / start.coerceAtLeast(1)
                    }
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) { then() }
                    })
                    start()
                }
            }.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (openRow?.get() === this) openRow = null
    }
}
