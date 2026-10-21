package pl.sinusoide.sinuschat

import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import pl.sinusoide.sinuschat.ui.HoldButton
import pl.sinusoide.sinuschat.util.AccountWiper
import pl.sinusoide.sinuschat.util.PopupHelper
import pl.sinusoide.sinuschat.util.ThemeManager
import java.text.SimpleDateFormat
import java.util.*

class DeleteAccountActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(pl.sinusoide.sinuschat.util.LocaleManager.wrap(newBase))
    }


    private lateinit var viewConfirm: View
    private lateinit var viewCountdown: View
    private lateinit var viewDeleted: View
    private var redirectTimer: CountDownTimer? = null

    companion object {
        private const val PREFS = "sinuschat_account_deletion"
        private const val KEY_SCHEDULED_AT = "deletion_scheduled_at"
        const val DELETION_DAYS = 14

        fun localScheduledAt(ctx: android.content.Context): Long =
            ctx.getSharedPreferences(PREFS, MODE_PRIVATE).getLong(KEY_SCHEDULED_AT, 0L)

        fun saveLocalScheduledAt(ctx: android.content.Context, at: Long) {
            val e = ctx.getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            if (at > 0L) e.putLong(KEY_SCHEDULED_AT, at) else e.remove(KEY_SCHEDULED_AT)
            e.apply()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_delete_account)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.layout_header)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = systemBars.top)
            insets
        }

        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }

        viewConfirm = findViewById(R.id.view_confirm)
        viewCountdown = findViewById(R.id.view_countdown)
        viewDeleted = findViewById(R.id.view_deleted)

        val scheduledAt = localScheduledAt(this)
        if (scheduledAt > 0L) showCountdown(scheduledAt) else showConfirm()
        syncWithServer()
    }

    private fun syncWithServer() {
        Thread {
            val resp = try {
                pl.sinusoide.sinuschat.network.ApiClient.getObject("/api/user/deletion", this)
            } catch (_: Exception) { null } ?: return@Thread
            val at = if (resp.isNull("scheduledAt")) 0L else resp.optLong("scheduledAt", 0L)
            runOnUiThread {
                if (isFinishing || viewDeleted.visibility == View.VISIBLE) return@runOnUiThread
                val local = localScheduledAt(this)
                saveLocalScheduledAt(this, at)
                if (at > 0L && at != local) showCountdown(at)
                else if (at == 0L && local > 0L) showConfirm()
            }
        }.start()
    }

    private fun showConfirm() {
        viewConfirm.visibility = View.VISIBLE
        viewCountdown.visibility = View.GONE
        viewDeleted.visibility = View.GONE

        val hold = findViewById<HoldButton>(R.id.btn_hold_delete)
        hold.holdMs = 300
        hold.reset()
        hold.isEnabled = true
        hold.onHoldComplete = { scheduleDeletion() }
        addConsequencesList(hold)

        findViewById<Button>(R.id.btn_cancel_confirm).setOnClickListener { finish() }
    }

    private var consequencesAdded = false
    private fun addConsequencesList(anchor: View) {
        if (consequencesAdded) return
        val card = anchor.parent as? android.widget.LinearLayout ?: return
        consequencesAdded = true
        val d = resources.displayMetrics.density
        val red = androidx.core.content.ContextCompat.getColor(this, R.color.sinuschat_error)
        val tv = android.util.TypedValue()
        val primary = pl.sinusoide.sinuschat.ui.ThemeUi.attrOrNull(this, R.attr.sinuschat_text_primary) ?: android.graphics.Color.WHITE
        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 14 * d
                setColor((red and 0x00FFFFFF) or 0x14000000)
            }
            setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (4 * d).toInt())
        }
        for (res in listOf(R.string.delete_account_item_profile, R.string.delete_account_item_messages,
                           R.string.delete_account_item_groups, R.string.delete_account_item_irreversible)) {
            box.addView(android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, (8 * d).toInt())
                addView(View(this@DeleteAccountActivity).apply {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(red)
                    }
                }, android.widget.LinearLayout.LayoutParams((6 * d).toInt(), (6 * d).toInt()).apply {
                    marginEnd = (10 * d).toInt()
                })
                addView(TextView(this@DeleteAccountActivity).apply {
                    text = getString(res); textSize = 13.5f; setTextColor(primary)
                })
            })
        }
        card.addView(box, card.indexOfChild(anchor), android.widget.LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = (20 * d).toInt()
        })
    }

    private fun scheduleDeletion() {
        val hold = findViewById<HoldButton>(R.id.btn_hold_delete)
        hold.isEnabled = false
        Thread {
            val resp = try {
                pl.sinusoide.sinuschat.network.ApiClient.post("/api/user/deletion", org.json.JSONObject(), this)
            } catch (_: Exception) { null }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                val at = resp?.optLong("scheduledAt", 0L) ?: 0L
                if (resp?.optBoolean("ok") == true && at > 0L) {
                    saveLocalScheduledAt(this, at)
                    showCountdown(at)
                } else {
                    hold.isEnabled = true
                    hold.reset()
                    PopupHelper.showError(this, getString(R.string.deletion_schedule_failed))
                }
            }
        }.start()
    }

    private fun showCountdown(scheduledAt: Long) {
        viewConfirm.visibility = View.GONE
        viewCountdown.visibility = View.VISIBLE
        viewDeleted.visibility = View.GONE

        val elapsedDays = ((System.currentTimeMillis() - scheduledAt) / (24 * 60 * 60 * 1000L))
            .toInt().coerceIn(0, DELETION_DAYS - 1)
        val daysLeft = (DELETION_DAYS - elapsedDays).coerceAtLeast(0)

        findViewById<TextView>(R.id.tv_days_big).text = daysLeft.toString()
        findViewById<TextView>(R.id.tv_days_left).text =
            resources.getQuantityString(R.plurals.days_to_deletion, daysLeft)

        val deleteDate = Date(scheduledAt + DELETION_DAYS * 24L * 60 * 60 * 1000)
        val fmt = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
        findViewById<TextView>(R.id.tv_delete_date).text =
            getString(R.string.permanent_deletion_date_format, fmt.format(deleteDate))

        buildCalendar(elapsedDays)

        findViewById<Button>(R.id.btn_cancel_deletion).setOnClickListener { cancelDeletion() }
        findViewById<Button>(R.id.btn_delete_now).setOnClickListener { confirmDeleteNow() }
    }

    private fun buildCalendar(elapsedDays: Int) {
        val grid = findViewById<android.widget.GridLayout>(R.id.calendar_grid)
        grid.removeAllViews()

        val density = resources.displayMetrics.density
        val cell = (34 * density).toInt()
        val margin = (3 * density).toInt()

        for (i in 0 until DELETION_DAYS) {
            val dayNum = i + 1
            val tv = TextView(this)
            tv.text = dayNum.toString()
            tv.gravity = android.view.Gravity.CENTER
            tv.textSize = 12f

            when {
                i < elapsedDays -> {
                    tv.setBackgroundResource(R.drawable.bg_day_past)
                    tv.setTextColor(android.graphics.Color.parseColor("#4A5560"))
                    tv.paintFlags = tv.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                }
                i == elapsedDays -> {
                    tv.setBackgroundResource(R.drawable.bg_day_today)
                    tv.setTextColor(android.graphics.Color.WHITE)
                    tv.setTypeface(null, android.graphics.Typeface.BOLD)
                }
                i == DELETION_DAYS - 1 -> {
                    tv.setBackgroundResource(R.drawable.bg_day_delete)
                    tv.setTextColor(android.graphics.Color.parseColor("#E74C3C"))
                    tv.setTypeface(null, android.graphics.Typeface.BOLD)
                }
                else -> {
                    tv.setBackgroundResource(R.drawable.bg_day_future)
                    tv.setTextColor(android.graphics.Color.parseColor("#8B98A5"))
                }
            }

            val params = android.widget.GridLayout.LayoutParams()
            params.width = cell
            params.height = cell
            params.setMargins(margin, margin, margin, margin)
            tv.layoutParams = params
            grid.addView(tv)
        }
    }

    private fun cancelDeletion() {
        val btn = findViewById<Button>(R.id.btn_cancel_deletion)
        btn.isEnabled = false
        Thread {
            val resp = try {
                pl.sinusoide.sinuschat.network.ApiClient.post("/api/user/deletion",
                    org.json.JSONObject().put("cancel", true), this)
            } catch (_: Exception) { null }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (resp?.optBoolean("ok") == true) {
                    saveLocalScheduledAt(this, 0L)
                    setResult(android.app.Activity.RESULT_OK,
                        android.content.Intent().putExtra("cancelled", true))
                    finish()
                } else {
                    btn.isEnabled = true
                    PopupHelper.showError(this, getString(R.string.error_connection))
                }
            }
        }.start()
    }

    private fun confirmDeleteNow() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_now, null)
        val hold = dialogView.findViewById<HoldButton>(R.id.btn_hold_now)
        val dialog = AlertDialog.Builder(this, R.style.Theme_SinusChat_Dialog)
            .setView(dialogView).setCancelable(true).create()

        dialogView.findViewById<Button>(R.id.btn_cancel_now).setOnClickListener { dialog.dismiss() }
        hold.holdMs = 300
        hold.onHoldComplete = {
            dialog.dismiss()
            deleteNow()
        }
        dialog.show()
    }

    private fun deleteNow() {
        Thread {
            val resp = try {
                pl.sinusoide.sinuschat.network.ApiClient.post("/api/user/delete-account",
                    org.json.JSONObject(), this)
            } catch (_: Exception) { null }

            runOnUiThread {
                if (resp != null && resp.optBoolean("ok", false)) {
                    saveLocalScheduledAt(this, 0L)
                    showDeleted()
                } else {
                    PopupHelper.showError(this, getString(R.string.account_delete_failed))
                }
            }
        }.start()
    }

    private fun showDeleted() {
        viewConfirm.visibility = View.GONE
        viewCountdown.visibility = View.GONE
        viewDeleted.visibility = View.VISIBLE
        findViewById<ImageView>(R.id.btn_back).visibility = View.GONE

        findViewById<Button>(R.id.btn_go_now).setOnClickListener { wipeAndGo() }

        val tvRedirect = findViewById<TextView>(R.id.tv_redirect)
        redirectTimer = object : CountDownTimer(10_000, 1000) {
            override fun onTick(ms: Long) {
                tvRedirect.text = getString(R.string.go_to_login_timer, ms / 1000)
            }
            override fun onFinish() { wipeAndGo() }
        }.start()
    }

    private fun wipeAndGo() {
        redirectTimer?.cancel()
        AccountWiper.wipeAndRestart(this)
        finishAffinity()
    }

    override fun onDestroy() {
        redirectTimer?.cancel()
        super.onDestroy()
    }
}
