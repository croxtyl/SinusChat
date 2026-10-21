package pl.sinusoide.sinuschat.call

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import pl.sinusoide.sinuschat.R
import pl.sinusoide.sinuschat.util.ContactCache
import pl.sinusoide.sinuschat.util.PopupHelper

class WalkieTalkieActivity : AppCompatActivity() {

    companion object {
        private const val REQ_MIC = 52

        fun intent(context: Context, groupId: String): Intent =
            Intent(context, WalkieTalkieActivity::class.java).putExtra("groupId", groupId)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(pl.sinusoide.sinuschat.util.LocaleManager.wrap(newBase))
    }

    private val dp get() = resources.displayMetrics.density
    private val main = Handler(Looper.getMainLooper())
    private lateinit var groupId: String
    private lateinit var title: String
    private var session: WalkieTalkie.Session? = null
    private val names = HashMap<String, String>()
    private lateinit var tvStatus: TextView
    private lateinit var tvHint: TextView
    private lateinit var ptt: FrameLayout
    private lateinit var pttIcon: ImageView
    private lateinit var btnSpeaker: pl.sinusoide.sinuschat.ui.CallButtonView
    private lateinit var btnVolume: pl.sinusoide.sinuschat.ui.CallButtonView
    private var tone: android.media.ToneGenerator? = null
    private var volumePtt = false
    private var volumeHeld = false

