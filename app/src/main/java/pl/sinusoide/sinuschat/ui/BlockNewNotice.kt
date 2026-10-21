package pl.sinusoide.sinuschat.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import pl.sinusoide.sinuschat.PrivacySettingsActivity
import pl.sinusoide.sinuschat.R
import pl.sinusoide.sinuschat.util.PrivacySettings

object BlockNewNotice {

    fun show(activity: Activity): Boolean {
        if (!PrivacySettings.isBlockNewEnabled(activity)) return false
        val dp = activity.resources.displayMetrics.density
        val bg = ThemeUi.attrOrNull(activity, R.attr.sinuschat_background) ?: 0xFF0E1621.toInt()
        val fg = ThemeUi.textPrimary(activity)
        val dim = ThemeUi.attrOrNull(activity, R.attr.sinuschat_text_secondary) ?: 0xFF7F91A4.toInt()
        val accent = ThemeUi.accent(activity)
        val red = 0xFFE5484D.toInt()

        val root = FrameLayout(activity).apply { setBackgroundColor(bg) }
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((32 * dp).toInt(), 0, (32 * dp).toInt(), 0)
        }
        body.addView(ImageView(activity).apply {
            setImageResource(R.drawable.ic_block); setColorFilter(red)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor((red and 0x00FFFFFF) or 0x26000000)
            }
            val p = (22 * dp).toInt(); setPadding(p, p, p, p)
        }, LinearLayout.LayoutParams((88 * dp).toInt(), (88 * dp).toInt()))
        body.addView(TextView(activity).apply {
            text = activity.getString(R.string.blocknew_notice_title)
            textSize = 21f; setTextColor(fg); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, (20 * dp).toInt(), 0, 0)
        })
        body.addView(TextView(activity).apply {
            text = activity.getString(R.string.blocknew_notice_desc)
            textSize = 15f; setTextColor(dim)
            gravity = Gravity.CENTER
            setLineSpacing(2 * dp, 1f)
            setPadding(0, (10 * dp).toInt(), 0, 0)
        })
        body.addView(TextView(activity).apply {
            text = activity.getString(R.string.blocknew_notice_open)
            textSize = 16f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val pv = (14 * dp).toInt(); setPadding((20 * dp).toInt(), pv, (20 * dp).toInt(), pv)
            background = GradientDrawable().apply { cornerRadius = 26 * dp; setColor(accent) }
            isClickable = true
            setOnClickListener {
                activity.startActivity(Intent(activity, PrivacySettingsActivity::class.java))
                activity.finish()
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (28 * dp).toInt() })
        body.addView(TextView(activity).apply {
            text = activity.getString(R.string.back)
            textSize = 16f; setTextColor(fg)
            gravity = Gravity.CENTER
            val pv = (14 * dp).toInt(); setPadding((20 * dp).toInt(), pv, (20 * dp).toInt(), pv)
            background = ContextCompat.getDrawable(activity, R.drawable.bg_menu_ripple)
            isClickable = true
            setOnClickListener { activity.finish() }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (6 * dp).toInt() })
        root.addView(body, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        activity.setContentView(root)
        return true
    }
}
