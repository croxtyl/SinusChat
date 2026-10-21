package pl.sinusoide.sinuschat.util

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class StreamGcm(key: ByteArray, iv: ByteArray) {

    private val ctr: Cipher
    private val tagMask: ByteArray
    private val hl = LongArray(16)
    private val hh = LongArray(16)
    private var xh = 0L
    private var xl = 0L
    private val pending = ByteArray(16)
    private var pendingLen = 0
    private var cipherBytes = 0L

    init {
        require(key.size == 32 && iv.size == 12)
        val spec = SecretKeySpec(key, "AES")
        val ecb = Cipher.getInstance("AES/ECB/NoPadding").apply { init(Cipher.ENCRYPT_MODE, spec) }
        buildTable(ecb.doFinal(ByteArray(16)))
        tagMask = ecb.doFinal(iv + byteArrayOf(0, 0, 0, 1))
        ctr = Cipher.getInstance("AES/CTR/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, spec, IvParameterSpec(iv + byteArrayOf(0, 0, 0, 2)))
        }
    }

    private fun buildTable(h: ByteArray) {
        var vh = beLong(h, 0)
        var vl = beLong(h, 8)
        hl[8] = vl; hh[8] = vh
        var i = 4
        while (i > 0) {
            val t = if (vl and 1L != 0L) 0xe1000000L else 0L
            vl = (vh shl 63) or (vl ushr 1)
            vh = (vh ushr 1) xor (t shl 32)
            hl[i] = vl; hh[i] = vh
            i = i shr 1
        }
        i = 2
        while (i <= 8) {
            vh = hh[i]; vl = hl[i]
            for (j in 1 until i) {
                hh[i + j] = vh xor hh[j]
                hl[i + j] = vl xor hl[j]
            }
            i *= 2
        }
    }

    private fun multiply() {
        val ah = xh
        val al = xl
        fun byteAt(i: Int): Int =
            ((if (i < 8) ah ushr (56 - 8 * i) else al ushr (56 - 8 * (i - 8))) and 0xff).toInt()
        var lo = byteAt(15) and 0xf
        var zh = hh[lo]
        var zl = hl[lo]
        for (i in 15 downTo 0) {
            val b = byteAt(i)
            lo = b and 0xf
            val hi = (b ushr 4) and 0xf
            if (i != 15) {
                val rem = (zl and 0xf).toInt()
                zl = (zh shl 60) or (zl ushr 4)
                zh = (zh ushr 4) xor (LAST4[rem] shl 48)
                zh = zh xor hh[lo]
                zl = zl xor hl[lo]
            }
            val rem = (zl and 0xf).toInt()
            zl = (zh shl 60) or (zl ushr 4)
            zh = (zh ushr 4) xor (LAST4[rem] shl 48)
            zh = zh xor hh[hi]
            zl = zl xor hl[hi]
        }
        xh = zh
        xl = zl
    }

    private fun absorb(data: ByteArray, off: Int, len: Int) {
        cipherBytes += len
        var p = off
        val end = off + len
        if (pendingLen > 0) {
            val take = minOf(16 - pendingLen, end - p)
            System.arraycopy(data, p, pending, pendingLen, take)
            pendingLen += take
            p += take
            if (pendingLen < 16) return
            xh = xh xor beLong(pending, 0); xl = xl xor beLong(pending, 8)
            multiply()
            pendingLen = 0
        }
        while (end - p >= 16) {
            xh = xh xor beLong(data, p); xl = xl xor beLong(data, p + 8)
            multiply()
            p += 16
        }
        if (p < end) {
            System.arraycopy(data, p, pending, 0, end - p)
            pendingLen = end - p
        }
    }

    private fun tag(): ByteArray {
        if (pendingLen > 0) {
            for (i in pendingLen until 16) pending[i] = 0
            xh = xh xor beLong(pending, 0); xl = xl xor beLong(pending, 8)
            multiply()
            pendingLen = 0
        }
        xl = xl xor (cipherBytes * 8)
        multiply()
        val out = ByteArray(16)
        putLong(out, 0, xh); putLong(out, 8, xl)
        for (i in 0 until 16) out[i] = (out[i].toInt() xor tagMask[i].toInt()).toByte()
        return out
    }

    fun encrypt(input: InputStream, output: OutputStream, onBytes: ((Long) -> Unit)? = null) {
        val buf = ByteArray(BUFFER)
        val out = ByteArray(BUFFER + 16)
        var done = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            if (n == 0) continue
            val m = ctr.update(buf, 0, n, out, 0)
            if (m > 0) { absorb(out, 0, m); output.write(out, 0, m) }
            done += n
            onBytes?.invoke(done)
        }
        val rest = ctr.doFinal()
        if (rest.isNotEmpty()) { absorb(rest, 0, rest.size); output.write(rest) }
        output.write(tag())
    }

    fun decrypt(input: InputStream, totalLength: Long, output: OutputStream, onBytes: ((Long) -> Unit)? = null): Boolean {
        if (totalLength < 16) return false
        var left = totalLength - 16
        val buf = ByteArray(BUFFER)
        val out = ByteArray(BUFFER + 16)
        var done = 0L
        while (left > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n < 0) return false
            if (n == 0) continue
            absorb(buf, 0, n)
            val m = ctr.update(buf, 0, n, out, 0)
            if (m > 0) output.write(out, 0, m)
            left -= n
            done += n
            onBytes?.invoke(done)
        }
        val rest = ctr.doFinal()
        if (rest.isNotEmpty()) output.write(rest)
        val given = ByteArray(16)
        var got = 0
        while (got < 16) {
            val n = input.read(given, got, 16 - got)
            if (n < 0) return false
            got += n
        }
        return MessageDigest.isEqual(given, tag())
    }

    companion object {
        private const val BUFFER = 64 * 1024

        private val LAST4 = longArrayOf(
            0x0000, 0x1c20, 0x3840, 0x2460, 0x7080, 0x6ca0, 0x48c0, 0x54e0,
            0xe100, 0xfd20, 0xd940, 0xc560, 0x9180, 0x8da0, 0xa9c0, 0xb5e0
        )

        private fun beLong(b: ByteArray, off: Int): Long {
            var v = 0L
            for (i in 0 until 8) v = (v shl 8) or (b[off + i].toLong() and 0xff)
            return v
        }

        private fun putLong(b: ByteArray, off: Int, v: Long) {
            for (i in 0 until 8) b[off + i] = (v ushr (56 - 8 * i)).toByte()
        }
    }
}
