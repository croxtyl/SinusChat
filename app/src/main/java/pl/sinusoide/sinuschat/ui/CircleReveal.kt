package pl.sinusoide.sinuschat.ui

import android.R
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.PathInterpolator
import pl.sinusoide.sinuschat.util.ThemeManager
import java.lang.ref.WeakReference
import kotlin.math.hypot

object CircleReveal {

    private var snapshot: Bitmap? = null
    private var centerX = 0f
    private var centerY = 0f
    private var startRadius = 0f
    private var takenAt = 0L

    @Volatile private var revealing: java.lang.ref.WeakReference<View>? = null

    fun covers(decor: View): Boolean = revealing?.get() === decor

    private const val DURATION_MS = 1250L

    private const val STALE_MS = 5_000L

    fun capture(activity: Activity, from: View, onReady: (Boolean) -> Unit) {
        val decor = activity.window.decorView
        if (decor.width == 0 || decor.height == 0 || from.width == 0) {
            onReady(false)
            return
        }
        val at = IntArray(2)
        from.getLocationInWindow(at)
        val cx = at[0] + from.width / 2f
        val cy = at[1] + from.height / 2f
        val r0 = minOf(from.width, from.height) * 0.3f

        val bitmap = try {
            Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        } catch (_: Throwable) {
            onReady(false)
            return
        }
        val done = { ok: Boolean ->
            if (ok) {
                snapshot?.recycle()
                snapshot = bitmap
                centerX = cx
                centerY = cy
                startRadius = r0
                takenAt = SystemClock.uptimeMillis()
            } else {
                bitmap.recycle()
            }
            onReady(ok)
        }
        try {
            PixelCopy.request(activity.window, bitmap, { result ->
                done(result == PixelCopy.SUCCESS)
            }, Handler(Looper.getMainLooper()))
        } catch (_: Throwable) {
            done(false)
        }
    }

    fun playOn(activity: Activity) {
        val shot = snapshot ?: return
        snapshot = null
        if (SystemClock.uptimeMillis() - takenAt > STALE_MS) return

        val decor = activity.window.decorView
        val content = activity.findViewById<ViewGroup>(R.id.content) ?: return
        val res = activity.resources

        pl.sinusoide.sinuschat.util.ThemeManager.applyBackdrop(activity)

        val windowBg = decor.background
        val ownBg = when {
            windowBg is FlowBackdropDrawable -> FlowBackdropDrawable(activity)
            else -> windowBg?.constantState?.newDrawable(res)?.mutate()
                ?: ColorDrawable(ThemeUi.backgroundSolid(activity))
        }
        val contentBg = content.background
        content.background = ownBg
        decor.background = android.graphics.drawable.BitmapDrawable(res, shot)
        revealing = java.lang.ref.WeakReference(decor)
        content.visibility = View.INVISIBLE

        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                content.viewTreeObserver.removeOnPreDrawListener(this)
                val at = IntArray(2)
                content.getLocationInWindow(at)
                val x = centerX - at[0]
                val y = centerY - at[1]
                val w = content.width.toFloat()
                val h = content.height.toFloat()
                val far = maxOf(hypot(x, y), hypot(w - x, y), hypot(x, h - y), hypot(w - x, h - y))
                content.visibility = View.VISIBLE
                ViewAnimationUtils.createCircularReveal(
                    content, x.toInt(), y.toInt(), startRadius, far
                ).apply {
                    duration = DURATION_MS
                    interpolator = PathInterpolator(0.45f, 0f, 0.2f, 1f)
                    addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            revealing = null
                            decor.background = windowBg
                            content.background = contentBg
                            pl.sinusoide.sinuschat.util.ThemeManager.applyBackdrop(activity)
                        }
                    })
                    start()
                }
                return true
            }
        })
    }
}
