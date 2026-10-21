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
import pl.sinusoide.sinuschat.util.PrivacySettings
import pl.sinusoide.sinuschat.util.ThemeManager

class PrivacySettingsActivity : AppCompatActivity() {
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

    private var cBg = 0; private var cSurface = 0; private var cCard = 0
    private var cPrimary = 0; private var cSecondary = 0
    private var cAccent = 0; private var cGreen = 0; private var cRed = 0; private var cOrange = 0

    private lateinit var lockSubtitle: TextView
    private lateinit var lockPill: TextView
    private lateinit var torPill: TextView
    private lateinit var heroTitle: TextView
    private lateinit var heroIcon: ImageView
    private lateinit var options: pl.sinusoide.sinuschat.ui.PrivacyOptionsView

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
        cOrange = 0xFFE67E22.toInt()

        buildUi()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
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
            background = ContextCompat.getDrawable(this@PrivacySettingsActivity, R.drawable.bg_menu_ripple)
            contentDescription = getString(R.string.back)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt()))
        header.addView(TextView(this).apply {
            text = getString(R.string.privacy); textSize = 20f
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

        content.addView(heroCard())

        content.addView(sectionTitle(getString(R.string.priv_section_lock)))
        val lockCard = card()
        val lockRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt())
            background = ContextCompat.getDrawable(this@PrivacySettingsActivity, R.drawable.bg_menu_ripple)
            isClickable = true
            setOnClickListener { openScreenLock() }
        }
        lockRow.addView(iconTile(R.drawable.ic_lock, cAccent))
        val lockTexts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        lockTexts.addView(TextView(this).apply {
            text = getString(R.string.lock_settings_title); textSize = 16f; setTextColor(cPrimary)
        })
        lockSubtitle = TextView(this).apply {
            textSize = 13f; setTextColor(cSecondary); setPadding(0, (2 * dp).toInt(), 0, 0)
        }
        lockTexts.addView(lockSubtitle)
        lockRow.addView(lockTexts, LinearLayout.LayoutParams(0, -2, 1f))
        lockRow.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_chevron_right); setColorFilter(cSecondary)
        }, LinearLayout.LayoutParams((20 * dp).toInt(), (20 * dp).toInt()))
        lockCard.addView(lockRow)
        content.addView(lockCard)

        options = pl.sinusoide.sinuschat.ui.PrivacyOptionsView(this)
        options.showTorRoute = true
        content.addView(options, LinearLayout.LayoutParams(-1, -2))

        setContentView(outer)
    }

    private fun heroCard(): View {
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = rounded(cCard, 20f)
            setPadding((20 * dp).toInt(), (22 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt())
        }
        heroIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_shield)
            val p = (16 * dp).toInt(); setPadding(p, p, p, p)
        }
        hero.addView(heroIcon, LinearLayout.LayoutParams((64 * dp).toInt(), (64 * dp).toInt()).apply {
            bottomMargin = (12 * dp).toInt()
        })
        heroTitle = TextView(this).apply {
            textSize = 17f; setTextColor(cPrimary); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        hero.addView(heroTitle)
        hero.addView(TextView(this).apply {
            text = getString(R.string.priv_hero_desc)
            textSize = 13f; setTextColor(cSecondary); gravity = Gravity.CENTER
            setPadding(0, (4 * dp).toInt(), 0, (14 * dp).toInt())
        })
        val pills = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
        }
        lockPill = pill()
        torPill = pill()
        pills.addView(lockPill)
        pills.addView(torPill, LinearLayout.LayoutParams(-2, -2).apply { marginStart = (8 * dp).toInt() })
        hero.addView(pills)
        return hero
    }

    private fun refreshStatus() {
        val hasPin = LockManager.hasPin(this)
        val bio = hasPin && LockManager.isBiometricEnabled(this)
        val tor = PrivacySettings.isTorEnabled(this)

        lockSubtitle.text = when {
            bio -> getString(R.string.priv_lock_on_bio)
            hasPin -> getString(R.string.priv_lock_on_pin)
            else -> getString(R.string.priv_lock_off)
        }
        options.refresh()
        stylePill(lockPill, getString(if (hasPin) R.string.priv_pill_lock_on else R.string.priv_pill_lock_off),
            if (hasPin) cGreen else cOrange)
        stylePill(torPill, getString(if (tor) R.string.priv_pill_tor_on else R.string.priv_pill_tor_off),
            if (tor) cGreen else cSecondary)

        val color = if (hasPin) cGreen else cOrange
        heroIcon.setColorFilter(color)
        heroIcon.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor((color and 0x00FFFFFF) or 0x26000000)
        }
        heroTitle.text = getString(if (hasPin) R.string.priv_hero_protected else R.string.priv_hero_unprotected)
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

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text.uppercase()
        textSize = 12f; setTextColor(cAccent); typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.06f
        setPadding((6 * dp).toInt(), (22 * dp).toInt(), 0, (8 * dp).toInt())
    }

    private fun iconTile(icon: Int, color: Int) = ImageView(this).apply {
        setImageResource(icon); setColorFilter(color)
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
        setPadding((12 * dp).toInt(), (5 * dp).toInt(), (12 * dp).toInt(), (5 * dp).toInt())
    }

    private fun stylePill(pill: TextView, text: String, color: Int) {
        pill.text = text
        pill.setTextColor(color)
        pill.background = GradientDrawable().apply {
            cornerRadius = 14 * dp
            setColor((color and 0x00FFFFFF) or 0x24000000)
        }
    }

    private fun openScreenLock() {
        if (LockManager.hasPin(this)) {
            requireAuth { startActivity(Intent(this, ScreenLockSettingsActivity::class.java)) }
        } else {
            ScreenLockActivity.start(this, ScreenLockActivity.MODE_SET, openSettings = true)
        }
    }

    private fun requireAuth(onSuccess: () -> Unit) {
        if (!LockManager.hasPin(this)) { onSuccess(); return }
        onAuthSuccess = onSuccess
        val askPin = {
            authLauncher.launch(Intent(this, ScreenLockActivity::class.java)
                .putExtra(ScreenLockActivity.EXTRA_MODE, ScreenLockActivity.MODE_CONFIRM))
        }
        if (LockManager.isBiometricEnabled(this) && LockManager.isBiometricAvailable(this)) {
            LockManager.showBiometricPrompt(this,
                onSuccess = { onAuthSuccess = null; onSuccess() },
                onFailed = { askPin() })
        } else {
            askPin()
        }
    }
}
