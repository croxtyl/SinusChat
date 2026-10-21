package pl.sinusoide.sinuschat.messenger

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import pl.sinusoide.sinuschat.R
import pl.sinusoide.sinuschat.ui.ThemeColors
import pl.sinusoide.sinuschat.ui.ThemeUi
import pl.sinusoide.sinuschat.util.ContactCache
import pl.sinusoide.sinuschat.util.PopupHelper

object LocalNameSheet {

    fun show(activity: Activity, hash: String, fallbackName: String, onChanged: (String) -> Unit = {}) {
        if (hash.isEmpty() || activity.isFinishing) return
        val tc = ThemeColors(activity)
        val d = activity.resources.displayMetrics.density
        fun px(v: Float) = (v * d).toInt()
        val accent = tc.accent
        val current = ContactCache.getNick(activity, hash).orEmpty()
        val real = ContactCache.realName(activity, hash)?.takeIf { it.isNotBlank() }
            ?: fallbackName.takeIf { current.isEmpty() || it != current }.orEmpty()
        val sheet = BottomSheetDialog(activity, R.style.Theme_SinusChat_BottomSheet_Input)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(px(24f), px(12f), px(24f), px(20f))
        }
        root.addView(View(activity).apply {
            background = GradientDrawable().apply {
                cornerRadius = 3 * d; setColor((tc.text2 and 0x00FFFFFF) or 0x55000000)
            }
        }, LinearLayout.LayoutParams(px(36f), px(4f)).apply { bottomMargin = px(20f) })

        root.addView(ImageView(activity).apply {
            setImageResource(R.drawable.ic_edit)
            setColorFilter(accent)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor((accent and 0x00FFFFFF) or 0x26000000)
            }
            val p = px(16f); setPadding(p, p, p, p)
        }, LinearLayout.LayoutParams(px(64f), px(64f)).apply { bottomMargin = px(16f) })

        root.addView(TextView(activity).apply {
            text = activity.getString(R.string.local_name_title)
            textSize = 20f; setTextColor(tc.text); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })
        root.addView(TextView(activity).apply {
            text = if (real.isNotEmpty()) activity.getString(R.string.local_name_desc, real)
                   else activity.getString(R.string.local_name_desc_plain)
            textSize = 13.5f; setTextColor(tc.text2); gravity = Gravity.CENTER
            setLineSpacing(0f, 1.15f)
            setPadding(0, px(6f), 0, px(20f))
        })

        val input = EditText(activity).apply {
            setText(current)
            setSelection(text.length)
            textSize = 16f
            setTextColor(tc.text)
            setHintTextColor(tc.muted)
            hint = real.ifEmpty { activity.getString(R.string.local_name_label) }
            isSingleLine = true
            filters = arrayOf(InputFilter.LengthFilter(ContactCache.MAX_NICK))
            background = null
            setPadding(0, px(4f), 0, px(4f))
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        val counter = TextView(activity).apply { textSize = 12f; setTextColor(tc.text2) }
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16f), px(10f), px(16f), px(10f))
        }
        fun cardBg(focused: Boolean) = GradientDrawable().apply {
            cornerRadius = 16 * d
            setColor(ThemeUi.card(activity))
            setStroke(px(1.5f), if (focused) accent else 0x00000000)
        }
        card.background = cardBg(false)
        val labelRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        labelRow.addView(TextView(activity).apply {
            text = activity.getString(R.string.local_name_label)
            textSize = 12f; setTextColor(accent)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        labelRow.addView(counter)
        card.addView(labelRow)
        card.addView(input, LinearLayout.LayoutParams(-1, -2))
        input.setOnFocusChangeListener { _, has -> card.background = cardBg(has) }
        root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(20f) })

        val saveBtn = TextView(activity).apply {
            text = activity.getString(R.string.save)
            textSize = 16f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = 26 * d; setColor(accent) }
            setPadding(0, px(14f), 0, px(14f))
            isClickable = true
        }
        root.addView(saveBtn, LinearLayout.LayoutParams(-1, -2))

        fun finish(nick: String?) {
            ContactCache.setNick(activity, hash, nick)
            val shown = ContactCache.getName(activity, hash) ?: real.ifEmpty { fallbackName }
            PopupHelper.show(activity, activity.getString(
                if (nick.isNullOrBlank()) R.string.local_name_removed else R.string.local_name_saved))
            onChanged(shown)
            sheet.dismiss()
        }

        if (current.isNotEmpty()) {
            root.addView(TextView(activity).apply {
                text = activity.getString(R.string.local_name_reset)
                textSize = 15f; setTextColor(accent); gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, px(16f), 0, px(2f))
                isClickable = true
                setOnClickListener { finish(null) }
            }, LinearLayout.LayoutParams(-1, -2))
        }
        root.addView(TextView(activity).apply {
            text = activity.getString(R.string.cancel)
            textSize = 15f; setTextColor(tc.text2); gravity = Gravity.CENTER
            setPadding(0, px(14f), 0, px(4f))
            isClickable = true
            setOnClickListener { sheet.dismiss() }
        }, LinearLayout.LayoutParams(-1, -2))

        fun render() {
            val value = input.text.toString().trim()
            counter.text = "${input.text.length}/${ContactCache.MAX_NICK}"
            val enabled = value.isNotEmpty() && value != current
            saveBtn.isEnabled = enabled
            saveBtn.alpha = if (enabled) 1f else 0.45f
        }
        saveBtn.setOnClickListener {
            val value = input.text.toString().trim()
            if (value.isEmpty() || value == current) return@setOnClickListener
            finish(if (value == real) null else value)
        }
        input.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) { saveBtn.performClick(); true } else false
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { render() }
        })
        render()

        sheet.setContentView(root)
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.behavior.skipCollapsed = true
        pl.sinusoide.sinuschat.ui.FlowGlass.show(sheet)
        input.requestFocus()
    }
}
