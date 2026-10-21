package pl.sinusoide.sinuschat.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

object SettingsHighlight {

    const val EXTRA = "pl.sinusoide.sinuschat.HIGHLIGHT_TITLE"

    fun intent(context: Context, target: Class<*>, titleRes: Int): Intent =
        Intent(context, target).putExtra(EXTRA, titleRes)

    fun apply(activity: Activity) {
        val res = activity.intent?.getIntExtra(EXTRA, 0) ?: 0
        if (res == 0) return
        activity.intent.removeExtra(EXTRA)
        val title = try { activity.getString(res) } catch (_: Exception) { return }
        val root = activity.window?.decorView ?: return
        val started = SystemClock.uptimeMillis()

        val finder = object : Runnable {
            override fun run() {
                if (activity.isFinishing || activity.isDestroyed) return
                val tv = findText(root, norm(title))
                if (tv != null && tv.isLaidOut && tv.height > 0) {
                    root.postDelayed({ reveal(tv) }, 280)
                } else if (SystemClock.uptimeMillis() - started < 4000) {
                    root.postDelayed(this, 120)
                }
            }
        }
        root.postDelayed(finder, 150)
    }

    private fun norm(s: String) = s.trim().lowercase()

    private fun findText(root: View, target: String): TextView? {
        val all = ArrayList<TextView>()
        collect(root, target, all)
        return all.firstOrNull { hasClickableAncestor(it) } ?: all.firstOrNull()
    }

    private fun collect(v: View, target: String, out: MutableList<TextView>) {
        if (v.visibility != View.VISIBLE) return
        if (v is TextView && v !is EditText && norm(v.text?.toString() ?: "") == target) out.add(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) collect(v.getChildAt(i), target, out)
    }

    private fun hasClickableAncestor(v: View): Boolean {
        var p = v.parent
        while (p is View) {
            if (p is ScrollView || p is NestedScrollView) return false
            if (p.isClickable) return true
            p = p.parent
        }
        return false
    }

    private fun reveal(tv: TextView) {
        val scroll = scrollAncestor(tv)
        val content = (scroll as? ViewGroup)?.getChildAt(0) as? ViewGroup
        val row = pickRow(tv, scroll, content)
        if (scroll != null && content != null) {
            val y = offsetWithin(row, content)
            val targetY = (y - (scroll.height - row.height) / 2).coerceAtLeast(0)
            when (scroll) {
                is NestedScrollView -> scroll.smoothScrollTo(0, targetY)
                is ScrollView -> scroll.smoothScrollTo(0, targetY)
            }
        }
        row.postDelayed({ flash(row) }, if (scroll != null) 380 else 0)
    }

    private fun scrollAncestor(v: View): View? {
        var p = v.parent
        while (p is View) {
            if (p is ScrollView || p is NestedScrollView) return p
            p = p.parent
        }
        return null
    }

    private fun pickRow(tv: View, scroll: View?, content: ViewGroup?): View {
        if (content != null && tv.parent === content) {
            val idx = content.indexOfChild(tv)
            if (idx + 1 < content.childCount) return content.getChildAt(idx + 1)
            return tv
        }
        var v: View = tv
        while (true) {
            val p = v.parent as? View ?: return v
            if (v !== tv && v.isClickable) return v
            if (p === content || p === scroll) return v
            if (content != null && p.parent === content) return v
            v = p
        }
    }

    private fun offsetWithin(v: View, ancestor: View): Int {
        var y = 0
        var cur: View? = v
        while (cur != null && cur !== ancestor) {
            y += cur.top
            cur = cur.parent as? View
        }
        return y
    }

    private fun flash(row: View) {
        val d = row.resources.displayMetrics.density
        val accent = pl.sinusoide.sinuschat.ui.ThemeUi.accent(row.context)
        val inCard = (row.parent as? View)?.clipToOutline == true
        val overlay = GradientDrawable().apply {
            cornerRadius = if (inCard) 0f else 18 * d
            setColor(accent)
            alpha = 0
        }
        val previous = row.foreground
        row.foreground = overlay
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1900
            addUpdateListener {
                val t = it.animatedFraction
                val pulse = abs(sin(t * PI * 2)).toFloat() * (1f - 0.3f * t)
                overlay.alpha = (pulse * 72).toInt()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { row.foreground = previous }
            })
            start()
        }
    }
}
