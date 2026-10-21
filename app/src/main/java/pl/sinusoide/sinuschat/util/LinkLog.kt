package pl.sinusoide.sinuschat.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Process
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import pl.sinusoide.sinuschat.LockManager
import pl.sinusoide.sinuschat.crypto.DbKeyManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LinkLog {
    private const val PREFS = "link_log"
    private const val KEY = "events"
    private const val KEY_STACK = "last_crash"
    private const val MAX = 25

    fun add(context: Context, step: String, detail: String = "", sync: Boolean = false) {
        try {
            val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val all = runCatching { JSONArray(p.getString(KEY, "[]")) }.getOrElse { JSONArray() }
            all.put(JSONObject().put("t", System.currentTimeMillis()).put("s", step).put("d", detail.take(240)))
            val kept = JSONArray()
            for (i in maxOf(0, all.length() - MAX) until all.length()) kept.put(all.get(i))
            val edit = p.edit().putString(KEY, kept.toString())
            if (sync) edit.commit() else edit.apply()
        } catch (_: Exception) {}
    }

    fun crash(context: Context, e: Throwable) {
        val root = generateSequence(e) { it.cause }.last()
        val frame = (root.stackTrace.firstOrNull { it.className.startsWith("pl.sinusoide") } ?: root.stackTrace.firstOrNull())
            ?.let { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" } ?: "?"
        val msg = root.message?.lineSequence()?.firstOrNull()?.take(120).orEmpty()
        add(context, "crash", "${root.javaClass.simpleName} @ $frame $msg".trim(), sync = true)
        try {
            val head = if (root !== e) "${e.javaClass.simpleName} > ${root.javaClass.simpleName}" else root.javaClass.simpleName
            val frames = root.stackTrace.take(12).joinToString("\n") { "  " + it.toString().replace("pl.sinusoide.sinuschat.", "") }
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_STACK, "${System.currentTimeMillis()}\n$head\n$frames").commit()
        } catch (_: Exception) {}
    }

    fun lastCrash(context: Context): String = try {
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_STACK, null)
        if (raw.isNullOrEmpty()) "" else {
            val at = raw.substringBefore('\n').toLongOrNull() ?: 0L
            SimpleDateFormat("dd.MM HH:mm:ss", Locale.ROOT).format(Date(at)) + " " + raw.substringAfter('\n')
        }
    } catch (_: Exception) { "" }

    fun env(context: Context): String {
        val age = (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()) / 100 / 10.0
        val lock = when {
            DbKeyManager.needsPinUnlock(context) -> "db"
            LockManager.hasPin(context) && !LockManager.sessionUnlocked -> "session"
            LockManager.hasPin(context) -> "open"
            else -> "none"
        }
        val tor = if (PrivacySettings.isTorEnabled(context)) " tor" else ""
        return "proc=${age}s lock=$lock net=${network(context)}$tor"
    }

    private fun network(context: Context): String = try {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        if (caps == null) "none" else buildString {
            append(NetworkSettings.connection(context).name.lowercase())
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) append("+vpn")
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) append("+unvalidated")
        }
    } catch (_: Exception) { "?" }

    fun report(context: Context): String {
        val all = try {
            JSONArray(context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
        } catch (_: Exception) { JSONArray() }
        if (all.length() == 0) return ""
        val fmt = SimpleDateFormat("dd.MM HH:mm:ss", Locale.ROOT)
        return buildString {
            for (i in 0 until all.length()) {
                val o = all.optJSONObject(i) ?: continue
                appendLine("${fmt.format(Date(o.optLong("t")))} ${o.optString("s")} ${o.optString("d")}".trim())
            }
        }.trim()
    }
}
