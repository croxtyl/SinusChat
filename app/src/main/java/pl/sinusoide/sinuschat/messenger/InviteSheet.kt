package pl.sinusoide.sinuschat.messenger

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import pl.sinusoide.sinuschat.R
import pl.sinusoide.sinuschat.util.AppExecutor
import pl.sinusoide.sinuschat.util.PopupHelper
import java.text.DateFormat
import java.util.Date

class InviteSheet(private val activity: Activity, private val kind: String) {

    private val dp = activity.resources.displayMetrics.density
    private val sheet = BottomSheetDialog(activity, R.style.Theme_SinusChat_BottomSheet)

    private var cPrimary = 0; private var cSecondary = 0; private var cCard = 0; private var cAccent = 0
    private val cGreen = 0xFF3FB97A.toInt()
    private val cOrange = 0xFFE67E22.toInt()
    private val cRed = 0xFFE5484D.toInt()

    private lateinit var qrBox: FrameLayout
    private lateinit var qrImage: ImageView
    private lateinit var progress: ProgressBar
    private lateinit var linkText: TextView
    private lateinit var infoText: TextView
    private lateinit var usedText: TextView
    private lateinit var actions: LinearLayout
    private var current: InviteManager.Invite? = null

    private fun attr(id: Int, fallback: Int): Int {
        val tv = android.util.TypedValue()
        return pl.sinusoide.sinuschat.ui.ThemeUi.attrOrNull(activity, id) ?: fallback
    }

    fun show() {
        cPrimary = attr(R.attr.sinuschat_text_primary, Color.WHITE)
        cSecondary = attr(R.attr.sinuschat_text_secondary, 0xFF7F91A4.toInt())
        cCard = attr(R.attr.sinuschat_surface_elevated, 0xFF1D2A38.toInt())
        cAccent = pl.sinusoide.sinuschat.ui.ThemeUi.accent(activity)
        val once = kind == InviteManager.KIND_ONCE
        val color = if (once) cOrange else cAccent

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((22 * dp).toInt(), (12 * dp).toInt(), (22 * dp).toInt(), (22 * dp).toInt())
        }
        root.addView(View(activity).apply {
            background = GradientDrawable().apply { cornerRadius = 3 * dp; setColor((cSecondary and 0x00FFFFFF) or 0x55000000) }
        }, LinearLayout.LayoutParams((36 * dp).toInt(), (4 * dp).toInt()).apply { bottomMargin = (18 * dp).toInt() })

