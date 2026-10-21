package pl.sinusoide.sinuschat.ui

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.SystemClock
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import pl.sinusoide.sinuschat.R

class CodeInputView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    private enum class State { NORMAL, SUCCESS, ERROR }

    private val d = resources.displayMetrics.density
    private val boxes = ArrayList<TextView>()
    private val row = LinearLayout(context)
    private var state = State.NORMAL
    private var shown = ""
    private var silent = false
    private var before = ""
    private var insStart = 0
    private var insCount = 0
    private var insReplaced = 0
    private var typingToken = 0
    private var typingEndsAt = 0L
    private var appending = false
    private var locked = false

    var onComplete: ((String) -> Unit)? = null

    var onFullCode: ((String) -> Unit)? = null

    var onEdit: (() -> Unit)? = null

    val input: EditText = EditText(context).apply {
        inputType = InputType.TYPE_CLASS_NUMBER
        filters = arrayOf(
            InputFilter { src, _, _, dest, ds, de -> if (locked && !silent && !appending) dest.subSequence(ds, de) else null },
            InputFilter { src, s, e, _, _, _ ->
                val digits = src.subSequence(s, e).filter { it.isDigit() }
                if (digits.length == e - s) null else digits
            },
            InputFilter.LengthFilter(64)
        )
        isCursorVisible = false
        setTextColor(Color.TRANSPARENT)
        highlightColor = Color.TRANSPARENT
        background = null
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        contentDescription = context.getString(R.string.verification_title)
    }

    val code: String get() = input.text.toString()

    init {
        clipChildren = false
        clipToPadding = false
        setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (10 * d).toInt())
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        row.clipChildren = false
        repeat(LENGTH) {
            val box = TextView(context).apply {
                gravity = Gravity.CENTER
                textSize = 24f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ThemeUi.textPrimary(context))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            boxes += box
            row.addView(box, LinearLayout.LayoutParams((46 * d).toInt(), (56 * d).toInt()).apply {
                marginStart = (4 * d).toInt(); marginEnd = (4 * d).toInt()
            })
        }
        addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        addView(input, LayoutParams((LENGTH * 54 * d).toInt(), (56 * d).toInt(), Gravity.CENTER))

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                before = s?.toString().orEmpty()
            }
            override fun onTextChanged(s: CharSequence?, start: Int, bef: Int, count: Int) {
                insStart = start
                insCount = count
                insReplaced = bef
            }
            override fun afterTextChanged(s: Editable?) {
                if (silent) return
                val now = s?.toString().orEmpty()
                if (insCount - insReplaced > 1) {
                    val pasted = now.substring(insStart, (insStart + insCount).coerceAtMost(now.length))
                    state = State.NORMAL
                    setSilently(before)
                    typeIn(pasted)
                    return
                }
                if (now.length > LENGTH) {
                    setSilently(now.take(LENGTH))
                    return
                }
                if (state != State.NORMAL) state = State.NORMAL
                val popAt = if (now.length == before.length + 1) now.length - 1 else -1
                render(now, popAt)
                onEdit?.invoke()
                if (now.length == LENGTH) onComplete?.invoke(now)
            }
        })
        input.setOnFocusChangeListener { _, _ -> render(code, -1) }
        render("", -1)
    }

    fun focus() {
        input.requestFocus()
        input.setSelection(input.text.length)
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
    }

    fun typeIn(digits: String) {
        val token = ++typingToken
        var clean = digits.filter { it.isDigit() }
        if (clean.length >= LENGTH) {
            clean = clean.take(LENGTH)
            if (state != State.NORMAL) state = State.NORMAL
            setSilently("")
            render("", -1)
        }
        typingEndsAt = SystemClock.uptimeMillis() + TYPE_MS * clean.length
        for ((i, ch) in clean.withIndex()) {
            postDelayed({
                if (token == typingToken && input.text.length < LENGTH) {
                    appending = true
                    try {
                        input.text.append(ch.toString())
                    } finally {
                        appending = false
                    }
                    input.setSelection(input.text.length)
                }
            }, TYPE_MS * (i + 1))
        }
        if (clean.length == LENGTH) onFullCode?.invoke(clean)
    }

    fun afterTyping(action: () -> Unit) {
        val wait = typingEndsAt - SystemClock.uptimeMillis()
        if (wait > 0) postDelayed(action, wait + 60) else action()
    }

    fun setLocked(locked: Boolean) {
        this.locked = locked
    }

    fun showSuccess() {
        state = State.SUCCESS
        for ((i, box) in boxes.withIndex()) {
            box.postDelayed({
                if (state == State.SUCCESS) {
                    style(box, i, code)
                    pop(box)
                }
            }, 45L * i)
        }
    }

    fun showError() {
        typingToken++
        state = State.ERROR
        render(code, -1)
        shakeRow()
        postDelayed({
            if (state == State.ERROR) {
                state = State.NORMAL
                setSilently("")
                render("", -1)
            }
        }, 520)
    }

    fun shake() = shakeRow()

    fun clear() {
        typingToken++
        state = State.NORMAL
        setSilently("")
        render("", -1)
    }

    private fun setSilently(text: String) {
        silent = true
        input.setText(text)
        input.setSelection(text.length)
        silent = false
    }

    private fun shakeRow() {
        ObjectAnimator.ofFloat(row, View.TRANSLATION_X,
            0f, -14f * d, 12f * d, -9f * d, 6f * d, -3f * d, 0f).apply {
            duration = 440
            start()
        }
        performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT
            else HapticFeedbackConstants.LONG_PRESS)
    }

    private fun render(text: String, popAt: Int) {
        shown = text
        for ((i, box) in boxes.withIndex()) {
            box.text = text.getOrNull(i)?.toString().orEmpty()
            style(box, i, text)
        }
        if (popAt in boxes.indices) pop(boxes[popAt])
    }

    private fun style(box: TextView, i: Int, text: String) {
        val ctx = context
        val green = ctx.getColor(R.color.sinuschat_success)
        val red = ctx.getColor(R.color.sinuschat_error)
        val accent = ThemeUi.accent(ctx)
        val fill = if (ThemeUi.isFlow(ctx)) 0x1FFFFFFF else ThemeUi.surface(ctx)
        val (stroke, width, bg, ink) = when {
            state == State.SUCCESS -> Quad(green, 2f, blend(fill, green, 0.16f), green)
            state == State.ERROR -> Quad(red, 2f, blend(fill, red, 0.12f), red)
            i == text.length && input.hasFocus() -> Quad(accent, 2f, fill, ThemeUi.textPrimary(ctx))
            i < text.length -> Quad(blend(ThemeUi.border(ctx), ThemeUi.textSecondary(ctx), 0.5f), 1.5f,
                fill, ThemeUi.textPrimary(ctx))
            else -> Quad(ThemeUi.border(ctx), 1f, fill, ThemeUi.textPrimary(ctx))
        }
        box.background = GradientDrawable().apply {
            cornerRadius = 14 * d
            setColor(bg)
            setStroke((width * d).toInt().coerceAtLeast(1), stroke)
        }
        box.setTextColor(ink)
    }

    private fun pop(box: View) {
        box.animate().cancel()
        box.animate().scaleX(1.12f).scaleY(1.12f).translationY(-5f * d).setDuration(90)
            .setInterpolator(null)
            .withEndAction {
                box.animate().scaleX(1f).scaleY(1f).translationY(0f).setDuration(260)
                    .setInterpolator(OvershootInterpolator(3f)).start()
            }.start()
    }

    private data class Quad(val stroke: Int, val width: Float, val bg: Int, val ink: Int)

    private fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(x: Int, y: Int) = (x + (y - x) * t).toInt()
        return Color.argb(ch(Color.alpha(a), 255), ch(Color.red(a), Color.red(b)),
            ch(Color.green(a), Color.green(b)), ch(Color.blue(a), Color.blue(b)))
    }

    companion object {
        const val LENGTH = 6

        private const val TYPE_MS = 110L
    }
}
