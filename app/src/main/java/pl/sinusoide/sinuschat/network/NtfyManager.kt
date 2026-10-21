package pl.sinusoide.sinuschat.network

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONObject
import pl.sinusoide.sinuschat.util.AppExecutor
import java.util.concurrent.TimeUnit

object NtfyManager {

    private const val TAG      = "NtfyManager"
    val NTFY_BASE get() = pl.sinusoide.sinuschat.AppConfig.NTFY_BASE
    private const val PREFS    = "sinuschat_ntfy"
    private const val KEY_TOPIC = "ntfy_topic"
    private const val KEY_TOKEN = "ntfy_token"

    fun saveTopic(context: Context, topic: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_TOPIC, topic).apply()
    }

    fun getTopic(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TOPIC, null)

    fun saveToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TOKEN, null)

    fun topicFromHash(emailHash: String): String =
        "sinuschat_" + emailHash.take(32)

    fun setup(context: Context, emailHash: String) {
        val topic = topicFromHash(emailHash)
        saveTopic(context, topic)
        Log.d(TAG, "ntfy topic: $topic")
    }

    @Volatile private var eventSource: EventSource? = null
    @Volatile private var wantSubscribed = false
    @Volatile private var connecting = false
    @Volatile private var retryDelayMs = 5_000L
    private val RETRY_MAX_MS = 300_000L

    fun subscribeUrl(context: Context): String? {
        val topic = getTopic(context) ?: return null
        return "$NTFY_BASE/$topic/json"
    }

    fun startListening(context: Context, onWake: (kind: String) -> Unit) {
        val url = subscribeUrl(context) ?: run {
            Log.w(TAG, "Brak tematu ntfy — pomijam subskrypcję")
            return
        }
        wantSubscribed = true
        if (connecting) return
        connecting = true
        val appCtx = context.applicationContext

        val client = buildClient(appCtx)
        val request = Request.Builder().url(url).build()

        eventSource?.cancel()
        eventSource = EventSources.createFactory(client).newEventSource(
            request,
            object : EventSourceListener() {
                override fun onOpen(eventSource: EventSource, response: Response) {
                    Log.d(TAG, "ntfy SSE otwarte")
                    connecting = false
                    retryDelayMs = 5_000L
                }

                override fun onEvent(
                    eventSource: EventSource,
                    id: String?, type: String?, data: String
                ) {
                    val kind = try {
                        val j = JSONObject(data)
                        if (j.optString("event") != "message") return
                        j.optString("message", "msg")
                    } catch (_: Exception) { "msg" }
                    Log.d(TAG, "ntfy wake: $kind")
                    AppExecutor.main { onWake(kind) }
                }

                override fun onClosed(eventSource: EventSource) {
                    connecting = false
                    if (wantSubscribed) scheduleReconnect(appCtx, onWake)
                }

                override fun onFailure(
                    eventSource: EventSource, t: Throwable?, response: Response?
                ) {
                    connecting = false
                    if (wantSubscribed) scheduleReconnect(appCtx, onWake)
                }
            }
        )
    }

    private val reconnectHandler = android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile private var reconnectPending = false

    private fun scheduleReconnect(context: Context, onWake: (String) -> Unit) {
        if (reconnectPending) return
        reconnectPending = true
        val delay = retryDelayMs
        retryDelayMs = (retryDelayMs * 2).coerceAtMost(RETRY_MAX_MS)
        reconnectHandler.postDelayed({
            reconnectPending = false
            if (wantSubscribed) startListening(context, onWake)
        }, delay)
    }

    fun stopListening() {
        wantSubscribed = false
        connecting = false
        reconnectPending = false
        reconnectHandler.removeCallbacksAndMessages(null)
        eventSource?.cancel()
        eventSource = null
    }

    private fun buildClient(context: Context): OkHttpClient {
        val b = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .pingInterval(30, TimeUnit.SECONDS)
        if (pl.sinusoide.sinuschat.util.PrivacySettings.isTorEnabled(context)) {
            b.proxy(java.net.Proxy(
                java.net.Proxy.Type.SOCKS,
                java.net.InetSocketAddress("127.0.0.1", pl.sinusoide.sinuschat.tor.TorManager.socksPort)
            ))
        }
        return b.build()
    }
}
