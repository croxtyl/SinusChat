package pl.sinusoide.sinuschat.messenger
import pl.sinusoide.sinuschat.util.optStringOrNull

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import pl.sinusoide.sinuschat.R
import pl.sinusoide.sinuschat.network.ApiClient
import pl.sinusoide.sinuschat.util.AppExecutor
import pl.sinusoide.sinuschat.util.PopupHelper
import pl.sinusoide.sinuschat.util.SecurityUtils
import pl.sinusoide.sinuschat.util.ThemeManager
import androidx.activity.enableEdgeToEdge

class SafetyNumberActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(pl.sinusoide.sinuschat.util.LocaleManager.wrap(newBase))
    }

    private val dp by lazy { resources.displayMetrics.density }
    private var myHash = ""
    private var otherHash = ""
    private var otherName = ""
    private var otherKeyCache: String? = null

    private lateinit var qrImage: ImageView
    private lateinit var numbersGrid: LinearLayout
    private lateinit var verifyButton: TextView
    private lateinit var descText: TextView

    private var myFlatDigits: String? = null

    private val scanLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val scanned = result.data?.getStringExtra("qr_result")?.trim() ?: return@registerForActivityResult
        compareScanned(scanned)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        myHash = intent.getStringExtra("myHash") ?: ""
        otherHash = intent.getStringExtra("otherUserHash") ?: ""
        otherName = intent.getStringExtra("otherUserName") ?: getString(R.string.caller_unknown)

        val bg = resolveThemeColor(R.attr.sinuschat_background)
        val scroll = android.widget.ScrollView(this).apply {
            setBackgroundColor(bg); fitsSystemWindows = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16*dp).toInt(), 0, (16*dp).toInt(), (24*dp).toInt())
        }
        scroll.addView(root)

        root.addView(header(getString(R.string.safety_number_title)))

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = roundRect(pl.sinusoide.sinuschat.ui.ThemeUi.accent(this@SafetyNumberActivity), 28f)
            setPadding((24*dp).toInt(), (28*dp).toInt(), (24*dp).toInt(), (24*dp).toInt())
        }
        val cardLp = LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = (12*dp).toInt(); marginStart = (4*dp).toInt(); marginEnd = (4*dp).toInt()
        }

        val qrTile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = roundRect(0xFFFFFFFF.toInt(), 20f)
            setPadding((20*dp).toInt(), (20*dp).toInt(), (20*dp).toInt(), (16*dp).toInt())
        }
        qrImage = ImageView(this)
        qrTile.addView(qrImage, LinearLayout.LayoutParams((200*dp).toInt(), (200*dp).toInt()))
        qrTile.isClickable = true
        qrTile.setOnClickListener { openScanner() }
        qrTile.addView(TextView(this).apply {
            text = getString(R.string.safety_tap_to_scan)
            textSize = 15f; setTextColor(0xFF666666.toInt())
            gravity = Gravity.CENTER
            setPadding(0, (12*dp).toInt(), 0, 0)
        })
        card.addView(qrTile, LinearLayout.LayoutParams((240*dp).toInt(), -2).apply {
            topMargin = (8*dp).toInt()
        })

        numbersGrid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        card.addView(numbersGrid, LinearLayout.LayoutParams(-2, -2).apply {
            topMargin = (24*dp).toInt(); bottomMargin = (20*dp).toInt()
        })

        verifyButton = TextView(this).apply {
            text = getString(R.string.mark_verified)
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(0xFF2A2A2A.toInt())
            gravity = Gravity.CENTER
            background = roundRect(0xFFFFFFFF.toInt(), 40f)
            setPadding(0, (16*dp).toInt(), 0, (16*dp).toInt())
            isClickable = true
            setOnClickListener { toggleVerified() }
        }
        card.addView(verifyButton, LinearLayout.LayoutParams(-1, -2))

        root.addView(card, cardLp)

        if (ThemeManager.isFlow(this)) {
            val glass = pl.sinusoide.sinuschat.ui.FlowGlass
            card.background = pl.sinusoide.sinuschat.ui.GlassDrawable(dp,
                pl.sinusoide.sinuschat.ui.ThemeUi.accent(this), false, 28 * dp, null, emphasis = false)
            card.setTag(R.id.flow_glass_keep, true)
            qrTile.foreground = pl.sinusoide.sinuschat.ui.GlassDrawable(dp, null, false, 20 * dp, null, rimOnly = true)
            glass.pressable(qrTile, 0.96f)
            verifyButton.setTextColor(0xFFFFFFFF.toInt())
            glass.glass(verifyButton, 40f)
        }

        descText = TextView(this).apply {
            text = getString(R.string.safety_desc, otherName)
            textSize = 15f; setTextColor(pl.sinusoide.sinuschat.ui.ThemeUi.textSecondary(this@SafetyNumberActivity))
            gravity = Gravity.CENTER
            setLineSpacing((4*dp), 1f)
            setPadding((12*dp).toInt(), (24*dp).toInt(), (12*dp).toInt(), 0)
        }
        root.addView(descText)

        setContentView(scroll)
        loadSafetyNumber()
    }

    private fun loadSafetyNumber() {
        if (myHash.isEmpty() || otherHash.isEmpty()) return
        AppExecutor.io {
            val myDoc    = ApiClient.getObject("/api/user/$myHash", this) ?: return@io
            val otherDoc = ApiClient.getObject("/api/user/$otherHash", this) ?: return@io
            val myKey    = myDoc.optStringOrNull("e2e_public_key") ?: return@io
            val otherKey = otherDoc.optStringOrNull("e2e_public_key") ?: return@io
            otherKeyCache = otherKey

            val groups = SecurityUtils.generateSafetyNumber(myKey, otherKey)
            val flatDigits = groups.joinToString(" ").replace("  ", " ")
            myFlatDigits = flatDigits
            val qrBmp = StyledQr.generate(flatDigits, (200 * dp).toInt())
            val stored = SecurityUtils.getStoredPublicKey(this, otherHash)
            val verified = stored == otherKey

            runOnUiThread {
                qrImage.setImageBitmap(qrBmp)
                renderNumbers(groups)
                setVerifiedState(verified)
            }
        }
    }

    private fun renderNumbers(rows: List<String>) {
        numbersGrid.removeAllViews()
        for (row in rows) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            for (group in row.trim().split(Regex("\\s+"))) {
                rowLayout.addView(TextView(this).apply {
                    text = group
                    textSize = 22f
                    setTextColor(0xFFFFFFFF.toInt())
                    typeface = android.graphics.Typeface.MONOSPACE
                    setPadding((10*dp).toInt(), (4*dp).toInt(), (10*dp).toInt(), (4*dp).toInt())
                })
            }
            numbersGrid.addView(rowLayout)
        }
    }

    private fun toggleVerified() {
        val otherKey = otherKeyCache ?: return
        val stored = SecurityUtils.getStoredPublicKey(this, otherHash)
        if (stored == otherKey) {
            SecurityUtils.storeContactKey(this, otherHash, "")
            setVerifiedState(false)
            PopupHelper.show(this, getString(R.string.mark_unverified_done))
        } else {
            SecurityUtils.storeContactKey(this, otherHash, otherKey)
            setVerifiedState(true)
            PopupHelper.showSuccess(this, getString(R.string.verified_done))
        }
    }

    private fun setVerifiedState(verified: Boolean) {
        verifyButton.text = if (verified)
            getString(R.string.marked_verified) else getString(R.string.mark_verified)
    }

    private fun openScanner() {
        if (myFlatDigits == null) return
        scanLauncher.launch(Intent(this, QrScanActivity::class.java))
    }

    private fun compareScanned(scanned: String) {
        val mine = myFlatDigits ?: return
        val a = mine.filter { it.isDigit() }
        val b = scanned.filter { it.isDigit() }
        if (b.isEmpty()) {
            PopupHelper.showError(this, getString(R.string.safety_scan_invalid))
            return
        }
        if (a == b) {
            otherKeyCache?.let { SecurityUtils.storeContactKey(this, otherHash, it) }
            setVerifiedState(true)
            PopupHelper.showSuccess(this, getString(R.string.safety_match))
        } else {
            PopupHelper.showError(this, getString(R.string.safety_mismatch))
        }
    }

    private fun header(title: String): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (12*dp).toInt(), 0, (12*dp).toInt())
        }
        bar.addView(android.widget.ImageButton(this).apply {
            setImageResource(R.drawable.ic_back)
            setBackgroundResource(0); setColorFilter(pl.sinusoide.sinuschat.ui.ThemeUi.textPrimary(this@SafetyNumberActivity))
            setPadding((10*dp).toInt(), (10*dp).toInt(), (10*dp).toInt(), (10*dp).toInt())
            setOnClickListener { finish() }
            pl.sinusoide.sinuschat.ui.FlowGlass.glass(this, 22f, oval = true)
        }, LinearLayout.LayoutParams((44*dp).toInt(), (44*dp).toInt()))
        bar.addView(TextView(this).apply {
            text = title; textSize = 20f; setTextColor(pl.sinusoide.sinuschat.ui.ThemeUi.textPrimary(this@SafetyNumberActivity))
            setPadding((8*dp).toInt(), 0, 0, 0)
        })
        return bar
    }

    private fun roundRect(color: Int, radiusDp: Float): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp * dp
        }
    }

    private fun resolveThemeColor(attr: Int): Int {
        val tv = android.util.TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) androidx.core.content.ContextCompat.getColor(this, tv.resourceId) else tv.data
    }
}