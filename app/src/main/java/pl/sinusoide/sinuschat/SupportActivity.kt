package pl.sinusoide.sinuschat

import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import pl.sinusoide.sinuschat.ui.ThemeUi
import pl.sinusoide.sinuschat.util.PopupHelper
import pl.sinusoide.sinuschat.util.SafeEngine
import pl.sinusoide.sinuschat.util.ThemeManager

class SupportActivity : AppCompatActivity() {

    private companion object {
        const val PATREON_URL = "https://www.patreon.com/cw/sinusoidetechnology"
        const val KOFI_URL = "https://ko-fi.com/sinusoide"
        const val PAGE_URL = "https://sinusoide.pl/wsparcie.html"
        const val RECIPIENT = "Błażej Stosio"
        const val ACCOUNT = "57 1140 2004 0000 3702 8433 6549"
        const val IBAN = "PL57 1140 2004 0000 3702 8433 6549"
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(pl.sinusoide.sinuschat.util.LocaleManager.wrap(newBase))
    }

    private val dp get() = resources.displayMetrics.density
    private fun px(v: Int) = (v * dp).toInt()

    private var cBg = 0; private var cSurface = 0; private var cCard = 0
    private var cPrimary = 0; private var cSecondary = 0; private var cAccent = 0
    private val cRed = 0xFFE5484D.toInt()
    private val cOrange = 0xFFE67E22.toInt()
    private val cGreen = 0xFF3FB97A.toInt()
    private val cPurple = 0xFF9B6BE0.toInt()
    private val cTeal = 0xFF26A5B8.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        cBg = ThemeUi.attr(this, R.attr.sinuschat_background, 0xFF0E1621.toInt())
        cSurface = ThemeUi.surface(this)
        cCard = ThemeUi.card(this)
        cPrimary = ThemeUi.textPrimary(this)
        cSecondary = ThemeUi.textSecondary(this)
        cAccent = ThemeUi.accent(this)
        buildUi()
    }

    private fun buildUi() {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(cBg)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = px(56)
            setBackgroundColor(cSurface)
            elevation = 2 * dp
        }
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_back); setColorFilter(cPrimary)
            val p = px(10); setPadding(p, p, p, p)
            background = ContextCompat.getDrawable(this@SupportActivity, R.drawable.bg_menu_ripple)
            contentDescription = getString(R.string.back)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(px(44), px(44)))
        header.addView(TextView(this).apply {
            text = getString(R.string.support_screen_title); textSize = 20f
            setTextColor(cPrimary); typeface = Typeface.DEFAULT_BOLD
            setPadding(px(8), 0, 0, 0)
        })
        outer.addView(header)
        ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(px(12), bars.top, px(12), 0)
            insets
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), px(16), px(16), px(28))
        }
        val scroll = ScrollView(this).apply { addView(content) }
        outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            v.setPadding(0, 0, 0, insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom); insets
        }

        content.addView(hero())
        content.addView(sectionTitle(getString(R.string.support_section_ways)))
        content.addView(methodCard(R.drawable.ic_patreon, cOrange, getString(R.string.support_patreon_title),
            getString(R.string.support_badge_monthly), getString(R.string.support_patreon_desc),
            getString(R.string.support_patreon_button), getString(R.string.support_patreon_note), PATREON_URL))
        content.addView(space(12))
        content.addView(bankCard())
        content.addView(space(12))
        content.addView(methodCard(R.drawable.ic_heart, cRed, getString(R.string.support_kofi_title),
            getString(R.string.support_badge_once), getString(R.string.support_kofi_desc),
            getString(R.string.support_kofi_button), getString(R.string.support_kofi_note), KOFI_URL))

        content.addView(sectionTitle(getString(R.string.support_section_costs)))
        content.addView(TextView(this).apply {
            text = getString(R.string.support_costs_intro)
            textSize = 13.5f; setTextColor(cSecondary); setLineSpacing(0f, 1.15f)
            setPadding(px(4), 0, px(4), px(10))
        })
        content.addView(card().apply {
            val items = listOf(
                Triple(R.drawable.ic_harddrive, R.string.support_cost_server, R.string.support_cost_server_desc),
                Triple(R.drawable.ic_call_video_on, R.string.support_cost_calls, R.string.support_cost_calls_desc),
                Triple(R.drawable.ic_storage, R.string.support_cost_files, R.string.support_cost_files_desc),
                Triple(R.drawable.ic_notifications, R.string.support_cost_push, R.string.support_cost_push_desc),
                Triple(R.drawable.ic_internet, R.string.support_cost_domain, R.string.support_cost_domain_desc),
                Triple(R.drawable.ic_code, R.string.support_cost_dev, R.string.support_cost_dev_desc)
            )
            val colors = listOf(cAccent, cGreen, cPurple, cOrange, cTeal, cRed)
            items.forEachIndexed { i, (icon, title, desc) ->
                if (i > 0) addView(divider())
                addView(infoRow(icon, colors[i], getString(title), getString(desc)))
            }
        })
        content.addView(TextView(this).apply {
            text = getString(R.string.support_thanks)
            textSize = 13.5f; setTextColor(cSecondary); gravity = Gravity.CENTER
            setLineSpacing(0f, 1.15f)
            setPadding(px(8), px(18), px(8), 0)
        })

        setContentView(outer)
    }

    private fun hero(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        background = ThemeUi.rounded(this@SupportActivity, cCard, 20f)
        setPadding(px(20), px(22), px(20), px(22))
        addView(ImageView(this@SupportActivity).apply {
            setImageResource(R.drawable.ic_heart); setColorFilter(cRed)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor((cRed and 0x00FFFFFF) or 0x26000000) }
            val p = px(16); setPadding(p, p, p, p)
        }, LinearLayout.LayoutParams(px(64), px(64)).apply { bottomMargin = px(12) })
        addView(TextView(this@SupportActivity).apply {
            text = getString(R.string.support_eyebrow); textSize = 12f; letterSpacing = 0.2f
            typeface = Typeface.DEFAULT_BOLD; setTextColor(cAccent); gravity = Gravity.CENTER
        })
        addView(TextView(this@SupportActivity).apply {
            text = getString(R.string.support_hero_title); textSize = 22f
            typeface = Typeface.DEFAULT_BOLD; setTextColor(cPrimary); gravity = Gravity.CENTER
            setPadding(0, px(6), 0, 0)
        })
        addView(TextView(this@SupportActivity).apply {
            text = getString(R.string.support_hero_desc); textSize = 14f
            setTextColor(cSecondary); gravity = Gravity.CENTER; setLineSpacing(0f, 1.2f)
            setPadding(0, px(8), 0, 0)
        })
    }

    private fun methodCard(icon: Int, color: Int, title: String, badge: String, desc: String,
                           button: String, note: String, url: String): View = card().apply {
        setPadding(px(16), px(16), px(16), px(16))
        addView(cardHeader(icon, color, title, badge))
        addView(bodyText(desc), LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(10) })
        addView(actionButton(button) { SafeEngine.openLink(this@SupportActivity, url) },
            LinearLayout.LayoutParams(-1, px(48)).apply { topMargin = px(14) })
        addView(noteText(note), LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(10) })
    }

    private fun bankCard(): View = card().apply {
        setPadding(px(16), px(16), px(16), px(16))
        addView(cardHeader(R.drawable.ic_document, cGreen, getString(R.string.support_bank_title), getString(R.string.support_badge_free)))
        addView(bodyText(getString(R.string.support_bank_desc)), LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(10) })
        val title = getString(R.string.support_bank_transfer_title_value)
        val fields = listOf(
            getString(R.string.support_bank_recipient) to RECIPIENT,
            getString(R.string.support_bank_account) to ACCOUNT,
            getString(R.string.support_bank_iban) to IBAN,
            getString(R.string.support_bank_transfer_title) to title
        )
        val box = LinearLayout(this@SupportActivity).apply {
            orientation = LinearLayout.VERTICAL
            background = ThemeUi.rounded(this@SupportActivity, (cPrimary and 0x00FFFFFF) or 0x0F000000, 14f)
        }
        fields.forEachIndexed { i, (label, value) ->
            if (i > 0) box.addView(View(this@SupportActivity).apply {
                setBackgroundColor(ThemeUi.divider(this@SupportActivity))
            }, LinearLayout.LayoutParams(-1, px(1).coerceAtLeast(1)).apply { marginStart = px(14) })
            box.addView(copyRow(label, value))
        }
        addView(box, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(14) })
        addView(actionButton(getString(R.string.support_bank_copy_all)) {
            copy(fields.joinToString("\n") { "${it.first}: ${it.second}" })
        }, LinearLayout.LayoutParams(-1, px(48)).apply { topMargin = px(14) })
        for (hint in listOf(R.string.support_bank_hint_pl, R.string.support_bank_hint_abroad,
                R.string.support_bank_hint_amount, R.string.support_bank_hint_nick)) {
            addView(noteText(getString(hint)), LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(8) })
        }
        addView(LinearLayout(this@SupportActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            background = ThemeUi.rounded(this@SupportActivity, (cOrange and 0x00FFFFFF) or 0x1F000000, 12f)
            setPadding(px(12), px(10), px(12), px(10))
            isClickable = true
            setOnClickListener { SafeEngine.openLink(this@SupportActivity, PAGE_URL) }
            addView(ImageView(this@SupportActivity).apply {
                setImageResource(R.drawable.ic_shield); setColorFilter(cOrange)
            }, LinearLayout.LayoutParams(px(18), px(18)).apply { topMargin = px(1); marginEnd = px(10) })
            addView(TextView(this@SupportActivity).apply {
                text = getString(R.string.support_bank_warning)
                textSize = 12.5f; setTextColor(cPrimary); setLineSpacing(0f, 1.15f)
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(12) })
    }

    private fun copyRow(label: String, value: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(px(14), px(10), px(8), px(10))
        background = ContextCompat.getDrawable(this@SupportActivity, R.drawable.bg_menu_ripple)
        isClickable = true
        setOnClickListener { copy(value) }
        addView(LinearLayout(this@SupportActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@SupportActivity).apply {
                text = label; textSize = 11.5f; setTextColor(cSecondary)
            })
            addView(TextView(this@SupportActivity).apply {
                text = value; textSize = 15f; setTextColor(cPrimary); typeface = Typeface.DEFAULT_BOLD
                setTextIsSelectable(false)
                setPadding(0, px(2), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(ImageView(this@SupportActivity).apply {
            setImageResource(R.drawable.ic_copy); setColorFilter(cAccent)
            contentDescription = getString(R.string.support_copy)
            val p = px(8); setPadding(p, p, p, p)
        }, LinearLayout.LayoutParams(px(36), px(36)))
    }

    private fun copy(text: String) {
        val cm = getSystemService(ClipboardManager::class.java) ?: return
        cm.setPrimaryClip(ClipData.newPlainText(getString(R.string.support_bank_title), text))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) PopupHelper.show(this, getString(R.string.copied))
    }

    private fun cardHeader(icon: Int, color: Int, title: String, badge: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(iconTile(icon, color))
        addView(TextView(this@SupportActivity).apply {
            text = title; textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(cPrimary)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(TextView(this@SupportActivity).apply {
            text = badge; textSize = 11f; typeface = Typeface.DEFAULT_BOLD; setTextColor(color)
            background = GradientDrawable().apply { cornerRadius = 10 * dp; setColor((color and 0x00FFFFFF) or 0x26000000) }
            setPadding(px(9), px(3), px(9), px(3))
        })
    }

    private fun actionButton(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        setTextColor(ThemeUi.onAccent(this@SupportActivity))
        background = GradientDrawable().apply { cornerRadius = 14 * dp; setColor(cAccent) }
        isClickable = true; isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun bodyText(text: String) = TextView(this).apply {
        this.text = text; textSize = 14f; setTextColor(cSecondary); setLineSpacing(0f, 1.2f)
    }

    private fun noteText(text: String) = TextView(this).apply {
        this.text = text; textSize = 12.5f; setTextColor(cSecondary); alpha = 0.85f; setLineSpacing(0f, 1.15f)
    }

    private fun infoRow(icon: Int, color: Int, title: String, desc: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(px(14), px(13), px(16), px(13))
        addView(iconTile(icon, color))
        addView(LinearLayout(this@SupportActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@SupportActivity).apply {
                text = title; textSize = 15f; setTextColor(cPrimary); typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(this@SupportActivity).apply {
                text = desc; textSize = 12.5f; setTextColor(cSecondary); setLineSpacing(0f, 1.1f)
                setPadding(0, px(3), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, -2, 1f))
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = ThemeUi.rounded(this@SupportActivity, cCard, 18f)
        clipToOutline = true
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(ThemeUi.divider(context))
        layoutParams = LinearLayout.LayoutParams(-1, px(1).coerceAtLeast(1)).apply { marginStart = px(68) }
    }

    private fun space(h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(-1, px(h)) }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text.uppercase()
        textSize = 12f; setTextColor(cAccent); typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.06f
        setPadding(px(6), px(22), 0, px(8))
    }

    private fun iconTile(icon: Int, color: Int) = ImageView(this).apply {
        setImageResource(icon); setColorFilter(color)
        background = GradientDrawable().apply { cornerRadius = 12 * dp; setColor((color and 0x00FFFFFF) or 0x24000000) }
        val p = px(9); setPadding(p, p, p, p)
        layoutParams = LinearLayout.LayoutParams(px(40), px(40)).apply { marginEnd = px(14) }
    }
}
