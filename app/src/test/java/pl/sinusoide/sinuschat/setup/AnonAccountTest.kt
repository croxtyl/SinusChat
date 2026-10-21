package pl.sinusoide.sinuschat.setup

import com.google.crypto.tink.subtle.Ed25519Sign
import com.google.crypto.tink.subtle.Ed25519Verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class AnonAccountTest {

    private fun serverZeroBits(buf: ByteArray): Int {
        var n = 0
        for (byte in buf) {
            val v = byte.toInt() and 0xFF
            if (v == 0) { n += 8; continue }
            for (b in 7 downTo 0) { if (v and (1 shl b) != 0) return n; n++ }
        }
        return n
    }

    @Test
    fun proofOfWorkMatchesServerCheck() {
        val challenge = "0123456789abcdef0123456789abcdef"
        val bits = 14
        val nonce = AnonAccount.solve(challenge, bits)
        val d = MessageDigest.getInstance("SHA-256").digest("$challenge:$nonce".toByteArray())
        assertTrue(serverZeroBits(d) >= bits)
        assertEquals(serverZeroBits(d), AnonAccount.zeroBits(d))
    }

    @Test
    fun zeroBitsAgreesWithServerOnEdges() {
        val cases = listOf(
            byteArrayOf(0x80.toByte()), byteArrayOf(0x01), byteArrayOf(0, 0x40), byteArrayOf(0, 0, 0), byteArrayOf(0x0F)
        )
        for (c in cases) assertEquals(serverZeroBits(c), AnonAccount.zeroBits(c))
    }

    @Test
    fun ed25519LoginSignatureVerifies() {
        val seed = MessageDigest.getInstance("SHA-256").digest("seed".toByteArray())
        val kp1 = Ed25519Sign.KeyPair.newKeyPairFromSeed(seed)
        val kp2 = Ed25519Sign.KeyPair.newKeyPairFromSeed(seed)
        assertTrue(kp1.publicKey.contentEquals(kp2.publicKey))
        assertEquals(32, kp1.publicKey.size)
        val msg = "SinusChat login v1|abc|def".toByteArray()
        val sig = Ed25519Sign(seed).sign(msg)
        assertEquals(64, sig.size)
        Ed25519Verify(kp1.publicKey).verify(sig, msg)
    }
}
