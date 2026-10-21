package pl.sinusoide.sinuschat

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import pl.sinusoide.sinuschat.network.ApiClient
import pl.sinusoide.sinuschat.setup.SetupActivity
import pl.sinusoide.sinuschat.util.AppExecutor
import pl.sinusoide.sinuschat.util.PopupHelper
import pl.sinusoide.sinuschat.util.AutoDelete
import pl.sinusoide.sinuschat.util.ThemeManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AccountSettingsActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(pl.sinusoide.sinuschat.util.LocaleManager.wrap(newBase))
    }

    private val dp get() = resources.displayMetrics.density
    private lateinit var emailView: TextView
    private lateinit var deletionBanner: LinearLayout
    private var autoDeleteRow: View? = null
    private lateinit var deletionBannerText: TextView

    private val deleteAccountLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK &&
            result.data?.getBooleanExtra("cancelled", false) == true) {
            PopupHelper.showSuccess(this, getString(R.string.deletion_cancelled_success))
        }
        refreshDeletionState()
    }

    private val changeEmailLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        emailView.text = accountLine()
    }

    private val anon get() = pl.sinusoide.sinuschat.setup.AnonAccount.isCurrent(this)

    private fun accountLine(): String =
        if (anon) getString(R.string.account_anon_hint)
        else SetupActivity.displayEmail(this).ifEmpty { getString(R.string.not_set) }

    private fun attr(id: Int, fallback: Int): Int {
        val tv = android.util.TypedValue()
        return pl.sinusoide.sinuschat.ui.ThemeUi.attrOrNull(this, id) ?: fallback
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pl.sinusoide.sinuschat.util.SettingsHighlight.apply(this)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        emailView.text = accountLine()
        refreshDeletionState()
    }

    private fun buildUi() {
        val bg = attr(R.attr.sinuschat_background, 0xFF0E1621.toInt())
        val surface = attr(R.attr.sinuschat_surface, 0xFF17212B.toInt())
        val card = attr(R.attr.sinuschat_surface_elevated, 0xFF1D2A38.toInt())
        val primary = attr(R.attr.sinuschat_text_primary, Color.WHITE)
        val secondary = attr(R.attr.sinuschat_text_secondary, 0xFF7F91A4.toInt())
        val accent = pl.sinusoide.sinuschat.ui.ThemeUi.accent(this)
        val red = ContextCompat.getColor(this, R.color.sinuschat_error)
        val green = ContextCompat.getColor(this, R.color.sinuschat_success)

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = (56 * dp).toInt()
            setPadding((12 * dp).toInt(), 0, (12 * dp).toInt(), 0)
            setBackgroundColor(surface)
            elevation = 2 * dp
        }
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_back); setColorFilter(primary)
            val p = (10 * dp).toInt(); setPadding(p, p, p, p)
            background = ContextCompat.getDrawable(this@AccountSettingsActivity, R.drawable.bg_menu_ripple)
            contentDescription = getString(R.string.back)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt()))
        header.addView(TextView(this).apply {
            text = getString(R.string.account); textSize = 20f
            setTextColor(primary); typeface = Typeface.DEFAULT_BOLD
            setPadding((8 * dp).toInt(), 0, 0, 0)
        })
        outer.addView(header)
        ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding((12 * dp).toInt(), bars.top, (12 * dp).toInt(), 0)
            insets
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt(), (24 * dp).toInt())
        }
        val scroll = ScrollView(this).apply { addView(content) }
        outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, 0, 0, bars.bottom); insets
        }

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = rounded(card, 20f)
            setPadding((20 * dp).toInt(), (24 * dp).toInt(), (20 * dp).toInt(), (22 * dp).toInt())
        }
        hero.addView(ImageView(this).apply {
            setImageResource(if (anon) R.drawable.ic_no_email else R.drawable.ic_mail); setColorFilter(accent)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor((accent and 0x00FFFFFF) or 0x26000000)
            }
            val p = (16 * dp).toInt(); setPadding(p, p, p, p)
        }, LinearLayout.LayoutParams((64 * dp).toInt(), (64 * dp).toInt()).apply {
            bottomMargin = (12 * dp).toInt()
        })
        hero.addView(TextView(this).apply {
            text = getString(if (anon) R.string.account_no_email else R.string.account_email_label)
            textSize = 13f; setTextColor(secondary)
            gravity = Gravity.CENTER
        })
        emailView = TextView(this).apply {
            textSize = 17f; setTextColor(primary); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, (2 * dp).toInt(), 0, (12 * dp).toInt())
            setTextIsSelectable(!anon)
        }
        hero.addView(emailView)
        hero.addView(pill(getString(if (anon) R.string.account_anon_pill else R.string.account_email_verified),
            R.drawable.ic_shield, green))
        content.addView(hero)

        deletionBanner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 16 * dp
                setColor((red and 0x00FFFFFF) or 0x1F000000)
                setStroke((1 * dp).toInt(), (red and 0x00FFFFFF) or 0x66000000)
            }
            setPadding((14 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt())
            visibility = View.GONE
            isClickable = true
            setOnClickListener { openDeleteAccount() }
        }
        deletionBanner.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_danger); setColorFilter(red)
        }, LinearLayout.LayoutParams((22 * dp).toInt(), (22 * dp).toInt()).apply { marginEnd = (12 * dp).toInt() })
        deletionBannerText = TextView(this).apply { textSize = 13.5f; setTextColor(primary) }
        deletionBanner.addView(deletionBannerText, LinearLayout.LayoutParams(0, -2, 1f))
        deletionBanner.addView(TextView(this).apply {
            text = getString(R.string.cancel); textSize = 13.5f; setTextColor(accent)
            typeface = Typeface.DEFAULT_BOLD
            val p = (10 * dp).toInt(); setPadding(p, p, p, p)
            isClickable = true
            setOnClickListener { cancelDeletionQuick() }
        })
        content.addView(deletionBanner, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (12 * dp).toInt() })

        content.addView(sectionTitle(getString(R.string.settings_account_management_title), accent))
        val manage = cardContainer(card)
        if (!pl.sinusoide.sinuschat.setup.AnonAccount.isCurrent(this)) {
            manage.addView(row(R.drawable.ic_mail, accent, getString(R.string.settings_change_email),
                getString(R.string.settings_change_email_sub), primary, secondary) {
                changeEmailLauncher.launch(Intent(this, ChangeEmailActivity::class.java))
            })
            manage.addView(divider(card))
        }
        autoDeleteRow = row(R.drawable.ic_autodelete, accent, getString(R.string.auto_delete_title),
            autoDeleteSubtitle(), primary, secondary) { showAutoDeleteSheet() }
        manage.addView(autoDeleteRow)
        content.addView(manage)

        content.addView(sectionTitle(getString(R.string.account_danger_zone), red))
        val danger = cardContainer(card)
        danger.addView(row(R.drawable.ic_delete, red, getString(R.string.settings_delete_account),
            getString(R.string.settings_delete_account_sub), red, secondary) { openDeleteAccount() })
        content.addView(danger)

        content.addView(TextView(this).apply {
            text = getString(R.string.account_footer_note)
            textSize = 12.5f; setTextColor(secondary); gravity = Gravity.CENTER
            setLineSpacing(0f, 1.2f)
            setPadding((12 * dp).toInt(), (18 * dp).toInt(), (12 * dp).toInt(), 0)
        })

        setContentView(outer)
    }

    private fun rounded(color: Int, radiusDp: Float) = pl.sinusoide.sinuschat.ui.ThemeUi.rounded(this, color, radiusDp)

    private fun cardContainer(color: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(color, 18f)
        clipToOutline = true
    }

    private fun sectionTitle(text: String, color: Int) = TextView(this).apply {
        this.text = text.uppercase()
        textSize = 12f; setTextColor(color); typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.06f
        setPadding((6 * dp).toInt(), (22 * dp).toInt(), 0, (8 * dp).toInt())
    }

    private fun pill(text: String, icon: Int, color: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = 14 * dp
            setColor((color and 0x00FFFFFF) or 0x24000000)
        }
        setPadding((10 * dp).toInt(), (5 * dp).toInt(), (12 * dp).toInt(), (5 * dp).toInt())
        addView(ImageView(this@AccountSettingsActivity).apply {
            setImageResource(icon); setColorFilter(color)
        }, LinearLayout.LayoutParams((14 * dp).toInt(), (14 * dp).toInt()).apply { marginEnd = (6 * dp).toInt() })
        addView(TextView(this@AccountSettingsActivity).apply {
            this.text = text; textSize = 12f; setTextColor(color)
        })
    }

    private fun row(
        icon: Int, iconColor: Int, title: String, subtitle: String,
        titleColor: Int, subColor: Int, onClick: () -> Unit
    ) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding((14 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt())
        background = ContextCompat.getDrawable(this@AccountSettingsActivity, R.drawable.bg_menu_ripple)
        isClickable = true
        setOnClickListener { onClick() }
        addView(ImageView(this@AccountSettingsActivity).apply {
            setImageResource(icon); setColorFilter(iconColor)
            background = GradientDrawable().apply {
                cornerRadius = 12 * dp
                setColor((iconColor and 0x00FFFFFF) or 0x24000000)
            }
            val p = (9 * dp).toInt(); setPadding(p, p, p, p)
        }, LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt()).apply { marginEnd = (14 * dp).toInt() })
        addView(LinearLayout(this@AccountSettingsActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@AccountSettingsActivity).apply {
                text = title; textSize = 16f; setTextColor(titleColor)
            })
            addView(TextView(this@AccountSettingsActivity).apply {
                text = subtitle; textSize = 13f; setTextColor(subColor)
                setPadding(0, (2 * dp).toInt(), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(ImageView(this@AccountSettingsActivity).apply {
            setImageResource(R.drawable.ic_chevron_right); setColorFilter(subColor)
        }, LinearLayout.LayoutParams((20 * dp).toInt(), (20 * dp).toInt()))
    }

    private fun sheetRoot(icon: Int, iconColor: Int, title: String, desc: String): Pair<LinearLayout, TextView> {
        val secondary = pl.sinusoide.sinuschat.ui.ThemeUi.textSecondary(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((24 * dp).toInt(), (12 * dp).toInt(), (24 * dp).toInt(), (20 * dp).toInt())
        }
        root.addView(View(this).apply {
            background = GradientDrawable().apply { cornerRadius = 3 * dp; setColor(pl.sinusoide.sinuschat.ui.ThemeUi.handle(context)) }
        }, LinearLayout.LayoutParams((36 * dp).toInt(), (4 * dp).toInt()).apply { bottomMargin = (20 * dp).toInt() })
        root.addView(ImageView(this).apply {
            setImageResource(icon); setColorFilter(iconColor)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor((iconColor and 0x00FFFFFF) or 0x26000000)
            }
            val p = (16 * dp).toInt(); setPadding(p, p, p, p)
        }, LinearLayout.LayoutParams((64 * dp).toInt(), (64 * dp).toInt()).apply { bottomMargin = (16 * dp).toInt() })
        root.addView(TextView(this).apply {
            text = title; textSize = 20f; setTextColor(pl.sinusoide.sinuschat.ui.ThemeUi.textPrimary(context))
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        })
        val descView = TextView(this).apply {
            text = desc; textSize = 14f; setTextColor(secondary); gravity = Gravity.CENTER
            setLineSpacing(0f, 1.2f)
            setPadding(0, (8 * dp).toInt(), 0, (18 * dp).toInt())
        }
        root.addView(descView)
        return root to descView
    }

    private fun sheetButton(text: String, color: Int, onClick: () -> Unit) = TextView(this).apply {
        this.text = text; textSize = 16f; setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        background = GradientDrawable().apply { cornerRadius = 26 * dp; setColor(color) }
        setPadding(0, (14 * dp).toInt(), 0, (14 * dp).toInt())
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun divider(cardColor: Int) = View(this).apply {
        setBackgroundColor(pl.sinusoide.sinuschat.ui.ThemeUi.divider(this@AccountSettingsActivity))
        layoutParams = LinearLayout.LayoutParams(-1, 1)
    }

    private fun autoDeleteSubtitle(): String =
        if (!AutoDelete.isOn(this)) getString(R.string.auto_delete_off)
        else getString(R.string.auto_delete_row_sub, AutoDelete.label(this, AutoDelete.seconds(this)))

    private fun refreshAutoDeleteRow() {
        val sub = (autoDeleteRow as? LinearLayout)?.getChildAt(1) as? LinearLayout ?: return
        (sub.getChildAt(1) as? TextView)?.text = autoDeleteSubtitle()
    }

    private fun showAutoDeleteSheet() {
        val accent = pl.sinusoide.sinuschat.ui.ThemeUi.accent(this)
        val primary = pl.sinusoide.sinuschat.ui.ThemeUi.textPrimary(this)
        val secondary = pl.sinusoide.sinuschat.ui.ThemeUi.textSecondary(this)
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.Theme_SinusChat_BottomSheet)
        val (root, _) = sheetRoot(R.drawable.ic_autodelete, accent,
            getString(R.string.auto_delete_title), getString(R.string.auto_delete_desc))

        val current = AutoDelete.seconds(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(pl.sinusoide.sinuschat.ui.ThemeUi.card(this@AccountSettingsActivity), 18f)
            clipToOutline = true
        }

        fun option(text: String, selected: Boolean, onClick: () -> Unit) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(this@AccountSettingsActivity, R.drawable.bg_menu_ripple)
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
            isClickable = true
            setOnClickListener { onClick() }
            addView(TextView(this@AccountSettingsActivity).apply {
                this.text = text; textSize = 15.5f
                setTextColor(if (selected) accent else primary)
                if (selected) typeface = Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams(0, -2, 1f))
            if (selected) addView(ImageView(this@AccountSettingsActivity).apply {
                setImageResource(R.drawable.ic_check); setColorFilter(accent)
            }, LinearLayout.LayoutParams((20 * dp).toInt(), (20 * dp).toInt()))
        }

        fun choose(sec: Long) {
            AutoDelete.setSeconds(this, sec)
            refreshAutoDeleteRow()
            sheet.dismiss()
            if (sec > 0L) PopupHelper.showSuccess(this,
                getString(R.string.auto_delete_set, AutoDelete.deadlineText(this)))
            else PopupHelper.showSuccess(this, getString(R.string.auto_delete_turned_off))
        }

        list.addView(option(getString(R.string.auto_delete_off), current == 0L) { choose(0L) })
        for (sec in AutoDelete.PRESETS) {
            list.addView(View(this).apply {
                setBackgroundColor(pl.sinusoide.sinuschat.ui.ThemeUi.divider(this@AccountSettingsActivity))
                layoutParams = LinearLayout.LayoutParams(-1, 1)
            })
            list.addView(option(AutoDelete.label(this, sec), current == sec) { choose(sec) })
        }
        list.addView(View(this).apply {
            setBackgroundColor(pl.sinusoide.sinuschat.ui.ThemeUi.divider(this@AccountSettingsActivity))
            layoutParams = LinearLayout.LayoutParams(-1, 1)
        })
        val customSelected = current > 0L && AutoDelete.PRESETS.none { it == current }
        list.addView(option(
            if (customSelected) getString(R.string.auto_delete_custom_value, AutoDelete.label(this, current))
            else getString(R.string.auto_delete_custom), customSelected) {
            sheet.dismiss()
            showAutoDeleteCustom()
        })
        root.addView(list, LinearLayout.LayoutParams(-1, -2))

        if (current > 0L) root.addView(TextView(this).apply {
            text = getString(R.string.auto_delete_deadline, AutoDelete.deadlineText(this@AccountSettingsActivity))
            textSize = 12.5f; setTextColor(secondary); gravity = Gravity.CENTER
            setPadding(0, (14 * dp).toInt(), 0, 0)
        })

        sheet.setContentView(root)
        pl.sinusoide.sinuschat.ui.FlowGlass.show(sheet)
        sheet.show()
    }

    private fun showAutoDeleteCustom() {
        val accent = pl.sinusoide.sinuschat.ui.ThemeUi.accent(this)
        val primary = pl.sinusoide.sinuschat.ui.ThemeUi.textPrimary(this)
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.Theme_SinusChat_BottomSheet)
        val (root, _) = sheetRoot(R.drawable.ic_autodelete, accent,
            getString(R.string.auto_delete_custom), getString(R.string.auto_delete_custom_desc))

        val input = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            textSize = 17f; setTextColor(primary)
            gravity = Gravity.CENTER
            background = rounded(pl.sinusoide.sinuschat.ui.ThemeUi.inset(this@AccountSettingsActivity), 16f)
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
            hint = "30"
            setHintTextColor(pl.sinusoide.sinuschat.ui.ThemeUi.textMuted(context))
        }
        root.addView(input, LinearLayout.LayoutParams(-1, -2))

        val units = listOf(
            AutoDelete.SECOND to R.string.unit_sec,
            AutoDelete.MINUTE to R.string.unit_min,
            AutoDelete.HOUR to R.string.unit_hour,
            AutoDelete.DAY to R.string.unit_day,
            AutoDelete.MONTH to R.string.unit_month,
            AutoDelete.YEAR to R.string.unit_year
        )
        var unit = AutoDelete.DAY
        val chips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (12 * dp).toInt(), 0, (4 * dp).toInt())
        }
        val chipViews = ArrayList<TextView>()
        fun paintChips() {
            for ((i, v) in chipViews.withIndex()) {
                val on = units[i].first == unit
                v.background = rounded(if (on) accent else pl.sinusoide.sinuschat.ui.ThemeUi.inset(this), 14f)
                v.setTextColor(if (on) Color.WHITE else primary)
            }
        }
        for ((value, nameRes) in units) {
            val chip = TextView(this).apply {
                text = getString(nameRes); textSize = 13f; gravity = Gravity.CENTER
                maxLines = 1
                setPadding((4 * dp).toInt(), (9 * dp).toInt(), (4 * dp).toInt(), (9 * dp).toInt())
                isClickable = true
                setOnClickListener { unit = value; paintChips() }
            }
            chipViews.add(chip)
            chips.addView(chip, LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = (2 * dp).toInt(); marginEnd = (2 * dp).toInt()
            })
        }
        paintChips()
        root.addView(chips, LinearLayout.LayoutParams(-1, -2))

        root.addView(sheetButton(getString(R.string.auto_delete_apply), accent) {
            val n = input.text.toString().trim().toLongOrNull() ?: 0L
            if (n <= 0L) {
                PopupHelper.showError(this, getString(R.string.auto_delete_bad_number))
                return@sheetButton
            }
            AutoDelete.setSeconds(this, n * unit)
            refreshAutoDeleteRow()
            sheet.dismiss()
            PopupHelper.showSuccess(this,
                getString(R.string.auto_delete_set, AutoDelete.deadlineText(this)))
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (16 * dp).toInt() })

        sheet.setContentView(root)
        pl.sinusoide.sinuschat.ui.FlowGlass.show(sheet)
        sheet.show()
    }

    private fun openDeleteAccount() {
        deleteAccountLauncher.launch(Intent(this, DeleteAccountActivity::class.java))
    }

    private fun refreshDeletionState() {
        showDeletionBanner(DeleteAccountActivity.localScheduledAt(this))
        AppExecutor.io {
            val resp = ApiClient.getObject("/api/user/deletion", this) ?: return@io
            val at = if (resp.isNull("scheduledAt")) 0L else resp.optLong("scheduledAt", 0L)
            DeleteAccountActivity.saveLocalScheduledAt(this, at)
            AppExecutor.main { if (!isFinishing) showDeletionBanner(at) }
        }
    }

    private fun showDeletionBanner(scheduledAt: Long) {
        if (scheduledAt <= 0L) { deletionBanner.visibility = View.GONE; return }
        val deleteAt = scheduledAt + DeleteAccountActivity.DELETION_DAYS * 86_400_000L
        val daysLeft = ((deleteAt - System.currentTimeMillis()) / 86_400_000L + 1).coerceAtLeast(0).toInt()
        val date = SimpleDateFormat("d MMMM", Locale.getDefault()).format(Date(deleteAt))
        deletionBannerText.text = resources.getQuantityString(
            R.plurals.account_deletion_banner, daysLeft, daysLeft, date)
        deletionBanner.visibility = View.VISIBLE
    }

    private fun cancelDeletionQuick() {
        AppExecutor.io {
            val resp = ApiClient.post("/api/user/deletion", org.json.JSONObject().put("cancel", true), this)
            AppExecutor.main {
                if (isFinishing) return@main
                if (resp?.optBoolean("ok") == true) {
                    DeleteAccountActivity.saveLocalScheduledAt(this, 0L)
                    showDeletionBanner(0L)
                    PopupHelper.showSuccess(this, getString(R.string.deletion_cancelled_success))
                } else {
                    PopupHelper.showError(this, getString(R.string.error_connection))
                }
            }
        }
    }
}
