package pl.sinusoide.sinuschat

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.materialswitch.MaterialSwitch
import pl.sinusoide.sinuschat.util.PopupHelper
import pl.sinusoide.sinuschat.util.ThemeManager
import pl.sinusoide.sinuschat.util.Translations

class TranslationSettingsActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(pl.sinusoide.sinuschat.util.LocaleManager.wrap(newBase))
    }

    private val dp get() = resources.displayMetrics.density

    private var cBg = 0; private var cSurface = 0; private var cCard = 0
    private var cPrimary = 0; private var cSecondary = 0; private var cAccent = 0
    private var cGreen = 0; private var cOrange = 0; private var cRed = 0

    private lateinit var langCard: LinearLayout
    private var blockedCard: LinearLayout? = null
    private val changed: () -> Unit = {
        if (!isFinishing && !isDestroyed) {
            refreshLangs()
            refreshBlocked()
        }
    }

    private fun attr(id: Int, fallback: Int): Int = pl.sinusoide.sinuschat.ui.ThemeUi.attrOrNull(this, id) ?: fallback

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

    override fun onStart() {
        super.onStart()
        Translations.addListener(changed)
        Translations.refresh(this)
        refreshLangs()
        refreshBlocked()
    }

    override fun onStop() {
        Translations.removeListener(changed)
        super.onStop()
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
            background = ContextCompat.getDrawable(this@TranslationSettingsActivity, R.drawable.bg_menu_ripple)
            contentDescription = getString(R.string.back)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt()))
        header.addView(TextView(this).apply {
            text = getString(R.string.translate_title); textSize = 20f
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

        if (!Translations.supported()) {
            content.addView(TextView(this).apply {
                text = getString(R.string.translate_unsupported)
                textSize = 14f; setTextColor(cOrange); gravity = Gravity.CENTER
                setLineSpacing(0f, 1.15f)
                background = rounded((cOrange and 0x00FFFFFF) or 0x1F000000, 16f)
                setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (16 * dp).toInt() })
            setContentView(outer)
            return
        }

        content.addView(sectionTitle(getString(R.string.translate_section_options)))
        content.addView(card().apply {
            addView(switchRow(R.drawable.ic_translate, cAccent, R.string.translate_enabled, R.string.translate_enabled_desc,
                Translations.enabledSetting(context)) { Translations.setEnabled(context, it) })
            addView(divider())
            addView(switchRow(R.drawable.ic_send, cGreen, R.string.translate_ask_send, R.string.translate_ask_send_desc,
                Translations.askSend(context)) { Translations.setAskSend(context, it) })
        })

        content.addView(sectionTitle(getString(R.string.translate_section_langs)))
        langCard = card()
        content.addView(langCard)
        langHint = TextView(this).apply {
            textSize = 12.5f; setTextColor(cSecondary)
            setLineSpacing(0f, 1.15f)
            setPadding((6 * dp).toInt(), (8 * dp).toInt(), (6 * dp).toInt(), 0)
        }
        content.addView(langHint)

        content.addView(sectionTitle(getString(R.string.translate_section_blocked)))
        blockedCard = card().also { content.addView(it) }

        content.addView(card().apply {
            addView(LinearLayout(this@TranslationSettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt())
                background = ContextCompat.getDrawable(this@TranslationSettingsActivity, R.drawable.bg_menu_ripple)
                isClickable = true
                setOnClickListener {
                    Translations.resetDecisions(this@TranslationSettingsActivity)
                    PopupHelper.showSuccess(this@TranslationSettingsActivity, getString(R.string.translate_reset_done))
                }
                addView(iconTile(R.drawable.ic_refresh, cOrange))
                addView(TextView(this@TranslationSettingsActivity).apply {
                    text = getString(R.string.translate_reset); textSize = 15.5f; setTextColor(cPrimary)
                }, LinearLayout.LayoutParams(0, -2, 1f))
            })
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (16 * dp).toInt() })

        content.addView(TextView(this).apply {
            text = getString(R.string.translate_footer)
            textSize = 12.5f; setTextColor(cSecondary); gravity = Gravity.CENTER
            setLineSpacing(0f, 1.2f)
            setPadding((12 * dp).toInt(), (20 * dp).toInt(), (12 * dp).toInt(), 0)
        })

        setContentView(outer)
    }

    private fun heroCard(): View {
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = rounded(cCard, 20f)
            setPadding((20 * dp).toInt(), (22 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt())
        }
        hero.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_translate)
            setColorFilter(cAccent)
            val p = (16 * dp).toInt(); setPadding(p, p, p, p)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor((cAccent and 0x00FFFFFF) or 0x26000000)
            }
        }, LinearLayout.LayoutParams((64 * dp).toInt(), (64 * dp).toInt()).apply { bottomMargin = (12 * dp).toInt() })
        hero.addView(TextView(this).apply {
            text = getString(R.string.translate_hero_title)
            textSize = 17f; setTextColor(cPrimary); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })
        hero.addView(TextView(this).apply {
            text = getString(R.string.translate_hero_desc)
            textSize = 13f; setTextColor(cSecondary); gravity = Gravity.CENTER
            setLineSpacing(0f, 1.15f)
            setPadding(0, (4 * dp).toInt(), 0, 0)
        })
        return hero
    }

    private var langList: pl.sinusoide.sinuschat.ui.TranslationLangList? = null
    private var langHint: TextView? = null

    private fun refreshLangs() {
        if (!::langCard.isInitialized) return
        val list = langList ?: pl.sinusoide.sinuschat.ui.TranslationLangList(this, langCard,
            cPrimary, cSecondary, cAccent, cGreen, cRed, cOrange).also { langList = it }
        list.refresh()
        langHint?.text = getString(R.string.translate_langs_hint, Translations.name(this, Translations.appLang(this)))
    }

    private fun switchRow(icon: Int, color: Int, titleRes: Int, descRes: Int, checked: Boolean,
                          onChange: (Boolean) -> Unit): View {
        val sw = MaterialSwitch(this).apply {
            isChecked = checked
            thumbTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Color.WHITE, 0xFF8AA0B8.toInt()))
            trackTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(cAccent, 0xFF2A3A4A.toInt()))
            setOnCheckedChangeListener { _, v -> onChange(v) }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt())
            background = ContextCompat.getDrawable(this@TranslationSettingsActivity, R.drawable.bg_menu_ripple)
            isClickable = true
            setOnClickListener { sw.toggle() }
            addView(iconTile(icon, color))
            addView(LinearLayout(this@TranslationSettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@TranslationSettingsActivity).apply {
                    text = getString(titleRes); textSize = 15.5f; setTextColor(cPrimary)
                })
                addView(TextView(this@TranslationSettingsActivity).apply {
                    text = getString(descRes); textSize = 12.5f; setTextColor(cSecondary)
                    setPadding(0, (2 * dp).toInt(), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = (8 * dp).toInt() })
            addView(sw)
        }
    }

    private fun refreshBlocked() {
        val box = blockedCard ?: return
        box.removeAllViews()
        val list = Translations.blockedChats(this)
        if (list.isEmpty()) {
            box.addView(TextView(this).apply {
                text = getString(R.string.translate_blocked_empty)
                textSize = 13.5f; setTextColor(cSecondary)
                setLineSpacing(0f, 1.15f)
                setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
            })
            return
        }
        list.forEachIndexed { i, b ->
            if (i > 0) box.addView(divider())
            val name = pl.sinusoide.sinuschat.util.ContactCache.getName(this, if (b.group) b.id else b.other)
                ?.takeIf { it.isNotBlank() }
                ?: b.name.ifBlank { getString(if (b.group) R.string.group else R.string.user_generic) }
            box.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((14 * dp).toInt(), (10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt())
                addView(iconTile(if (b.group) R.drawable.ic_group else R.drawable.ic_person, cAccent))
                addView(TextView(this@TranslationSettingsActivity).apply {
                    text = name
                    textSize = 15.5f; setTextColor(cPrimary)
                    maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = (8 * dp).toInt() })
                addView(ImageView(this@TranslationSettingsActivity).apply {
                    setImageResource(R.drawable.ic_close); setColorFilter(cSecondary)
                    val p = (10 * dp).toInt(); setPadding(p, p, p, p)
                    background = ContextCompat.getDrawable(this@TranslationSettingsActivity, R.drawable.bg_menu_ripple)
                    contentDescription = getString(R.string.translate_unblock)
                    setOnClickListener {
                        Translations.setBlocked(this@TranslationSettingsActivity, b.id, false)
                        PopupHelper.showSuccess(this@TranslationSettingsActivity, getString(R.string.translate_unblocked, name))
                    }
                }, LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt()))
            })
        }
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
}
