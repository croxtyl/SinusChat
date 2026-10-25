package pl.sinusoide.sinuschat.messenger

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import pl.sinusoide.sinuschat.R
import pl.sinusoide.sinuschat.network.ApiClient
import pl.sinusoide.sinuschat.setup.SetupActivity
import pl.sinusoide.sinuschat.ui.SineProgressRing
import pl.sinusoide.sinuschat.ui.ThemeUi
import pl.sinusoide.sinuschat.util.AvatarHelper
import pl.sinusoide.sinuschat.util.LinkLog

class QrLinkActivity : AppCompatActivity() {
    private companion object {
        const val TAG = "QrLink"
        const val ERROR_RED = 0xFFE5484D.toInt()
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(pl.sinusoide.sinuschat.util.LocaleManager.wrap(newBase))
    }

    private lateinit var ring: SineProgressRing
    private lateinit var avatar: ImageView
    private lateinit var eyebrow: TextView
    private lateinit var title: TextView
    private lateinit var desc: TextView
    private lateinit var e2eRow: View
    private lateinit var acceptBtn: TextView
    private lateinit var declineBtn: TextView
    private lateinit var codeLine: TextView
    private var working = false

    private val dp get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        PendingLink.clear()
        if (pl.sinusoide.sinuschat.ui.BlockNewNotice.show(this)) return
        initUi()
    }

    private fun initUi() {
        val uri = intent?.data
        val token = (uri?.pathSegments?.lastOrNull()
            ?: uri?.toString()?.substringAfter("/add/", "")?.substringBefore("?")?.substringBefore("#"))
            ?.let { t -> linkFragment(uri)?.let { "$t#$it" } ?: t }

        val t = token.orEmpty()
        val kind = when {
            t.isEmpty() -> "empty"
            GroupInviteManager.isGroupInviteToken(t) -> "group"
            InviteManager.isInviteToken(t) -> "invite"
            else -> "qr"
        }
        val trace = "${uri?.scheme ?: "-"}://${uri?.host ?: "-"} $kind len=${t.length} key=${if (InviteManager.hasKey(t)) 1 else 0} ${LinkLog.env(this)}"
        Log.i(TAG, "Link: $trace")
        LinkLog.add(this, "open", trace)

        val myHash = SetupActivity.getUserPhoneHash(this)
        if (myHash.isEmpty() || !SetupActivity.isSetupDone(this) || SetupActivity.wizardStep(this) != null) {
            Log.w(TAG, "Brak konta albo konfiguracja w toku")
            LinkLog.add(this, "setup", "link czeka na koniec konfiguracji")
            PendingLink.keep(uri)
            goHome()
            return
        }

        if (kind == "group") {
            GroupJoinSheet(this, t) { if (!isFinishing) goHome() }.show()
            return
        }

        buildScreen()
        when (kind) {
            "empty" -> fail("link_empty")
            "invite" -> ensureSession { loadInvite(t) }
            else -> {
                val decoded = ContactQr.decode(t.substringBefore('#'))
                if (decoded == null) {
                    LinkLog.add(this, "qr", "link_damaged")
                    fail("link_damaged")
                    return
                }
                val (hash, name) = decoded
                if (hash == myHash) { fail("invite_own"); return }
                showInvite(name, hash, null) {
                    ContactQr.remember(this, t)
                    openChat(myHash, hash, name)
                }
            }
        }
    }

    private fun ensureSession(then: () -> Unit) {
        if (ApiClient.isAuthenticated(this)) { then(); return }
        showLoading()
        LinkLog.add(this, "auth", "brak tokena, rejestruję sesję")
        ApiClient.autoRegisterIfNeeded(this) { ok ->
            if (isFinishing || isDestroyed) return@autoRegisterIfNeeded
            LinkLog.add(this, "auth", if (ok) "ok" else "session")
            if (ok) then() else fail("session") { ensureSession(then) }
        }
    }

    private fun loadInvite(token: String) {
        showLoading()
        InviteManager.preview(this, token) { result ->
            if (isFinishing || isDestroyed) return@preview
            when (result) {
                is InviteManager.PreviewResult.Ready -> {
                    LinkLog.add(this, "preview", "ok name=${if (result.name != null) 1 else 0}")
                    showInvite(result.name, result.hash, result.color) { accept(token) }
                }
                is InviteManager.PreviewResult.Failure -> {
                    Log.w(TAG, "Podgląd zaproszenia: ${result.errorCode} (HTTP ${result.http})")
                    LinkLog.add(this, "preview", "${result.errorCode} http=${result.http}")
                    fail(result.errorCode, result.http) { ensureSession { loadInvite(token) } }
                }
            }
        }
    }

    private fun accept(token: String) {
        if (working) return
        working = true
        setBusy(true)
        InviteManager.redeem(this, token) { result ->
            if (isFinishing || isDestroyed) return@redeem
            working = false
            when (result) {
                is InviteManager.RedeemResult.Success -> {
                    LinkLog.add(this, "redeem", "ok")
                    startActivity(Intent(this, ChatActivity::class.java).apply {
                        putExtra("conversationId", result.conversationId)
                        putExtra("otherUserHash", result.hash)
                        putExtra("otherUserName", result.name)
                        putExtra("weStarted", true)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    })
                    finish()
                }
                is InviteManager.RedeemResult.Failure -> {
                    Log.w(TAG, "Zaproszenie odrzucone: ${result.errorCode} (HTTP ${result.http})")
                    LinkLog.add(this, "redeem", "${result.errorCode} http=${result.http}")
                    fail(result.errorCode, result.http) { ensureSession { loadInvite(token) } }
                }
            }
        }
    }

    private fun buildScreen() {
        if (::title.isInitialized) return
        val accent = ThemeUi.accent(this)
        val primary = ThemeUi.textPrimary(this)
        val secondary = ThemeUi.textSecondary(this)
        fun px(v: Int) = (v * dp).toInt()

        val root = FrameLayout(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val avatarBox = FrameLayout(this)
        ring = SineProgressRing(this).apply {
            color = accent
            disc = 0
            showCancel = false
            isClickable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        avatar = ImageView(this).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        avatarBox.addView(ring, FrameLayout.LayoutParams(px(172), px(172)))
        avatarBox.addView(avatar, FrameLayout.LayoutParams(px(112), px(112), Gravity.CENTER))
        column.addView(avatarBox, LinearLayout.LayoutParams(px(172), px(172)))

        eyebrow = TextView(this).apply {
            text = getString(R.string.invite_screen_eyebrow)
            setTextColor(accent)
            textSize = 12f
            letterSpacing = 0.2f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        column.addView(eyebrow, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(26) })

        title = TextView(this).apply {
            setTextColor(primary)
            textSize = 26f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }
        column.addView(title, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(10) })

        desc = TextView(this).apply {
            setTextColor(secondary)
            textSize = 15f
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.2f)
        }
        column.addView(desc, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(12) })

        e2eRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ThemeUi.rounded(this@QrLinkActivity, ThemeUi.card(this@QrLinkActivity), 18f)
            setPadding(px(14), px(8), px(16), px(8))
            addView(ImageView(this@QrLinkActivity).apply {
                setImageResource(R.drawable.ic_shield)
                setColorFilter(accent)
            }, LinearLayout.LayoutParams(px(16), px(16)))
            addView(TextView(this@QrLinkActivity).apply {
                text = getString(R.string.e2e_encryption)
                setTextColor(secondary)
                textSize = 13f
                setPadding(px(8), 0, 0, 0)
            })
        }
        column.addView(e2eRow, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(22) })

        codeLine = TextView(this).apply {
            setTextColor(secondary)
            alpha = 0.7f
            textSize = 12f
            gravity = Gravity.CENTER
            setTextIsSelectable(true)
            visibility = View.GONE
        }
        column.addView(codeLine, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(16) })

        root.addView(column, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        acceptBtn = actionButton(filled = true)
        declineBtn = actionButton(filled = false)
        buttons.addView(acceptBtn, LinearLayout.LayoutParams(-1, px(54)))
        buttons.addView(declineBtn, LinearLayout.LayoutParams(-1, px(54)).apply { topMargin = px(10) })
        root.addView(buttons, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            column.setPadding(px(32), bars.top + px(24), px(32), px(150))
            buttons.setPadding(px(24), 0, px(24), bars.bottom + px(24))
            insets
        }
        setContentView(root)
    }

    private fun actionButton(filled: Boolean): TextView = TextView(this).apply {
        val accent = ThemeUi.accent(this@QrLinkActivity)
        val radius = 27 * dp
        val shape = GradientDrawable().apply {
            cornerRadius = radius
            if (filled) setColor(accent)
            else {
                setColor(0x00000000)
                setStroke((1.2f * dp).toInt(), ThemeUi.border(this@QrLinkActivity))
            }
        }
        val mask = GradientDrawable().apply { cornerRadius = radius; setColor(0xFFFFFFFF.toInt()) }
        background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), shape, mask)
        setTextColor(if (filled) ThemeUi.onAccent(this@QrLinkActivity) else ThemeUi.textPrimary(this@QrLinkActivity))
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
    }

    private fun showLoading() {
        ring.color = ThemeUi.accent(this)
        ring.progress = { null }
        avatar.setImageDrawable(null)
        eyebrow.visibility = View.VISIBLE
        title.text = getString(R.string.invite_screen_checking)
        desc.text = ""
        e2eRow.visibility = View.GONE
        codeLine.visibility = View.GONE
        acceptBtn.visibility = View.GONE
        declineBtn.isEnabled = true
        declineBtn.alpha = 1f
        declineBtn.text = getString(R.string.cancel)
        declineBtn.setOnClickListener { finish() }
    }

    private fun showInvite(name: String?, hash: String?, color: String?, onAccept: () -> Unit) {
        val accent = ThemeUi.accent(this)
        ring.color = accent
        ring.progress = { 1f }
        val size = (112 * dp).toInt()
        val hex = color ?: hash?.let { AvatarHelper.getContactColor(this, it) } ?: String.format("#%06X", 0xFFFFFF and accent)
        avatar.clearColorFilter()
        avatar.scaleType = ImageView.ScaleType.FIT_CENTER
        avatar.setPadding(0, 0, 0, 0)
        avatar.setImageBitmap(AvatarHelper.createLetterBitmap(AvatarHelper.initialOf(name ?: "?"), hex, size))
        eyebrow.visibility = View.VISIBLE
        title.text = name?.let { getString(R.string.invite_screen_title, it) } ?: getString(R.string.invite_screen_title_unknown)
        desc.text = getString(R.string.invite_screen_desc)
        e2eRow.visibility = View.VISIBLE
        codeLine.visibility = View.GONE
        acceptBtn.visibility = View.VISIBLE
        acceptBtn.text = getString(R.string.invite_screen_accept)
        acceptBtn.setOnClickListener { onAccept() }
        declineBtn.text = getString(R.string.decline)
        declineBtn.setOnClickListener { finish() }
        setBusy(false)
    }

    private fun setBusy(busy: Boolean) {
        acceptBtn.isEnabled = !busy
        declineBtn.isEnabled = !busy
        acceptBtn.alpha = if (busy) 0.6f else 1f
        declineBtn.alpha = if (busy) 0.6f else 1f
        ring.progress = if (busy) ({ null }) else ({ 1f })
    }

    private fun fail(code: String, http: Int = 0, retry: (() -> Unit)? = null) {
        ring.color = ERROR_RED
        ring.progress = { 1f }
        val pad = (30 * dp).toInt()
        avatar.scaleType = ImageView.ScaleType.FIT_CENTER
        avatar.setPadding(pad, pad, pad, pad)
        avatar.setImageResource(R.drawable.ic_warning)
        avatar.setColorFilter(ERROR_RED)
        eyebrow.visibility = View.GONE
        title.text = InviteManager.errorMessage(this, code)
        desc.text = InviteManager.errorHint(this, code)
        e2eRow.visibility = View.GONE
        codeLine.text = getString(R.string.invite_error_code, if (http > 0) "$code · HTTP $http" else code)
        codeLine.visibility = View.VISIBLE
        if (retry != null && InviteManager.isRetryable(code)) {
            acceptBtn.visibility = View.VISIBLE
            acceptBtn.isEnabled = true
            acceptBtn.alpha = 1f
            acceptBtn.text = getString(R.string.invite_screen_retry)
            acceptBtn.setOnClickListener { retry() }
        } else acceptBtn.visibility = View.GONE
        declineBtn.isEnabled = true
        declineBtn.alpha = 1f
        declineBtn.text = getString(R.string.invite_screen_close)
        declineBtn.setOnClickListener { finish() }
    }

    private fun linkFragment(uri: android.net.Uri?): String? {
        if (uri == null) return null
        uri.fragment?.takeIf { it.isNotEmpty() }?.let { return it }
        return try {
            uri.getQueryParameter("f")?.takeIf { it.isNotEmpty() }
                ?: uri.getQueryParameter("p")?.takeIf { it.isNotEmpty() }?.let { "p=$it" }
        } catch (_: UnsupportedOperationException) { null }
    }

    private fun openChat(myHash: String, hash: String, name: String) {
        val convId = if (myHash < hash) "${myHash}_${hash}" else "${hash}_${myHash}"
        InviteManager.introduce(this, hash)
        startActivity(Intent(this, ChatActivity::class.java).apply {
            putExtra("conversationId", convId)
            putExtra("otherUserHash", hash)
            putExtra("otherUserName", name)
            putExtra("weStarted", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        })
        finish()
    }

    private fun goHome() {
        startActivity(Intent(this, pl.sinusoide.sinuschat.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        })
        finish()
    }
}
