package pl.sinusoide.sinuschat.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.signal.libsignal.protocol.ecc.ECKeyPair

class SealedSenderTest {

    private val alice = ECKeyPair.generate()
    private val bob = ECKeyPair.generate()
    private val mallory = ECKeyPair.generate()
    private val inner = """{"s":"alice","k":"msg","p":"abc"}""".toByteArray()

    private fun sealFor(from: ECKeyPair, to: ECKeyPair, body: ByteArray = inner) =
        SealedSender.sealRaw(from.privateKey, from.publicKey.serialize(), to.publicKey.serialize(), body)

    private fun openBy(who: ECKeyPair, blob: ByteArray) =
        SealedSender.openRaw(who.privateKey, who.publicKey.serialize(), blob)

    @Test
    fun adresatOtwieraIPoznajeKluczNadawcy() {
        val opened = openBy(bob, sealFor(alice, bob))
        assertNotNull(opened)
        assertArrayEquals(alice.publicKey.serialize(), opened!!.first)
        assertArrayEquals(inner, opened.second)
    }

    @Test
    fun ktosInnyNieOtworzy() {
        assertNull(openBy(mallory, sealFor(alice, bob)))
    }

    @Test
    fun zmienionaKopertaSieNieOtwiera() {
        val blob = sealFor(alice, bob)
        for (i in listOf(1, 20, 40, blob.size / 2, blob.size - 1)) {
            val bad = blob.copyOf()
            bad[i] = (bad[i].toInt() xor 1).toByte()
            assertNull("bajt $i", openBy(bob, bad))
        }
        assertNull(openBy(bob, blob.copyOf(blob.size - 5)))
        assertNull(openBy(bob, ByteArray(10)))
    }

    @Test
    fun podszycieSieWidacPoKluczu() {
        val opened = openBy(bob, sealFor(mallory, bob))!!
        assertFalse(alice.publicKey.serialize().contentEquals(opened.first))
        assertArrayEquals(mallory.publicKey.serialize(), opened.first)
    }

    @Test
    fun kazdaKopertaInna() {
        assertFalse(sealFor(alice, bob).contentEquals(sealFor(alice, bob)))
    }

    @Test
    fun kluczDostepuZaleznyOdKluczaProfilu() {
        val a = SealedSender.accessKey(ByteArray(32) { 1 })
        val b = SealedSender.accessKey(ByteArray(32) { 2 })
        assertEquals(16, a.size)
        assertFalse(a.contentEquals(b))
        assertArrayEquals(a, SealedSender.accessKey(ByteArray(32) { 1 }))
    }
}
