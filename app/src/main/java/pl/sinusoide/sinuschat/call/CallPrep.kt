package pl.sinusoide.sinuschat.call

import android.content.Context
import org.json.JSONObject
import pl.sinusoide.sinuschat.crypto.SecurePrefs
import pl.sinusoide.sinuschat.network.ApiClient
import pl.sinusoide.sinuschat.util.AppExecutor
import java.util.concurrent.ConcurrentHashMap

object CallPrep {

    private const val PREFS = "call_prep"
    private const val KEY_TURN = "turn"
    private const val KEY_TURN_UNTIL = "turn_until"
    private const val MARGIN_MS = 5 * 60_000L
    private const val FRESH_MS = 2 * 60_000L

    private val prepared = ConcurrentHashMap<String, Long>()

    private fun cachedTurn(ctx: Context): JSONObject? {
        val p = SecurePrefs.get(ctx, PREFS)
        if (p.getLong(KEY_TURN_UNTIL, 0L) - System.currentTimeMillis() < MARGIN_MS) return null
        return p.getString(KEY_TURN, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
    }

    fun turn(ctx: Context): JSONObject? {
        cachedTurn(ctx)?.let { return it }
        val creds = ApiClient.getObject("/api/turn-credentials", ctx) ?: return null
        val ttl = creds.optLong("ttl", 0L)
        if (ttl > 0L) SecurePrefs.get(ctx, PREFS).edit()
            .putString(KEY_TURN, creds.toString())
            .putLong(KEY_TURN_UNTIL, System.currentTimeMillis() + ttl * 1000L)
            .apply()
        return creds
    }

    fun channel(ctx: Context, peer: String): CallCrypto.Channel {
        if (System.currentTimeMillis() - (prepared[peer] ?: 0L) < FRESH_MS) return CallCrypto.cached(ctx, peer)
        return CallCrypto.prepare(ctx, peer).also { prepared[peer] = System.currentTimeMillis() }
    }

    fun prefetch(ctx: Context, peer: String) {
        val app = ctx.applicationContext
        AppExecutor.io { try { turn(app) } catch (_: Exception) {} }
        if (peer.isEmpty()) return
        AppExecutor.io {
            try {
                CallCrypto.prepare(app, peer)
                prepared[peer] = System.currentTimeMillis()
            } catch (_: Exception) {}
        }
    }
}
