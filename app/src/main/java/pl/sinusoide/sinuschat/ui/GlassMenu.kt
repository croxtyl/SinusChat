package pl.sinusoide.sinuschat.ui

import android.R
import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import pl.sinusoide.sinuschat.util.ThemeManager

object GlassMenu {

    class Item(val icon: Int, val label: String, val danger: Boolean = false, val run: () -> Unit)

    class Handle internal constructor(
        private val layer: FrameLayout,
        private val card: View,
        private val lens: FlowLens.Handle?,
        private val back: OnBackPressedCallback,
    ) {
        var isShowing = true
            private set

        fun dismiss(then: (() -> Unit)? = null) {
            if (!isShowing) { then?.invoke(); return }
            isShowing = false
            back.remove()
            layer.isClickable = false
            val finish = {
                (layer.parent as? ViewGroup)?.removeView(layer)
                lens?.remove(card)
            }
            if (!card.isAttachedToWindow) {
                finish()
            } else {
                card.animate().cancel()
                card.animate().alpha(0f).scaleX(0.85f).scaleY(0.6f).setStartDelay(0).setDuration(170)
                    .setInterpolator(AccelerateInterpolator())
                    .withEndAction { finish() }
                    .start()
            }
            then?.invoke()
        }
    }

    fun show(
        activity: Activity,
        anchor: View,
        items: List<Item>,
        atRow: Boolean = false,
        lens: FlowLens.Handle? = null,
    ): Handle? {
        val content = activity.findViewById<FrameLayout>(R.id.content) ?: return null
        if (items.isEmpty() || !anchor.isAttachedToWindow) return null
        val dp = activity.resources.displayMetrics.density
        val flow = ThemeManager.isFlow(activity)
        val lensGlass = flow && lens != null
        val textColor = if (flow) Color.WHITE else ThemeUi.textPrimary(activity)
        val iconColor = if (flow) Color.WHITE else ThemeUi.textSecondary(activity)
        val danger = 0xFFFF5A5F.toInt()
        val highlight = when {
            flow -> 0x2EFFFFFF
            ThemeUi.isDark(activity) -> 0x1FFFFFFF
            else -> 0x14000000
        }

        val layer = FrameLayout(activity).apply { isClickable = true }
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val p = (6 * dp).toInt(); setPadding(p, p, p, p)
            isClickable = true
            background = when {
                lensGlass -> GlassDrawable(dp, null, false, 22 * dp, null)
                flow -> GradientDrawable().apply {
                    cornerRadius = 22 * dp; setColor(0xE6141C2B.toInt()); setStroke((1 * dp).toInt(), 0x3DFFFFFF)
                }
                else -> GradientDrawable().apply {
                    cornerRadius = 22 * dp; setColor(ThemeUi.sheet(activity))
                    setStroke((1 * dp).toInt(), ThemeUi.border(activity))
                }
            }
            if (!flow) elevation = 14 * dp
        }

        lateinit var handle: Handle
        val rows = mutableListOf<View>()
        items.forEach { item ->
            val fg = if (item.danger) danger else textColor
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumWidth = (212 * dp).toInt()
                setPadding((14 * dp).toInt(), (13 * dp).toInt(), (22 * dp).toInt(), (13 * dp).toInt())
                background = RippleDrawable(ColorStateList.valueOf(highlight), null,
                    GradientDrawable().apply { cornerRadius = 16 * dp; setColor(Color.WHITE) })
                isClickable = true
                setOnClickListener { handle.dismiss(item.run) }
                addView(ImageView(activity).apply {
                    setImageResource(item.icon); setColorFilter(if (item.danger) danger else iconColor)
                }, LinearLayout.LayoutParams((20 * dp).toInt(), (20 * dp).toInt()).apply { marginEnd = (14 * dp).toInt() })
                addView(TextView(activity).apply {
                    text = item.label; textSize = 15.5f; setTextColor(fg)
                })
            }
            card.addView(row, LinearLayout.LayoutParams(-1, -2))
            rows += row
        }

        val a = IntArray(2); val c = IntArray(2)
        anchor.getLocationInWindow(a); content.getLocationInWindow(c)
        val anchorTop = a[1] - c[1]
        val anchorBottom = anchorTop + anchor.height
        card.measure(
            View.MeasureSpec.makeMeasureSpec(content.width, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(content.height, View.MeasureSpec.AT_MOST))
        val h = card.measuredHeight
        val margin = (12 * dp).toInt()
        val bottomLimit = content.height - (72 * dp).toInt()
        var above = false
        val top = if (atRow) {
            val below = anchorBottom - (10 * dp).toInt()
            if (below + h > bottomLimit) { above = true; (anchorTop - h + (10 * dp).toInt()).coerceAtLeast(margin) } else below
        } else anchorBottom + (8 * dp).toInt()
        val marginEnd = if (atRow) (16 * dp).toInt()
            else (content.width - (a[0] - c[0] + anchor.width)).coerceAtLeast(margin)
        layer.addView(card, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
            topMargin = top
            this.marginEnd = marginEnd
        })
        content.addView(layer, FrameLayout.LayoutParams(-1, -1))
        if (lensGlass) lens.add(FlowLens.Lens(card, 22 * dp, blurDp = 11f))

        val back = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { handle.dismiss() }
        }
        handle = Handle(layer, card, if (lensGlass) lens else null, back)
        (activity as? ComponentActivity)?.onBackPressedDispatcher?.addCallback(back)
        layer.setOnClickListener { handle.dismiss() }

        card.alpha = 0f
        card.scaleX = if (flow) 0.3f else 0.7f
        card.scaleY = if (flow) 0.15f else 0.7f
        rows.forEach { it.alpha = 0f; it.translationY = (if (above) 6 else -6) * dp }
        card.post {
            val anchorCenterX = a[0] - c[0] + anchor.width / 2f
            card.pivotX = if (atRow) card.width - 28 * dp
                else (anchorCenterX - card.left).coerceIn(0f, card.width.toFloat())
            card.pivotY = if (above) card.height.toFloat() else 0f
            card.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(if (flow) 420 else 260)
                .setInterpolator(OvershootInterpolator(if (flow) 1.25f else 1.1f))
                .start()
            rows.forEachIndexed { i, r ->
                r.animate().alpha(1f).translationY(0f).setStartDelay(70L + i * 45L).setDuration(220)
                    .setInterpolator(DecelerateInterpolator()).start()
            }
        }
        return handle
    }
}
