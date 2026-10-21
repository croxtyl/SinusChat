package pl.sinusoide.sinuschat.util

import android.app.Activity
import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import pl.sinusoide.sinuschat.R
import pl.sinusoide.sinuschat.ui.FlowBackdropDrawable

object ThemeManager {

    const val THEME_DEFAULT = "default"
    const val THEME_LIGHT   = "light"
    const val THEME_DYNAMIC = "dynamic"
    const val THEME_FLOW    = "flow"

    private const val PREFS = "sinuschat_theme_prefs"
    private const val KEY_THEME = "theme_mode"

    fun getCurrent(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THEME, THEME_DEFAULT) ?: THEME_DEFAULT

    fun isFlow(ctx: Context): Boolean = getCurrent(ctx) == THEME_FLOW

    fun setCurrent(ctx: Context, theme: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME, theme).apply()
        applyNightMode(ctx)
    }

    fun applyNightMode(ctx: Context) {
        val mode = when (getCurrent(ctx)) {
            THEME_LIGHT   -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_DYNAMIC -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else          -> AppCompatDelegate.MODE_NIGHT_YES
        }
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }

    fun applyTheme(activity: Activity) {
        val themeKey = getCurrent(activity)
        activity.setTheme(getThemeRes(activity))

        val bg = getBackgroundColor(activity)
        activity.window?.setBackgroundDrawable(ColorDrawable(bg))

        if (themeKey == THEME_DYNAMIC) {
            DynamicColors.applyToActivityIfAvailable(activity)
        }

        applyNightMode(activity)
    }

    fun getBackgroundColor(ctx: Context): Int = when (getCurrent(ctx)) {
        THEME_LIGHT   -> 0xFFD6DDE3.toInt()
        THEME_FLOW    -> pl.sinusoide.sinuschat.ui.FlowBackdropDrawable.INK
        else          -> 0xFF0E1621.toInt()
    }

    fun applyBackdrop(activity: Activity) {
        if (!isFlow(activity)) return
        val window = activity.window ?: return
        val decor = window.peekDecorView() ?: return
        if (decor.background is FlowBackdropDrawable) return
        if (pl.sinusoide.sinuschat.ui.CircleReveal.covers(decor)) return
        window.setBackgroundDrawable(FlowBackdropDrawable(activity))
    }

    private fun getThemeRes(ctx: Context): Int = when (getCurrent(ctx)) {
        THEME_LIGHT   -> R.style.Theme_SinusChat_Light
        THEME_DYNAMIC -> R.style.Theme_SinusChat_Dynamic
        THEME_FLOW    -> R.style.Theme_SinusChat_Flow
        else          -> R.style.Theme_SinusChat
    }
}
