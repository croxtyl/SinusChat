package pl.sinusoide.sinuschat.messenger

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
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
import pl.sinusoide.sinuschat.util.AvatarHelper

object BlockSheet {

    private const val RED = 0xFFE5484D.toInt()

    fun show(
        activity: Activity,
        name: String,
        otherHash: String,
        avatarUrl: String?,
        allowDelete: Boolean,
        onBlock: (deleteChat: Boolean) -> Unit,
        onCancel: () -> Unit = {}
    ) {
        val d = activity.resources.displayMetrics.density
        val secondary = ThemeUi.textSecondary(activity)
        val primary = ThemeUi.textPrimary(activity)
        val sheet = BottomSheetDialog(activity, R.style.Theme_SinusChat_BottomSheet)
        var confirmed = false

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((24 * d).toInt(), (12 * d).toInt(), (24 * d).toInt(), (20 * d).toInt())
        }
        root.addView(View(activity).apply {
            background = GradientDrawable().apply { cornerRadius = 3 * d; setColor(ThemeUi.handle(activity)) }
        }, LinearLayout.LayoutParams((36 * d).toInt(), (4 * d).toInt()).apply { bottomMargin = (22 * d).toInt() })

        val halo = LinearLayout(activity).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor((RED and 0x00FFFFFF) or 0x14000000) }
        }
        halo.addView(ImageView(activity).apply {
            setImageResource(R.drawable.ic_block)
            setColorFilter(RED)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor((RED and 0x00FFFFFF) or 0x2E000000) }
            val p = (15 * d).toInt(); setPadding(p, p, p, p)
        }, LinearLayout.LayoutParams((58 * d).toInt(), (58 * d).toInt()))
        root.addView(halo, LinearLayout.LayoutParams((84 * d).toInt(), (84 * d).toInt()).apply { bottomMargin = (14 * d).toInt() })
        val breathe = ObjectAnimator.ofPropertyValuesHolder(halo,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.07f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.07f, 1f)).apply {
            duration = 1600; repeatCount = ObjectAnimator.INFINITE
            start()
        }

        root.addView(TextView(activity).apply {
            text = activity.getString(R.string.block_user_q, name)
            textSize = 20f; setTextColor(primary); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 2; ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 0, 0, (16 * d).toInt())
        })

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 18 * d
                setColor(ThemeUi.card(activity))
                setStroke((1 * d).toInt().coerceAtLeast(1), (RED and 0x00FFFFFF) or 0x33000000)
            }
            setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (14 * d).toInt())
        }
        val who = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val avatarSize = (42 * d).toInt()
        val avatar = ImageView(activity)
        avatar.setImageBitmap(AvatarHelper.createLetterBitmap(
            AvatarHelper.initialOf(name), AvatarHelper.getContactColor(activity, otherHash), avatarSize))
        avatarUrl?.takeIf { it.isNotEmpty() }?.let { url ->
            com.bumptech.glide.Glide.with(avatar).load(url).circleCrop().into(avatar)
        }
        who.addView(avatar, LinearLayout.LayoutParams(avatarSize, avatarSize).apply { marginEnd = (12 * d).toInt() })
        who.addView(TextView(activity).apply {
            text = name; textSize = 15.5f; setTextColor(primary); typeface = Typeface.DEFAULT_BOLD
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(who)
        card.addView(View(activity).apply { setBackgroundColor((secondary and 0x00FFFFFF) or 0x22000000) },
            LinearLayout.LayoutParams(-1, (1 * d).toInt().coerceAtLeast(1)).apply {
                topMargin = (12 * d).toInt(); bottomMargin = (10 * d).toInt()
            })
        for (res in intArrayOf(R.string.block_fact_server, R.string.block_fact_profile, R.string.block_fact_undo)) {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, (3 * d).toInt(), 0, (3 * d).toInt())
            }
            row.addView(View(activity).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(RED) }
            }, LinearLayout.LayoutParams((6 * d).toInt(), (6 * d).toInt()).apply {
                topMargin = (7 * d).toInt(); marginEnd = (10 * d).toInt()
            })
            row.addView(TextView(activity).apply {
                text = activity.getString(res); textSize = 13.5f; setTextColor(secondary)
                setLineSpacing(0f, 1.12f)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(row)
        }
        root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = (22 * d).toInt() })

        fun button(icon: Int, label: Int, filled: Boolean, deleteChat: Boolean) = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = 26 * d
                if (filled) setColor(RED)
                else { setColor(Color.TRANSPARENT); setStroke((1.5f * d).toInt().coerceAtLeast(1), RED) }
            }
            setPadding(0, (14 * d).toInt(), 0, (14 * d).toInt())
            isClickable = true
            val fg = if (filled) Color.WHITE else RED
            addView(ImageView(activity).apply {
                setImageResource(icon)
                setColorFilter(fg)
            }, LinearLayout.LayoutParams((20 * d).toInt(), (20 * d).toInt()).apply { marginEnd = (8 * d).toInt() })
            addView(TextView(activity).apply {
                text = activity.getString(label)
                textSize = 16f; setTextColor(fg); typeface = Typeface.DEFAULT_BOLD
            })
            setOnClickListener {
                confirmed = true
                sheet.dismiss()
                onBlock(deleteChat)
            }
        }
        root.addView(button(R.drawable.ic_block, R.string.block, true, false), LinearLayout.LayoutParams(-1, -2))
        if (allowDelete) {
            root.addView(button(R.drawable.ic_delete, R.string.block_and_delete_chat, false, true),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = (10 * d).toInt() })
        }
        root.addView(TextView(activity).apply {
            text = activity.getString(R.string.cancel)
            textSize = 15f; setTextColor(secondary); gravity = Gravity.CENTER
            setPadding(0, (14 * d).toInt(), 0, (4 * d).toInt())
            isClickable = true
            setOnClickListener { sheet.dismiss() }
        }, LinearLayout.LayoutParams(-1, -2))

        sheet.setOnDismissListener {
            breathe.cancel()
            if (!confirmed) onCancel()
        }
        sheet.setContentView(root)
        pl.sinusoide.sinuschat.ui.FlowGlass.show(sheet)
    }
}
