package pl.sinusoide.sinuschat.ui

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator

class ViewerChrome(
    var views: List<View>,
    private val autoHideMs: Long = 0L,
    private val keepVisible: () -> Boolean = { false }
) {
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { if (!keepVisible()) hide() }

    var shown = true
        private set

    fun show() {
        shown = true
        for (v in views) {
            v.animate().cancel()
            v.visibility = View.VISIBLE
            v.animate().alpha(1f).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
        }
        bump()
    }

    fun hide() {
        shown = false
        handler.removeCallbacks(hideRunnable)
        for (v in views) {
            v.animate().cancel()
            v.animate().alpha(0f).setDuration(450).setInterpolator(DecelerateInterpolator())
                .withEndAction { if (!shown) v.visibility = View.INVISIBLE }
                .start()
        }
    }

    fun toggle() { if (shown) hide() else show() }

    fun bump() {
        handler.removeCallbacks(hideRunnable)
        if (shown && autoHideMs > 0) handler.postDelayed(hideRunnable, autoHideMs)
    }

    fun release() {
        handler.removeCallbacks(hideRunnable)
        for (v in views) v.animate().cancel()
    }
}
