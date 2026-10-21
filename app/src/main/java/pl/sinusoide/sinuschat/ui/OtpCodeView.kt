package pl.sinusoide.sinuschat.ui

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import pl.sinusoide.sinuschat.R

class OtpCodeView : LinearLayout {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    private val digitCount = 6
    private val boxes = ArrayList<TextView>()
    private val hidden = EditText(getContext())
    var onComplete: ((String) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER

        hidden.inputType = InputType.TYPE_CLASS_NUMBER
        hidden.filters = arrayOf(android.text.InputFilter.LengthFilter(digitCount))
        hidden.setTextColor(Color.TRANSPARENT)
        hidden.setBackgroundColor(Color.TRANSPARENT)
        hidden.isCursorVisible = false
        hidden.width = 1
        hidden.height = 1

        for (i in 0 until digitCount) {
            val box = TextView(context).apply {
                gravity = Gravity.CENTER
                textSize = 22f
                setTextColor(pl.sinusoide.sinuschat.ui.ThemeUi.textPrimary(context))
                background = ContextCompat.getDrawable(context, R.drawable.bg_otp_box)
                layoutParams = LayoutParams(dp(42), dp(52)).apply {
                    marginStart = if (i == 0) 0 else dp(7)
                }
            }
            boxes.add(box)
            addView(box)
        }
        addView(hidden)

        hidden.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                render(s?.toString() ?: "")
            }
        })

        setOnClickListener { focusInput() }
        boxes.forEach { it.setOnClickListener { focusInput() } }
    }

    private fun focusInput() {
        hidden.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(hidden, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun render(text: String, animateLast: Boolean = true) {
        for (i in 0 until digitCount) {
            val box = boxes[i]
            if (i < text.length) {
                val newChar = box.text?.toString() != text[i].toString()
                box.text = text[i].toString()
                box.background = ContextCompat.getDrawable(context, R.drawable.bg_otp_box_filled)
                if (animateLast && newChar && i == text.length - 1) popBox(box)
            } else {
                box.text = ""
                box.background = ContextCompat.getDrawable(context, R.drawable.bg_otp_box)
            }
        }
        if (text.length == digitCount) {
            onComplete?.invoke(text)
        }
    }

    private fun popBox(box: TextView) {
        box.scaleX = 0.7f; box.scaleY = 0.7f
        box.animate().scaleX(1f).scaleY(1f).setDuration(180).start()
    }

    fun clear() {
        hidden.setText("")
        render("")
    }

    fun showError() {
        boxes.forEach {
            it.background = ContextCompat.getDrawable(context, R.drawable.bg_otp_box_error)
            it.setTextColor(ContextCompat.getColor(context, R.color.sinuschat_error))
        }
        val shake = android.view.animation.TranslateAnimation(0f, dp(8).toFloat(), 0f, 0f).apply {
            duration = 400
            repeatCount = 3
            repeatMode = android.view.animation.Animation.REVERSE
        }
        startAnimation(shake)
        postDelayed({
            clearAnimation()
            boxes.forEach {
                it.setTextColor(pl.sinusoide.sinuschat.ui.ThemeUi.textPrimary(context))
            }
            clear()
        }, 600)
    }

    fun showSuccess() {
        boxes.forEach {
            it.background = ContextCompat.getDrawable(context, R.drawable.bg_otp_box_success)
            it.setTextColor(ContextCompat.getColor(context, R.color.sinuschat_success))
        }
    }
}
