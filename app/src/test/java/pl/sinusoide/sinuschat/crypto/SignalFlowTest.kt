package pl.sinusoide.sinuschat.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.NoSessionException
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.groups.GroupCipher
import org.signal.libsignal.protocol.groups.GroupSessionBuilder
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.kem.KEMPublicKey
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SenderKeyDistributionMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.state.impl.InMemorySignalProtocolStore
import java.util.Base64
import java.util.UUID

class SignalFlowTest {

    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)
    private fun unb64(s: String) = Base64.getDecoder().decode(s)

    private fun identity(): IdentityKeyPair {
        val kp = ECKeyPair.generate()
        return IdentityKeyPair(IdentityKey(ECPublicKey(kp.publicKey.serialize())), kp.privateKey)
    }

    private class Party(val name: String) {
        val id = IdentityKeyPairHolder.make()
        val store = InMemorySignalProtocolStore(id, 1 + (Math.random() * 16000).toInt())
        val addr = SignalProtocolAddress(name, 1)
    }

    private object IdentityKeyPairHolder {
        fun make(): IdentityKeyPair {
            val kp = ECKeyPair.generate()
            return IdentityKeyPair(IdentityKey(kp.publicKey), kp.privateKey)
        }
    }

    private fun bundleOf(bob: Party, withOneTime: Boolean): PreKeyBundle {
        val now = System.currentTimeMillis()
        val signed = ECKeyPair.generate()
        val sig = bob.id.privateKey.calculateSignature(signed.publicKey.serialize())
        bob.store.storeSignedPreKey(7, SignedPreKeyRecord(7, now, signed, sig))
        val kem = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        val ksig = bob.id.privateKey.calculateSignature(kem.publicKey.serialize())
        bob.store.storeKyberPreKey(7, KyberPreKeyRecord(7, now, kem, ksig))
        val pre = ECKeyPair.generate()
        bob.store.storePreKey(42, PreKeyRecord(42, pre))

        val identityB64 = b64(bob.id.publicKey.serialize())
        return PreKeyBundle(
            bob.store.localRegistrationId, 1,
            if (withOneTime) 42 else PreKeyBundle.NULL_PRE_KEY_ID,
            if (withOneTime) ECPublicKey(unb64(b64(pre.publicKey.serialize()))) else null,
            7, ECPublicKey(unb64(b64(signed.publicKey.serialize()))), unb64(b64(sig)),
            IdentityKey(unb64(identityB64)),
            7, KEMPublicKey(unb64(b64(kem.publicKey.serialize()))), unb64(b64(ksig))
        )
    }

    @Test
    fun oneToOneSessionWithKyber() {
        val alice = Party("alice"); val bob = Party("bob")
        SessionBuilder(alice.store, bob.addr, alice.addr).process(bundleOf(bob, withOneTime = true))

        val m1 = SessionCipher(alice.store, alice.addr, bob.addr).encrypt("cześć".toByteArray())
        assertEquals(CiphertextMessage.PREKEY_TYPE, m1.type)
        val p1 = SessionCipher(bob.store, bob.addr, alice.addr).decrypt(PreKeySignalMessage(m1.serialize()))
        assertEquals("cześć", String(p1))
        assertTrue(!bob.store.containsPreKey(42))

        val r1 = SessionCipher(bob.store, bob.addr, alice.addr).encrypt("hej".toByteArray())
        assertEquals(CiphertextMessage.WHISPER_TYPE, r1.type)
        val q1 = SessionCipher(alice.store, alice.addr, bob.addr).decrypt(SignalMessage(r1.serialize()))
        assertEquals("hej", String(q1))
    }

    @Test
    fun sessionWithoutOneTimeKey() {
        val alice = Party("alice"); val bob = Party("bob")
        SessionBuilder(alice.store, bob.addr, alice.addr).process(bundleOf(bob, withOneTime = false))
        val m = SessionCipher(alice.store, alice.addr, bob.addr).encrypt("x".toByteArray())
        val p = SessionCipher(bob.store, bob.addr, alice.addr).decrypt(PreKeySignalMessage(m.serialize()))
        assertEquals("x", String(p))
    }

    @Test
    fun outOfOrderMessages() {
        val alice = Party("alice"); val bob = Party("bob")
        SessionBuilder(alice.store, bob.addr, alice.addr).process(bundleOf(bob, withOneTime = true))
        val c = SessionCipher(alice.store, alice.addr, bob.addr)
        val a = c.encrypt("1".toByteArray()); val b = c.encrypt("2".toByteArray())
        val bc = SessionCipher(bob.store, bob.addr, alice.addr)
        assertEquals("2", String(bc.decrypt(PreKeySignalMessage(b.serialize()))))
        assertEquals("1", String(bc.decrypt(PreKeySignalMessage(a.serialize()))))
    }

    @Test
    fun forgedSignedPreKeyIsRejected() {
        val alice = Party("alice"); val bob = Party("bob"); val mallory = Party("mallory")
        val good = bundleOf(bob, withOneTime = false)
        val fake = ECKeyPair.generate()
        val forged = PreKeyBundle(
            good.registrationId, 1, PreKeyBundle.NULL_PRE_KEY_ID, null,
            7, fake.publicKey, mallory.id.privateKey.calculateSignature(fake.publicKey.serialize()),
            good.identityKey, good.kyberPreKeyId, good.kyberPreKey, good.kyberPreKeySignature
        )
        try {
            SessionBuilder(alice.store, bob.addr, alice.addr).process(forged)
            fail("Podrobiony klucz przeszedł")
        } catch (_: Exception) { }
    }

    @Test
    fun groupSenderKeys() {
        val alice = Party("alice"); val bob = Party("bob"); val carol = Party("carol")
        val dist = UUID.randomUUID()
        val skdm = GroupSessionBuilder(alice.store).create(alice.addr, dist).serialize()
        GroupSessionBuilder(bob.store).process(alice.addr, SenderKeyDistributionMessage(skdm))

        val m1 = GroupCipher(alice.store, alice.addr).encrypt(dist, "grupa".toByteArray()).serialize()
        assertEquals("grupa", String(GroupCipher(bob.store, alice.addr).decrypt(m1)))

        try {
            GroupCipher(carol.store, alice.addr).decrypt(m1)
            fail("Carol odczytała bez klucza")
        } catch (e: NoSessionException) { }

        val late = GroupSessionBuilder(alice.store).create(alice.addr, dist).serialize()
        GroupSessionBuilder(carol.store).process(alice.addr, SenderKeyDistributionMessage(late))
        val m2 = GroupCipher(alice.store, alice.addr).encrypt(dist, "dalej".toByteArray()).serialize()
        assertEquals("dalej", String(GroupCipher(carol.store, alice.addr).decrypt(m2)))
        assertEquals("dalej", String(GroupCipher(bob.store, alice.addr).decrypt(m2)))

        GroupSessionBuilder(bob.store).process(alice.addr, SenderKeyDistributionMessage(late))
        val m3 = GroupCipher(alice.store, alice.addr).encrypt(dist, "trzy".toByteArray()).serialize()
        assertEquals("trzy", String(GroupCipher(bob.store, alice.addr).decrypt(m3)))
    }

    @Test
    fun groupReplySkippedByOldClient() {
        val alice = Party("alice"); val bob = Party("bob"); val old = Party("old")
        val dist = UUID.randomUUID()
        val skdm = GroupSessionBuilder(alice.store).create(alice.addr, dist).serialize()
        GroupSessionBuilder(bob.store).process(alice.addr, SenderKeyDistributionMessage(skdm))
        GroupSessionBuilder(old.store).process(alice.addr, SenderKeyDistributionMessage(skdm))
        val cipher = GroupCipher(alice.store, alice.addr)

        val text = cipher.encrypt(dist, "odpowiedź".toByteArray()).serialize()
        val reply = cipher.encrypt(dist, "{\"id\":\"x\",\"text\":\"Zdjęcie\"}".toByteArray()).serialize()
        val next = cipher.encrypt(dist, "dalej".toByteArray()).serialize()

        assertEquals("odpowiedź", String(GroupCipher(bob.store, alice.addr).decrypt(text)))
        assertEquals("{\"id\":\"x\",\"text\":\"Zdjęcie\"}", String(GroupCipher(bob.store, alice.addr).decrypt(reply)))
        assertEquals("dalej", String(GroupCipher(bob.store, alice.addr).decrypt(next)))

        assertEquals("odpowiedź", String(GroupCipher(old.store, alice.addr).decrypt(text)))
        assertEquals("dalej", String(GroupCipher(old.store, alice.addr).decrypt(next)))
    }

    @Test
    fun groupMessageFromOtherMemberCannotBeForged() {
        val alice = Party("alice"); val bob = Party("bob"); val mallory = Party("mallory")
        val dist = UUID.randomUUID()
        val skdm = GroupSessionBuilder(alice.store).create(alice.addr, dist).serialize()
        GroupSessionBuilder(bob.store).process(alice.addr, SenderKeyDistributionMessage(skdm))
        GroupSessionBuilder(mallory.store).process(alice.addr, SenderKeyDistributionMessage(skdm))
        try {
            GroupCipher(mallory.store, alice.addr).encrypt(dist, "podróbka".toByteArray())
            fail("Mallory zaszyfrowała jako Alice")
        } catch (_: Exception) { }
    }

    @Test
    fun rotatedDistributionLocksOutRemovedMember() {
        val alice = Party("alice"); val bob = Party("bob"); val eve = Party("eve")
        val d1 = UUID.randomUUID()
        val s1 = GroupSessionBuilder(alice.store).create(alice.addr, d1).serialize()
        GroupSessionBuilder(bob.store).process(alice.addr, SenderKeyDistributionMessage(s1))
        GroupSessionBuilder(eve.store).process(alice.addr, SenderKeyDistributionMessage(s1))
        val d2 = UUID.randomUUID()
        val s2 = GroupSessionBuilder(alice.store).create(alice.addr, d2).serialize()
        GroupSessionBuilder(bob.store).process(alice.addr, SenderKeyDistributionMessage(s2))
        val m = GroupCipher(alice.store, alice.addr).encrypt(d2, "bez Eve".toByteArray()).serialize()
        assertEquals("bez Eve", String(GroupCipher(bob.store, alice.addr).decrypt(m)))
        try {
            GroupCipher(eve.store, alice.addr).decrypt(m)
            fail("Eve odczytała po odejściu")
        } catch (_: Exception) { }
    }

    @Test
    fun padding() {
        fun pad(b: ByteArray): ByteArray {
            val len = ((b.size + 1 + 63) / 64) * 64
            return b.copyOf(len).also { it[b.size] = 0x80.toByte() }
        }
        fun unpad(b: ByteArray): ByteArray {
            var i = b.size - 1
            while (i >= 0 && b[i] == 0.toByte()) i--
            return if (i >= 0 && b[i] == 0x80.toByte()) b.copyOf(i) else b
        }
        for (s in listOf("", "a", "x".repeat(63), "y".repeat(64), "ząb\u0000")) {
            val p = pad(s.toByteArray())
            assertEquals(0, p.size % 64)
            assertArrayEquals(s.toByteArray(), unpad(p))
        }
    }
}
