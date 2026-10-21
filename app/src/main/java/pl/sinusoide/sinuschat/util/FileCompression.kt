package pl.sinusoide.sinuschat.util

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object FileCompression {

    val MARKER = "SCGZIP01".toByteArray(Charsets.US_ASCII)

    private class Limited(out: OutputStream, private val limit: Long) : FilterOutputStream(out) {
        var written = 0L
            private set
        var exceeded = false
            private set

        override fun write(b: Int) {
            written++
            if (written > limit) { exceeded = true; throw LimitReached() }
            out.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            written += len
            if (written > limit) { exceeded = true; throw LimitReached() }
            out.write(b, off, len)
        }
    }

    private class LimitReached : java.io.IOException()

    fun pack(input: InputStream, target: File, limit: Long, totalHint: Long = -1L,
             isCancelled: () -> Boolean = { false }, onProgress: ((Float) -> Unit)? = null): Boolean {
        var ok = false
        try {
            BufferedOutputStream(FileOutputStream(target), 256 * 1024).use { file ->
                val limited = Limited(file, limit)
                limited.write(MARKER)
                val gz = object : GZIPOutputStream(limited, 256 * 1024) {
                    init { def.setLevel(Deflater.DEFAULT_COMPRESSION) }
                }
                val buf = ByteArray(256 * 1024)
                var read = 0L
                BufferedInputStream(input, 256 * 1024).use { src ->
                    while (true) {
                        if (isCancelled()) return false
                        val n = src.read(buf)
                        if (n < 0) break
                        gz.write(buf, 0, n)
                        read += n
                        if (totalHint > 0) onProgress?.invoke((read.toFloat() / totalHint).coerceIn(0f, 1f))
                    }
                }
                gz.finish()
                gz.flush()
                ok = !limited.exceeded
            }
        } catch (_: LimitReached) {
            ok = false
        } catch (_: Exception) {
            ok = false
        } finally {
            if (!ok) target.delete()
        }
        return ok
    }

    fun isPacked(file: File): Boolean {
        if (file.length() < MARKER.size) return false
        val head = ByteArray(MARKER.size)
        FileInputStream(file).use { input ->
            var got = 0
            while (got < head.size) {
                val n = input.read(head, got, head.size - got)
                if (n < 0) return false
                got += n
            }
        }
        return head.contentEquals(MARKER)
    }

    fun unpack(source: File, target: File): Boolean = try {
        FileInputStream(source).use { raw ->
            var skipped = 0L
            while (skipped < MARKER.size) {
                val n = raw.skip(MARKER.size - skipped)
                if (n <= 0) return false
                skipped += n
            }
            GZIPInputStream(BufferedInputStream(raw, 256 * 1024), 256 * 1024).use { gz ->
                BufferedOutputStream(FileOutputStream(target), 256 * 1024).use { out -> gz.copyTo(out, 256 * 1024) }
            }
        }
        true
    } catch (_: Exception) {
        target.delete()
        false
    }
}
