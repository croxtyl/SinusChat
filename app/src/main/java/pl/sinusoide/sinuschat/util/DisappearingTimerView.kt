package pl.sinusoide.sinuschat.util

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import pl.sinusoide.sinuschat.R

class DisappearingTimerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private var expiresAtMs = 0L
    private var totalTtlMs = 0L

    private val ticker = object : Runnable {
        override fun run() {
            updateIcon()
            if (isAttachedToWindow && remainingFraction() > 0f) {
                postDelayed(this, 1000L)
            }
        }
    }

    fun set(expiresAtMs: Long, totalTtlMs: Long) {
        this.expiresAtMs = expiresAtMs
        this.totalTtlMs = if (totalTtlMs > 0) totalTtlMs else 1L
        removeCallbacks(ticker)
        updateIcon()
        post(ticker)
    }

    private fun remainingFraction(): Float {
        if (expiresAtMs <= 0L) return 0f
        val left = expiresAtMs - System.currentTimeMillis()
        if (left <= 0L) return 0f
        return (left.toFloat() / totalTtlMs.toFloat()).coerceIn(0f, 1f)
    }

    private fun updateIcon() {
        val frac = remainingFraction()
        val iconRes = when {
            frac > 0.9f -> R.drawable.ic_dm90
            frac > 0.8f -> R.drawable.ic_dm80
            frac > 0.6f -> R.drawable.ic_dm60
            frac > 0.5f -> R.drawable.ic_dm50
            frac > 0.4f -> R.drawable.ic_dm40
            frac > 0.2f -> R.drawable.ic_dm20
            frac > 0.0f -> R.drawable.ic_dm10
            else -> R.drawable.ic_dm10
        }
        setImageResource(iconRes)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(ticker)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(ticker)
    }
}
