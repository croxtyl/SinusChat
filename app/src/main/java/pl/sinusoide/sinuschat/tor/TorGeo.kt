package pl.sinusoide.sinuschat.tor

import android.content.Context
import android.util.Log
import java.io.DataInputStream

object TorGeo {

    private class Db(val table: Array<String>, val starts: IntArray, val codes: ByteArray)

    @Volatile private var db: Db? = null

    @Synchronized
    private fun load(ctx: Context): Db? {
        db?.let { return it }
        return try {
            ctx.applicationContext.assets.open("geoip4.bin").use { parse(it) }?.also { db = it }
        } catch (e: Exception) {
            Log.w("TorGeo", "Baza krajów niedostępna: ${e.javaClass.simpleName}")
            null
        }
    }

    private fun parse(stream: java.io.InputStream): Db? {
        val input = DataInputStream(stream.buffered(256 * 1024))
        val magic = ByteArray(4)
        input.readFully(magic)
        if (String(magic, Charsets.US_ASCII) != "SCG1") return null
        val m = input.readUnsignedShort()
        val n = input.readInt()
        val table = Array(m) {
            val b = ByteArray(2); input.readFully(b); String(b, Charsets.US_ASCII)
        }
        val starts = IntArray(n) { input.readInt() }
        val codes = ByteArray(n)
        input.readFully(codes)
        return Db(table, starts, codes)
    }

    internal fun lookup(stream: java.io.InputStream, ip: String): String? {
        val d = parse(stream) ?: return null
        return find(d, ip)
    }

    private fun find(d: Db, ip: String): String? {
        val v = ipv4(ip) ?: return null
        var lo = 0
        var hi = d.starts.size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if ((d.starts[mid].toLong() and 0xFFFFFFFFL) <= v) { found = mid; lo = mid + 1 } else hi = mid - 1
        }
        if (found < 0) return null
        val idx = d.codes[found].toInt() and 0xFF
        return if (idx == 255 || idx >= d.table.size) null else d.table[idx]
    }

    private fun ipv4(ip: String): Long? {
        val p = ip.trim().split('.')
        if (p.size != 4) return null
        var v = 0L
        for (s in p) {
            val n = s.toIntOrNull() ?: return null
            if (n !in 0..255) return null
            v = (v shl 8) or n.toLong()
        }
        return v
    }

    fun country(ctx: Context, ip: String): String? {
        if (ipv4(ip) == null) return null
        val d = load(ctx) ?: return null
        return find(d, ip)
    }
}
