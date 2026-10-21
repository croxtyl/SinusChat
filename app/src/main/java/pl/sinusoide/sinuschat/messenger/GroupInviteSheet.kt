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

class GroupInviteSheet(
    private val activity: Activity,
    private val groupId: String,
    private val groupName: String,
    private val onChanged: (Boolean) -> Unit = {}
) {
    private val dp = activity.resources.displayMetrics.density
    private val sheet = BottomSheetDialog(activity, R.style.Theme_SinusChat_BottomSheet)

    private var cPrimary = 0; private var cSecondary = 0; private var cCard = 0; private var cAccent = 0
    private val cGreen = 0xFF2ECC71.toInt()
    private val cRed = 0xFFE5484D.toInt()

    private lateinit var qrImage: ImageView
    private lateinit var progress: ProgressBar
    private lateinit var linkText: TextView
    private lateinit var countText: TextView
    private lateinit var actions: LinearLayout
    private var current: GroupInviteManager.Link? = null

    private fun attr(id: Int, fallback: Int): Int {
        val tv = android.util.TypedValue()
        return pl.sinusoide.sinuschat.ui.ThemeUi.attrOrNull(activity, id) ?: fallback
    }

    fun show() {
        cPrimary = attr(R.attr.sinuschat_text_primary, Color.WHITE)
        cSecondary = attr(R.attr.sinuschat_text_secondary, 0xFF7F91A4.toInt())
        cCard = attr(R.attr.sinuschat_surface_elevated, 0xFF1D2A38.toInt())
        cAccent = pl.sinusoide.sinuschat.ui.ThemeUi.accent(activity)

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
                setImageResource(R.drawable.ic_community); setColorFilter(cGreen)
                background = GradientDrawable().apply { cornerRadius = 12 * dp; setColor((cGreen and 0x00FFFFFF) or 0x24000000) }
                val p = (9 * dp).toInt(); setPadding(p, p, p, p)
            }, LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt()).apply { marginEnd = (12 * dp).toInt() })
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(activity).apply {
                    text = activity.getString(R.string.group_invite_title)
                    textSize = 19f; setTextColor(cPrimary); typeface = Typeface.DEFAULT_BOLD
                })
                addView(TextView(activity).apply {
                    text = groupName; textSize = 13f; setTextColor(cSecondary)
                    maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                })
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }, LinearLayout.LayoutParams(-1, -2))

        val qrBox = FrameLayout(activity).apply {
            background = GradientDrawable().apply { cornerRadius = 20 * dp; setColor(Color.WHITE) }
            val p = (12 * dp).toInt(); setPadding(p, p, p, p)
        }
        qrImage = ImageView(activity).apply { alpha = 0.15f }
        progress = ProgressBar(activity).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(cAccent)
        }
        qrBox.addView(qrImage, FrameLayout.LayoutParams(-1, -1))
        qrBox.addView(progress, FrameLayout.LayoutParams((36 * dp).toInt(), (36 * dp).toInt(), Gravity.CENTER))
        root.addView(qrBox, LinearLayout.LayoutParams((220 * dp).toInt(), (220 * dp).toInt()).apply { topMargin = (18 * dp).toInt() })

        linkText = TextView(activity).apply {
            textSize = 13f; setTextColor(cSecondary); gravity = Gravity.CENTER
            typeface = Typeface.MONOSPACE; maxLines = 2
            text = activity.getString(R.string.invite_loading)
            background = GradientDrawable().apply { cornerRadius = 12 * dp; setColor(cCard) }
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
            setTextIsSelectable(true)
        }
        root.addView(linkText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (16 * dp).toInt() })

        countText = TextView(activity).apply {
            textSize = 12.5f; setTextColor(cGreen); gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
            setPadding(0, (8 * dp).toInt(), 0, 0)
            visibility = View.GONE
        }
        root.addView(countText)

        root.addView(TextView(activity).apply {
            text = activity.getString(R.string.group_invite_info)
            textSize = 13f; setTextColor(cSecondary); gravity = Gravity.CENTER
            setLineSpacing(0f, 1.15f)
            setPadding((4 * dp).toInt(), (10 * dp).toInt(), (4 * dp).toInt(), (16 * dp).toInt())
        })

        actions = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; alpha = 0.4f }
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(button(activity.getString(R.string.invite_share), filled = true) { share() }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(button(activity.getString(R.string.invite_copy), filled = false) { copy() },
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = (10 * dp).toInt() })
        actions.addView(row)
        val lower = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        lower.addView(textAction(activity.getString(R.string.group_invite_reset), cAccent) { confirm(reset = true) },
            LinearLayout.LayoutParams(0, -2, 1f))
        lower.addView(textAction(activity.getString(R.string.group_invite_revoke), cRed) { confirm(reset = false) },
            LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(lower)
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

    private fun textAction(label: String, color: Int, onClick: () -> Unit) = TextView(activity).apply {
        text = label; textSize = 14.5f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
        setTextColor(color)
        setPadding(0, (16 * dp).toInt(), 0, (4 * dp).toInt())
        isClickable = true
        setOnClickListener { if (current != null) onClick() }
    }

    private fun load(reset: Boolean) {
        progress.visibility = View.VISIBLE
        qrImage.animate().alpha(0.15f).setDuration(150).start()
        actions.alpha = 0.4f
        GroupInviteManager.getLink(activity, groupId, reset) { link ->
            if (!sheet.isShowing) return@getLink
            if (link == null) {
                progress.visibility = View.GONE
                linkText.text = activity.getString(R.string.group_invite_failed)
                return@getLink
            }
            current = link
            onChanged(true)
            linkText.text = link.url.removePrefix("https://").substringBefore('#')
            countText.visibility = View.VISIBLE
            countText.text = activity.resources.getQuantityString(R.plurals.group_join_members, link.memberCount, link.memberCount)
            actions.animate().alpha(1f).setDuration(200).start()
            if (reset) PopupHelper.show(activity, activity.getString(R.string.group_invite_reset_done))
            renderQr(link.url)
        }
    }

    private fun renderQr(url: String) {
        val size = (196 * dp).toInt()
        AppExecutor.io {
            val logo = try {
                val d = activity.packageManager.getApplicationIcon(activity.packageName)
                val b = Bitmap.createBitmap(d.intrinsicWidth.coerceAtLeast(1), d.intrinsicHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                val c = Canvas(b); d.setBounds(0, 0, c.width, c.height); d.draw(c); b
            } catch (_: Exception) { null }
            val bmp = try { StyledQr.generate(data = url, sizePx = size, logo = logo) } catch (_: Exception) { null }
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
        val url = current?.url ?: return
        pl.sinusoide.sinuschat.util.SafeEngine.guard(activity, pl.sinusoide.sinuschat.util.SafeEngine.Risk.SHARE_GROUP_INVITE) { shareLink(url) }
    }

    private fun shareLink(url: String) {
        activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, activity.getString(R.string.group_invite_share_text, groupName, url))
        }, null))
    }

    private fun copy() {
        val url = current?.url ?: return
        activity.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("SinusChat", url))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) PopupHelper.show(activity, activity.getString(R.string.copied))
    }

    private fun confirm(reset: Boolean) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(activity, R.style.Theme_SinusChat_Dialog)
            .setTitle(activity.getString(if (reset) R.string.group_invite_reset_q else R.string.group_invite_revoke_q))
            .setMessage(activity.getString(if (reset) R.string.group_invite_reset_desc else R.string.group_invite_revoke_desc))
            .setPositiveButton(activity.getString(if (reset) R.string.group_invite_reset else R.string.group_invite_revoke)) { _, _ ->
                if (reset) load(reset = true) else revoke()
            }
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .show()
    }

    private fun revoke() {
        actions.alpha = 0.4f
        GroupInviteManager.revoke(activity, groupId) { ok ->
            if (ok) {
                onChanged(false)
                sheet.dismiss()
                PopupHelper.showSuccess(activity, activity.getString(R.string.group_invite_revoked))
            } else {
                actions.alpha = 1f
                PopupHelper.showError(activity, activity.getString(R.string.error_connection))
            }
        }
    }
}
