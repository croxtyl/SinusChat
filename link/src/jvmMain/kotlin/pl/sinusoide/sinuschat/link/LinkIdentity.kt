package pl.sinusoide.sinuschat.link

import java.security.PrivateKey
import java.security.PublicKey

class LinkIdentity private constructor(
    val privateKey: PrivateKey,
    val publicKey: PublicKey,
    val privateBytes: ByteArray,
    val publicBytes: ByteArray
) {
    val id: String get() = LinkCrypto.fingerprint(publicBytes)

    companion object {
        fun generate(): LinkIdentity {
            val kp = LinkCrypto.generateKeyPair()
            return LinkIdentity(kp.private, kp.public, kp.private.encoded, kp.public.encoded)
        }

        fun restore(privateBytes: ByteArray, publicBytes: ByteArray): LinkIdentity =
            LinkIdentity(
                LinkCrypto.decodePrivate(privateBytes),
                LinkCrypto.decodePublic(publicBytes),
                privateBytes.copyOf(),
                publicBytes.copyOf()
            )
    }
}