        root.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageView(activity).apply {
                setImageResource(if (once) R.drawable.ic_timer else R.drawable.ic_link)
                setColorFilter(color)
                background = GradientDrawable().apply { cornerRadius = 12 * dp; setColor((color and 0x00FFFFFF) or 0x24000000) }
                val p = (9 * dp).toInt(); setPadding(p, p, p, p)
            }, LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt()).apply { marginEnd = (12 * dp).toInt() })
            addView(TextView(activity).apply {
                text = activity.getString(if (once) R.string.invite_once_title else R.string.invite_multi_title)
                textSize = 19f; setTextColor(cPrimary); typeface = Typeface.DEFAULT_BOLD
            })
        }, LinearLayout.LayoutParams(-1, -2))

        qrBox = FrameLayout(activity).apply {
            background = GradientDrawable().apply { cornerRadius = 20 * dp; setColor(Color.WHITE) }
            val p = (12 * dp).toInt(); setPadding(p, p, p, p)
        }
        qrImage = ImageView(activity)
        progress = ProgressBar(activity).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(cAccent)
        }
        qrBox.addView(qrImage, FrameLayout.LayoutParams(-1, -1))
        qrBox.addView(progress, FrameLayout.LayoutParams((36 * dp).toInt(), (36 * dp).toInt(), Gravity.CENTER))
        root.addView(qrBox, LinearLayout.LayoutParams((220 * dp).toInt(), (220 * dp).toInt()).apply {
            topMargin = (18 * dp).toInt()
        })

        linkText = TextView(activity).apply {
            textSize = 13f; setTextColor(cSecondary); gravity = Gravity.CENTER
            typeface = Typeface.MONOSPACE
            maxLines = 2
            text = activity.getString(R.string.invite_loading)
            background = GradientDrawable().apply { cornerRadius = 12 * dp; setColor(cCard) }
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
            setTextIsSelectable(true)
        }
        root.addView(linkText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (16 * dp).toInt() })

        usedText = TextView(activity).apply {
            textSize = 12.5f; setTextColor(cGreen); gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            visibility = View.GONE
            setPadding(0, (8 * dp).toInt(), 0, 0)
        }
        root.addView(usedText)

        infoText = TextView(activity).apply {
            textSize = 13f; setTextColor(cSecondary); gravity = Gravity.CENTER
            setLineSpacing(0f, 1.15f)
            setPadding((4 * dp).toInt(), (10 * dp).toInt(), (4 * dp).toInt(), (16 * dp).toInt())
        }
        root.addView(infoText)

        actions = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; alpha = 0.4f }
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(button(activity.getString(R.string.invite_share), filled = true) { share() },
            LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(button(activity.getString(R.string.invite_copy), filled = false) { copy() },
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = (10 * dp).toInt() })
        actions.addView(row)
        actions.addView(TextView(activity).apply {
            text = activity.getString(if (once) R.string.invite_new_once else R.string.invite_reset)
            textSize = 14.5f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (once) cAccent else cRed)
            setPadding(0, (14 * dp).toInt(), 0, (4 * dp).toInt())
            isClickable = true
            setOnClickListener { if (once) load(reset = false) else confirmReset() }
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(actions, LinearLayout.LayoutParams(-1, -2))

        sheet.setContentView(root)
        pl.sinusoide.sinuschat.ui.FlowGlass.show(sheet)
        load(reset = false)
    }

    private fun button(label: String, filled: Boolean, onClick: () -> Unit) = TextView(activity).apply {
        text = label; textSize = 15f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
        setTextColor(if (filled) Color.WHITE else cAccent)
        background = GradientDrawable().apply {
            cornerRadius = 24 * dp
            if (filled) setColor(cAccent) else { setColor(Color.TRANSPARENT); setStroke((1.5f * dp).toInt(), cAccent) }
        }
        setPadding(0, (13 * dp).toInt(), 0, (13 * dp).toInt())
        isClickable = true
        setOnClickListener { if (current != null) onClick() }
    }

    private fun load(reset: Boolean) {
        progress.visibility = View.VISIBLE
        qrImage.animate().alpha(0.15f).setDuration(150).start()
        actions.alpha = 0.4f
        InviteManager.create(activity, kind, reset) { invite ->
            if (!sheet.isShowing) return@create
            if (invite == null) {
                progress.visibility = View.GONE
                linkText.text = activity.getString(R.string.invite_create_failed)
                return@create
            }
            current = invite
            linkText.text = invite.shortLink.removePrefix("https://")
            infoText.text = if (invite.kind == InviteManager.KIND_ONCE)
                activity.getString(R.string.invite_once_info,
                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(invite.expiresAt ?: 0L)))
            else activity.getString(R.string.invite_multi_info)
            usedText.visibility = if (invite.kind == InviteManager.KIND_MULTI && invite.useCount > 0) View.VISIBLE else View.GONE
            usedText.text = activity.getString(R.string.invite_used_times, invite.useCount)
            actions.animate().alpha(1f).setDuration(200).start()
            if (reset) PopupHelper.show(activity, activity.getString(R.string.invite_reset_done))
            renderQr(invite.link)
        }
    }

    private fun renderQr(link: String) {
        val size = (196 * dp).toInt()
        AppExecutor.io {
            val logo = try {
                val d = activity.packageManager.getApplicationIcon(activity.packageName)
                val b = Bitmap.createBitmap(d.intrinsicWidth.coerceAtLeast(1), d.intrinsicHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                val c = Canvas(b); d.setBounds(0, 0, c.width, c.height); d.draw(c); b
            } catch (_: Exception) { null }
            val bmp = try { StyledQr.generate(data = link, sizePx = size, logo = logo) } catch (_: Exception) { null }
            AppExecutor.main {
                if (!sheet.isShowing) return@main
                progress.visibility = View.GONE
                if (bmp != null) qrImage.setImageBitmap(bmp)
                qrImage.scaleX = 0.9f; qrImage.scaleY = 0.9f
                qrImage.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260)
                    .setInterpolator(android.view.animation.OvershootInterpolator(1.6f)).start()
            }
        }
    }

    private fun share() {
        val link = current?.link ?: return
        pl.sinusoide.sinuschat.util.SafeEngine.guard(activity, pl.sinusoide.sinuschat.util.SafeEngine.Risk.SHARE_INVITE) { shareLink(link) }
    }

    private fun shareLink(link: String) {
        activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, activity.getString(R.string.invite_share_text, link))
        }, null))
    }

    private fun copy() {
        val link = current?.link ?: return
        activity.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("SinusChat invite", link))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            PopupHelper.show(activity, activity.getString(R.string.copied))
        }
    }

    private fun confirmReset() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(activity, R.style.Theme_SinusChat_Dialog)
            .setTitle(activity.getString(R.string.invite_reset_q))
            .setMessage(activity.getString(R.string.invite_reset_desc))
            .setPositiveButton(activity.getString(R.string.invite_reset)) { _, _ -> load(reset = true) }
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .show()
    }
}
