package pl.sinusoide.sinuschat.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView

object PhotoViewer {

    private class ViewerDialog(activity: Activity) : Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen) {
        var onBack: (() -> Unit)? = null
        @Deprecated("Deprecated in Java")
        override fun onBackPressed() {
            val back = onBack
            if (back != null) back() else super.onBackPressed()
        }
    }

    fun show(activity: Activity, bind: (ImageView) -> Unit) {
        val dialog = ViewerDialog(activity)
        pl.sinusoide.sinuschat.util.PopupHelper.trackOverlay(dialog)
        dialog.window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            setWindowAnimations(0)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        val root = FrameLayout(activity).apply { setBackgroundColor(Color.BLACK); alpha = 0f }
        val img = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            scaleX = 0.9f; scaleY = 0.9f
        }
        root.addView(img, FrameLayout.LayoutParams(-1, -1))

        var closing = false
        val close = {
            if (!closing) {
                closing = true
                root.animate().cancel(); img.animate().cancel()
                img.animate().scaleX(0.92f).scaleY(0.92f).setDuration(190)
                    .setInterpolator(AccelerateInterpolator(1.4f)).start()
                root.animate().alpha(0f).setDuration(190)
                    .setInterpolator(AccelerateInterpolator(1.4f))
                    .withEndAction { try { dialog.dismiss() } catch (_: Exception) {} }.start()
            }
        }
        dialog.onBack = close
        root.setOnClickListener { close() }
        img.setOnClickListener { close() }
        bind(img)
        dialog.setContentView(root)
        dialog.show()
        root.animate().alpha(1f).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
        img.animate().scaleX(1f).scaleY(1f).setDuration(280).setInterpolator(DecelerateInterpolator(1.8f)).start()
    }
}
