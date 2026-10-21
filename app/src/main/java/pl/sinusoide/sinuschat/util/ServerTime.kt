package pl.sinusoide.sinuschat.util

import org.json.JSONObject

object ServerTime {

    @Volatile private var offsetMs: Long = 0L
    @Volatile private var synced: Boolean = false

    private const val COARSE_TOLERANCE_MS = 2_000L

    fun now(): Long = System.currentTimeMillis() + offsetMs

    fun ageMs(serverTimestampMs: Long): Long =
        if (serverTimestampMs <= 0L) Long.MAX_VALUE
        else (now() - serverTimestampMs).coerceAtLeast(0L)

    fun sync(serverNowMs: Long) {
        if (serverNowMs <= 0L) return
        offsetMs = serverNowMs - System.currentTimeMillis()
        synced = true
    }

    fun syncFrom(json: JSONObject?) {
        val v = json?.optLong("server_now_ms", 0L) ?: 0L
        if (v > 0L) sync(v)
    }

    fun syncCoarse(headerDateMs: Long) {
        if (headerDateMs <= 0L) return
        val candidate = headerDateMs - System.currentTimeMillis()
        if (!synced || Math.abs(candidate - offsetMs) > COARSE_TOLERANCE_MS) {
            offsetMs = candidate
            synced = true
        }
    }
}
