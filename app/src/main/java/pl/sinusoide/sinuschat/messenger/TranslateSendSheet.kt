package pl.sinusoide.sinuschat.messenger

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import pl.sinusoide.sinuschat.R
import pl.sinusoide.sinuschat.ui.ThemeUi
import pl.sinusoide.sinuschat.util.Translations

object TranslateSendSheet {

    fun show(
        activity: Activity,
        original: String,
        translated: String,
        from: String,
        to: String,
        onTranslated: () -> Unit,
        onOriginal: () -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) return
        val d = activity.resources.displayMetrics.density
        val primary = ThemeUi.textPrimary(activity)
        val secondary = ThemeUi.textSecondary(activity)
        val accent = ThemeUi.accent(activity)
        val sheet = BottomSheetDialog(activity, R.style.Theme_SinusChat_BottomSheet)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((22 * d).toInt(), (12 * d).toInt(), (22 * d).toInt(), (20 * d).toInt())
        }
        root.addView(View(activity).apply {
            background = GradientDrawable().apply { cornerRadius = 3 * d; setColor(ThemeUi.handle(activity)) }
        }, LinearLayout.LayoutParams((36 * d).toInt(), (4 * d).toInt()).apply {
            gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = (20 * d).toInt()
        })

        root.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageView(activity).apply {
                setImageResource(R.drawable.ic_translate)
                setColorFilter(accent)
                background = GradientDrawable().apply { cornerRadius = 14 * d; setColor((accent and 0x00FFFFFF) or 0x24000000) }
                val p = (10 * d).toInt(); setPadding(p, p, p, p)
            }, LinearLayout.LayoutParams((44 * d).toInt(), (44 * d).toInt()).apply { marginEnd = (14 * d).toInt() })
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(activity).apply {
                    text = activity.getString(R.string.translate_send_title)
                    textSize = 18f; setTextColor(primary); typeface = Typeface.DEFAULT_BOLD
                })
                addView(TextView(activity).apply {
                    text = Translations.pair(activity, from, to)
                    textSize = 13f; setTextColor(accent); typeface = Typeface.DEFAULT_BOLD
                    setPadding(0, (2 * d).toInt(), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = (16 * d).toInt() })

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { cornerRadius = 18 * d; setColor(ThemeUi.card(activity)) }
            setPadding((16 * d).toInt(), (14 * d).toInt(), (16 * d).toInt(), (14 * d).toInt())
        }
        fun label(res: Int) = TextView(activity).apply {
            text = activity.getString(res).uppercase()
            textSize = 11f; setTextColor(secondary); typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
        }
        card.addView(label(R.string.translate_send_yours))
        card.addView(TextView(activity).apply {
            text = original
            textSize = 14f; setTextColor(secondary)
            maxLines = 4; ellipsize = TextUtils.TruncateAt.END
            setPadding(0, (4 * d).toInt(), 0, (12 * d).toInt())
        })
        card.addView(View(activity).apply { setBackgroundColor(ThemeUi.divider(activity)) },
            LinearLayout.LayoutParams(-1, (1 * d).toInt().coerceAtLeast(1)).apply { bottomMargin = (12 * d).toInt() })
        card.addView(label(R.string.translate_send_preview))
        card.addView(TextView(activity).apply {
            text = translated
            textSize = 15.5f; setTextColor(primary)
            maxLines = 8; ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(0f, 1.1f)
            setPadding(0, (4 * d).toInt(), 0, 0)
        })
        root.addView(card, LinearLayout.LayoutParams(-1, -2))

        root.addView(TextView(activity).apply {
            text = activity.getString(R.string.translate_send_hint)
            textSize = 12.5f; setTextColor(secondary); gravity = Gravity.CENTER
            setPadding((8 * d).toInt(), (12 * d).toInt(), (8 * d).toInt(), (18 * d).toInt())
        }, LinearLayout.LayoutParams(-1, -2))

        root.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = 26 * d; setColor(accent) }
            setPadding(0, (14 * d).toInt(), 0, (14 * d).toInt())
            isClickable = true
            addView(ImageView(activity).apply {
                setImageResource(R.drawable.ic_translate)
                setColorFilter(ThemeUi.onAccent(activity))
            }, LinearLayout.LayoutParams((20 * d).toInt(), (20 * d).toInt()).apply { marginEnd = (8 * d).toInt() })
            addView(TextView(activity).apply {
                text = activity.getString(R.string.translate_send_translated)
                textSize = 16f; setTextColor(ThemeUi.onAccent(activity)); typeface = Typeface.DEFAULT_BOLD
            })
            setOnClickListener { sheet.dismiss(); onTranslated() }
        }, LinearLayout.LayoutParams(-1, -2))

        root.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = 26 * d
                setColor(Color.TRANSPARENT)
                setStroke((1.5f * d).toInt().coerceAtLeast(1), (accent and 0x00FFFFFF) or 0x66000000)
            }
            setPadding(0, (13 * d).toInt(), 0, (13 * d).toInt())
            isClickable = true
            addView(ImageView(activity).apply {
                setImageResource(R.drawable.ic_send)
                setColorFilter(accent)
            }, LinearLayout.LayoutParams((18 * d).toInt(), (18 * d).toInt()).apply { marginEnd = (8 * d).toInt() })
            addView(TextView(activity).apply {
                text = activity.getString(R.string.translate_send_original)
                textSize = 15f; setTextColor(accent); typeface = Typeface.DEFAULT_BOLD
            })
            setOnClickListener { sheet.dismiss(); onOriginal() }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (10 * d).toInt() })

        root.addView(TextView(activity).apply {
            text = activity.getString(R.string.cancel)
            textSize = 15f; setTextColor(secondary); gravity = Gravity.CENTER
            setPadding(0, (14 * d).toInt(), 0, (4 * d).toInt())
            isClickable = true
            setOnClickListener { sheet.dismiss() }
        }, LinearLayout.LayoutParams(-1, -2))

        sheet.setContentView(root)
        pl.sinusoide.sinuschat.ui.FlowGlass.show(sheet)
    }
}
