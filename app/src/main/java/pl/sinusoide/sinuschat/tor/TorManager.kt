package pl.sinusoide.sinuschat.tor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import org.torproject.jni.TorService

object TorManager {

    @Volatile var socksPort: Int = TorService.socksPort.let { if (it > 0) it else 9050 }
        private set

    @Volatile var isReady: Boolean = false
        private set

    @Volatile private var starting = false
    @Volatile private var running = false
    @Volatile private var desiredOn = false

    private var appContext: Context? = null
    private var receiverRegistered = false
    private val readyCallbacks = mutableListOf<() -> Unit>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingReconcile: Runnable? = null

    private const val DEBOUNCE_MS = 1200L

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.getStringExtra(TorService.EXTRA_STATUS)) {
                TorService.STATUS_ON -> {
                    val p = TorService.socksPort
                    if (p > 0) socksPort = p
                    starting = false
                    running = true
                    appContext?.let { TorCircuits.attach(it) }
                    waitReady()
                    if (!desiredOn) scheduleReconcile()
                }
                TorService.STATUS_OFF -> {
                    TorCircuits.detach()
                    isReady = false
                    running = false
                    starting = false
                }
            }
        }
    }

    fun onReady(cb: () -> Unit) {
        synchronized(this) {
            if (isReady) { cb(); return }
            readyCallbacks.add(cb)
        }
    }

    private var waitToken = 0

    private fun waitReady() {
        val token = ++waitToken
        TorCircuits.awaitBootstrap(90_000L) {
            if (token == waitToken && running && desiredOn) fireReady()
        }
    }

    private fun fireReady() {
        val cbs: List<() -> Unit>
        synchronized(this) {
            isReady = true
            cbs = readyCallbacks.toList()
            readyCallbacks.clear()
        }
        cbs.forEach { runCatching { it() } }
    }

    fun start(context: Context) {
        appContext = context.applicationContext
        desiredOn = true
        scheduleReconcile()
    }

    fun stop() {
        desiredOn = false
        waitToken++
        synchronized(this) {
            isReady = false
            readyCallbacks.clear()
        }
        scheduleReconcile()
    }

    private fun scheduleReconcile() {
        pendingReconcile?.let { mainHandler.removeCallbacks(it) }
        val r = Runnable { reconcile() }
        pendingReconcile = r
        mainHandler.postDelayed(r, DEBOUNCE_MS)
    }

    private fun reconcile() {
        pendingReconcile = null
        val app = appContext ?: return

        if (desiredOn && !running && !starting) {
            if (!receiverRegistered) {
                ContextCompat.registerReceiver(
                    app, statusReceiver,
                    IntentFilter(TorService.ACTION_STATUS),
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                receiverRegistered = true
            }
            starting = true
            try {
                app.startService(Intent(app, TorService::class.java))
            } catch (t: Throwable) {
                Log.e("TorManager", "Nie udało się wystartować TorService", t)
                starting = false
            }
        } else if (desiredOn && running && !starting && !isReady) {
            waitReady()
        } else if (!desiredOn && running && !starting) {
            running = false
            TorCircuits.detach()
            if (receiverRegistered) {
                runCatching { app.unregisterReceiver(statusReceiver) }
                receiverRegistered = false
            }
            runCatching { app.stopService(Intent(app, TorService::class.java)) }
        } else if (!desiredOn && starting) {
            Log.d("TorManager", "stop w trakcie rozruchu — odłożone do STATUS_ON")
        }
    }
}
