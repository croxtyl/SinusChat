package pl.sinusoide.sinuschat.util

import android.content.Context
import pl.sinusoide.sinuschat.R
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class RecoveryKey private constructor(private val entropy: ByteArray) {

    val id: ByteArray = hmac(entropy, ID_LABEL.toByteArray()).copyOf(ID_LEN)

    val shortId: String get() = formatId(id)

    fun words(ctx: Context): List<String> = words(wordList(ctx))

    internal fun words(list: List<String>): List<String> {
        val bits = BooleanArray(BITS + CHECK_BITS)
        for (i in 0 until BITS) bits[i] = (entropy[i / 8].toInt() shr (7 - i % 8)) and 1 == 1
        val check = sha256(entropy)[0].toInt()
        for (i in 0 until CHECK_BITS) bits[BITS + i] = (check shr (7 - i)) and 1 == 1
        return List(WORDS) { w ->
            var index = 0
            for (b in 0 until 11) index = (index shl 1) or (if (bits[w * 11 + b]) 1 else 0)
            list[index]
        }
    }

    fun qrPayload(): String = QR_PREFIX + hex(entropy) + hex(sha256(entropy).copyOf(1))

    fun fileKey(salt: ByteArray): SecretKey {
        val prk = hmac(salt, entropy)
        val okm = hmac(prk, FILE_INFO.toByteArray() + byteArrayOf(1))
        return SecretKeySpec(okm, "AES")
    }

    fun matches(otherId: ByteArray): Boolean = MessageDigest.isEqual(id, otherId)

    fun proof(label: String, data: ByteArray): ByteArray = hmac(entropy, label.toByteArray() + data)

    fun toStorage(): String = hex(entropy)

    companion object {
        const val WORDS = 12
        const val ID_LEN = 8
        private const val BITS = 128
        private const val CHECK_BITS = 4
        private const val QR_PREFIX = "SINUSCHAT-KEY:1:"
        private const val ID_LABEL = "SinusChat backup key id"
        private const val FILE_INFO = "SinusChat backup file key v2"

        @Volatile private var cachedWords: List<String>? = null

        fun generate(): RecoveryKey =
            RecoveryKey(ByteArray(BITS / 8).also { SecureRandom().nextBytes(it) })

        fun fromStorage(value: String): RecoveryKey? =
            unhex(value)?.takeIf { it.size == BITS / 8 }?.let { RecoveryKey(it) }

        fun wordList(ctx: Context): List<String> = cachedWords ?: synchronized(this) {
            cachedWords ?: ctx.applicationContext.resources.openRawResource(R.raw.backup_words)
                .bufferedReader().readLines().map { it.trim() }.filter { it.isNotEmpty() }
                .also { cachedWords = it }
        }

        fun resolve(ctx: Context, typed: String): String? = resolve(wordList(ctx), typed)

        internal fun resolve(list: List<String>, typed: String): String? {
            val t = typed.trim().lowercase()
            if (t.isEmpty()) return null
            if (list.binarySearch(t) >= 0) return t
            if (t.length < 4) return null
            val hits = list.filter { it.startsWith(t) }
            return hits.singleOrNull()
        }

        fun fromWords(ctx: Context, typed: List<String>): RecoveryKey? = fromWords(wordList(ctx), typed)

        internal fun fromWords(list: List<String>, typed: List<String>): RecoveryKey? {
            if (typed.size != WORDS) return null
            val bits = BooleanArray(BITS + CHECK_BITS)
            for ((w, word) in typed.withIndex()) {
                val index = list.binarySearch(resolve(list, word) ?: return null)
                if (index < 0) return null
                for (b in 0 until 11) bits[w * 11 + b] = (index shr (10 - b)) and 1 == 1
            }
            val entropy = ByteArray(BITS / 8)
            for (i in 0 until BITS) if (bits[i]) entropy[i / 8] = (entropy[i / 8].toInt() or (1 shl (7 - i % 8))).toByte()
            val check = sha256(entropy)[0].toInt()
            for (i in 0 until CHECK_BITS) if (bits[BITS + i] != ((check shr (7 - i)) and 1 == 1)) return null
            return RecoveryKey(entropy)
        }

        fun fromQr(ctx: Context, text: String): RecoveryKey? = fromQr(wordList(ctx), text)

        internal fun fromQr(list: List<String>, text: String): RecoveryKey? {
            val t = text.trim()
            if (t.startsWith(QR_PREFIX, ignoreCase = true)) {
                val raw = unhex(t.substring(QR_PREFIX.length)) ?: return null
                if (raw.size != BITS / 8 + 1) return null
                val entropy = raw.copyOf(BITS / 8)
                if (sha256(entropy)[0] != raw[BITS / 8]) return null
                return RecoveryKey(entropy)
            }
            return fromWords(list, tokens(t))
        }

        fun tokens(text: String): List<String> =
            text.split(Regex("[^A-Za-z]+")).filter { it.isNotEmpty() }.map { it.lowercase() }

        fun formatId(id: ByteArray): String =
            hex(id.copyOf(4)).let { it.substring(0, 4) + "-" + it.substring(4) }

        private fun sha256(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)

        private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
            Mac.getInstance("HmacSHA256").run {
                init(SecretKeySpec(key, "HmacSHA256"))
                doFinal(data)
            }

        private fun hex(b: ByteArray): String {
            val digits = "0123456789ABCDEF"
            val sb = StringBuilder(b.size * 2)
            for (x in b) sb.append(digits[(x.toInt() shr 4) and 0xF]).append(digits[x.toInt() and 0xF])
            return sb.toString()
        }

        private fun unhex(s: String): ByteArray? {
            if (s.length % 2 != 0) return null
            val out = ByteArray(s.length / 2)
            for (i in out.indices) {
                val hi = Character.digit(s[i * 2], 16)
                val lo = Character.digit(s[i * 2 + 1], 16)
                if (hi < 0 || lo < 0) return null
                out[i] = ((hi shl 4) or lo).toByte()
            }
            return out
        }
    }
}
