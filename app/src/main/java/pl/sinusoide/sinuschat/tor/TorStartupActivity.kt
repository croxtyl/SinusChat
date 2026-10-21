package pl.sinusoide.sinuschat.tor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import pl.sinusoide.sinuschat.R
import androidx.activity.enableEdgeToEdge

class TorStartupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tor_startup)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener((findViewById<android.view.ViewGroup>(android.R.id.content)).getChildAt(0)) { v, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        findViewById<Button>(R.id.btn_tor_done).setOnClickListener { finish() }

        val slot = findViewById<android.widget.FrameLayout>(R.id.tor_ring_slot)
        slot.addView(pl.sinusoide.sinuschat.ui.SineProgressRing(this).apply {
            color = pl.sinusoide.sinuschat.ui.ThemeUi.accent(this@TorStartupActivity)
            disc = 0
            showCancel = false
            isClickable = false
            progress = { TorCircuits.lastProgress?.let { it.coerceIn(0, 100) / 100f } }
        }, 0, android.widget.FrameLayout.LayoutParams(-1, -1))
        progressText = findViewById(R.id.tor_progress_text)
        TorCircuits.addListener(progressChanged)

        TorManager.start(this)

        if (TorManager.isReady) {
            showSuccess()
        } else {
            showStarting()
            TorManager.onReady {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) showSuccess()
                }
            }
        }
    }

    private var progressText: android.widget.TextView? = null

    private val progressChanged: () -> Unit = {
        val p = TorCircuits.lastProgress
        progressText?.text = if (p == null) getString(R.string.tor_startup_building)
            else getString(R.string.tor_startup_progress, p.coerceIn(0, 100))
    }

    override fun onDestroy() {
        TorCircuits.removeListener(progressChanged)
        super.onDestroy()
    }

    private fun showStarting() {
        findViewById<View>(R.id.group_tor_starting).visibility = View.VISIBLE
        findViewById<View>(R.id.group_tor_success).visibility = View.GONE
    }

    private fun showSuccess() {
        findViewById<View>(R.id.group_tor_starting).visibility = View.GONE
        findViewById<View>(R.id.group_tor_success).visibility = View.VISIBLE
        notifyTorReady()
    }

    private fun notifyTorReady() {
        val mgr = getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.tor_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            mgr.createNotificationChannel(ch)
        }
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(getString(R.string.tor_notif_title))
            .setContentText(getString(R.string.tor_notif_text))
            .setAutoCancel(true)
            .build()
        runCatching { mgr.notify(NOTIF_ID, notif) }
    }

    companion object {
        private const val CHANNEL_ID = "tor_status"
        private const val NOTIF_ID = 4711
    }
}
