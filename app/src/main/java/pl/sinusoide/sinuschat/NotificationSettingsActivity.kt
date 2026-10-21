package pl.sinusoide.sinuschat

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.materialswitch.MaterialSwitch
import pl.sinusoide.sinuschat.network.ApiClient
import pl.sinusoide.sinuschat.network.WebSocketManager
import pl.sinusoide.sinuschat.push.PushManager
import pl.sinusoide.sinuschat.util.AppExecutor
import pl.sinusoide.sinuschat.util.MessageNotifier
import pl.sinusoide.sinuschat.util.MuteSettings
import pl.sinusoide.sinuschat.util.NotificationPrefs
import pl.sinusoide.sinuschat.util.NotificationPrefs.Preview
import pl.sinusoide.sinuschat.util.NotificationPrefs.PushMode
import pl.sinusoide.sinuschat.util.PopupHelper
import pl.sinusoide.sinuschat.util.ThemeManager

class NotificationSettingsActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(pl.sinusoide.sinuschat.util.LocaleManager.wrap(newBase))
    }

    private val dp get() = resources.displayMetrics.density

    private var cBg = 0; private var cSurface = 0; private var cCard = 0
    private var cPrimary = 0; private var cSecondary = 0
    private var cAccent = 0; private var cGreen = 0; private var cOrange = 0; private var cRed = 0
    private val cPurple = 0xFF9B6BE0.toInt()
    private val cPink = 0xFFE0578B.toInt()
    private val cTeal = 0xFF26A5B8.toInt()

    private lateinit var heroIcon: ImageView
    private lateinit var heroTitle: TextView
    private lateinit var heroDesc: TextView
    private lateinit var heroPill: TextView

    private lateinit var optNoGoogle: View
    private lateinit var optFcm: View
    private lateinit var fcmDesc: TextView

    private lateinit var stSystem: TextView
    private lateinit var stBackground: TextView
    private lateinit var stFullScreen: TextView
    private lateinit var stServer: TextView

    private lateinit var previewValue: TextView
    private lateinit var quietFromValue: TextView
    private lateinit var quietToValue: TextView
    private val quietDependents = mutableListOf<View>()
    private var quietMain: View? = null
    private var quietUnavailable: TextView? = null
    private lateinit var mutedValue: TextView

    private var serverFcm: Boolean? = null
    private var fcmBusy = false

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
        cGreen = ContextCompat.getColor(this, R.color.sinuschat_success)
        cRed = ContextCompat.getColor(this, R.color.sinuschat_error)
        cOrange = 0xFFE67E22.toInt()

        authed = savedInstanceState?.getBoolean(STATE_AUTHED) == true || !LockManager.hasPin(this)
        if (authed) buildUi() else {
            window.decorView.setBackgroundColor(cBg)
            window.decorView.post { if (!isFinishing && !uiBuilt) requireAuth() }
        }
    }

    private var authed = false
    private var uiBuilt = false

    private val pinConfirm = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) onAuthed() else finish()
    }

    private fun requireAuth() {
        val askPin = {
            pinConfirm.launch(Intent(this, ScreenLockActivity::class.java)
                .putExtra(ScreenLockActivity.EXTRA_MODE, ScreenLockActivity.MODE_CONFIRM)
                .putExtra(ScreenLockActivity.EXTRA_TITLE, getString(R.string.notif_auth_title)))
        }
        if (LockManager.isBiometricEnabled(this) && LockManager.isBiometricAvailable(this)) {
            LockManager.showBiometricPrompt(this,
                onSuccess = { onAuthed() },
                onFailed = { if (!isFinishing) askPin() })
        } else askPin()
    }

    private fun onAuthed() {
        if (isFinishing || uiBuilt) return
        authed = true
        buildUi()
        refreshAll()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_AUTHED, authed)
    }

    override fun onResume() {
        super.onResume()
        if (!uiBuilt) return
        refreshAll()
        if (serverFcm == null && PushManager.isFcmBuilt() && ApiClient.isAuthenticated(this)) {
            AppExecutor.io {
                val ok = PushManager.serverSupportsFcm(this)
                AppExecutor.main { if (!isFinishing) { serverFcm = ok; refreshDelivery() } }
            }
        }
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
            background = ContextCompat.getDrawable(this@NotificationSettingsActivity, R.drawable.bg_menu_ripple)
            contentDescription = getString(R.string.back)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt()))
        header.addView(TextView(this).apply {
            text = getString(R.string.notif_settings_title); textSize = 20f
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
            setPadding((16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt(), (44 * dp).toInt())
        }
        val scroll = ScrollView(this).apply { addView(content); clipToPadding = false }
        outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            v.setPadding(0, 0, 0, insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom); insets
        }

        content.addView(heroCard())

        content.addView(sectionTitle(getString(R.string.notif_section_delivery)))
        content.addView(card().apply {
            optNoGoogle = modeRow(R.drawable.ic_shield, cGreen, getString(R.string.notif_mode_no_google),
                getString(R.string.notif_mode_no_google_desc), getString(R.string.notif_mode_recommended), null) {
                chooseNoGoogle()
            }
            addView(optNoGoogle)
            addView(divider())
            val fcmHolder = arrayOfNulls<TextView>(1)
            optFcm = modeRow(R.drawable.ic_internet, cOrange, getString(R.string.notif_mode_fcm),
                getString(R.string.notif_mode_fcm_desc), null, fcmHolder) {
                chooseFcm()
            }
            fcmDesc = fcmHolder[0]!!
            addView(optFcm)
        })

        content.addView(sectionTitle(getString(R.string.notif_section_status)))
        content.addView(card().apply {
            stSystem = statusRow(R.drawable.ic_notifications, cAccent, R.string.notif_status_system) { openAppNotificationSettings() }
            addView(divider())
            stBackground = statusRow(R.drawable.ic_timer, cOrange, R.string.notif_status_background) { openBatterySettings() }
            if (Build.VERSION.SDK_INT >= 34) {
                addView(divider())
                stFullScreen = statusRow(R.drawable.ic_call_answer, cGreen, R.string.notif_status_fullscreen) { openFullScreenSettings() }
            }
            addView(divider())
            stServer = statusRow(R.drawable.ic_internet, cTeal, R.string.notif_status_server, null)
        })

        content.addView(sectionTitle(getString(R.string.notif_section_messages)))
        content.addView(card().apply {
            addView(switchRow(R.drawable.ic_chat, cAccent, R.string.notif_msg_private, R.string.notif_msg_private_desc,
                NotificationPrefs.messagesEnabled(context)) { NotificationPrefs.setMessagesEnabled(context, it) })
            addView(divider())
            addView(switchRow(R.drawable.ic_group, cPurple, R.string.notif_msg_groups, R.string.notif_msg_groups_desc,
                NotificationPrefs.groupsEnabled(context)) { NotificationPrefs.setGroupsEnabled(context, it) })
            addView(divider())
            addView(switchRow(R.drawable.ic_notifications_off, cSecondary, R.string.notif_msg_ignore_requests,
                R.string.notif_msg_ignore_requests_desc,
                NotificationPrefs.ignoreRequests(context)) { NotificationPrefs.setIgnoreRequests(context, it) })
            addView(divider())
            val pv = valueRow(R.drawable.ic_visibility, cPink, R.string.notif_msg_preview, null) { showPreviewSheet() }
            previewValue = pv.second
            addView(pv.first)
            addView(divider())
            addView(switchRow(R.drawable.ic_notifications, cOrange, R.string.notif_msg_sound, R.string.notif_msg_sound_desc,
                NotificationPrefs.soundEnabled(context)) { NotificationPrefs.setSoundEnabled(context, it) })
            addView(divider())
            addView(switchRow(R.drawable.ic_settings, cTeal, R.string.notif_msg_in_app, R.string.notif_msg_in_app_desc,
                NotificationPrefs.inAppEnabled(context)) { NotificationPrefs.setInAppEnabled(context, it) })
            addView(divider())
            addView(switchRow(R.drawable.ic_close, cRed, R.string.notif_undelivered, R.string.notif_undelivered_desc,
                NotificationPrefs.undeliveredNotice(context)) {
                NotificationPrefs.setUndeliveredNotice(context, it)
                val app = context.applicationContext
                pl.sinusoide.sinuschat.util.AppExecutor.io { pl.sinusoide.sinuschat.messenger.MessageRepository.refreshAllExpiryNotices(app) }
            })
        })

        content.addView(sectionTitle(getString(R.string.notif_section_calls)))
        content.addView(card().apply {
            addView(switchRow(R.drawable.ic_call_missed, cRed, R.string.notif_call_missed, R.string.notif_call_missed_desc,
                NotificationPrefs.missedCallsEnabled(context)) { NotificationPrefs.setMissedCallsEnabled(context, it) })
        })

        content.addView(sectionTitle(getString(R.string.notif_section_safety)))
        content.addView(card().apply {
            addView(switchRow(R.drawable.ic_privacy_warning, cAccent, R.string.notif_safety, R.string.notif_safety_desc,
                NotificationPrefs.safetyPrompts(context)) { NotificationPrefs.setSafetyPrompts(context, it) })
        })

        content.addView(sectionTitle(getString(R.string.notif_section_updates)))
        content.addView(card().apply {
            addView(switchRow(R.drawable.ic_update, cAccent, R.string.notif_update, R.string.notif_update_desc,
                NotificationPrefs.updateNotices(context)) {
                NotificationPrefs.setUpdateNotices(context, it)
            })
        })

        content.addView(sectionTitle(getString(R.string.notif_section_quiet)))
        content.addView(card().apply {
            val main = switchRow(R.drawable.ic_moon, cPurple, R.string.notif_quiet, R.string.notif_quiet_desc,
                NotificationPrefs.quietEnabled(context)) {
                NotificationPrefs.setQuietEnabled(context, it)
                refreshQuiet(); refreshHero()
            }
            quietMain = main
            addView(main)
            addView(divider())
            val from = valueRow(R.drawable.ic_timer, cPurple, R.string.notif_quiet_from, null) { pickQuietTime(true) }
            quietFromValue = from.second
            addView(from.first); quietDependents.add(from.first)
            addView(divider())
            val to = valueRow(R.drawable.ic_timer, cPurple, R.string.notif_quiet_to, null) { pickQuietTime(false) }
            quietToValue = to.second
            addView(to.first); quietDependents.add(to.first)
            addView(divider())
            val calls = switchRow(R.drawable.ic_call_answer, cGreen, R.string.notif_quiet_calls, R.string.notif_quiet_calls_desc,
                NotificationPrefs.quietAllowCalls(context)) { NotificationPrefs.setQuietAllowCalls(context, it) }
            addView(calls); quietDependents.add(calls)
            addView(divider())
            val vib = switchRow(R.drawable.ic_vibrations, cPurple, R.string.notif_quiet_vibrate, R.string.notif_quiet_vibrate_desc,
                NotificationPrefs.quietVibrate(context)) { NotificationPrefs.setQuietVibrate(context, it) }
            addView(vib); quietDependents.add(vib)
        })
        quietUnavailable = TextView(this).apply {
            text = getString(R.string.notif_quiet_unavailable)
            textSize = 12.5f; setTextColor(cSecondary)
            setLineSpacing(2 * dp, 1f)
            setPadding((6 * dp).toInt(), (10 * dp).toInt(), (6 * dp).toInt(), 0)
            visibility = View.GONE
        }
        content.addView(quietUnavailable)

        content.addView(sectionTitle(getString(R.string.notif_section_other)))
        content.addView(card().apply {
            addView(switchRow(R.drawable.ic_group, cAccent, R.string.notif_other_group_added, null,
                NotificationPrefs.groupAddedEnabled(context)) { NotificationPrefs.setGroupAddedEnabled(context, it) })
            addView(divider())
            addView(switchRow(R.drawable.ic_report, cOrange, R.string.notif_other_reports, null,
                NotificationPrefs.reportUpdatesEnabled(context)) { NotificationPrefs.setReportUpdatesEnabled(context, it) })
            addView(divider())
            val muted = valueRow(R.drawable.ic_notifications_off, cSecondary, R.string.notif_other_muted, R.string.notif_other_muted_desc) {
                confirmUnmuteAll()
            }
            mutedValue = muted.second
            addView(muted.first)
        })

        content.addView(sectionTitle(getString(R.string.notif_section_system)))
        content.addView(card().apply {
            addView(valueRow(R.drawable.ic_settings, cTeal, R.string.notif_sys_channel, R.string.notif_sys_channel_desc) {
                openChannelSettings()
            }.first)
            addView(divider())
            addView(valueRow(R.drawable.ic_send, cAccent, R.string.notif_sys_test, R.string.notif_sys_test_desc) {
                sendTestNotification()
            }.first)
        })

        content.addView(TextView(this).apply {
            text = getString(R.string.notif_footer)
            textSize = 12.5f; setTextColor(cSecondary); gravity = Gravity.CENTER
            setLineSpacing(0f, 1.2f)
            setPadding((12 * dp).toInt(), (20 * dp).toInt(), (12 * dp).toInt(), 0)
        })

        setContentView(outer)
        ViewCompat.requestApplyInsets(outer)
        uiBuilt = true
        pl.sinusoide.sinuschat.util.SettingsHighlight.apply(this)

        for (i in 0 until content.childCount) {
            val v = content.getChildAt(i)
            v.alpha = 0f; v.translationY = 14 * dp
            v.animate().alpha(1f).translationY(0f).setStartDelay(20L * i.coerceAtMost(14))
                .setDuration(320).start()
        }
    }

    private fun heroCard(): View {
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = rounded(cCard, 20f)
            setPadding((20 * dp).toInt(), (22 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt())
        }
        heroIcon = ImageView(this).apply { val p = (16 * dp).toInt(); setPadding(p, p, p, p) }
        hero.addView(heroIcon, LinearLayout.LayoutParams((64 * dp).toInt(), (64 * dp).toInt()).apply {
            bottomMargin = (12 * dp).toInt()
        })
        heroTitle = TextView(this).apply {
            textSize = 17f; setTextColor(cPrimary); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        }
        hero.addView(heroTitle)
        heroDesc = TextView(this).apply {
            textSize = 13f; setTextColor(cSecondary); gravity = Gravity.CENTER
            setLineSpacing(0f, 1.15f)
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

    private fun refreshAll() {
        refreshHero()
        refreshDelivery()
        refreshStatus()
        refreshPreview()
        refreshQuiet()
        refreshMuted()
    }

    private fun notificationsAllowed() = NotificationManagerCompat.from(this).areNotificationsEnabled()

    private fun backgroundUnrestricted(): Boolean =
        getSystemService(android.os.PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) == true

    private fun fullScreenAllowed(): Boolean =
        Build.VERSION.SDK_INT < 34 ||
            getSystemService(android.app.NotificationManager::class.java)?.canUseFullScreenIntent() != false

    private fun refreshHero() {
        val mode = NotificationPrefs.pushMode(this)
        val (icon, color, title, desc) = when {
            !notificationsAllowed() -> Quad(R.drawable.ic_notifications_off, cRed,
                getString(R.string.notif_hero_off), getString(R.string.notif_hero_off_desc))
            mode == PushMode.NO_GOOGLE && !backgroundUnrestricted() -> Quad(R.drawable.ic_timer, cOrange,
                getString(R.string.notif_hero_bg), getString(R.string.notif_hero_bg_desc))
            NotificationPrefs.isQuietNow(this) -> Quad(R.drawable.ic_moon, cPurple,
                getString(R.string.notif_hero_quiet),
                getString(R.string.notif_hero_quiet_desc, NotificationPrefs.formatMinutes(NotificationPrefs.quietTo(this))))
            else -> Quad(R.drawable.ic_notifications, cGreen, getString(R.string.notif_hero_ok),
                getString(if (mode == PushMode.FCM) R.string.notif_hero_ok_desc_fcm else R.string.notif_hero_ok_desc_ng))
        }
        heroIcon.setImageResource(icon)
        heroIcon.setColorFilter(color)
        heroIcon.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL; setColor((color and 0x00FFFFFF) or 0x26000000)
        }
        heroTitle.text = title
        heroDesc.text = desc
        val pillColor = if (mode == PushMode.FCM) cOrange else cGreen
        heroPill.text = getString(if (mode == PushMode.FCM) R.string.notif_mode_fcm else R.string.notif_mode_no_google)
        heroPill.setTextColor(pillColor)
        heroPill.background = GradientDrawable().apply {
            cornerRadius = 14 * dp; setColor((pillColor and 0x00FFFFFF) or 0x24000000)
        }
    }

    private fun refreshDelivery() {
        val mode = NotificationPrefs.pushMode(this)
        styleOption(optNoGoogle, mode == PushMode.NO_GOOGLE)
        styleOption(optFcm, mode == PushMode.FCM)
        val unavailable = fcmUnavailableReason()
        fcmDesc.text = when {
            fcmBusy -> getString(R.string.notif_fcm_connecting)
            unavailable != null && mode != PushMode.FCM -> unavailable
            else -> getString(R.string.notif_mode_fcm_desc)
        }
        optFcm.alpha = if (unavailable != null && mode != PushMode.FCM) 0.55f else 1f
    }

    private fun fcmUnavailableReason(): String? = when {
        !PushManager.isFcmBuilt() -> getString(R.string.notif_fcm_not_built)
        !PushManager.hasPlayServices(this) -> getString(R.string.notif_fcm_no_play)
        serverFcm == false -> getString(R.string.notif_fcm_no_server)
        else -> null
    }

    private fun refreshStatus() {
        setState(stSystem, notificationsAllowed(), R.string.notif_state_on, R.string.notif_state_off)
        if (NotificationPrefs.pushMode(this) == PushMode.FCM && !backgroundUnrestricted())
            setState(stBackground, true, R.string.notif_state_not_needed, R.string.notif_state_restricted)
        else setState(stBackground, backgroundUnrestricted(), R.string.notif_state_unrestricted, R.string.notif_state_restricted)
        if (Build.VERSION.SDK_INT >= 34) setState(stFullScreen, fullScreenAllowed(), R.string.notif_state_allowed, R.string.notif_state_off)
        if (NotificationPrefs.pushMode(this) == PushMode.FCM && !WebSocketManager.isConnected())
            setState(stServer, true, R.string.notif_state_on_demand, R.string.notif_state_disconnected)
        else setState(stServer, WebSocketManager.isConnected(), R.string.notif_state_connected, R.string.notif_state_disconnected)
    }

    private fun setState(tv: TextView, ok: Boolean, okRes: Int, badRes: Int) {
        tv.text = getString(if (ok) okRes else badRes)
        tv.setTextColor(if (ok) cGreen else cOrange)
    }

    private fun refreshPreview() {
        previewValue.text = getString(when (NotificationPrefs.preview(this)) {
            Preview.FULL -> R.string.notif_preview_full
            Preview.NAME -> R.string.notif_preview_name
            Preview.HIDDEN -> R.string.notif_preview_hidden_short
        })
    }

    private fun refreshQuiet() {
        quietFromValue.text = NotificationPrefs.formatMinutes(NotificationPrefs.quietFrom(this))
        quietToValue.text = NotificationPrefs.formatMinutes(NotificationPrefs.quietTo(this))
        val available = NotificationPrefs.quietAvailable(this)
        val on = available && NotificationPrefs.quietEnabled(this)
        quietMain?.let { v ->
            v.animate().alpha(if (available) 1f else 0.45f).setDuration(180).start()
            v.isEnabled = available
            v.findViewWithTag<MaterialSwitch>("switch")?.isEnabled = available
        }
        quietUnavailable?.visibility = if (available) View.GONE else View.VISIBLE
        quietDependents.forEach { v ->
            v.animate().alpha(if (on) 1f else 0.45f).setDuration(180).start()
            v.isEnabled = on
            v.findViewWithTag<MaterialSwitch>("switch")?.isEnabled = on
        }
    }

    private fun mutedConversationIds(): List<String> =
        getSharedPreferences("sinuschat_mute", MODE_PRIVATE).all.keys
            .filter { it.startsWith("mute_") }
            .map { it.removePrefix("mute_") }
            .filter { MuteSettings.isMuted(this, it) }

    private fun refreshMuted() {
        val n = mutedConversationIds().size
        mutedValue.text = n.toString()
        mutedValue.setTextColor(if (n > 0) cPrimary else cSecondary)
    }

    private fun chooseNoGoogle() {
        if (fcmBusy || NotificationPrefs.pushMode(this) == PushMode.NO_GOOGLE) return
        showSheet(R.drawable.ic_shield, cGreen, getString(R.string.notif_ng_confirm_title),
            getString(R.string.notif_ng_confirm_desc), getString(R.string.notif_ng_confirm_btn), cGreen) {
            fcmBusy = true
            refreshDelivery()
            PushManager.disableFcm(this) { ok ->
                fcmBusy = false
                if (isFinishing) return@disableFcm
                refreshAll()
                if (ok) PopupHelper.showSuccess(this, getString(R.string.notif_ng_on))
                else PopupHelper.show(this, getString(R.string.notif_ng_offline))
            }
            pl.sinusoide.sinuschat.messenger.MessageListenerService.modeChanged(this)
        }
    }

    private fun chooseFcm() {
        if (fcmBusy || NotificationPrefs.pushMode(this) == PushMode.FCM) return
        fcmUnavailableReason()?.let { PopupHelper.show(this, it); return }
        showSheet(R.drawable.ic_internet, cOrange, getString(R.string.notif_fcm_confirm_title),
            getString(R.string.notif_fcm_confirm_desc), getString(R.string.notif_fcm_confirm_btn), cOrange) {
            fcmBusy = true
            refreshDelivery()
            PushManager.enableFcm(this) { result ->
                fcmBusy = false
                if (result == PushManager.Result.OK)
                    pl.sinusoide.sinuschat.messenger.MessageListenerService.modeChanged(applicationContext)
                if (isFinishing) return@enableFcm
                if (result == PushManager.Result.SERVER_UNAVAILABLE) serverFcm = false
                refreshAll()
                when (result) {
                    PushManager.Result.OK -> PopupHelper.showSuccess(this, getString(R.string.notif_fcm_on))
                    PushManager.Result.NOT_BUILT -> PopupHelper.showError(this, getString(R.string.notif_fcm_not_built))
                    PushManager.Result.NO_PLAY_SERVICES -> PopupHelper.showError(this, getString(R.string.notif_fcm_no_play))
                    PushManager.Result.SERVER_UNAVAILABLE -> PopupHelper.showError(this, getString(R.string.notif_fcm_no_server))
                    PushManager.Result.FAILED -> PopupHelper.showError(this, getString(R.string.notif_fcm_failed))
                }
            }
        }
    }

    private fun showPreviewSheet() {
        val sheet = BottomSheetDialog(this, R.style.Theme_SinusChat_BottomSheet)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (20 * dp).toInt())
        }
        root.addView(View(this).apply { background = rounded(pl.sinusoide.sinuschat.ui.ThemeUi.handle(context), 3f) },
            LinearLayout.LayoutParams((36 * dp).toInt(), (4 * dp).toInt()).apply {
                gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = (16 * dp).toInt()
            })
        root.addView(TextView(this).apply {
            text = getString(R.string.notif_msg_preview); textSize = 19f; setTextColor(cPrimary)
            typeface = Typeface.DEFAULT_BOLD
            setPadding((8 * dp).toInt(), 0, 0, (12 * dp).toInt())
        })
        val current = NotificationPrefs.preview(this)
        val options = listOf(
            Triple(Preview.FULL, R.string.notif_preview_full, R.string.notif_preview_full_desc),
            Triple(Preview.NAME, R.string.notif_preview_name, R.string.notif_preview_name_desc),
            Triple(Preview.HIDDEN, R.string.notif_preview_hidden, R.string.notif_preview_hidden_desc)
        )
        val box = card()
        options.forEachIndexed { i, (mode, title, desc) ->
            if (i > 0) box.addView(divider())
            val row = optionRow(when (mode) {
                Preview.FULL -> R.drawable.ic_chat
                Preview.NAME -> R.drawable.ic_visibility
                Preview.HIDDEN -> R.drawable.ic_lock
            }, when (mode) { Preview.FULL -> cAccent; Preview.NAME -> cPink; Preview.HIDDEN -> cPurple },
                getString(title), getString(desc)) {
                NotificationPrefs.setPreview(this, mode)
                refreshPreview()
                sheet.dismiss()
            }
            styleOption(row, mode == current)
            box.addView(row)
        }
        root.addView(box)
        sheet.setContentView(root)
        pl.sinusoide.sinuschat.ui.FlowGlass.show(sheet)
    }

    private fun pickQuietTime(from: Boolean) {
        if (!NotificationPrefs.quietEnabled(this)) return
        val minutes = if (from) NotificationPrefs.quietFrom(this) else NotificationPrefs.quietTo(this)
        val picker = com.google.android.material.timepicker.MaterialTimePicker.Builder()
            .setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_24H)
            .setHour(minutes / 60).setMinute(minutes % 60)
            .setTitleText(getString(if (from) R.string.notif_quiet_from else R.string.notif_quiet_to))
            .build()
        picker.addOnPositiveButtonClickListener {
            val m = picker.hour * 60 + picker.minute
            if (from) NotificationPrefs.setQuietRange(this, m, NotificationPrefs.quietTo(this))
            else NotificationPrefs.setQuietRange(this, NotificationPrefs.quietFrom(this), m)
            refreshQuiet(); refreshHero()
        }
        picker.show(supportFragmentManager, "quiet_time")
    }

    private fun confirmUnmuteAll() {
        val ids = mutedConversationIds()
        if (ids.isEmpty()) { PopupHelper.show(this, getString(R.string.notif_other_muted_none)); return }
        showSheet(R.drawable.ic_notifications, cAccent, getString(R.string.notif_unmute_all_title),
            getString(R.string.notif_unmute_all_desc, ids.size), getString(R.string.notif_unmute_all_btn), cAccent) {
            ids.forEach { MuteSettings.setMutedUntil(this, it, 0L) }
            sendBroadcast(Intent("pl.sinusoide.sinuschat.REFRESH_CONVERSATIONS").setPackage(packageName))
            refreshMuted()
            PopupHelper.showSuccess(this, getString(R.string.notif_unmute_all_done))
        }
    }

    private fun sendTestNotification() {
        if (!notificationsAllowed()) {
            PopupHelper.showError(this, getString(R.string.notif_test_blocked))
            openAppNotificationSettings()
            return
        }
        MessageNotifier.createChannel(this)
        val hidden = NotificationPrefs.preview(this) == Preview.HIDDEN
        val notif = NotificationCompat.Builder(this, MessageNotifier.channelId(this))
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(if (hidden) getString(R.string.app_name) else getString(R.string.notif_test_title))
            .setContentText(if (hidden) getString(R.string.new_message) else getString(R.string.notif_test_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setColor(cAccent)
            .setSilent(NotificationPrefs.silentMessage(this))
            .build()
        try {
            NotificationManagerCompat.from(this).notify(TEST_NOTIF_ID, notif)
            pl.sinusoide.sinuschat.util.NotificationSound.alert(this, false)
            PopupHelper.showSuccess(this, getString(R.string.notif_test_sent))
        } catch (_: SecurityException) {
            PopupHelper.showError(this, getString(R.string.notif_test_blocked))
        }
    }

    private fun openAppNotificationSettings() = safeStart(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))

    private fun openChannelSettings() {
        MessageNotifier.createChannel(this)
        safeStart(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, MessageNotifier.channelId(this)))
    }

    private fun openBatterySettings() {
        if (!backgroundUnrestricted()) {
            @android.annotation.SuppressLint("BatteryLife")
            val req = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            if (safeStart(req)) return
        }
        safeStart(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    private fun openFullScreenSettings() {
        if (Build.VERSION.SDK_INT < 34) return
        if (!safeStart(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))) {
            openAppNotificationSettings()
        }
    }

    private fun safeStart(intent: Intent): Boolean = try { startActivity(intent); true } catch (_: Exception) { false }

    private fun showSheet(icon: Int, color: Int, title: String, desc: String, button: String, buttonColor: Int, onConfirm: () -> Unit) {
        val sheet = BottomSheetDialog(this, R.style.Theme_SinusChat_BottomSheet)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((24 * dp).toInt(), (12 * dp).toInt(), (24 * dp).toInt(), (20 * dp).toInt())
        }
        root.addView(View(this).apply { background = rounded(pl.sinusoide.sinuschat.ui.ThemeUi.handle(context), 3f) },
            LinearLayout.LayoutParams((36 * dp).toInt(), (4 * dp).toInt()).apply { bottomMargin = (20 * dp).toInt() })
        root.addView(ImageView(this).apply {
            setImageResource(icon); setColorFilter(color)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor((color and 0x00FFFFFF) or 0x26000000) }
            val p = (16 * dp).toInt(); setPadding(p, p, p, p)
        }, LinearLayout.LayoutParams((64 * dp).toInt(), (64 * dp).toInt()).apply { bottomMargin = (16 * dp).toInt() })
        root.addView(TextView(this).apply {
            text = title; textSize = 20f; setTextColor(cPrimary); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = desc; textSize = 14f; setTextColor(cSecondary); gravity = Gravity.CENTER
            setLineSpacing(0f, 1.15f)
            setPadding(0, (8 * dp).toInt(), 0, (22 * dp).toInt())
        })
        root.addView(TextView(this).apply {
            text = button; textSize = 16f; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            background = rounded(buttonColor, 26f)
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

    private data class Quad(val icon: Int, val color: Int, val title: String, val desc: String)

    private fun modeRow(icon: Int, color: Int, title: String, desc: String, badge: String?,
                        descOut: Array<TextView?>?, onClick: () -> Unit): View {
        val row = optionRow(icon, color, title, desc, badge, onClick)
        descOut?.set(0, row.findViewWithTag("desc"))
        return row
    }

    private fun optionRow(icon: Int, color: Int, title: String, desc: String, badge: String? = null, onClick: () -> Unit): View {
        val check = ImageView(this).apply { tag = "check"; val p = (4 * dp).toInt(); setPadding(p, p, p, p) }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * dp).toInt(), (13 * dp).toInt(), (14 * dp).toInt(), (13 * dp).toInt())
            background = ContextCompat.getDrawable(this@NotificationSettingsActivity, R.drawable.bg_menu_ripple)
            isClickable = true
            setOnClickListener { onClick() }
            addView(iconTile(icon, color))
            addView(LinearLayout(this@NotificationSettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(LinearLayout(this@NotificationSettingsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    addView(TextView(this@NotificationSettingsActivity).apply {
                        text = title; textSize = 15.5f; setTextColor(cPrimary)
                    })
                    if (badge != null) addView(TextView(this@NotificationSettingsActivity).apply {
                        text = badge; textSize = 10.5f; typeface = Typeface.DEFAULT_BOLD
                        setTextColor(cGreen)
                        background = GradientDrawable().apply {
                            cornerRadius = 8 * dp; setColor((cGreen and 0x00FFFFFF) or 0x24000000)
                        }
                        setPadding((7 * dp).toInt(), (2 * dp).toInt(), (7 * dp).toInt(), (2 * dp).toInt())
                    }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = (8 * dp).toInt() })
                })
                addView(TextView(this@NotificationSettingsActivity).apply {
                    tag = "desc"
                    text = desc; textSize = 12.5f; setTextColor(cSecondary)
                    setLineSpacing(0f, 1.12f)
                    setPadding(0, (3 * dp).toInt(), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = (8 * dp).toInt() })
            addView(FrameLayout(this@NotificationSettingsActivity).apply { addView(check, FrameLayout.LayoutParams(-1, -1)) },
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
    }

    private fun switchRow(icon: Int, color: Int, titleRes: Int, descRes: Int?, checked: Boolean,
                          onChange: (Boolean) -> Unit): View {
        val sw = MaterialSwitch(this).apply {
            tag = "switch"
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
            background = ContextCompat.getDrawable(this@NotificationSettingsActivity, R.drawable.bg_menu_ripple)
            isClickable = true
            setOnClickListener { if (sw.isEnabled) sw.toggle() }
            addView(iconTile(icon, color))
            addView(textBlock(getString(titleRes), descRes?.let { getString(it) }),
                LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = (8 * dp).toInt() })
            addView(sw)
        }
    }

    private fun valueRow(icon: Int, color: Int, titleRes: Int, descRes: Int?, onClick: () -> Unit): Pair<View, TextView> {
        val value = TextView(this).apply {
            textSize = 14f; setTextColor(cSecondary); typeface = Typeface.DEFAULT_BOLD
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
            background = ContextCompat.getDrawable(this@NotificationSettingsActivity, R.drawable.bg_menu_ripple)
            isClickable = true
            setOnClickListener { if (isEnabled) onClick() }
            addView(iconTile(icon, color))
            addView(textBlock(getString(titleRes), descRes?.let { getString(it) }),
                LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = (8 * dp).toInt() })
            addView(value)
            addView(ImageView(this@NotificationSettingsActivity).apply {
                setImageResource(R.drawable.ic_chevron_right); setColorFilter(cSecondary)
            }, LinearLayout.LayoutParams((20 * dp).toInt(), (20 * dp).toInt()).apply { marginStart = (4 * dp).toInt() })
        }
        return row to value
    }

    private fun LinearLayout.statusRow(icon: Int, color: Int, titleRes: Int, onClick: (() -> Unit)?): TextView {
        val value = TextView(this@NotificationSettingsActivity).apply {
            textSize = 13.5f; typeface = Typeface.DEFAULT_BOLD
        }
        addView(LinearLayout(this@NotificationSettingsActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
            if (onClick != null) {
                background = ContextCompat.getDrawable(this@NotificationSettingsActivity, R.drawable.bg_menu_ripple)
                isClickable = true
                setOnClickListener { onClick() }
            }
            addView(iconTile(icon, color))
            addView(textBlock(getString(titleRes), null), LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = (8 * dp).toInt() })
            addView(value)
            if (onClick != null) addView(ImageView(this@NotificationSettingsActivity).apply {
                setImageResource(R.drawable.ic_chevron_right); setColorFilter(cSecondary)
            }, LinearLayout.LayoutParams((20 * dp).toInt(), (20 * dp).toInt()).apply { marginStart = (4 * dp).toInt() })
        })
        return value
    }

    private fun textBlock(title: String, desc: String?) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(this@NotificationSettingsActivity).apply {
            text = title; textSize = 15.5f; setTextColor(cPrimary)
        })
        if (desc != null) addView(TextView(this@NotificationSettingsActivity).apply {
            text = desc; textSize = 12.5f; setTextColor(cSecondary)
            setLineSpacing(0f, 1.12f)
            setPadding(0, (2 * dp).toInt(), 0, 0)
        })
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

    companion object {
        private const val TEST_NOTIF_ID = 9190
        private const val STATE_AUTHED = "authed"
    }
}
