package pl.sinusoide.sinuschat.util

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppExits {

    private const val MAX_EXITS = 5
    private const val MAX_TRACES = 2
    private const val MAX_FRAMES = 16
    private const val MAX_TRACE_BYTES = 4 * 1024 * 1024

    fun report(context: Context): String {
        if (Build.VERSION.SDK_INT < 30) return ""
        return try { build(context) } catch (_: Throwable) { "" }
    }

    @RequiresApi(30)
    private fun build(context: Context): String {
        val am = context.getSystemService(ActivityManager::class.java) ?: return ""
        val exits = am.getHistoricalProcessExitReasons(context.packageName, 0, 16)
            .filter { label(it) != null }
            .take(MAX_EXITS)
        if (exits.isEmpty()) return ""
        val fmt = SimpleDateFormat("dd.MM HH:mm:ss", Locale.ROOT)
        var traces = 0
        return buildString {
            for (e in exits) {
                val proc = e.processName.substringAfter(':', "").let { if (it.isEmpty()) "" else " [$it]" }
                val fg = if (e.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE) "foreground" else "background"
                append("${fmt.format(Date(e.timestamp))} ${label(e)}$proc $fg pss=${e.pss / 1024}MB rss=${e.rss / 1024}MB")
                e.description?.lineSequence()?.firstOrNull()?.take(120)?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                appendLine()
                if (traces < MAX_TRACES) {
                    val t = trace(e)
                    if (!t.isNullOrBlank()) { appendLine(t); traces++ }
                }
            }
        }.trim()
    }

    @RequiresApi(30)
    private fun label(e: ApplicationExitInfo): String? = when (e.reason) {
        ApplicationExitInfo.REASON_CRASH -> "crash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "low memory"
        ApplicationExitInfo.REASON_SIGNALED -> "killed (signal ${e.status})"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "init failure"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "excessive resource use"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "dependency died"
        ApplicationExitInfo.REASON_OTHER -> "killed by system"
        else -> null
    }

    @RequiresApi(30)
    private fun trace(e: ApplicationExitInfo): String? = try {
        when {
            e.reason == ApplicationExitInfo.REASON_ANR ->
                e.traceInputStream?.use { anrMainThread(String(readCapped(it), Charsets.UTF_8)) }
            e.reason == ApplicationExitInfo.REASON_CRASH_NATIVE && Build.VERSION.SDK_INT >= 31 ->
                e.traceInputStream?.use { Tombstone.summary(readCapped(it), MAX_FRAMES) }
            else -> null
        }
    } catch (_: Throwable) { null }

    private fun readCapped(input: InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (out.size() < MAX_TRACE_BYTES) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    internal fun anrMainThread(text: String): String? {
        val lines = text.lines()
        val start = lines.indexOfFirst { it.startsWith("\"main\"") }
        if (start < 0) return null
        val out = mutableListOf("  " + lines[start].trim().take(100))
        var frames = 0
        for (i in start + 1 until lines.size) {
            val l = lines[i]
            if (l.isBlank() || l.startsWith("\"")) break
            val t = l.trim()
            if (t.startsWith("at ") || t.startsWith("- ") || t.startsWith("native:")) {
                out += "  " + t.removePrefix("at ").replace("pl.sinusoide.sinuschat.", "").take(140)
                if (++frames >= MAX_FRAMES) break
            }
        }
        return out.joinToString("\n")
    }

    internal object Tombstone {

        private class Pb(val b: ByteArray, var p: Int, val end: Int) {
            fun more() = p < end
            fun varint(): Long {
                var r = 0L
                var s = 0
                while (true) {
                    require(p < end)
                    val x = b[p++].toInt() and 0xFF
                    r = r or ((x and 0x7F).toLong() shl s)
                    if (x < 0x80) return r
                    s += 7
                    require(s < 64)
                }
            }
            fun key(): Pair<Int, Int> { val k = varint(); return (k ushr 3).toInt() to (k and 7).toInt() }
            fun sub(): Pb {
                val len = varint()
                require(len >= 0 && p + len <= end)
                val s = Pb(b, p, p + len.toInt())
                p += len.toInt()
                return s
            }
            fun str(): String = sub().let { String(b, it.p, it.end - it.p, Charsets.UTF_8) }
            fun skip(wt: Int) {
                when (wt) {
                    0 -> varint()
                    1 -> p += 8
                    2 -> sub()
                    5 -> p += 4
                    else -> throw IllegalArgumentException()
                }
                require(p <= end)
            }
        }

        private class Frame(val relPc: Long, val fn: String, val fnOff: Long, val file: String, val buildId: String)
        private class Thread(val id: Long, val name: String, val frames: List<Frame>)

        fun summary(bytes: ByteArray, maxFrames: Int): String? {
            val r = Pb(bytes, 0, bytes.size)
            var tid = -1L
            var sigName = ""
            var sigCode = ""
            var abort = ""
            var cause = ""
            val threads = mutableListOf<Thread>()
            while (r.more()) {
                val (f, wt) = r.key()
                when {
                    f == 6 && wt == 0 -> tid = r.varint()
                    f == 10 && wt == 2 -> {
                        val s = r.sub()
                        while (s.more()) {
                            val (sf, swt) = s.key()
                            when {
                                sf == 2 && swt == 2 -> sigName = s.str()
                                sf == 4 && swt == 2 -> sigCode = s.str()
                                else -> s.skip(swt)
                            }
                        }
                    }
                    f == 14 && wt == 2 -> abort = r.str()
                    f == 15 && wt == 2 && cause.isEmpty() -> {
                        val c = r.sub()
                        while (c.more()) {
                            val (cf, cwt) = c.key()
                            if (cf == 1 && cwt == 2) cause = c.str() else c.skip(cwt)
                        }
                    }
                    f == 16 && wt == 2 -> {
                        val entry = r.sub()
                        while (entry.more()) {
                            val (ef, ewt) = entry.key()
                            if (ef == 2 && ewt == 2) threads += thread(entry.sub()) else entry.skip(ewt)
                        }
                    }
                    else -> r.skip(wt)
                }
            }
            val crashed = threads.firstOrNull { it.id == tid } ?: threads.firstOrNull()
            if (sigName.isEmpty() && crashed == null) return null
            return buildString {
                append("  ").append(listOf(sigName, sigCode).filter { it.isNotEmpty() }.joinToString(" "))
                if (cause.isNotEmpty()) append(" · ").append(cause.take(120))
                appendLine()
                if (abort.isNotEmpty()) appendLine("  abort: " + abort.lineSequence().first().take(200))
                if (crashed != null) {
                    appendLine("  thread \"${crashed.name.take(40)}\"")
                    crashed.frames.take(maxFrames).forEachIndexed { i, fr ->
                        append("  #%02d %s +0x%x".format(i, fr.file.substringAfterLast('/').substringAfterLast('!').ifEmpty { "?" }, fr.relPc))
                        if (fr.fn.isNotEmpty()) append(" (${fr.fn.take(80)}+${fr.fnOff})")
                        if (fr.buildId.isNotEmpty()) append(" [${fr.buildId.take(12)}]")
                        appendLine()
                    }
                }
            }.trimEnd()
        }

        private fun thread(t: Pb): Thread {
            var id = -1L
            var name = ""
            val frames = mutableListOf<Frame>()
            while (t.more()) {
                val (f, wt) = t.key()
                when {
                    f == 1 && wt == 0 -> id = t.varint()
                    f == 2 && wt == 2 -> name = t.str()
                    f == 4 && wt == 2 -> frames += frame(t.sub())
                    else -> t.skip(wt)
                }
            }
            return Thread(id, name, frames)
        }

        private fun frame(fr: Pb): Frame {
            var relPc = 0L
            var fn = ""
            var fnOff = 0L
            var file = ""
            var buildId = ""
            while (fr.more()) {
                val (f, wt) = fr.key()
                when {
                    f == 1 && wt == 0 -> relPc = fr.varint()
                    f == 4 && wt == 2 -> fn = fr.str()
                    f == 5 && wt == 0 -> fnOff = fr.varint()
                    f == 6 && wt == 2 -> file = fr.str()
                    f == 8 && wt == 2 -> buildId = fr.str()
                    else -> fr.skip(wt)
                }
            }
            return Frame(relPc, fn, fnOff, file, buildId)
        }
    }
}
