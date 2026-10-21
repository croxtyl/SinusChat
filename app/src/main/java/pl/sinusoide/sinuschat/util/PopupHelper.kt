package pl.sinusoide.sinuschat.util

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import pl.sinusoide.sinuschat.R
import java.lang.ref.WeakReference

object PopupHelper {

    enum class State { INFO, SUCCESS, ERROR }

    private class Active(
        val popup: PopupWindow, val view: View, val anchor: View,
        val message: String, val state: State, val until: Long
    )
    private val active = ArrayList<Active>()
    private val handler = Handler(Looper.getMainLooper())
    private val overlays = ArrayList<WeakReference<View>>()

    private const val BASE_Y = 180
    private const val SPACING_DP = 64

    fun trackOverlay(dialog: Dialog) {
        val decor = dialog.window?.decorView ?: return
        overlays.removeAll { it.get() == null || it.get() === decor }
        overlays.add(WeakReference(decor))
        decor.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                overlays.removeAll { it.get() == null || it.get() === v }
                moveOff(v)
            }
        })
    }

    private fun activityOf(context: Context?): Activity? {
        var c = context
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }

    private fun anchorFor(activity: Activity): View {
        for (i in overlays.indices.reversed()) {
            val v = overlays[i].get() ?: continue
            if (v.isAttachedToWindow && v.windowToken != null && v.isShown && activityOf(v.context) === activity) return v
        }
        return activity.window.decorView
    }

    private fun moveOff(anchor: View) {
        val moving = active.filter { it.anchor === anchor }
        if (moving.isEmpty()) return
        active.removeAll(moving.toSet())
        val activity = activityOf(anchor.context)
        val now = SystemClock.uptimeMillis()
        for (m in moving) {
            try { m.popup.dismiss() } catch (_: Exception) {}
            val left = (m.until - now).toInt()
            if (activity != null && left > 400) handler.post { show(activity, m.message, m.state, left) }
        }
    }

    fun show(activity: Activity, message: String, state: State = State.INFO, duration: Int = 3000) {
        if (activity.isFinishing || activity.isDestroyed) return
        try {
            val inflater = LayoutInflater.from(activity)
            val layout = inflater.inflate(R.layout.layout_custom_toast, null)

            val text: TextView = layout.findViewById(R.id.toast_text)
            text.text = message

            val icon: ImageView = layout.findViewById(R.id.toast_icon)
            when (state) {
                State.SUCCESS -> {
                    icon.setImageResource(R.drawable.ic_check)
                    icon.setColorFilter(activity.getColor(R.color.sinuschat_success))
                    icon.visibility = View.VISIBLE
                }
                State.ERROR -> {
                    icon.setImageResource(R.drawable.ic_danger)
                    icon.setColorFilter(activity.getColor(R.color.sinuschat_error))
                    icon.visibility = View.VISIBLE
                }
                else -> icon.visibility = View.GONE
            }

            val popupWindow = PopupWindow(
                layout,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                isClippingEnabled = false
                elevation = 12f
            }

            val anchor = anchorFor(activity)
            val entry = Active(popupWindow, layout, anchor, message, state, SystemClock.uptimeMillis() + duration)
            active.add(entry)

            layout.alpha = 0f
            layout.translationY = 40f
            popupWindow.showAtLocation(anchor, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, BASE_Y)
            layout.animate().alpha(1f).translationY(0f)
                .setDuration(250)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()

            restack(activity)

            handler.postDelayed({ dismiss(activity, entry) }, duration.toLong())

        } catch (e: Exception) {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun dismiss(activity: Activity, entry: Active) {
        if (!active.contains(entry)) return
        val idxFromBottom = active.size - 1 - active.indexOf(entry)
        active.remove(entry)
        val density = activity.resources.displayMetrics.density
        val currentY = -idxFromBottom * SPACING_DP * density
        try {
            entry.view.animate().alpha(0f).translationY(currentY - 40f).setDuration(220)
                .withEndAction {
                    try { entry.popup.dismiss() } catch (_: Exception) {}
                    if (!activity.isFinishing && !activity.isDestroyed) restack(activity)
                }.start()
        } catch (_: Exception) {
            try { entry.popup.dismiss() } catch (_: Exception) {}
        }
    }

    private fun restack(activity: Activity) {
        val density = activity.resources.displayMetrics.density
        val spacing = SPACING_DP * density
        val n = active.size
        active.forEachIndexed { index, a ->
            val fromBottom = (n - 1 - index)
            val targetY = -fromBottom * spacing
            a.view.animate().translationY(targetY)
                .setDuration(250)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }
    }

    fun showSuccess(activity: Activity, message: String) = show(activity, message, State.SUCCESS)
    fun showError(activity: Activity, message: String) = show(activity, message, State.ERROR)
}
