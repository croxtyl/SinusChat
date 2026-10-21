package pl.sinusoide.sinuschat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
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
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.materialswitch.MaterialSwitch
import pl.sinusoide.sinuschat.crypto.DbCrypto
import pl.sinusoide.sinuschat.crypto.DbKeyManager
import pl.sinusoide.sinuschat.crypto.E2EManager
import pl.sinusoide.sinuschat.util.PopupHelper
import pl.sinusoide.sinuschat.util.ThemeManager

class ScreenLockSettingsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FROM_SETUP = "from_setup"
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(pl.sinusoide.sinuschat.util.LocaleManager.wrap(newBase))
    }

    private val dp get() = resources.displayMetrics.density

    private var onAuthSuccess: (() -> Unit)? = null
    private val authLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val action = onAuthSuccess
        onAuthSuccess = null
        if (result.resultCode == RESULT_OK) action?.invoke()
    }

    private val migrationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            runOnUiThread { refresh() }
        }
    }

    private var cBg = 0; private var cSurface = 0; private var cCard = 0
    private var cPrimary = 0; private var cSecondary = 0
    private var cAccent = 0; private var cGreen = 0; private var cRed = 0; private var cYellow = 0

    private lateinit var encIcon: ImageView
    private lateinit var encTitle: TextView
    private lateinit var encDesc: TextView
    private lateinit var pillBio: TextView
    private lateinit var pillDuress: TextView
    private lateinit var bioCard: View
    private lateinit var bioSwitch: MaterialSwitch
    private lateinit var bioRow: View
    private lateinit var bioDesc: TextView
    private lateinit var duressIcon: ImageView
    private lateinit var duressTitle: TextView
    private lateinit var duressRemoveRow: View
    private lateinit var duressRemoveDivider: View
    private lateinit var wipeSwitch: MaterialSwitch
    private lateinit var autoSwitch: MaterialSwitch
    private lateinit var shuffleSwitch: MaterialSwitch
    private lateinit var silentRow: LinearLayout
    private lateinit var silentSwitch: MaterialSwitch
    private lateinit var relockDesc: TextView

    private fun attr(id: Int, fallback: Int): Int {
        val tv = android.util.TypedValue()
        return pl.sinusoide.sinuschat.ui.ThemeUi.attrOrNull(this, id) ?: fallback
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pl.sinusoide.sinuschat.util.SettingsHighlight.apply(this)

        cBg = attr(R.attr.sinuschat_background, 0xFF0E1621.toInt())
        cSurface = attr(R.attr.sinuschat_surface, 0xFF17212B.toInt())
        cCard = attr(R.attr.sinuschat_surface_elevated, 0xFF1D2A38.toInt())
        cPrimary = attr(R.attr.sinuschat_text_primary, Color.WHITE)
        cSecondary = attr(R.attr.sinuschat_text_secondary, 0xFF7F91A4.toInt())
        cAccent = pl.sinusoide.sinuschat.ui.ThemeUi.accent(this)
        cGreen = ContextCompat.getColor(this, R.color.sinuschat_success)
        cRed = ContextCompat.getColor(this, R.color.sinuschat_error)
        cYellow = 0xFFE0A030.toInt()

        buildUi()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(migrationReceiver,
                IntentFilter("pl.sinusoide.sinuschat.DB_MIGRATION_STATUS_CHANGED"), RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(migrationReceiver, IntentFilter("pl.sinusoide.sinuschat.DB_MIGRATION_STATUS_CHANGED"))
        }
    }

    override fun onResume() {
        super.onResume()
        if (!LockManager.hasPin(this)) { finish(); return }
        refresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(migrationReceiver) } catch (_: Exception) {}
    }

    private fun buildUi() {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(cBg)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = (56 * dp).toInt()
            setBackgroundColor(cSurface)
            elevation = 2 * dp
        }
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_back); setColorFilter(cPrimary)
            val p = (10 * dp).toInt(); setPadding(p, p, p, p)
            background = ContextCompat.getDrawable(this@ScreenLockSettingsActivity, R.drawable.bg_menu_ripple)
            contentDescription = getString(R.string.back)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt()))
        header.addView(TextView(this).apply {
            text = getString(R.string.lock_settings_title); textSize = 20f
            setTextColor(cPrimary); typeface = Typeface.DEFAULT_BOLD
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
            setPadding((16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt(), (28 * dp).toInt())
        }
        val scroll = ScrollView(this).apply { addView(content) }
        outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            v.setPadding(0, 0, 0, insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom); insets
        }

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = rounded(cCard, 20f)
            setPadding((20 * dp).toInt(), (22 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt())
        }
        encIcon = ImageView(this).apply { val p = (14 * dp).toInt(); setPadding(p, p, p, p) }
        hero.addView(encIcon, LinearLayout.LayoutParams((64 * dp).toInt(), (64 * dp).toInt()).apply {
            bottomMargin = (12 * dp).toInt()
        })
        encTitle = TextView(this).apply {
            textSize = 17f; setTextColor(cPrimary); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        }
        hero.addView(encTitle)
        encDesc = TextView(this).apply {
            textSize = 13f; setTextColor(cSecondary); gravity = Gravity.CENTER
            setLineSpacing(0f, 1.15f)
            setPadding(0, (4 * dp).toInt(), 0, (14 * dp).toInt())
        }
        hero.addView(encDesc)
        val pills = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val pillPin = pill().also { stylePill(it, getString(R.string.lock_pill_pin), cGreen) }
        pillBio = pill()
        pillDuress = pill()
        pills.addView(pillPin)
        pills.addView(pillBio, LinearLayout.LayoutParams(-2, -2).apply { marginStart = (6 * dp).toInt() })
        pills.addView(pillDuress, LinearLayout.LayoutParams(-2, -2).apply { marginStart = (6 * dp).toInt() })
        hero.addView(android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(pills)
        })
        content.addView(hero)

        content.addView(sectionTitle(getString(R.string.lock_section_pin), cAccent))
        content.addView(card().apply {
            addView(navRow(R.drawable.ic_changecode, cAccent, getString(R.string.lock_change_pin),
                getString(R.string.lock_change_pin_desc), cPrimary) {
                ScreenLockActivity.start(this@ScreenLockSettingsActivity, ScreenLockActivity.MODE_SET)
            })
            addView(View(this@ScreenLockSettingsActivity).apply {
                setBackgroundColor(pl.sinusoide.sinuschat.ui.ThemeUi.divider(context))
                layoutParams = LinearLayout.LayoutParams(-1, (1 * dp).toInt().coerceAtLeast(1)).apply {
                    marginStart = (68 * dp).toInt()
                }
            })
            val dotsRow = switchRow(R.drawable.ic_visibility, cAccent,
                getString(R.string.lock_show_dots), getString(R.string.lock_show_dots_desc)) { checked ->
                LockManager.setShowPinDots(this@ScreenLockSettingsActivity, checked)
            }
            dotsRow.findViewWithTag<MaterialSwitch>("switch")?.apply {
                tag = "sync"; isChecked = LockManager.isShowPinDots(this@ScreenLockSettingsActivity); tag = "switch"
            }
            addView(dotsRow)
            addView(divider())
            val shuffleRow = switchRow(R.drawable.ic_dialpad, cAccent,
                getString(R.string.lock_shuffle_title), getString(R.string.lock_shuffle_desc)) { checked ->
                if (checked) {
                    setSilently(shuffleSwitch, false)
                    startActivity(Intent(this@ScreenLockSettingsActivity, ScreenLockActivity::class.java)
                        .putExtra(ScreenLockActivity.EXTRA_MODE, ScreenLockActivity.MODE_SHUFFLE_TEST))
                } else {
                    LockManager.setShuffleKeypad(this@ScreenLockSettingsActivity, false)
                }
            }
            shuffleSwitch = shuffleRow.findViewWithTag("switch")
            addView(shuffleRow)
            addView(divider())
            val autoRow = switchRow(R.drawable.ic_check, cAccent,
                getString(R.string.lock_auto_confirm), getString(R.string.lock_auto_confirm_desc)) { checked ->
                LockManager.setAutoConfirm(this@ScreenLockSettingsActivity, checked)
                refresh()
            }
            autoSwitch = autoRow.findViewWithTag("switch")
            addView(autoRow)
            addView(divider())
            silentRow = switchRow(R.drawable.ic_lock, cAccent,
                getString(R.string.lock_silent_title), getString(R.string.lock_silent_desc)) { checked ->
                LockManager.setSilentUnlock(this@ScreenLockSettingsActivity, checked)
            }
            silentSwitch = silentRow.findViewWithTag("switch")
            addView(silentRow)
        })

        content.addView(sectionTitle(getString(R.string.lock_section_auto), cAccent))
        content.addView(card().apply {
            val row = navRow(R.drawable.ic_lock_clock, cAccent, getString(R.string.lock_auto_title), "", cPrimary) {
                chooseRelock()
            }
            relockDesc = row.findViewWithTag("subtitle")
            addView(row)
        })

        val bioTitle = sectionTitle(getString(R.string.lock_biometrics), cAccent)
        content.addView(bioTitle)
        val bioRowView = switchRow(R.drawable.ic_fingerprint, cAccent,
            getString(R.string.lock_biometrics_desc), getString(R.string.lock_biometrics_more)) { checked ->
            onBiometricToggled(checked)
        }
        bioRow = bioRowView
        bioSwitch = bioRowView.findViewWithTag("switch")
        bioDesc = bioRowView.findViewWithTag("subtitle")
        bioCard = card().apply { addView(bioRowView) }
        content.addView(bioCard)
        if (!LockManager.isHardwareDetected(this)) {
            bioTitle.visibility = View.GONE
            bioCard.visibility = View.GONE
        }

        content.addView(sectionTitle(getString(R.string.lock_section_emergency), cYellow))
        content.addView(card().apply {
            val duressRow = navRow(R.drawable.ic_duresspin, cYellow, getString(R.string.lock_set_duress),
                getString(R.string.lock_duress_desc), cPrimary) { onDuressClicked() }
            duressIcon = duressRow.findViewWithTag("icon")
            duressTitle = duressRow.findViewWithTag("title")
            addView(duressRow)

            duressRemoveDivider = divider()
            addView(duressRemoveDivider)
            duressRemoveRow = navRow(R.drawable.ic_duresspinoff, cRed, getString(R.string.lock_disable_duress),
                getString(R.string.lock_disable_duress_desc), cRed) { onRemoveDuress() }
            addView(duressRemoveRow)

            addView(divider())
            val wipeRow = switchRow(R.drawable.ic_danger, cRed,
                getString(R.string.reset_after_10_attempts), getString(R.string.reset_after_10_attempts_desc)) { checked ->
                onWipeToggled(checked)
            }
            wipeSwitch = wipeRow.findViewWithTag("switch")
            addView(wipeRow)
        })

        content.addView(sectionTitle(getString(R.string.account_danger_zone), cRed))
        content.addView(card().apply {
            addView(navRow(R.drawable.ic_codeoff, cRed, getString(R.string.lock_disable_pin),
                getString(R.string.lock_disable_pin_desc), cRed) { confirmDisableLock() })
        })

        if (intent.getBooleanExtra(EXTRA_FROM_SETUP, false)) {
            content.addView(TextView(this).apply {
                text = getString(R.string.next)
                textSize = 16f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    cornerRadius = 26 * dp
                    setColor(cAccent)
                }
                setPadding(0, (15 * dp).toInt(), 0, (15 * dp).toInt())
                isClickable = true
                setOnClickListener {
                    setResult(RESULT_OK)
                    finish()
                }
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = (18 * dp).toInt()
                }
            })
        }

        setContentView(outer)
    }

    private fun refresh() {
        val hasHardware = LockManager.isHardwareDetected(this)
        if (hasHardware) {
            val available = LockManager.isBiometricAvailable(this)
            val enabled = LockManager.isBiometricEnabled(this)
            setSilently(bioSwitch, enabled)
            val usable = available || enabled
            bioSwitch.isEnabled = usable
            bioRow.isEnabled = usable
            bioRow.alpha = if (usable) 1f else 0.6f
            bioDesc.text = getString(if (usable) R.string.lock_biometrics_more else R.string.lock_biometrics_setup_hint)
            bioDesc.setTextColor(if (usable) cSecondary else cYellow)
            stylePill(pillBio, getString(if (enabled) R.string.lock_pill_bio_on else R.string.lock_pill_bio_off),
                if (enabled) cGreen else cSecondary)
            pillBio.visibility = View.VISIBLE
        } else {
            pillBio.visibility = View.GONE
        }

        val duress = LockManager.hasDuressPin(this)
        duressTitle.text = getString(if (duress) R.string.lock_change_duress else R.string.lock_set_duress)
        duressIcon.setImageResource(if (duress) R.drawable.ic_duresspinchange else R.drawable.ic_duresspin)
        duressRemoveRow.visibility = if (duress) View.VISIBLE else View.GONE
        duressRemoveDivider.visibility = duressRemoveRow.visibility
        stylePill(pillDuress, getString(if (duress) R.string.lock_pill_duress_on else R.string.lock_pill_duress_off),
            if (duress) cYellow else cSecondary)

        setSilently(wipeSwitch, LockManager.isWipeOnFailEnabled(this))

        val auto = LockManager.isAutoConfirmEnabled(this)
        setSilently(autoSwitch, auto)
        setSilently(silentSwitch, LockManager.isSilentUnlock(this))
        setSilently(shuffleSwitch, LockManager.isShuffleKeypad(this))
        silentSwitch.isEnabled = auto
        silentRow.isEnabled = auto
        silentRow.alpha = if (auto) 1f else 0.5f

        val after = resources.getStringArray(R.array.lock_auto_after)
        val i = LockManager.RELOCK_OPTIONS.indexOf(LockManager.relockAfterMs(this))
        relockDesc.text = getString(R.string.lock_auto_desc, after[i.coerceAtLeast(0)])

        val dbEncrypted = DbCrypto.isDbEncrypted(this) || !DbCrypto.dbExists(this)
        val keyEnabled = DbKeyManager.isEnabled(this)
        val migrationError = DbKeyManager.getMigrationError(this)
        val migrating = DbKeyManager.isMigrating(this)
        val e2e = E2EManager.encryptionStatus(this)
        val (icon, color, title, desc) = when {
            migrating -> Quad(R.drawable.ic_clock, cAccent, getString(R.string.lock_enc_title_migrating),
                getString(R.string.enc_status_migrating))
            migrationError != null -> Quad(R.drawable.ic_encryptionerror, cYellow, getString(R.string.lock_enc_title_error),
                getString(R.string.enc_status_error, migrationError))
            keyEnabled && !dbEncrypted -> Quad(R.drawable.ic_encryptionerror, cYellow, getString(R.string.lock_enc_title_error),
                getString(R.string.enc_status_db_error))
            e2e.state == E2EManager.EncState.ERROR -> Quad(R.drawable.ic_encryptionerror, cYellow,
                getString(R.string.lock_enc_title_error), getString(R.string.enc_status_error, e2e.error ?: ""))
            dbEncrypted && keyEnabled -> Quad(R.drawable.ic_encrypted, cGreen, getString(R.string.enc_status_on),
                getString(if (LockManager.hasPin(this)) R.string.lock_enc_on_desc else R.string.lock_enc_on_desc_nopin))
            else -> Quad(R.drawable.ic_noencypted, cRed, getString(R.string.enc_status_off),
                getString(R.string.lock_enc_off_desc))
        }
        encIcon.setImageResource(icon)
        encIcon.setColorFilter(color)
        encIcon.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor((color and 0x00FFFFFF) or 0x26000000)
        }
        encTitle.text = title
        encDesc.text = desc
    }

    private data class Quad(val icon: Int, val color: Int, val title: String, val desc: String)

    private fun onBiometricToggled(checked: Boolean) {
        if (!checked) {
            LockManager.setBiometric(this, false)
            refresh()
            return
        }
        if (!LockManager.isBiometricAvailable(this)) {
            PopupHelper.showError(this, getString(R.string.no_biometrics))
            setSilently(bioSwitch, false)
            return
        }
        LockManager.showBiometricPrompt(this,
            onSuccess = { LockManager.setBiometric(this, true); refresh() },
            onFailed = { setSilently(bioSwitch, false) })
    }

    private fun onDuressClicked() {
        if (LockManager.hasDuressPin(this)) {
            onAuthSuccess = { ScreenLockActivity.start(this, ScreenLockActivity.MODE_SET_DURESS) }
            authLauncher.launch(Intent(this, ScreenLockActivity::class.java)
                .putExtra(ScreenLockActivity.EXTRA_MODE, ScreenLockActivity.MODE_CONFIRM_DURESS))
        } else {
            ScreenLockActivity.start(this, ScreenLockActivity.MODE_SET_DURESS)
        }
    }

    private fun onRemoveDuress() {
        onAuthSuccess = {
            LockManager.removeDuressPin(this)
            refresh()
            PopupHelper.showSuccess(this, getString(R.string.lock_duress_disabled_msg))
        }
        authLauncher.launch(Intent(this, ScreenLockActivity::class.java)
            .putExtra(ScreenLockActivity.EXTRA_MODE, ScreenLockActivity.MODE_CONFIRM_DURESS))
    }

    private fun onWipeToggled(checked: Boolean) {
        if (!checked) {
            LockManager.setWipeOnFail(this, false)
            return
        }
        setSilently(wipeSwitch, false)
        confirmSheet(
            icon = R.drawable.ic_danger, color = cRed,
            title = getString(R.string.enable_reset_10_q),
            desc = getString(R.string.lock_wipe_confirm_desc),
            action = getString(R.string.enable)
        ) {
            LockManager.setWipeOnFail(this, true)
            setSilently(wipeSwitch, true)
        }
    }

    private fun confirmDisableLock() {
        confirmSheet(
            icon = R.drawable.ic_codeoff, color = cRed,
            title = getString(R.string.lock_confirm_disable_title),
            desc = getString(R.string.lock_confirm_disable_desc_full),
            action = getString(R.string.lock_disable_pin)
        ) {
            val appCtx = applicationContext
            pl.sinusoide.sinuschat.util.AppExecutor.io {
                val ok = !DbKeyManager.isEnabled(appCtx) ||
                    (DbKeyManager.cachedKey() != null && DbKeyManager.rewrapWithCachedKey(appCtx, ""))
                if (ok) LockManager.removePin(appCtx)
                runOnUiThread {
                    if (ok) {
                        PopupHelper.showSuccess(this, getString(R.string.lock_disabled_msg))
                        finish()
                    } else {
                        PopupHelper.showError(this, getString(R.string.lock_disable_failed))
                    }
                }
            }
        }
    }

    private fun chooseRelock() {
        val sheet = BottomSheetDialog(this, R.style.Theme_SinusChat_BottomSheet)
        val root = sheetHead(R.drawable.ic_lock_clock, cAccent, getString(R.string.lock_auto_title),
            getString(R.string.lock_auto_sheet_desc))
        val labels = resources.getStringArray(R.array.lock_auto_options)
        val current = LockManager.relockAfterMs(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(cCard, 18f)
            clipToOutline = true
        }
        for ((i, ms) in LockManager.RELOCK_OPTIONS.withIndex()) {
            if (i > 0) list.addView(View(this).apply {
                setBackgroundColor(pl.sinusoide.sinuschat.ui.ThemeUi.divider(context))
                layoutParams = LinearLayout.LayoutParams(-1, (1 * dp).toInt().coerceAtLeast(1))
            })
            val selected = ms == current
            list.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = ContextCompat.getDrawable(this@ScreenLockSettingsActivity, R.drawable.bg_menu_ripple)
                setPadding((16 * dp).toInt(), (13 * dp).toInt(), (16 * dp).toInt(), (13 * dp).toInt())
                isClickable = true
                setOnClickListener {
                    LockManager.setRelockAfterMs(this@ScreenLockSettingsActivity, ms)
                    sheet.dismiss()
                    refresh()
                }
                addView(TextView(this@ScreenLockSettingsActivity).apply {
                    text = labels[i]; textSize = 15.5f
                    setTextColor(if (selected) cAccent else cPrimary)
                    if (selected) typeface = Typeface.DEFAULT_BOLD
                }, LinearLayout.LayoutParams(0, -2, 1f))
                if (selected) addView(ImageView(this@ScreenLockSettingsActivity).apply {
                    setImageResource(R.drawable.ic_check); setColorFilter(cAccent)
                }, LinearLayout.LayoutParams((20 * dp).toInt(), (20 * dp).toInt()))
            }, LinearLayout.LayoutParams(-1, -2))
        }
        root.addView(list, LinearLayout.LayoutParams(-1, -2))
        sheet.setContentView(root)
        pl.sinusoide.sinuschat.ui.FlowGlass.show(sheet)
    }

    private fun setSilently(sw: MaterialSwitch, value: Boolean) {
        if (sw.isChecked == value) return
        sw.tag = "sync"; sw.isChecked = value; sw.tag = "switch"
    }

    private fun rounded(color: Int, radiusDp: Float) = pl.sinusoide.sinuschat.ui.ThemeUi.rounded(this, color, radiusDp)

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(cCard, 18f)
        clipToOutline = true
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(pl.sinusoide.sinuschat.ui.ThemeUi.divider(context))
        layoutParams = LinearLayout.LayoutParams(-1, (1 * dp).toInt().coerceAtLeast(1)).apply {
            marginStart = (68 * dp).toInt()
        }
    }

    private fun sectionTitle(text: String, color: Int) = TextView(this).apply {
        this.text = text.uppercase()
        textSize = 12f; setTextColor(color); typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.06f
        setPadding((6 * dp).toInt(), (22 * dp).toInt(), 0, (8 * dp).toInt())
    }

    private fun iconTile(icon: Int, color: Int) = ImageView(this).apply {
        setImageResource(icon); setColorFilter(color)
        tag = "icon"
        background = GradientDrawable().apply {
            cornerRadius = 12 * dp
            setColor((color and 0x00FFFFFF) or 0x24000000)
        }
        val p = (9 * dp).toInt(); setPadding(p, p, p, p)
        layoutParams = LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt()).apply {
            marginEnd = (14 * dp).toInt()
        }
    }

    private fun pill() = TextView(this).apply {
        textSize = 12f; typeface = Typeface.DEFAULT_BOLD
        setPadding((11 * dp).toInt(), (5 * dp).toInt(), (11 * dp).toInt(), (5 * dp).toInt())
    }

    private fun stylePill(pill: TextView, text: String, color: Int) {
        pill.text = text
        pill.setTextColor(color)
        pill.background = GradientDrawable().apply {
            cornerRadius = 14 * dp
            setColor((color and 0x00FFFFFF) or 0x24000000)
        }
    }

    private fun texts(title: String, desc: String, titleColor: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(this@ScreenLockSettingsActivity).apply {
            text = title; textSize = 15.5f; setTextColor(titleColor); tag = "title"
        })
        addView(TextView(this@ScreenLockSettingsActivity).apply {
            text = desc; textSize = 12.5f; setTextColor(cSecondary); tag = "subtitle"
            setPadding(0, (2 * dp).toInt(), 0, 0)
        })
    }

    private fun navRow(icon: Int, iconColor: Int, title: String, desc: String, titleColor: Int, onClick: () -> Unit) =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * dp).toInt(), (13 * dp).toInt(), (12 * dp).toInt(), (13 * dp).toInt())
            background = ContextCompat.getDrawable(this@ScreenLockSettingsActivity, R.drawable.bg_menu_ripple)
            isClickable = true
            setOnClickListener { onClick() }
            addView(iconTile(icon, iconColor))
            addView(texts(title, desc, titleColor), LinearLayout.LayoutParams(0, -2, 1f))
            addView(ImageView(this@ScreenLockSettingsActivity).apply {
                setImageResource(R.drawable.ic_chevron_right); setColorFilter(cSecondary)
            }, LinearLayout.LayoutParams((20 * dp).toInt(), (20 * dp).toInt()))
        }

    private fun switchRow(icon: Int, iconColor: Int, title: String, desc: String, onChange: (Boolean) -> Unit): LinearLayout {
        val sw = MaterialSwitch(this).apply {
            tag = "switch"
            thumbTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Color.WHITE, 0xFF8AA0B8.toInt()))
            trackTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(cAccent, 0xFF2A3A4A.toInt()))
            setOnCheckedChangeListener { v, checked -> if (v.tag != "sync") onChange(checked) }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt())
            background = ContextCompat.getDrawable(this@ScreenLockSettingsActivity, R.drawable.bg_menu_ripple)
            isClickable = true
            setOnClickListener { if (sw.isEnabled) sw.isChecked = !sw.isChecked }
            addView(iconTile(icon, iconColor))
            addView(texts(title, desc, cPrimary), LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginEnd = (8 * dp).toInt()
            })
            addView(sw)
        }
    }

    private fun confirmSheet(icon: Int, color: Int, title: String, desc: String, action: String, onConfirm: () -> Unit) {
        val sheet = BottomSheetDialog(this, R.style.Theme_SinusChat_BottomSheet)
        val root = sheetHead(icon, color, title, desc)
        root.addView(TextView(this).apply {
            text = action; textSize = 16f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = 26 * dp; setColor(color) }
            setPadding(0, (14 * dp).toInt(), 0, (14 * dp).toInt())
            isClickable = true
            setOnClickListener { sheet.dismiss(); onConfirm() }
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(TextView(this).apply {
            text = getString(R.string.cancel); textSize = 15f; setTextColor(cSecondary); gravity = Gravity.CENTER
            setPadding(0, (14 * dp).toInt(), 0, (4 * dp).toInt())
            isClickable = true
            setOnClickListener { sheet.dismiss() }
        }, LinearLayout.LayoutParams(-1, -2))
        sheet.setContentView(root)
        pl.sinusoide.sinuschat.ui.FlowGlass.show(sheet)
    }

    private fun sheetHead(icon: Int, color: Int, title: String, desc: String): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((24 * dp).toInt(), (12 * dp).toInt(), (24 * dp).toInt(), (20 * dp).toInt())
        }
        root.addView(View(this).apply {
            background = GradientDrawable().apply { cornerRadius = 3 * dp; setColor(pl.sinusoide.sinuschat.ui.ThemeUi.handle(context)) }
        }, LinearLayout.LayoutParams((36 * dp).toInt(), (4 * dp).toInt()).apply { bottomMargin = (20 * dp).toInt() })
        root.addView(ImageView(this).apply {
            setImageResource(icon); setColorFilter(color)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor((color and 0x00FFFFFF) or 0x26000000)
            }
            val p = (16 * dp).toInt(); setPadding(p, p, p, p)
        }, LinearLayout.LayoutParams((64 * dp).toInt(), (64 * dp).toInt()).apply { bottomMargin = (16 * dp).toInt() })
        root.addView(TextView(this).apply {
            text = title; textSize = 20f; setTextColor(pl.sinusoide.sinuschat.ui.ThemeUi.textPrimary(context)); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = desc; textSize = 14f; setTextColor(cSecondary); gravity = Gravity.CENTER
            setLineSpacing(0f, 1.2f)
            setPadding(0, (8 * dp).toInt(), 0, (22 * dp).toInt())
        })
        return root
    }
}
