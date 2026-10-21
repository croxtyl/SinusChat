package pl.sinusoide.sinuschat.ui

import android.view.View

object PillWarmUp {

    fun prime(pill: View, isOpen: () -> Boolean) {
        pill.post {
            if (isOpen() || pill.visibility != View.INVISIBLE || !pill.isAttachedToWindow) return@post
            pill.alpha = 0.01f
            pill.visibility = View.VISIBLE
            pill.postOnAnimation {
                pill.postOnAnimation {
                    if (!isOpen()) {
                        pill.visibility = View.INVISIBLE
                        pill.alpha = 1f
                    }
                }
            }
        }
    }
}
