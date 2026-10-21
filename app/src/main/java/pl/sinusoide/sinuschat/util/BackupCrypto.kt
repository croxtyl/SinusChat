package pl.sinusoide.sinuschat.util

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object BackupCrypto {

    val MAGIC = "SINUSCHAT".toByteArray(Charsets.US_ASCII)
    private val LEGACY_MAGIC = "SCBK".toByteArray(Charsets.US_ASCII)
    const val VERSION = 2
    const val FLAG_MEDIA = 1
    const val KEY_RECOVERY_WORDS = 1

    const val HEADER_LEN = 44
    private const val SALT_LEN = 16
    private const val NONCE_PREFIX_LEN = 8
    private const val CHUNK = 1 shl 20
    private const val TAG_BITS = 128
    private const val TAG_LEN = TAG_BITS / 8
    private const val LAST_FLAG = 0x8000_0000.toInt()

    class LegacyFormatException : IOException("Stary format kopii")

    class WrongKeyException : IOException("Inny klucz odzyskiwania")

    class Header(
        val version: Int,
        val flags: Int,
        val keyType: Int,
        val keyId: ByteArray,
        val salt: ByteArray,
        val noncePrefix: ByteArray
    ) {
        val withMedia: Boolean get() = (flags and FLAG_MEDIA) != 0

        fun bytes(): ByteArray {
            val b = ByteArray(HEADER_LEN)
            System.arraycopy(MAGIC, 0, b, 0, MAGIC.size)
            b[9] = version.toByte()
            b[10] = flags.toByte()
            b[11] = keyType.toByte()
            System.arraycopy(keyId, 0, b, 12, RecoveryKey.ID_LEN)
            System.arraycopy(salt, 0, b, 20, SALT_LEN)
            System.arraycopy(noncePrefix, 0, b, 36, NONCE_PREFIX_LEN)
            return b
        }

        companion object {
            fun create(flags: Int, key: RecoveryKey): Header {
                val rnd = SecureRandom()
                val salt = ByteArray(SALT_LEN).also { rnd.nextBytes(it) }
                val prefix = ByteArray(NONCE_PREFIX_LEN).also { rnd.nextBytes(it) }
                return Header(VERSION, flags, KEY_RECOVERY_WORDS, key.id.copyOf(), salt, prefix)
            }

            fun read(inp: InputStream): Header {
                val b = ByteArray(HEADER_LEN)
                val got = readUpTo(inp, b)
                if (got >= LEGACY_MAGIC.size && (0 until LEGACY_MAGIC.size).all { b[it] == LEGACY_MAGIC[it] })
                    throw LegacyFormatException()
                if (got < HEADER_LEN || (MAGIC.indices).any { b[it] != MAGIC[it] })
                    throw IOException("To nie jest kopia SinusChat")
                val version = b[9].toInt() and 0xFF
                if (version != VERSION) throw IOException("Nieobsługiwana wersja kopii: $version")
                val keyType = b[11].toInt() and 0xFF
                if (keyType != KEY_RECOVERY_WORDS) throw IOException("Nieznany rodzaj klucza: $keyType")
                return Header(version, b[10].toInt() and 0xFF, keyType,
                    b.copyOfRange(12, 12 + RecoveryKey.ID_LEN),
                    b.copyOfRange(20, 20 + SALT_LEN),
                    b.copyOfRange(36, 36 + NONCE_PREFIX_LEN))
            }
        }
    }

    fun encryptingStream(out: OutputStream, key: RecoveryKey, header: Header): OutputStream =
        ChunkedOut(out, key.fileKey(header.salt), header)

    fun decryptingStream(inp: InputStream, key: RecoveryKey, header: Header): InputStream {
        if (!key.matches(header.keyId)) throw WrongKeyException()
        return ChunkedIn(inp, key.fileKey(header.salt), header)
    }

    private fun nonce(header: Header, counter: Int): ByteArray {
        val n = ByteArray(12)
        System.arraycopy(header.noncePrefix, 0, n, 0, NONCE_PREFIX_LEN)
        writeInt(n, NONCE_PREFIX_LEN, counter)
        return n
    }

    private fun aad(headerBytes: ByteArray, counter: Int, last: Boolean): ByteArray {
        val a = ByteArray(headerBytes.size + 5)
        System.arraycopy(headerBytes, 0, a, 0, headerBytes.size)
        writeInt(a, headerBytes.size, counter)
        a[a.size - 1] = if (last) 1 else 0
        return a
    }

    private class ChunkedOut(
        private val out: OutputStream,
        private val key: SecretKey,
        private val header: Header
    ) : OutputStream() {

        private val headerBytes = header.bytes()
        private val buf = ByteArray(CHUNK)
        private var len = 0
        private var counter = 0
        private var closed = false

        override fun write(b: Int) {
            buf[len++] = b.toByte()
            if (len == CHUNK) seal(false)
        }

        override fun write(b: ByteArray, off: Int, l: Int) {
            var o = off
            var left = l
            while (left > 0) {
                val n = minOf(left, CHUNK - len)
                System.arraycopy(b, o, buf, len, n)
                len += n; o += n; left -= n
                if (len == CHUNK) seal(false)
            }
        }

        private fun seal(last: Boolean) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce(header, counter)))
            cipher.updateAAD(aad(headerBytes, counter, last))
            val ct = cipher.doFinal(buf, 0, len)
            val head = ByteArray(4)
            writeInt(head, 0, if (last) (len or LAST_FLAG) else len)
            out.write(head)
            out.write(ct)
            counter++
            len = 0
        }

        override fun flush() = out.flush()

        override fun close() {
            if (closed) return
            closed = true
            seal(true)
            out.flush()
            out.close()
        }
    }

    private class ChunkedIn(
        private val inp: InputStream,
        private val key: SecretKey,
        private val header: Header
    ) : InputStream() {

        private val headerBytes = header.bytes()
        private var plain = ByteArray(0)
        private var pos = 0
        private var counter = 0
        private var finished = false

        private fun fill(): Boolean {
            if (finished) return false
            val head = ByteArray(4)
            readFully(inp, head, 4)
            val raw = readInt(head, 0)
            val last = (raw and LAST_FLAG) != 0
            val len = raw and LAST_FLAG.inv()
            if (len < 0 || len > CHUNK) throw IOException("Uszkodzona kopia")
            val ct = ByteArray(len + TAG_LEN)
            readFully(inp, ct, ct.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce(header, counter)))
            cipher.updateAAD(aad(headerBytes, counter, last))
            plain = cipher.doFinal(ct)
            pos = 0
            counter++
            if (last) finished = true
            return plain.isNotEmpty() || !last
        }

        override fun read(): Int {
            while (pos >= plain.size) if (!fill()) return -1
            return plain[pos++].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, l: Int): Int {
            if (l == 0) return 0
            while (pos >= plain.size) if (!fill()) return -1
            val n = minOf(l, plain.size - pos)
            System.arraycopy(plain, pos, b, off, n)
            pos += n
            return n
        }

        override fun available(): Int = plain.size - pos

        override fun close() = inp.close()
    }

    private fun writeInt(b: ByteArray, off: Int, v: Int) {
        b[off] = (v ushr 24).toByte()
        b[off + 1] = (v ushr 16).toByte()
        b[off + 2] = (v ushr 8).toByte()
        b[off + 3] = v.toByte()
    }

    private fun readInt(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 24) or
        ((b[off + 1].toInt() and 0xFF) shl 16) or
        ((b[off + 2].toInt() and 0xFF) shl 8) or
        (b[off + 3].toInt() and 0xFF)

    private fun readUpTo(inp: InputStream, b: ByteArray): Int {
        var read = 0
        while (read < b.size) {
            val n = inp.read(b, read, b.size - read)
            if (n < 0) break
            read += n
        }
        return read
    }

    private fun readFully(inp: InputStream, b: ByteArray, len: Int) {
        var read = 0
        while (read < len) {
            val n = inp.read(b, read, len - read)
            if (n < 0) throw IOException("Plik kopii jest niepełny")
            read += n
        }
    }
}