    private val listener: () -> Unit = { render() }
    private val ticker = object : Runnable {
        override fun run() {
            render()
            main.postDelayed(this, 400)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        groupId = intent.getStringExtra("groupId").orEmpty()
        if (!groupId.startsWith("g_")) { finish(); return }
        if (WalkieTalkie.keys(this, groupId) == null) {
            PopupHelper.showError(this, getString(R.string.walkie_no_key))
            finish(); return
        }
        if (GroupCallSession.current?.ended == false || CallActivity.activeConversationId != null) {
            PopupHelper.showError(this, getString(R.string.gcall_other_active))
            finish(); return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        title = GroupCallRing.groupTitle(this, groupId)
        volumePtt = getSharedPreferences("walkie", MODE_PRIVATE).getBoolean("volume_ptt", false)
        buildUi()
        loadNames()
        try { tone = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 40) } catch (_: Exception) {}
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) begin()
        else requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_MIC) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) begin()
        else { PopupHelper.showError(this, getString(R.string.gcall_need_mic)); finish() }
    }

    private fun begin() {
        val s = WalkieTalkie.start(this, groupId, getString(R.string.walkie_notif_title, title))
        if (s == null) {
            PopupHelper.showError(this, getString(R.string.walkie_failed))
            finish(); return
        }
        session = s
        s.addListener(listener)
        main.post(ticker)
    }

    private fun loadNames() {
        pl.sinusoide.sinuschat.messenger.GroupRepository.getGroupInfo(this, groupId) { info ->
            if (info == null || isFinishing) return@getGroupInfo
            info.members.forEach { m -> if (m.displayName.isNotBlank() && !m.displayName.startsWith("p1:")) names[m.userHash] = m.displayName }
            render()
        }
    }

    private fun nameOf(peer: WalkieTalkie.Peer): String {
        val hash = peer.hash
        if (hash.isEmpty()) return getString(R.string.caller_unknown)
        return ContactCache.getNick(this, hash) ?: names[hash] ?: ContactCache.getName(this, hash) ?: getString(R.string.caller_unknown)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(0xFF0B1620.toInt()) }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((20 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt(), (28 * dp).toInt())
        }
        column.addView(TextView(this).apply {
            text = title; textSize = 22f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(-1, -2))
        column.addView(TextView(this).apply {
            text = getString(R.string.walkie_local_only); textSize = 13f; setTextColor(0xFF7F91A4.toInt())
            gravity = Gravity.CENTER; setPadding(0, (4 * dp).toInt(), 0, 0)
        }, LinearLayout.LayoutParams(-1, -2))
        tvStatus = TextView(this).apply {
            textSize = 16f; setTextColor(0xFFB0C4D6.toInt()); gravity = Gravity.CENTER
            setPadding(0, (28 * dp).toInt(), 0, 0)
        }
        column.addView(tvStatus, LinearLayout.LayoutParams(-1, -2))
        column.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))

        ptt = FrameLayout(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF1E3A52.toInt()) }
            isClickable = true
            contentDescription = getString(R.string.walkie_hold)
        }
        pttIcon = ImageView(this).apply { setImageResource(R.drawable.ic_walkie_talkie); setColorFilter(Color.WHITE) }
        ptt.addView(pttIcon, FrameLayout.LayoutParams((84 * dp).toInt(), (84 * dp).toInt(), Gravity.CENTER))
        ptt.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> pressTalk()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> releaseTalk()
            }
            true
        }
        column.addView(ptt, LinearLayout.LayoutParams((200 * dp).toInt(), (200 * dp).toInt()))
        tvHint = TextView(this).apply {
            text = getString(R.string.walkie_hold); textSize = 15f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(0, (16 * dp).toInt(), 0, 0)
        }
        column.addView(tvHint, LinearLayout.LayoutParams(-1, -2))
        column.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))

        val glass = pl.sinusoide.sinuschat.ui.LockPalette.glass(pl.sinusoide.sinuschat.ui.ThemeUi.accent(this))
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false; clipToPadding = false
        }
        btnSpeaker = pl.sinusoide.sinuschat.ui.CallButtonView(this, R.drawable.ic_call_speaker_on, palette = glass).apply {
            contentDescription = getString(R.string.walkie_speaker)
            setOnClickListener {
                val s = session ?: return@setOnClickListener
                s.setSpeaker(!s.speakerOn)
                render()
            }
        }
        bar.addView(btnSpeaker, LinearLayout.LayoutParams((62 * dp).toInt(), (62 * dp).toInt()))
        val end = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = 31 * dp; setColor(0xFFE5484D.toInt()) }
            foreground = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x40FFFFFF), null,
                GradientDrawable().apply { cornerRadius = 31 * dp; setColor(Color.WHITE) })
            isClickable = true
            contentDescription = getString(R.string.walkie_leave)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                session?.stop(); finish()
            }
            addView(ImageView(this@WalkieTalkieActivity).apply {
                setImageResource(R.drawable.ic_call_end); setColorFilter(Color.WHITE)
            }, LinearLayout.LayoutParams((30 * dp).toInt(), (30 * dp).toInt()))
        }
        bar.addView(end, LinearLayout.LayoutParams(0, (62 * dp).toInt(), 1f).apply {
            marginStart = (16 * dp).toInt(); marginEnd = (16 * dp).toInt()
        })
        btnVolume = pl.sinusoide.sinuschat.ui.CallButtonView(this, R.drawable.ic_volume_touch, palette = glass).apply {
            contentDescription = getString(R.string.walkie_volume_ptt)
            active = volumePtt
            setOnClickListener { toggleVolumePtt() }
        }
        bar.addView(btnVolume, LinearLayout.LayoutParams((62 * dp).toInt(), (62 * dp).toInt()))
        column.addView(bar, LinearLayout.LayoutParams(-1, -2))

        root.addView(column, FrameLayout.LayoutParams(-1, -1))
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(root)
    }

    private fun pressTalk() {
        if (session?.startTalking() == true) {
            ptt.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            try { tone?.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 70) } catch (_: Exception) {}
        }
        render()
    }

    private fun releaseTalk() {
        if (session?.isTalking == true) {
            session?.stopTalking()
            try { tone?.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 60) } catch (_: Exception) {}
        }
        render()
    }

    private fun toggleVolumePtt() {
        volumePtt = !volumePtt
        getSharedPreferences("walkie", MODE_PRIVATE).edit().putBoolean("volume_ptt", volumePtt).apply()
        btnVolume.active = volumePtt
        PopupHelper.show(this, getString(if (volumePtt) R.string.walkie_volume_on else R.string.walkie_volume_off))
        render()
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val code = event.keyCode
        if (volumePtt && session != null &&
            (code == android.view.KeyEvent.KEYCODE_VOLUME_UP || code == android.view.KeyEvent.KEYCODE_VOLUME_DOWN)) {
            when (event.action) {
                android.view.KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0 && !volumeHeld) { volumeHeld = true; pressTalk() }
                android.view.KeyEvent.ACTION_UP -> if (volumeHeld) { volumeHeld = false; releaseTalk() }
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        super.onPause()
        if (session?.isTalking == true || volumeHeld) {
            volumeHeld = false
            releaseTalk()
        }
    }

    private fun render() {
        val s = session
        if (s == null) { tvStatus.text = getString(R.string.gcall_connecting); return }
        if (s.stopped) { finish(); return }
        val now = System.currentTimeMillis()
        val talkers = s.peers.values.filter { now - it.talkingAt < 600 }
        tvStatus.text = when {
            talkers.isNotEmpty() -> getString(R.string.walkie_speaking, talkers.joinToString(", ") { nameOf(it) })
            s.peers.isEmpty() -> getString(R.string.walkie_alone)
            else -> getString(R.string.walkie_peers, s.peers.values.joinToString(", ") { nameOf(it) })
        }
        val talking = s.isTalking
        (ptt.background as? GradientDrawable)?.setColor(when {
            talking -> 0xFF3FB97A.toInt()
            talkers.isNotEmpty() -> 0xFF5288C1.toInt()
            else -> 0xFF1E3A52.toInt()
        })
        ptt.scaleX = if (talking) 1.06f else 1f
        ptt.scaleY = ptt.scaleX
        tvHint.text = getString(when {
            talking -> R.string.walkie_talking
            volumePtt -> R.string.walkie_hold_volume
            else -> R.string.walkie_hold
        })
        btnSpeaker.setIcon(if (s.speakerOn) R.drawable.ic_call_speaker_on else R.drawable.ic_call_speaker_off)
        btnSpeaker.active = s.speakerOn
    }

    override fun onDestroy() {
        super.onDestroy()
        main.removeCallbacks(ticker)
        session?.let { it.stopTalking(); it.removeListener(listener) }
        try { tone?.release() } catch (_: Exception) {}
    }
}
