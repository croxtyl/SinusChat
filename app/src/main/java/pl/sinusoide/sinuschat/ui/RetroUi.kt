package pl.sinusoide.sinuschat.ui

import android.app.Activity
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.WindowCompat
import pl.sinusoide.sinuschat.util.ThemeManager

object RetroUi {

    const val TITLE_TOP = 0xFF3D7BE0.toInt()
    const val TITLE_MID = 0xFF245EDB.toInt()
    const val TITLE_BOTTOM = 0xFF1E4FC2.toInt()
    const val TITLE_LINE = 0xFF0A246A.toInt()
    const val CHROME = 0xFFC7D7EE.toInt()
    const val PANEL = 0xFFE6EEF9.toInt()
    const val SECTION = 0xFFB3C9EA.toInt()
    const val FRAME = 0xFF7F9DB9.toInt()
    const val ROW_MINE = 0xFFF3F7FD.toInt()
    const val ROW_OTHER = 0xFFDDE8F7.toInt()
    const val NAVY = 0xFF10203A.toInt()
    const val TIME_MINE = 0xFF4F6E9C.toInt()
    const val SELECTED = 0xFF0A246A.toInt()

    fun active(ctx: Context): Boolean = ThemeManager.isRetro(ctx)

    private fun dp(ctx: Context, v: Float) = v * ctx.resources.displayMetrics.density

    fun titleBar(ctx: Context): Drawable {
        val fill = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(TITLE_TOP, TITLE_MID, TITLE_MID, TITLE_BOTTOM))
        val line = GradientDrawable().apply { setColor(TITLE_LINE) }
        return LayerDrawable(arrayOf(line, fill)).apply {
            setLayerInset(1, 0, 0, 0, dp(ctx, 1f).toInt())
        }
    }

    fun statusArea(): Drawable = GradientDrawable().apply { setColor(TITLE_TOP) }

    fun bottomBar(ctx: Context): Drawable {
        val fill = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFFD6E2F3.toInt(), CHROME))
        val line = GradientDrawable().apply { setColor(FRAME) }
        return LayerDrawable(arrayOf(line, fill)).apply {
            setLayerInset(1, 0, dp(ctx, 1f).toInt(), 0, 0)
        }
    }

    fun sunken(ctx: Context): Drawable = GradientDrawable().apply {
        setColor(0xFFFFFFFF.toInt())
        setStroke(dp(ctx, 1f).toInt(), FRAME)
    }

    fun button(ctx: Context): Drawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(0xFFFFFFFF.toInt(), 0xFFECF1F9.toInt(), 0xFFD5E0F0.toInt())
    ).apply {
        cornerRadius = dp(ctx, 3f)
        setStroke(dp(ctx, 1f).toInt(), FRAME)
    }

    fun pressed(ctx: Context): Drawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(0xFFA9C2E6.toInt(), 0xFFC4D5EE.toInt(), 0xFFD9E4F4.toInt())
    ).apply {
        cornerRadius = dp(ctx, 3f)
        setStroke(dp(ctx, 1f).toInt(), TITLE_LINE)
    }

    fun styleTitleBar(bar: View) {
        bar.background = titleBar(bar.context)
        whiten(bar, true)
    }

    private fun isPicture(v: View): Boolean {
        if (v.id == View.NO_ID) return false
        val name = try { v.resources.getResourceEntryName(v.id) } catch (_: Exception) { return false }
        return name.contains("avatar") || name.contains("photo") || name.contains("image")
    }

    private fun whiten(v: View, root: Boolean) {
        val bg = v.background
        if (!root && bg != null && bg !is android.graphics.drawable.RippleDrawable) return
        when (v) {
            is EditText -> return
            is TextView -> v.setTextColor(0xFFFFFFFF.toInt())
            is ImageView -> if (v.drawable !is BitmapDrawable && !isPicture(v)) v.setColorFilter(0xFFFFFFFF.toInt())
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) whiten(v.getChildAt(i), false)
    }

    fun lightStatusIcons(activity: Activity) {
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            .isAppearanceLightStatusBars = false
    }
}
