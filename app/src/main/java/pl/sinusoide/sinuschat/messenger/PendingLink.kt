package pl.sinusoide.sinuschat.messenger

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.SystemClock

object PendingLink {
    private const val TTL_MS = 30 * 60_000L

    @Volatile private var link: Uri? = null
    @Volatile private var keptAt = 0L

    fun keep(uri: Uri?) {
        if (uri == null) return
        link = uri
        keptAt = SystemClock.elapsedRealtime()
    }

    fun clear() {
        link = null
    }

    fun resume(from: Activity): Boolean {
        val uri = link ?: return false
        link = null
        if (SystemClock.elapsedRealtime() - keptAt > TTL_MS) return false
        from.startActivity(Intent(from, QrLinkActivity::class.java).setData(uri))
        return true
    }
}
