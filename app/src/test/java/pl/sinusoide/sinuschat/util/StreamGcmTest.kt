package pl.sinusoide.sinuschat.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

class StreamGcmTest {

    private val sizes = listOf(0, 1, 15, 16, 17, 31, 32, 33, 1000, 65_536, 65_537, 200_003, 1_000_000)

    private fun javaGcm(key: ByteArray, iv: ByteArray, data: ByteArray, mode: Int): ByteArray =
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            doFinal(data)
        }

    @Test
    fun szyfrujeTakJakZwyklyGcm() {
        for (n in sizes) {
            val rnd = Random(n + 7)
            val key = rnd.nextBytes(32); val iv = rnd.nextBytes(12); val data = rnd.nextBytes(n)
            val out = ByteArrayOutputStream()
            StreamGcm(key, iv).encrypt(ByteArrayInputStream(data), out)
            assertArrayEquals("n=$n", javaGcm(key, iv, data, Cipher.ENCRYPT_MODE), out.toByteArray())
        }
    }

    @Test
    fun odszyfrowujeZwyklyGcm() {
        for (n in sizes) {
            val rnd = Random(n + 11)
            val key = rnd.nextBytes(32); val iv = rnd.nextBytes(12); val data = rnd.nextBytes(n)
            val sealed = javaGcm(key, iv, data, Cipher.ENCRYPT_MODE)
            val out = ByteArrayOutputStream()
            assertTrue("n=$n", StreamGcm(key, iv).decrypt(ByteArrayInputStream(sealed), sealed.size.toLong(), out))
            assertArrayEquals("n=$n", data, out.toByteArray())
        }
    }

    @Test
    fun odrzucaZmienionyPlik() {
        val rnd = Random(3)
        val key = rnd.nextBytes(32); val iv = rnd.nextBytes(12); val data = rnd.nextBytes(5000)
        val sealed = javaGcm(key, iv, data, Cipher.ENCRYPT_MODE)
        for (pos in listOf(0, 2500, sealed.size - 1)) {
            val bad = sealed.copyOf()
            bad[pos] = (bad[pos].toInt() xor 1).toByte()
            assertFalse("pos=$pos", StreamGcm(key, iv).decrypt(ByteArrayInputStream(bad), bad.size.toLong(), ByteArrayOutputStream()))
        }
        val wrongKey = key.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertFalse(StreamGcm(wrongKey, iv).decrypt(ByteArrayInputStream(sealed), sealed.size.toLong(), ByteArrayOutputStream()))
    }
}
