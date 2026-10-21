package pl.sinusoide.sinuschat

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
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
import pl.sinusoide.sinuschat.util.NetworkSettings
import pl.sinusoide.sinuschat.util.PopupHelper
import pl.sinusoide.sinuschat.util.StorageUsage
import pl.sinusoide.sinuschat.util.ThemeManager
import java.text.DateFormat
import java.util.Date

class InternetSettingsActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(pl.sinusoide.sinuschat.util.LocaleManager.wrap(newBase))
    }

    private val dp get() = resources.displayMetrics.density

    private var cBg = 0; private var cSurface = 0; private var cCard = 0
    private var cPrimary = 0; private var cSecondary = 0; private var cTrack = 0
    private var cAccent = 0; private var cGreen = 0; private var cOrange = 0; private var cRed = 0

    private lateinit var heroIcon: ImageView
    private lateinit var heroTitle: TextView
    private lateinit var heroDesc: TextView
    private lateinit var heroPill: TextView
    private lateinit var optWifi: View
    private lateinit var optAny: View
    private lateinit var totalValue: TextView
    private lateinit var totalUnit: TextView
    private lateinit var sinceText: TextView
    private lateinit var receivedValue: TextView
    private lateinit var sentValue: TextView
    private lateinit var splitBar: LinearLayout

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

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
        cTrack = (cSecondary and 0x00FFFFFF) or 0x26000000

        buildUi()
    }

    override fun onStart() {
        super.onStart()
        refreshAll()
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = post()
            override fun onLost(network: Network) = post()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = post()
            private fun post() { runOnUiThread { if (!isFinishing) refreshHero() } }
        }
        try { cm.registerDefaultNetworkCallback(cb); networkCallback = cb } catch (_: Exception) {}
    }

    override fun onStop() {
        super.onStop()
        networkCallback?.let { cb ->
            try { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) } catch (_: Exception) {}
        }
        networkCallback = null
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
            background = ContextCompat.getDrawable(this@InternetSettingsActivity, R.drawable.bg_menu_ripple)
            contentDescription = getString(R.string.back)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt()))
        header.addView(TextView(this).apply {
            text = getString(R.string.internet); textSize = 20f
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

        content.addView(sectionTitle(getString(R.string.net_section_auto)))
        content.addView(card().apply {
            optWifi = optionRow(R.drawable.ic_wifi, cGreen, R.string.wifi_only, R.string.saves_mobile_data) {
                select(true)
            }
            addView(optWifi)
            addView(divider())
            optAny = optionRow(R.drawable.ic_internet, cOrange, R.string.any_network, R.string.consumes_mobile_data) {
                select(false)
            }
            addView(optAny)
        })

        content.addView(sectionTitle(getString(R.string.net_section_mobile)))
        content.addView(usageCard())

        content.addView(TextView(this).apply {
            text = getString(R.string.net_footer)
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
        heroIcon = ImageView(this).apply {
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
        heroDesc = TextView(this).apply {
            textSize = 13f; setTextColor(cSecondary); gravity = Gravity.CENTER
            setPadding(0, (4 * dp).toInt(), 0, (14 * dp).toInt())
        }
        hero.addView(heroDesc)
        heroPill = TextView(this).apply {
            textSize = 12f; typeface = Typeface.DEFAULT_BOLD
            setPadding((12 * dp).toInt(), (5 * dp).toInt(), (12 * dp).toInt(), (5 * dp).toInt())
        }
        hero.addView(heroPill)
        return hero
    }

    private fun usageCard(): View {
        val c = card()

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * dp).toInt(), (16 * dp).toInt(), (18 * dp).toInt(), (14 * dp).toInt())
        }
        val big = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM
        }
        totalValue = TextView(this).apply {
            textSize = 30f; setTextColor(cPrimary); typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
        }
        totalUnit = TextView(this).apply {
            textSize = 15f; setTextColor(cSecondary); typeface = Typeface.DEFAULT_BOLD
            setPadding((5 * dp).toInt(), 0, 0, (3 * dp).toInt())
        }
        big.addView(totalValue); big.addView(totalUnit)
        top.addView(big)
        top.addView(TextView(this).apply {
            text = getString(R.string.net_counter_label); textSize = 13f; setTextColor(cSecondary)
            setPadding(0, (2 * dp).toInt(), 0, 0)
        })

        splitBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(cTrack, 5f)
            clipToOutline = true
        }
        top.addView(splitBar, LinearLayout.LayoutParams(-1, (10 * dp).toInt()).apply {
            topMargin = (14 * dp).toInt()
        })
        sinceText = TextView(this).apply {
            textSize = 12f; setTextColor(cSecondary)
            setPadding(0, (8 * dp).toInt(), 0, 0)
        }
        top.addView(sinceText)
        c.addView(top)

        c.addView(fullDivider())
        val rx = valueRow(R.drawable.ic_download, cGreen, R.string.net_received, R.string.net_received_desc)
        receivedValue = rx.second
        c.addView(rx.first)
        c.addView(divider())
        val tx = valueRow(R.drawable.ic_send, cAccent, R.string.net_sent, R.string.net_sent_desc)
        sentValue = tx.second
        c.addView(tx.first)
        c.addView(fullDivider())

        c.addView(TextView(this).apply {
            text = getString(R.string.reset_counter)
            textSize = 15f; setTextColor(cRed); gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, (14 * dp).toInt(), 0, (14 * dp).toInt())
            background = ContextCompat.getDrawable(this@InternetSettingsActivity, R.drawable.bg_menu_ripple)
            isClickable = true
            setOnClickListener {
                NetworkSettings.resetMobileCounter(this@InternetSettingsActivity)
                refreshCounter()
                PopupHelper.show(this@InternetSettingsActivity, getString(R.string.net_counter_reset_done))
            }
        })
        return c
    }

    private fun refreshAll() {
        refreshHero()
        refreshOptions()
        refreshCounter()
    }

    private fun select(wifiOnly: Boolean) {
        if (NetworkSettings.isWifiOnly(this) == wifiOnly) return
        NetworkSettings.setWifiOnly(this, wifiOnly)
        refreshOptions()
        refreshHero()
    }

    private fun refreshHero() {
        val conn = NetworkSettings.connection(this)
        val wifiOnly = NetworkSettings.isWifiOnly(this)
        val metered = NetworkSettings.isMetered(this)

        val (icon, color, title) = when (conn) {
            NetworkSettings.Connection.WIFI -> Triple(R.drawable.ic_wifi, cGreen, R.string.net_conn_wifi)
            NetworkSettings.Connection.MOBILE -> Triple(R.drawable.ic_internet, cOrange, R.string.net_conn_mobile)
            NetworkSettings.Connection.OTHER -> Triple(R.drawable.ic_internet, cAccent, R.string.net_conn_other)
            NetworkSettings.Connection.NONE -> Triple(R.drawable.ic_offline, cSecondary, R.string.net_conn_none)
        }
        heroIcon.setImageResource(icon)
        heroIcon.setColorFilter(color)
        heroIcon.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor((color and 0x00FFFFFF) or 0x26000000)
        }
        heroTitle.text = getString(title)
        heroDesc.text = getString(when {
            conn == NetworkSettings.Connection.NONE -> R.string.net_hero_offline
            NetworkSettings.canDownloadNow(this) -> R.string.net_hero_auto_on
            wifiOnly -> R.string.net_hero_waiting_wifi
            else -> R.string.net_hero_auto_on
        })
        if (conn == NetworkSettings.Connection.NONE) {
            heroPill.visibility = View.GONE
        } else {
            heroPill.visibility = View.VISIBLE
            val pillColor = if (metered) cOrange else cGreen
            heroPill.text = getString(if (metered) R.string.net_pill_metered else R.string.net_pill_unmetered)
            heroPill.setTextColor(pillColor)
            heroPill.background = GradientDrawable().apply {
                cornerRadius = 14 * dp
                setColor((pillColor and 0x00FFFFFF) or 0x24000000)
            }
        }
    }

    private fun refreshOptions() {
        val wifiOnly = NetworkSettings.isWifiOnly(this)
        styleOption(optWifi, wifiOnly)
        styleOption(optAny, !wifiOnly)
    }

    private fun refreshCounter() {
        val rx = NetworkSettings.mobileBytesUsed(this)
        val tx = NetworkSettings.mobileBytesSent(this)
        val parts = StorageUsage.formatSize(rx + tx).split(" ")
        totalValue.text = parts.getOrElse(0) { "0" }
        totalUnit.text = parts.getOrElse(1) { "B" }
        receivedValue.text = StorageUsage.formatSize(rx)
        sentValue.text = StorageUsage.formatSize(tx)

        splitBar.removeAllViews()
        val sum = (rx + tx).toFloat()
        if (sum > 0f) {
            if (rx > 0) splitBar.addView(View(this).apply { setBackgroundColor(cGreen) },
                LinearLayout.LayoutParams(0, -1, rx / sum))
            if (rx > 0 && tx > 0) splitBar.addView(View(this).apply { setBackgroundColor(cCard) },
                LinearLayout.LayoutParams((2 * dp).toInt(), -1))
            if (tx > 0) splitBar.addView(View(this).apply { setBackgroundColor(cAccent) },
                LinearLayout.LayoutParams(0, -1, tx / sum))
        }

        val since = NetworkSettings.counterSince(this)
        sinceText.visibility = if (since > 0L) View.VISIBLE else View.GONE
        if (since > 0L) {
            sinceText.text = getString(R.string.net_counter_since,
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(since)))
        }
    }

    private fun optionRow(icon: Int, color: Int, titleRes: Int, descRes: Int, onClick: () -> Unit): View {
        val check = ImageView(this).apply {
            tag = "check"
            val p = (4 * dp).toInt(); setPadding(p, p, p, p)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * dp).toInt(), (13 * dp).toInt(), (14 * dp).toInt(), (13 * dp).toInt())
            background = ContextCompat.getDrawable(this@InternetSettingsActivity, R.drawable.bg_menu_ripple)
            isClickable = true
            setOnClickListener { onClick() }
            addView(iconTile(icon, color))
            addView(LinearLayout(this@InternetSettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@InternetSettingsActivity).apply {
                    text = getString(titleRes); textSize = 15.5f; setTextColor(cPrimary)
                })
                addView(TextView(this@InternetSettingsActivity).apply {
                    text = getString(descRes); textSize = 12.5f; setTextColor(cSecondary)
                    setPadding(0, (2 * dp).toInt(), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = (8 * dp).toInt() })
            addView(FrameLayout(this@InternetSettingsActivity).apply { addView(check, FrameLayout.LayoutParams(-1, -1)) },
                LinearLayout.LayoutParams((24 * dp).toInt(), (24 * dp).toInt()))
        }
    }

    private fun styleOption(row: View, selected: Boolean) {
        val check = row.findViewWithTag<ImageView>("check") ?: return
        if (selected) {
            check.setImageResource(R.drawable.ic_check)
            check.setColorFilter(Color.WHITE)
            check.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(cAccent) }
        } else {
            check.setImageDrawable(null)
            check.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke((2 * dp).toInt(), (cSecondary and 0x00FFFFFF) or 0x80000000.toInt())
            }
        }
        pl.sinusoide.sinuschat.ui.FlowGlass.check(check, selected)
        row.isSelected = selected
    }

    private fun valueRow(icon: Int, color: Int, titleRes: Int, descRes: Int): Pair<View, TextView> {
        val value = TextView(this).apply {
            textSize = 14.5f; setTextColor(cPrimary); typeface = Typeface.DEFAULT_BOLD
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
            addView(iconTile(icon, color))
            addView(LinearLayout(this@InternetSettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@InternetSettingsActivity).apply {
                    text = getString(titleRes); textSize = 15.5f; setTextColor(cPrimary)
                })
                addView(TextView(this@InternetSettingsActivity).apply {
                    text = getString(descRes); textSize = 12.5f; setTextColor(cSecondary)
                    setPadding(0, (2 * dp).toInt(), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = (8 * dp).toInt() })
            addView(value)
        }
        return row to value
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

    private fun fullDivider() = View(this).apply {
        setBackgroundColor(pl.sinusoide.sinuschat.ui.ThemeUi.divider(context))
        layoutParams = LinearLayout.LayoutParams(-1, (1 * dp).toInt().coerceAtLeast(1))
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
