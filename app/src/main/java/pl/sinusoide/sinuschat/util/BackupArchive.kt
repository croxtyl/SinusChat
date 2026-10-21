package pl.sinusoide.sinuschat.util

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

object BackupArchive {

    const val END = 0
    const val MANIFEST = 1
    const val MESSAGES = 2
    const val PREFS = 3
    const val FILE = 4
    const val CALLS = 5

    private const val FLAG_DEFLATE = 1
    private const val MAX_NAME = 4096
    private const val BUF = 64 * 1024

    class Writer(out: OutputStream) : Closeable {

        private val out = DataOutputStream(out)
        private var count = 0
        private var closed = false

        fun bytes(type: Int, name: String, data: ByteArray, compress: Boolean = true) {
            val body = if (compress) deflate(data) else data
            head(type, if (compress) FLAG_DEFLATE else 0, name, body.size.toLong())
            out.write(body)
            count++
        }

        fun file(name: String, file: File) {
            val len = file.length()
            head(FILE, 0, name, len)
            val buf = ByteArray(BUF)
            var left = len
            file.inputStream().use { src ->
                while (left > 0) {
                    val n = src.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                    if (n < 0) throw IOException("Plik zmienił się w trakcie kopii: $name")
                    out.write(buf, 0, n)
                    left -= n
                }
            }
            count++
        }

        private fun head(type: Int, flags: Int, name: String, size: Long) {
            val n = name.toByteArray(Charsets.UTF_8)
            require(n.size <= MAX_NAME) { "Za długa nazwa: $name" }
            out.writeByte(type)
            out.writeByte(flags)
            out.writeShort(n.size)
            out.write(n)
            out.writeLong(size)
        }

        override fun close() {
            if (closed) return
            closed = true
            head(END, 0, "", 4)
            out.writeInt(count)
            out.close()
        }

        private fun deflate(data: ByteArray): ByteArray {
            val bos = ByteArrayOutputStream(data.size / 3 + 64)
            DeflaterOutputStream(bos, Deflater(Deflater.BEST_COMPRESSION)).use { it.write(data) }
            return bos.toByteArray()
        }
    }

    class Entry(val type: Int, val name: String, val size: Long, internal val compressed: Boolean)

    class Reader(inp: InputStream) : Closeable {

        private val inp = DataInputStream(inp)
        private var current: Entry? = null
        private var left = 0L
        private var count = 0
        private var ended = false

        fun next(): Entry? {
            if (ended) return null
            skipRest()
            current = null
            val type = inp.readUnsignedByte()
            val flags = inp.readUnsignedByte()
            val nameLen = inp.readUnsignedShort()
            if (nameLen > MAX_NAME) throw IOException("Uszkodzona kopia")
            val name = ByteArray(nameLen).also { inp.readFully(it) }.toString(Charsets.UTF_8)
            val size = inp.readLong()
            if (size < 0) throw IOException("Uszkodzona kopia")
            if (type == END) {
                if (size != 4L || inp.readInt() != count) throw IOException("Kopia jest niepełna")
                ended = true
                return null
            }
            count++
            left = size
            return Entry(type, name, size, (flags and FLAG_DEFLATE) != 0).also { current = it }
        }

        fun stream(): InputStream {
            val e = current ?: throw IllegalStateException("Brak bieżącego wpisu")
            val raw = object : InputStream() {
                override fun read(): Int {
                    if (left <= 0) return -1
                    val b = inp.read()
                    if (b < 0) throw EOFException("Kopia jest niepełna")
                    left--
                    return b
                }

                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    if (left <= 0) return -1
                    val n = inp.read(b, off, minOf(len.toLong(), left).toInt())
                    if (n < 0) throw EOFException("Kopia jest niepełna")
                    left -= n
                    return n
                }

                override fun close() = Unit
            }
            return if (e.compressed) InflaterInputStream(raw) else raw
        }

        fun readBytes(): ByteArray = stream().use { it.readBytes() }

        private fun skipRest() {
            while (left > 0) {
                val n = inp.skip(left)
                if (n > 0) { left -= n; continue }
                if (inp.read() < 0) throw EOFException("Kopia jest niepełna")
                left--
            }
        }

        override fun close() = inp.close()
    }
}
