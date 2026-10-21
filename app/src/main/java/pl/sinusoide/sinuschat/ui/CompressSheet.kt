package pl.sinusoide.sinuschat.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import pl.sinusoide.sinuschat.R
import pl.sinusoide.sinuschat.util.MediaManager

class CompressSheet private constructor(private val activity: Activity, fileName: String, private val onCancel: () -> Unit) {

    private val dp = activity.resources.displayMetrics.density
    private val sheet = BottomSheetDialog(activity, R.style.Theme_SinusChat_BottomSheet)
    private val title: TextView
    private val desc: TextView
    private val bar: ProgressBar
    private val action: TextView
    private val cancel: TextView
    private var answer: ((Boolean) -> Unit)? = null
    private var finished = false

    init {
        val accent = ThemeUi.accent(activity)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((24 * dp).toInt(), (12 * dp).toInt(), (24 * dp).toInt(), (20 * dp).toInt())
        }
        root.addView(View(activity).apply { background = ThemeUi.rounded(activity, ThemeUi.handle(activity), 3f) },
            LinearLayout.LayoutParams((36 * dp).toInt(), (4 * dp).toInt()).apply { bottomMargin = (20 * dp).toInt() })
        root.addView(ImageView(activity).apply {
            setImageResource(R.drawable.ic_zip); setColorFilter(accent)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor((accent and 0x00FFFFFF) or 0x26000000)
            }
            val p = (16 * dp).toInt(); setPadding(p, p, p, p)
        }, LinearLayout.LayoutParams((64 * dp).toInt(), (64 * dp).toInt()).apply { bottomMargin = (16 * dp).toInt() })
        title = TextView(activity).apply {
            text = activity.getString(R.string.compress_checking_title)
            textSize = 20f; setTextColor(ThemeUi.textPrimary(activity))
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        }
        root.addView(title)
        desc = TextView(activity).apply {
            text = activity.getString(R.string.compress_checking_desc, fileName)
            textSize = 14f; setTextColor(ThemeUi.textSecondary(activity)); gravity = Gravity.CENTER
            setLineSpacing(0f, 1.15f)
            setPadding(0, (8 * dp).toInt(), 0, (18 * dp).toInt())
        }
        root.addView(desc)
        bar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            isIndeterminate = false
            progressTintList = android.content.res.ColorStateList.valueOf(accent)
        }
        root.addView(bar, LinearLayout.LayoutParams(-1, (8 * dp).toInt()).apply { bottomMargin = (18 * dp).toInt() })
        action = TextView(activity).apply {
            text = activity.getString(R.string.compress_send)
            textSize = 16f; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            background = ThemeUi.rounded(activity, accent, 26f)
            setPadding(0, (14 * dp).toInt(), 0, (14 * dp).toInt())
            isClickable = true
            visibility = View.GONE
            setOnClickListener { finish(true) }
        }
        root.addView(action, LinearLayout.LayoutParams(-1, -2))
        cancel = TextView(activity).apply {
            text = activity.getString(R.string.cancel)
            textSize = 15f; setTextColor(ThemeUi.textSecondary(activity)); gravity = Gravity.CENTER
            setPadding(0, (14 * dp).toInt(), 0, (4 * dp).toInt())
            isClickable = true
            setOnClickListener { finish(false) }
        }
        root.addView(cancel, LinearLayout.LayoutParams(-1, -2))
        sheet.setContentView(root)
        sheet.setOnCancelListener { finish(false) }
        FlowGlass.show(sheet)
    }

    fun progress(p: Float) {
        bar.progress = (p.coerceIn(0f, 1f) * 1000).toInt()
    }

    fun ask(original: Long, packed: Long, onAnswer: (Boolean) -> Unit) {
        if (finished) { onAnswer(false); return }
        answer = onAnswer
        title.text = activity.getString(R.string.compress_title)
        desc.text = activity.getString(R.string.compress_message) + "\n\n" +
            activity.getString(R.string.compress_sizes, MediaManager.formatSize(original), MediaManager.formatSize(packed))
        bar.visibility = View.GONE
        action.visibility = View.VISIBLE
    }

    fun dismiss() {
        finished = true
        try { sheet.dismiss() } catch (_: Exception) {}
    }

    private fun finish(yes: Boolean) {
        if (finished) return
        finished = true
        val a = answer
        try { sheet.dismiss() } catch (_: Exception) {}
        if (a != null) a(yes) else onCancel()
    }

    companion object {
        fun show(activity: Activity, fileName: String, onCancel: () -> Unit) = CompressSheet(activity, fileName, onCancel)
    }
}
