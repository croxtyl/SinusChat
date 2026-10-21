package pl.sinusoide.sinuschat.messenger

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
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
import pl.sinusoide.sinuschat.R
import pl.sinusoide.sinuschat.setup.SetupActivity
import pl.sinusoide.sinuschat.util.AppExecutor
import pl.sinusoide.sinuschat.util.AvatarHelper
import pl.sinusoide.sinuschat.util.PopupHelper
import pl.sinusoide.sinuschat.util.PrivacySettings
import pl.sinusoide.sinuschat.util.ThemeManager

class AddHubActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(pl.sinusoide.sinuschat.util.LocaleManager.wrap(newBase))
    }

    private val dp get() = resources.displayMetrics.density

    private var cBg = 0; private var cSurface = 0; private var cCard = 0
    private var cPrimary = 0; private var cSecondary = 0; private var cAccent = 0
    private val cOrange = 0xFFE67E22.toInt()
    private val cGreen = 0xFF3FB97A.toInt()

    private lateinit var qrImage: ImageView
    private var qrOverlay: View? = null
    private val unblurHandler = Handler(Looper.getMainLooper())
    private var unblurRunnable: Runnable? = null

    private fun attr(id: Int, fallback: Int): Int {
        val tv = android.util.TypedValue()
        return pl.sinusoide.sinuschat.ui.ThemeUi.attrOrNull(this, id) ?: fallback
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        cBg = attr(R.attr.sinuschat_background, 0xFF0E1621.toInt())
        cSurface = attr(R.attr.sinuschat_surface, 0xFF17212B.toInt())
        cCard = attr(R.attr.sinuschat_surface_elevated, 0xFF1D2A38.toInt())
        cPrimary = attr(R.attr.sinuschat_text_primary, Color.WHITE)
        cSecondary = attr(R.attr.sinuschat_text_secondary, 0xFF7F91A4.toInt())
        cAccent = pl.sinusoide.sinuschat.ui.ThemeUi.accent(this)
        if (pl.sinusoide.sinuschat.ui.BlockNewNotice.show(this)) return

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
            background = ContextCompat.getDrawable(this@AddHubActivity, R.drawable.bg_menu_ripple)
            contentDescription = getString(R.string.back)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt()))
        header.addView(TextView(this).apply {
            text = getString(R.string.add); textSize = 20f
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
            clipChildren = false
            clipToPadding = false
        }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipChildren = false
            addView(content)
        }
        outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            v.setPadding(0, 0, 0, insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom); insets
        }

        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            addView(bigTile(R.drawable.ic_person_add, getString(R.string.add_contact), getString(R.string.enter_email_person),
                0xFF5AA9F0.toInt(), 0xFF2F7ED8.toInt()) {
                startActivity(Intent(this@AddHubActivity, NewChatActivity::class.java))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(bigTile(R.drawable.ic_group, getString(R.string.new_group), getString(R.string.choose_people_group),
                0xFF5ED38A.toInt(), 0xFF2FA862.toInt()) {
                startActivity(Intent(this@AddHubActivity, GroupCreatorActivity::class.java))
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = (12 * dp).toInt() })
        })

        content.addView(sectionTitle(getString(R.string.add_section_invite)))
        content.addView(card().apply {
            addView(row(R.drawable.ic_timer, cOrange, getString(R.string.invite_once_title), getString(R.string.invite_once_sub)) {
                InviteSheet(this@AddHubActivity, InviteManager.KIND_ONCE).show()
            })
            addView(divider())
            addView(row(R.drawable.ic_link, cAccent, getString(R.string.invite_multi_title), getString(R.string.invite_multi_sub)) {
                InviteSheet(this@AddHubActivity, InviteManager.KIND_MULTI).show()
            })
        })

        content.addView(sectionTitle(getString(R.string.add_section_qr)))
        content.addView(qrCard())

        setContentView(outer)

        for (i in 0 until content.childCount) {
            val child = content.getChildAt(i)
            child.alpha = 0f; child.translationY = 16 * dp
            child.animate().alpha(1f).translationY(0f).setStartDelay(i * 45L).setDuration(280)
                .setInterpolator(DecelerateInterpolator()).start()
        }

        generateMyQr(qrImage, (200 * dp).toInt())
    }

    private fun bigTile(icon: Int, title: String, subtitle: String, from: Int, to: Int, onClick: () -> Unit): View {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(from, to)).apply {
                cornerRadius = 22 * dp
            }
            elevation = 4 * dp
            setPadding((16 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt())
            isClickable = true
            contentDescription = title
        }
        tile.addView(ImageView(this).apply {
            setImageResource(icon); setColorFilter(Color.WHITE)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x33FFFFFF) }
            val p = (9 * dp).toInt(); setPadding(p, p, p, p)
        }, LinearLayout.LayoutParams((42 * dp).toInt(), (42 * dp).toInt()).apply { bottomMargin = (22 * dp).toInt() })
        tile.addView(TextView(this).apply {
            text = title; textSize = 16f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        })
        tile.addView(TextView(this).apply {
            text = subtitle; textSize = 12.5f; setTextColor(0xD9FFFFFF.toInt())
            maxLines = 2; setPadding(0, (2 * dp).toInt(), 0, 0)
        })
        tile.setOnClickListener {
            tile.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).withEndAction {
                tile.animate().scaleX(1f).scaleY(1f).setDuration(200)
                    .setInterpolator(OvershootInterpolator(2.5f)).start()
                onClick()
            }.start()
        }
        return tile
    }

    private fun row(icon: Int, color: Int, title: String, subtitle: String, onClick: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding((14 * dp).toInt(), (13 * dp).toInt(), (12 * dp).toInt(), (13 * dp).toInt())
        background = ContextCompat.getDrawable(this@AddHubActivity, R.drawable.bg_menu_ripple)
        isClickable = true
        setOnClickListener { onClick() }
        addView(ImageView(this@AddHubActivity).apply {
            setImageResource(icon); setColorFilter(color)
            background = GradientDrawable().apply { cornerRadius = 12 * dp; setColor((color and 0x00FFFFFF) or 0x24000000) }
            val p = (9 * dp).toInt(); setPadding(p, p, p, p)
        }, LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt()).apply { marginEnd = (14 * dp).toInt() })
        addView(LinearLayout(this@AddHubActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@AddHubActivity).apply {
                text = title; textSize = 15.5f; setTextColor(cPrimary)
            })
            addView(TextView(this@AddHubActivity).apply {
                text = subtitle; textSize = 12.5f; setTextColor(cSecondary)
                setPadding(0, (2 * dp).toInt(), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = (8 * dp).toInt() })
        addView(ImageView(this@AddHubActivity).apply {
            setImageResource(R.drawable.ic_chevron_right); setColorFilter((cSecondary and 0x00FFFFFF) or 0xAA000000.toInt())
        }, LinearLayout.LayoutParams((20 * dp).toInt(), (20 * dp).toInt()))
    }

    private fun qrCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply { cornerRadius = 22 * dp; setColor(cCard) }
            setPadding((18 * dp).toInt(), (18 * dp).toInt(), (18 * dp).toInt(), (18 * dp).toInt())
        }

        val name = SetupActivity.getUserDisplayName(this)
        card.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val size = (36 * dp).toInt()
            addView(ImageView(this@AddHubActivity).apply {
                setImageBitmap(AvatarHelper.createLetterBitmap(AvatarHelper.initialOf(name), AvatarHelper.getMyColor(this@AddHubActivity), size))
                background = ContextCompat.getDrawable(this@AddHubActivity, R.drawable.bg_avatar_circle)
                clipToOutline = true
                if (AvatarHelper.loadAvatarInto(this@AddHubActivity, this)) scaleType = ImageView.ScaleType.CENTER_CROP
            }, LinearLayout.LayoutParams(size, size).apply { marginEnd = (10 * dp).toInt() })
            addView(TextView(this@AddHubActivity).apply {
                text = name; textSize = 16f; setTextColor(cPrimary); typeface = Typeface.DEFAULT_BOLD
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            })
        })
        card.addView(TextView(this).apply {
            text = getString(R.string.qr_show_hint); textSize = 13f; setTextColor(cSecondary)
            gravity = Gravity.CENTER
            setPadding(0, (6 * dp).toInt(), 0, (14 * dp).toInt())
        })

        val qrFrame = FrameLayout(this).apply {
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 20 * dp)
                }
            }
        }
        val qrPaper = FrameLayout(this).apply {
            background = GradientDrawable().apply { cornerRadius = 20 * dp; setColor(Color.WHITE) }
            val p = (12 * dp).toInt(); setPadding(p, p, p, p)
        }
        qrImage = ImageView(this)
        qrPaper.addView(qrImage, FrameLayout.LayoutParams(-1, -1))
        qrFrame.addView(qrPaper, FrameLayout.LayoutParams(-1, -1))

        if (PrivacySettings.isQrProtectionEnabled(this)) {
            val overlay = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(0xD9000000.toInt())
                isClickable = true
                addView(ImageView(this@AddHubActivity).apply {
                    setImageResource(R.drawable.ic_visibility_off); setColorFilter(Color.WHITE)
                }, LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt()))
                addView(TextView(this@AddHubActivity).apply {
                    text = getString(R.string.add_qr_tap_reveal); textSize = 13f
                    setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                    setPadding((20 * dp).toInt(), (8 * dp).toInt(), (20 * dp).toInt(), 0)
                }, LinearLayout.LayoutParams(-1, -2))
                setOnClickListener { unblurQr() }
            }
            qrOverlay = overlay
            qrFrame.addView(overlay, FrameLayout.LayoutParams(-1, -1))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                qrImage.setRenderEffect(RenderEffect.createBlurEffect(25f, 25f, Shader.TileMode.CLAMP))
            }
        }
        card.addView(qrFrame, LinearLayout.LayoutParams((224 * dp).toInt(), (224 * dp).toInt()))

        card.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = 24 * dp; setColor(cAccent) }
            setPadding((22 * dp).toInt(), (13 * dp).toInt(), (22 * dp).toInt(), (13 * dp).toInt())
            isClickable = true
            setOnClickListener { startScan() }
            addView(ImageView(this@AddHubActivity).apply {
                setImageResource(R.drawable.ic_qr_code); setColorFilter(Color.WHITE)
            }, LinearLayout.LayoutParams((18 * dp).toInt(), (18 * dp).toInt()).apply { marginEnd = (8 * dp).toInt() })
            addView(TextView(this@AddHubActivity).apply {
                text = getString(R.string.scan_someones_code); textSize = 15f
                setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            })
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (18 * dp).toInt() })

        if (android.nfc.NfcAdapter.getDefaultAdapter(this) != null) {
            card.addView(TextView(this).apply {
                text = getString(R.string.add_nfc_hint); textSize = 12.5f; setTextColor(cSecondary)
                gravity = Gravity.CENTER
                setLineSpacing(0f, 1.15f)
                setPadding((4 * dp).toInt(), (12 * dp).toInt(), (4 * dp).toInt(), 0)
            })
        }
        return card
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = pl.sinusoide.sinuschat.ui.ThemeUi.rounded(context, cCard, 18f)
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
        setPadding((6 * dp).toInt(), (24 * dp).toInt(), 0, (8 * dp).toInt())
    }

    override fun onResume() {
        super.onResume()
        pl.sinusoide.sinuschat.nfc.NfcContactService.active = true
        try {
            val adapter = android.nfc.NfcAdapter.getDefaultAdapter(this)
            if (adapter != null) {
                val cardEmulation = android.nfc.cardemulation.CardEmulation.getInstance(adapter)
                val component = android.content.ComponentName(this, pl.sinusoide.sinuschat.nfc.NfcContactService::class.java)
                cardEmulation.setPreferredService(this, component)
            }
        } catch (e: Exception) {
            android.util.Log.e("AddHubNfc", "setPreferredService błąd", e)
        }
    }

    override fun onPause() {
        super.onPause()
        reblurQr()
        pl.sinusoide.sinuschat.nfc.NfcContactService.active = false
        try {
            val adapter = android.nfc.NfcAdapter.getDefaultAdapter(this)
            if (adapter != null) {
                android.nfc.cardemulation.CardEmulation.getInstance(adapter).unsetPreferredService(this)
            }
        } catch (_: Exception) {}
    }

    private fun generateMyQr(target: ImageView, size: Int) {
        AppExecutor.io {
            try {
                val myHash = SetupActivity.getUserPhoneHash(this)
                val myName = SetupActivity.getUserDisplayName(this)
                val token = ContactQr.encode(myHash, myName)
                val payload = "${pl.sinusoide.sinuschat.AppConfig.ADD_LINK_BASE}$token"
                val logo = try {
                    val d = packageManager.getApplicationIcon(packageName)
                    val b = Bitmap.createBitmap(d.intrinsicWidth.coerceAtLeast(1), d.intrinsicHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    val c = Canvas(b); d.setBounds(0, 0, c.width, c.height); d.draw(c); b
                } catch (_: Exception) { null }
                val bmp = StyledQr.generate(data = payload, sizePx = size, logo = logo,
                    fgColor = Color.BLACK, bgColor = Color.WHITE)
                AppExecutor.main { target.setImageBitmap(bmp) }
            } catch (e: Exception) {
                android.util.Log.e("AddHub", "QR error: ${e.message}")
            }
        }
    }

    private fun unblurQr() {
        val overlay = qrOverlay ?: return
        unblurRunnable?.let { unblurHandler.removeCallbacks(it) }
        overlay.animate().alpha(0f).scaleX(1.15f).scaleY(1.15f).setDuration(350)
            .withEndAction {
                overlay.visibility = View.GONE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) qrImage.setRenderEffect(null)
            }.start()
        val runnable = Runnable { reblurQr() }
        unblurRunnable = runnable
        unblurHandler.postDelayed(runnable, 60000)
    }

    private fun reblurQr() {
        unblurRunnable?.let { unblurHandler.removeCallbacks(it) }
        val overlay = qrOverlay ?: return
        overlay.visibility = View.VISIBLE
        overlay.scaleX = 1f; overlay.scaleY = 1f
        overlay.animate().alpha(1f).setDuration(300).start()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            qrImage.setRenderEffect(RenderEffect.createBlurEffect(25f, 25f, Shader.TileMode.CLAMP))
        }
    }

    private fun startScan() {
        qrScanLauncher.launch(Intent(this, QrScanActivity::class.java))
    }

    private val qrScanLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val contents = result.data?.getStringExtra("qr_result")
        if (contents.isNullOrEmpty()) return@registerForActivityResult
        handleScannedQr(contents)
    }

    private fun handleScannedQr(raw: String) {
        val token = when {
            raw.contains("/add/") -> raw.substringAfter("/add/").substringBefore("?").substringBefore("/")
            raw.startsWith("sinuschat://add/") -> raw.removePrefix("sinuschat://add/")
            else -> {
                PopupHelper.showError(this, getString(R.string.not_sinuschat_code))
                return
            }
        }
        if (GroupInviteManager.isGroupInviteToken(token)) {
            GroupJoinSheet(this, token).show()
            return
        }
        if (InviteManager.isInviteToken(token)) {
            startActivity(Intent(this, QrLinkActivity::class.java).setData(android.net.Uri.parse("sinuschat://add/$token")))
            return
        }
        val decoded = ContactQr.decode(token)
        if (decoded == null) {
            PopupHelper.showError(this, getString(R.string.invalid_code))
            return
        }
        val (hash, name) = decoded
        val myHash = SetupActivity.getUserPhoneHash(this)
        if (hash == myHash) {
            PopupHelper.show(this, getString(R.string.your_own_code))
            return
        }
        val convId = if (myHash < hash) "${myHash}_${hash}" else "${hash}_${myHash}"
        startActivity(Intent(this, ChatActivity::class.java).apply {
            putExtra("conversationId", convId)
            putExtra("otherUserHash", hash)
            putExtra("otherUserName", name)
            putExtra("weStarted", true)
        })
        finish()
    }
}
