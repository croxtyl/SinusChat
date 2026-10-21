package pl.sinusoide.sinuschat.ui

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.materialswitch.MaterialSwitch
import pl.sinusoide.sinuschat.R
import pl.sinusoide.sinuschat.util.PopupHelper
import pl.sinusoide.sinuschat.util.Translations

object TranslateBlockRow {

    fun create(activity: Activity, conversationId: String, group: Boolean, other: String, name: () -> String,
               cText: Int, cText2: Int, cAccent: Int): View? {
        if (!Translations.supported() || conversationId.isEmpty()) return null
        val dp = activity.resources.displayMetrics.density
        val sw = MaterialSwitch(activity).apply {
            isChecked = Translations.isBlocked(activity, conversationId)
            thumbTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Color.WHITE, 0xFF8AA0B8.toInt()))
            trackTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(cAccent, 0xFF2A3A4A.toInt()))
            setOnCheckedChangeListener { _, on ->
                Translations.setBlocked(activity, conversationId, on, name(), other, group)
                PopupHelper.show(activity, activity.getString(
                    if (on) R.string.translate_blocked_toast else R.string.translate_unblocked_toast))
            }
        }
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt())
            isClickable = true
            setOnClickListener { sw.toggle() }
            addView(ImageView(activity).apply {
                setImageResource(R.drawable.ic_translate); setColorFilter(cAccent)
            }, LinearLayout.LayoutParams((24 * dp).toInt(), (24 * dp).toInt()).apply { marginEnd = (18 * dp).toInt() })
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(activity).apply {
                    text = activity.getString(R.string.translate_block_chat); textSize = 16f; setTextColor(cText)
                })
                addView(TextView(activity).apply {
                    text = activity.getString(R.string.translate_block_chat_desc); textSize = 13f; setTextColor(cText2)
                })
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = (8 * dp).toInt() })
            addView(sw)
        }
    }
}
