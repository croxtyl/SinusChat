package pl.sinusoide.sinuschat.messenger

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

object PendingDeletes {

    private class Batch(val ids: List<String>, val deadline: Long, val commit: () -> Unit, val job: Runnable)

    private val handler = Handler(Looper.getMainLooper())
    private val batches = HashMap<String, Batch>()
    private val listeners = HashMap<String, () -> Unit>()

    fun start(conversationId: String, ids: List<String>, delayMs: Long, commit: () -> Unit) {
        commitNow(conversationId)
        if (ids.isEmpty()) return
        val job = Runnable { commitNow(conversationId) }
        batches[conversationId] = Batch(ids, SystemClock.elapsedRealtime() + delayMs, commit, job)
        handler.postDelayed(job, delayMs)
    }

    fun commitNow(conversationId: String) {
        val b = batches.remove(conversationId) ?: return
        handler.removeCallbacks(b.job)
        b.commit()
        listeners[conversationId]?.invoke()
    }

    fun cancel(conversationId: String): List<String>? {
        val b = batches.remove(conversationId) ?: return null
        handler.removeCallbacks(b.job)
        return b.ids
    }

    fun pendingIds(conversationId: String): List<String> = batches[conversationId]?.ids.orEmpty()

    fun remainingMs(conversationId: String): Long =
        batches[conversationId]?.let { (it.deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L) } ?: 0L

    fun setListener(conversationId: String, listener: (() -> Unit)?) {
        if (listener == null) listeners.remove(conversationId) else listeners[conversationId] = listener
    }
}
