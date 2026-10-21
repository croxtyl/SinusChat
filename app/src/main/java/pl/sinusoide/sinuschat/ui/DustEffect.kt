package pl.sinusoide.sinuschat.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Picture
import android.os.Build
import android.view.View

object DustEffect {

    fun disintegrate(dustOverlay: DisintegrateView, view: View, reverse: Boolean = false, onFinish: () -> Unit = {}) {
        val bitmap = capture(view) ?: run { onFinish(); return }

        val location = IntArray(2)
        view.getLocationOnScreen(location)

        disintegrateManual(dustOverlay, bitmap, location[0].toFloat(), location[1].toFloat(), reverse) {
            view.visibility = View.VISIBLE
            bitmap.recycle()
            onFinish()
        }
        view.visibility = View.INVISIBLE
    }

    fun capture(view: View): Bitmap? {
        val w = view.width; val h = view.height
        if (w <= 0 || h <= 0) return null
        try {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bmp))
                return bmp
            } catch (e: Exception) {
                bmp.recycle()
            }
        } catch (_: Throwable) { return null }
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                val picture = Picture()
                view.draw(picture.beginRecording(w, h))
                picture.endRecording()
                return Bitmap.createBitmap(picture).copy(Bitmap.Config.ARGB_8888, false)
            } catch (_: Exception) {}
        }
        return null
    }

    fun disintegrateManual(
        dustOverlay: DisintegrateView,
        bitmap: Bitmap,
        x: Float,
        y: Float,
        reverse: Boolean = false,
        onFinish: () -> Unit = {}
    ) {
        dustOverlay.addBurst(bitmap, x, y, reverse, if (reverse) 600L else 800L, onFinish)
    }
}
