package pl.sinusoide.sinuschat

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import pl.sinusoide.sinuschat.messenger.StyledQr
import pl.sinusoide.sinuschat.ui.FlowGlass
import pl.sinusoide.sinuschat.ui.MoveDataIconView
import pl.sinusoide.sinuschat.ui.ThemeUi
import pl.sinusoide.sinuschat.util.AppExecutor
import pl.sinusoide.sinuschat.util.DeviceTransfer
import pl.sinusoide.sinuschat.util.LocaleManager
import pl.sinusoide.sinuschat.util.ThemeManager
import androidx.activity.enableEdgeToEdge

class TransferSendActivity : AppCompatActivity() {

    private val dp get() = resources.displayMetrics.density
    private lateinit var content: LinearLayout
    private lateinit var moveIcon: MoveDataIconView
    private lateinit var body: LinearLayout
    private var sender: DeviceTransfer.Sender? = null
    private var bar: ProgressBar? = null
    private var detail: TextView? = null
    private val identity = pl.sinusoide.sinuschat.util.IdentityConfirm(this)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val flow = ThemeManager.isFlow(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(if (flow) Color.TRANSPARENT else ThemeUi.backgroundSolid(this@TransferSendActivity))
        }
        root.addView(topBar())
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((18 * dp).toInt(), (4 * dp).toInt(), (18 * dp).toInt(), (28 * dp).toInt())
        }
        moveIcon = MoveDataIconView(this).apply {
            setColor(pl.sinusoide.sinuschat.ui.IconDrawView.ink(context))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            visibility = View.GONE
        }
        content.addView(moveIcon, LinearLayout.LayoutParams(-1, (196 * dp).toInt()))
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        content.addView(body, LinearLayout.LayoutParams(-1, -2))
        root.addView(ScrollView(this).apply { addView(content) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        content.post {
            identity.confirm(getString(R.string.transfer_send_auth_title), getString(R.string.transfer_send_auth_sub),
                onOk = { start() }, onCancel = { finish() })
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sender?.cancel()
    }

    private fun start() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        sender?.cancel()
        val s = DeviceTransfer.Sender(this)
        sender = s
        AppExecutor.io {
            val code = try { s.open() } catch (_: Throwable) { null }
            AppExecutor.main {
                if (isFinishing) return@main
                if (code == null) showProblem(getString(R.string.transfer_send_no_network)) else showCode(code)
            }
            if (code == null) return@io
            val result = s.serve(
                onConnected = { AppExecutor.main { showSending() } },
                onProgress = { d, t -> AppExecutor.main { progress(d, t) } })
            AppExecutor.main {
                if (isFinishing) return@main
                when (result) {
                    is DeviceTransfer.Result.Ok -> showDone()
                    is DeviceTransfer.Result.OtherAccount -> showProblem(getString(R.string.transfer_send_other_account))
                    is DeviceTransfer.Result.Unconfirmed -> showProblem(getString(R.string.transfer_send_unconfirmed))
                    is DeviceTransfer.Result.Failed -> showProblem(getString(R.string.transfer_send_failed, result.message))
                    is DeviceTransfer.Result.Cancelled -> Unit
                }
            }
        }
    }

    private fun showCode(code: DeviceTransfer.Code) {
        moveIcon.visibility = View.GONE
        body.removeAllViews()
        body.addView(lead(getString(R.string.transfer_send_sub)))
        val qrPx = (220 * dp).toInt()
        body.addView(ImageView(this).apply {
            setImageBitmap(StyledQr.generate(code.encode(), qrPx))
            background = ThemeUi.rounded(context, Color.WHITE, 20f)
            val p = (14 * dp).toInt(); setPadding(p, p, p, p)
        }, LinearLayout.LayoutParams(qrPx + (28 * dp).toInt(), qrPx + (28 * dp).toInt()).apply {
            topMargin = (10 * dp).toInt()
        })
        body.addView(note(getString(R.string.transfer_send_wifi)))
        body.addView(note(code.hosts.joinToString("  ·  ")).apply { alpha = 0.7f })
    }

    private fun showSending() {
        showIcon(MoveDataIconView.Mode.MOVING)
        body.removeAllViews()
        body.addView(title(getString(R.string.transfer_send_sending)))
        body.addView(lead(getString(R.string.setup_transfer_keep_open)))
        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            progressTintList = android.content.res.ColorStateList.valueOf(ThemeUi.accent(context))
            indeterminateTintList = progressTintList
        }
        body.addView(bar, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (14 * dp).toInt() })
        detail = note("")
        body.addView(detail)
    }

    private fun progress(done: Int, total: Int) {
        val b = bar ?: return
        b.isIndeterminate = false
        b.max = total.coerceAtLeast(1)
        b.progress = done.coerceAtMost(b.max)
        detail?.text = getString(R.string.backup_progress, done.coerceAtMost(total), total)
    }

    private fun showDone() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        showIcon(MoveDataIconView.Mode.DONE)
        body.removeAllViews()
        body.addView(title(getString(R.string.transfer_send_done)))
        body.addView(lead(getString(R.string.transfer_send_done_sub)))
        body.addView(bigButton(getString(R.string.transfer_send_close)) { finish() })
    }

    private fun showProblem(message: String) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        showIcon(MoveDataIconView.Mode.ERROR)
        body.removeAllViews()
        body.addView(title(getString(R.string.setup_transfer_error_title)))
        body.addView(lead(message))
        body.addView(bigButton(getString(R.string.transfer_send_retry)) { start() })
        body.addView(TextView(this).apply {
            text = getString(R.string.cancel)
            textSize = 15f
            setTextColor(subColor())
            gravity = Gravity.CENTER
            setPadding(0, (16 * dp).toInt(), 0, (6 * dp).toInt())
            isClickable = true
            setOnClickListener { finish() }
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        })
    }

    private fun showIcon(mode: MoveDataIconView.Mode) {
        moveIcon.visibility = View.VISIBLE
        moveIcon.setMode(mode)
    }

    private fun onScreen(): Int =
        if (ThemeManager.isFlow(this)) Color.WHITE else ThemeUi.textPrimary(this)

    private fun subColor(): Int =
        if (ThemeManager.isFlow(this)) 0xB3FFFFFF.toInt() else ThemeUi.textSecondary(this)

    private fun title(text: String) = TextView(this).apply {
        this.text = text
        textSize = 22f
        setTextColor(onScreen())
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
    }

    private fun lead(text: String) = TextView(this).apply {
        this.text = text
        textSize = 14.5f
        setTextColor(subColor())
        gravity = Gravity.CENTER
        setLineSpacing(0f, 1.25f)
        setPadding((6 * dp).toInt(), (10 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt())
    }

    private fun note(text: String) = TextView(this).apply {
        this.text = text
        textSize = 12.5f
        setTextColor(subColor())
        gravity = Gravity.CENTER
        setLineSpacing(0f, 1.2f)
        setPadding((10 * dp).toInt(), (14 * dp).toInt(), (10 * dp).toInt(), 0)
    }

    private fun bigButton(text: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        textSize = 16f
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        background = ThemeUi.rounded(context, ThemeUi.accent(context), 26f)
        setPadding(0, (14 * dp).toInt(), 0, (14 * dp).toInt())
        isClickable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (22 * dp).toInt() }
    }

    private fun topBar(): View {
        val onBar = onScreen()
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        bar.addView(android.widget.ImageButton(this).apply {
            setImageResource(R.drawable.ic_back)
            background = null
            imageTintList = android.content.res.ColorStateList.valueOf(onBar)
            setPadding((9 * dp).toInt(), (9 * dp).toInt(), (9 * dp).toInt(), (9 * dp).toInt())
            contentDescription = getString(R.string.action_back)
            layoutParams = LinearLayout.LayoutParams((42 * dp).toInt(), (42 * dp).toInt())
            setOnClickListener { finish() }
        })
        bar.addView(TextView(this).apply {
            text = getString(R.string.transfer_send_title)
            setTextColor(onBar); textSize = 17f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                .apply { marginStart = (6 * dp).toInt() }
        })
        FlowGlass.bar(bar, 22f)
        return bar
    }
}
