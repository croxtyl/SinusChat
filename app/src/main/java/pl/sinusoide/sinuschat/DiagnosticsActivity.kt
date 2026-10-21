package pl.sinusoide.sinuschat

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import pl.sinusoide.sinuschat.crypto.DbCrypto
import pl.sinusoide.sinuschat.crypto.DbKeyManager
import pl.sinusoide.sinuschat.messenger.MessageListenerService
import pl.sinusoide.sinuschat.network.ApiClient
import pl.sinusoide.sinuschat.network.WebSocketManager
import pl.sinusoide.sinuschat.util.AppExecutor
import pl.sinusoide.sinuschat.util.NetworkSettings
import pl.sinusoide.sinuschat.util.PopupHelper
import pl.sinusoide.sinuschat.util.PrivacySettings
import pl.sinusoide.sinuschat.util.ThemeManager

class DiagnosticsActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(pl.sinusoide.sinuschat.util.LocaleManager.wrap(newBase))
    }

    private val dp get() = resources.displayMetrics.density

    private var cBg = 0; private var cSurface = 0; private var cCard = 0
    private var cPrimary = 0; private var cSecondary = 0
    private var cAccent = 0; private var cGreen = 0; private var cRed = 0; private var cOrange = 0

    private enum class State { OK, PROBLEM, LOADING, INFO }

    private class Check(
        val title: String,
        var state: State,
        var text: String,
        var fixLabel: String? = null,
        var onFix: (() -> Unit)? = null
    )

    private lateinit var content: LinearLayout
    private lateinit var heroIcon: ImageView
    private lateinit var heroProgress: ProgressBar
    private lateinit var heroTitle: TextView
    private lateinit var heroDesc: TextView

    private val checks = mutableListOf<Check>()
    private var runId = 0

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

        buildShell()
    }

    override fun onResume() {
        super.onResume()
        runAllChecks()
    }

    private fun buildShell() {
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
            background = ContextCompat.getDrawable(this@DiagnosticsActivity, R.drawable.bg_menu_ripple)
            contentDescription = getString(R.string.back)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt()))
        header.addView(TextView(this).apply {
            text = getString(R.string.diagnostics_title); textSize = 20f
            setTextColor(cPrimary); typeface = Typeface.DEFAULT_BOLD
            setPadding((8 * dp).toInt(), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_refresh); setColorFilter(cPrimary)
            val p = (10 * dp).toInt(); setPadding(p, p, p, p)
            background = ContextCompat.getDrawable(this@DiagnosticsActivity, R.drawable.bg_menu_ripple)
            contentDescription = getString(R.string.diag_fix_retry)
            setOnClickListener { runAllChecks() }
        }, LinearLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt()))
        outer.addView(header)
        ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding((12 * dp).toInt(), bars.top, (8 * dp).toInt(), 0)
            insets
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt(), (28 * dp).toInt())
        }
        column.addView(heroCard())
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(content)

        val scroll = ScrollView(this).apply { addView(column) }
        outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            v.setPadding(0, 0, 0, insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom); insets
        }
        setContentView(outer)
    }

    private fun heroCard(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        background = rounded(cCard, 20f)
        setPadding((20 * dp).toInt(), (22 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt())
        val frame = FrameLayout(this@DiagnosticsActivity)
        heroIcon = ImageView(this@DiagnosticsActivity).apply {
            val p = (16 * dp).toInt(); setPadding(p, p, p, p)
        }
        heroProgress = ProgressBar(this@DiagnosticsActivity).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(cAccent)
        }
        frame.addView(heroIcon, FrameLayout.LayoutParams(-1, -1))
        frame.addView(heroProgress, FrameLayout.LayoutParams((36 * dp).toInt(), (36 * dp).toInt(), Gravity.CENTER))
        addView(frame, LinearLayout.LayoutParams((64 * dp).toInt(), (64 * dp).toInt()).apply {
            bottomMargin = (12 * dp).toInt()
        })
        heroTitle = TextView(this@DiagnosticsActivity).apply {
            textSize = 17f; setTextColor(cPrimary); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        }
        addView(heroTitle)
        heroDesc = TextView(this@DiagnosticsActivity).apply {
            textSize = 13f; setTextColor(cSecondary); gravity = Gravity.CENTER
            setLineSpacing(0f, 1.15f)
            setPadding(0, (4 * dp).toInt(), 0, 0)
        }
        addView(heroDesc)
    }

    private fun runAllChecks() {
        val run = ++runId
        checks.clear()
        content.removeAllViews()

        section(R.string.diag_section_background, listOf(
            serviceCheck(), notificationsCheck(), batteryCheck()
        ) + listOfNotNull(exactAlarmsCheck(), backgroundDataCheck()))

        val internet = internetCheck()
        val server = Check(getString(R.string.diag_server), State.LOADING,
            getString(if (PrivacySettings.isTorEnabled(this)) R.string.diag_server_tor else R.string.diag_checking))
        val serverRow = section(R.string.diag_section_connection, listOf(internet, server))[1]

        section(R.string.diag_section_security, listOf(encryptionCheck()))
        val version = Check(getString(R.string.diag_version), State.LOADING, getString(R.string.diag_checking))
        val versionRow = section(R.string.diag_section_app, listOf(version))[0]
        moreSection()
        infoSection()
        if (pl.sinusoide.sinuschat.util.NotificationPrefs.updateNotices(this)) {
            checkVersion(run, version, versionRow)
        } else {
            version.state = State.INFO
            version.text = getString(R.string.diag_version_off,
                pl.sinusoide.sinuschat.util.UpdateChecker.installedName(this))
            version.fixLabel = getString(R.string.diag_check_now)
            version.onFix = { checkVersion(runId, version, versionRow, manual = true) }
            bindRow(versionRow, version)
        }
        refreshHero()

        AppExecutor.io {
            val reachable = pingServer()
            AppExecutor.main {
                if (run != runId || isFinishing || isDestroyed) return@main
                server.state = if (reachable) State.OK else State.PROBLEM
                server.text = getString(if (reachable) R.string.diag_server_ok else R.string.diag_server_problem)
                server.fixLabel = if (reachable) null else getString(R.string.diag_fix_retry)
                server.onFix = { runAllChecks() }
                bindRow(serverRow, server)
                refreshHero()
            }
        }
    }

    private fun checkVersion(run: Int, version: Check, row: RowViews, manual: Boolean = false) {
        val checker = pl.sinusoide.sinuschat.util.UpdateChecker
        version.state = State.LOADING
        version.text = getString(R.string.diag_checking)
        version.fixLabel = null
        bindRow(row, version)
        refreshHero()
        AppExecutor.io {
            val latest = checker.fetchLatest(this)
            AppExecutor.main {
                if (run != runId || isFinishing || isDestroyed) return@main
                val current = checker.installedName(this)
                when {
                    latest == null -> {
                        version.state = State.INFO
                        version.text = getString(R.string.diag_version_unknown, current)
                        version.fixLabel = getString(R.string.diag_fix_retry)
                        version.onFix = { checkVersion(runId, version, row, manual = true) }
                    }
                    checker.isOutdated(this, latest) -> {
                        version.state = State.PROBLEM
                        version.text = getString(R.string.diag_version_old, current, latest.name)
                        version.fixLabel = getString(R.string.update_now)
                        version.onFix = { UpdateActivity.open(this, latest) }
                        if (manual) UpdateActivity.open(this, latest)
                    }
                    else -> {
                        version.state = State.OK
                        version.text = getString(R.string.diag_version_ok, current)
                        version.fixLabel = null
                    }
                }
                bindRow(row, version)
                refreshHero()
            }
        }
    }

    private fun serviceCheck(): Check {
        val running = MessageListenerService.isRunning
        val live = running && WebSocketManager.isConnected()
        val onDemand = MessageListenerService.onDemand(this)
        val ok = running || onDemand
        return Check(getString(R.string.diag_service),
            if (ok) State.OK else State.PROBLEM,
            getString(when { live -> R.string.diag_service_live; running -> R.string.diag_service_ok
                             onDemand -> R.string.diag_service_on_demand
                             else -> R.string.diag_service_problem }),
            if (ok) null else getString(R.string.diag_fix_start_service)
        ) {
            MessageListenerService.startChecked(this)
            content.postDelayed({ if (!isFinishing) runAllChecks() }, 1500)
        }
    }

    private fun notificationsCheck(): Check {
        val enabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
        return Check(getString(R.string.diag_notifications),
            if (enabled) State.OK else State.PROBLEM,
            getString(if (enabled) R.string.diag_notif_ok else R.string.diag_notif_problem),
            if (enabled) null else getString(R.string.diag_fix_open_settings)
        ) { openAppNotificationSettings() }
    }

    private fun batteryCheck(): Check {
        val pm = getSystemService(android.os.PowerManager::class.java)
        val ignoring = pm?.isIgnoringBatteryOptimizations(packageName) ?: true
        if (!ignoring && MessageListenerService.onDemand(this)) {
            return Check(getString(R.string.diag_battery), State.OK, getString(R.string.diag_battery_fcm))
        }
        return Check(getString(R.string.diag_battery),
            if (ignoring) State.OK else State.PROBLEM,
            getString(if (ignoring) R.string.diag_battery_ok else R.string.diag_battery_problem),
            if (ignoring) null else getString(R.string.diag_fix_disable_opt)
        ) {
            tryStart(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun exactAlarmsCheck(): Check? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val allowed = getSystemService(android.app.AlarmManager::class.java)?.canScheduleExactAlarms() ?: true
        if (!allowed && MessageListenerService.onDemand(this)) {
            return Check(getString(R.string.diag_exact_alarms), State.OK, getString(R.string.diag_battery_fcm))
        }
        return Check(getString(R.string.diag_exact_alarms),
            if (allowed) State.OK else State.PROBLEM,
            getString(if (allowed) R.string.diag_exact_alarms_ok else R.string.diag_exact_alarms_problem),
            if (allowed) null else getString(R.string.diag_fix_open_settings)
        ) {
            tryStart(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    private fun backgroundDataCheck(): Check? {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return null
        val blocked = cm.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        return Check(getString(R.string.diag_bgdata),
            if (blocked) State.PROBLEM else State.OK,
            getString(if (blocked) R.string.diag_bgdata_problem else R.string.diag_bgdata_ok),
            if (blocked) getString(R.string.diag_fix_allow_bgdata) else null
        ) {
            tryStart(Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS, Uri.parse("package:$packageName")),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    private fun internetCheck(): Check {
        val conn = NetworkSettings.connection(this)
        val text = when (conn) {
            NetworkSettings.Connection.WIFI -> getString(R.string.net_conn_wifi)
            NetworkSettings.Connection.MOBILE -> getString(R.string.net_conn_mobile)
            NetworkSettings.Connection.OTHER -> getString(R.string.net_conn_other)
            NetworkSettings.Connection.NONE -> getString(R.string.diag_internet_problem)
        }
        val none = conn == NetworkSettings.Connection.NONE
        return Check(getString(R.string.diag_internet),
            if (none) State.PROBLEM else State.OK, text,
            if (none) getString(R.string.diag_fix_network_settings) else null
        ) {
            tryStart(Intent(Settings.ACTION_WIRELESS_SETTINGS), Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun encryptionCheck(): Check {
        val enabled = DbKeyManager.isEnabled(this)
        val encrypted = DbCrypto.isDbEncrypted(this) || !DbCrypto.dbExists(this)
        val migrationError = DbKeyManager.getMigrationError(this)
        val problem = migrationError != null || (enabled && !encrypted)
        val text = when {
            migrationError != null -> getString(R.string.diag_db_error_prefix, migrationError)
            enabled && !encrypted -> getString(R.string.diag_db_enabled_but_not_encrypted)
            enabled -> getString(R.string.diag_db_ok)
            else -> getString(R.string.diag_db_off)
        }
        val state = when { problem -> State.PROBLEM; enabled -> State.OK; else -> State.INFO }
        return Check(getString(R.string.diag_db_encryption), state, text,
            if (problem) getString(R.string.diag_fix_open_privacy) else null
        ) { startActivity(Intent(this, PrivacySettingsActivity::class.java)) }
    }

    private fun appVersion(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
    } catch (_: Exception) { "?" }

    private fun deviceName(): String {
        val maker = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        return if (Build.MODEL.startsWith(Build.MANUFACTURER, ignoreCase = true)) Build.MODEL else "$maker ${Build.MODEL}"
    }

    private fun moreSection() {
        content.addView(sectionTitle(getString(R.string.diag_section_more)))
        content.addView(card().apply {
            addView(LinearLayout(this@DiagnosticsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((14 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
                background = androidx.core.content.ContextCompat.getDrawable(this@DiagnosticsActivity, R.drawable.bg_menu_ripple)
                isClickable = true
                setOnClickListener {
                    pl.sinusoide.sinuschat.util.SafeEngine.openLink(this@DiagnosticsActivity, AppConfig.HELP_URL)
                }
                addView(tile(R.drawable.ic_help, cAccent))
                addView(LinearLayout(this@DiagnosticsActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(this@DiagnosticsActivity).apply {
                        text = getString(R.string.diag_more_title); textSize = 15.5f; setTextColor(cPrimary)
                    })
                    addView(TextView(this@DiagnosticsActivity).apply {
                        text = getString(R.string.diag_more_desc); textSize = 12.5f; setTextColor(cSecondary)
                        setPadding(0, (2 * dp).toInt(), 0, 0)
                    })
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = (8 * dp).toInt() })
                addView(ImageView(this@DiagnosticsActivity).apply {
                    setImageResource(R.drawable.ic_chevron_right); setColorFilter(cSecondary)
                }, LinearLayout.LayoutParams((20 * dp).toInt(), (20 * dp).toInt()))
            })
        })
    }

    private fun infoSection() {
        content.addView(sectionTitle(getString(R.string.diag_section_info)))
        content.addView(card().apply {
            addView(infoRow(R.drawable.ic_dolphin, getString(R.string.diag_version),
                getString(R.string.diag_version_codename, appVersion(), AppConfig.CODENAME)).apply {
                isClickable = true
                background = androidx.core.content.ContextCompat.getDrawable(this@DiagnosticsActivity, R.drawable.bg_menu_ripple)
                setOnClickListener { onVersionTap() }
            })
            addView(divider())
            addView(infoRow(R.drawable.ic_settings, getString(R.string.diag_device),
                getString(R.string.diag_device_value, deviceName(), Build.VERSION.RELEASE)))
        })
    }

    private var versionTaps = 0
    private var lastVersionTap = 0L

    private fun onVersionTap() {
        val now = System.currentTimeMillis()
        if (now - lastVersionTap > 2000) versionTaps = 0
        lastVersionTap = now
        versionTaps++
        if (versionTaps >= 5) {
            versionTaps = 0
            pl.sinusoide.sinuschat.ui.DolphinEasterEgg.show(this, appVersion())
        } else if (versionTaps > 2) {
            android.widget.Toast.makeText(this, (5 - versionTaps).toString(), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun pingServer(): Boolean {
        return try {
            val tor = PrivacySettings.isTorEnabled(this)
            val client = ApiClient.httpClient(this).newBuilder()
                .callTimeout(if (tor) 60 else 10, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val req = okhttp3.Request.Builder().url(AppConfig.API_BASE).head().build()
            client.newCall(req).execute().use { resp -> resp.code in 200..499 }
        } catch (_: Exception) {
            false
        }
    }

    private fun refreshHero() {
        val loading = checks.any { it.state == State.LOADING }
        val problems = checks.count { it.state == State.PROBLEM }
        heroProgress.visibility = if (loading && problems == 0) View.VISIBLE else View.GONE
        val color = when {
            problems > 0 -> cOrange
            loading -> cAccent
            else -> cGreen
        }
        heroIcon.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor((color and 0x00FFFFFF) or 0x26000000)
        }
        when {
            problems > 0 -> {
                heroIcon.setImageResource(R.drawable.ic_warning); heroIcon.setColorFilter(cOrange)
                heroTitle.text = resources.getQuantityString(R.plurals.diag_hero_problems, problems, problems)
                heroDesc.text = getString(R.string.diag_hero_problems_desc)
            }
            loading -> {
                heroIcon.setImageDrawable(null)
                heroTitle.text = getString(R.string.diag_checking)
                heroDesc.text = getString(R.string.diag_hero_checking)
            }
            else -> {
                heroIcon.setImageResource(R.drawable.ic_check); heroIcon.setColorFilter(cGreen)
                heroTitle.text = getString(R.string.diag_hero_ok)
                heroDesc.text = getString(R.string.diag_hero_ok_desc)
            }
        }
    }

    private class RowViews(val tileFrame: FrameLayout, val status: TextView, val fix: TextView)

    private fun section(titleRes: Int, items: List<Check>): List<RowViews> {
        content.addView(sectionTitle(getString(titleRes)))
        val rows = mutableListOf<RowViews>()
        content.addView(card().apply {
            items.forEachIndexed { i, check ->
                if (i > 0) addView(divider())
                checks.add(check)
                val (view, rv) = checkRow(check)
                rows.add(rv)
                addView(view)
            }
        })
        return rows
    }

    private fun checkRow(check: Check): Pair<View, RowViews> {
        val tileFrame = FrameLayout(this)
        val status = TextView(this).apply {
            textSize = 12.5f; setTextColor(cSecondary)
            setLineSpacing(0f, 1.1f)
            setPadding(0, (2 * dp).toInt(), 0, 0)
        }
        val fix = TextView(this).apply {
            textSize = 13.5f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            background = rounded(cAccent, 16f)
            setPadding((14 * dp).toInt(), (7 * dp).toInt(), (14 * dp).toInt(), (7 * dp).toInt())
            isClickable = true
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding((14 * dp).toInt(), (13 * dp).toInt(), (14 * dp).toInt(), (13 * dp).toInt())
            addView(tileFrame, LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt()).apply {
                marginEnd = (14 * dp).toInt()
            })
            addView(LinearLayout(this@DiagnosticsActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@DiagnosticsActivity).apply {
                    text = check.title; textSize = 15.5f; setTextColor(cPrimary)
                })
                addView(status)
                addView(fix, LinearLayout.LayoutParams(-2, -2).apply { topMargin = (10 * dp).toInt() })
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        val rv = RowViews(tileFrame, status, fix)
        bindRow(rv, check)
        return row to rv
    }

    private fun bindRow(rv: RowViews, check: Check) {
        rv.tileFrame.removeAllViews()
        val color = when (check.state) {
            State.OK -> cGreen; State.PROBLEM -> cRed; State.LOADING -> cAccent; State.INFO -> cSecondary
        }
        rv.tileFrame.background = GradientDrawable().apply {
            cornerRadius = 12 * dp
            setColor((color and 0x00FFFFFF) or 0x24000000)
        }
        if (check.state == State.LOADING) {
            rv.tileFrame.addView(ProgressBar(this).apply {
                isIndeterminate = true
                indeterminateTintList = android.content.res.ColorStateList.valueOf(cAccent)
            }, FrameLayout.LayoutParams((20 * dp).toInt(), (20 * dp).toInt(), Gravity.CENTER))
        } else {
            rv.tileFrame.addView(ImageView(this).apply {
                setImageResource(when (check.state) {
                    State.OK -> R.drawable.ic_check
                    State.PROBLEM -> R.drawable.ic_warning
                    else -> R.drawable.ic_info
                })
                setColorFilter(color)
                val p = (10 * dp).toInt(); setPadding(p, p, p, p)
            }, FrameLayout.LayoutParams(-1, -1))
        }
        rv.status.text = check.text
        rv.status.setTextColor(if (check.state == State.PROBLEM) cRed else cSecondary)
        val label = check.fixLabel
        if (label == null) {
            rv.fix.visibility = View.GONE
        } else {
            rv.fix.visibility = View.VISIBLE
            rv.fix.text = label
            rv.fix.setOnClickListener { check.onFix?.invoke() }
        }
    }

    private fun infoRow(icon: Int, title: String, value: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding((14 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
        addView(tile(icon, cSecondary))
        addView(LinearLayout(this@DiagnosticsActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@DiagnosticsActivity).apply {
                text = title; textSize = 15.5f; setTextColor(cPrimary)
            })
            addView(TextView(this@DiagnosticsActivity).apply {
                text = value; textSize = 12.5f; setTextColor(cSecondary)
                setPadding(0, (2 * dp).toInt(), 0, 0)
                setTextIsSelectable(true)
            })
        }, LinearLayout.LayoutParams(0, -2, 1f))
    }

    private fun tryStart(primary: Intent, fallback: Intent) {
        try { startActivity(primary) } catch (_: Exception) {
            try { startActivity(fallback) } catch (_: Exception) {}
        }
    }

    private fun openAppNotificationSettings() {
        tryStart(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
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

    private fun tile(icon: Int, color: Int) = ImageView(this).apply {
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
