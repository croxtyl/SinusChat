package pl.sinusoide.sinuschat.link

import java.util.Base64

class PairingCode(val pcKey: ByteArray, val token: ByteArray) {

    fun encode(): String =
        LinkProtocol.PAIRING_PREFIX + b64.encodeToString(pcKey) + ":" + b64.encodeToString(token)

    companion object {
        private const val TOKEN_BYTES = 16
        private val b64 = Base64.getUrlEncoder().withoutPadding()
        private val b64d = Base64.getUrlDecoder()

        fun create(pcKey: ByteArray): PairingCode = PairingCode(pcKey, LinkCrypto.randomBytes(TOKEN_BYTES))

        fun isPairingCode(text: String): Boolean = text.trim().startsWith(LinkProtocol.PAIRING_PREFIX)

        fun decode(text: String): PairingCode? = try {
            val body = text.trim().removePrefix(LinkProtocol.PAIRING_PREFIX)
            val parts = body.split(':')
            if (!isPairingCode(text) || parts.size != 2) null
            else {
                val key = b64d.decode(parts[0])
                val token = b64d.decode(parts[1])
                LinkCrypto.decodePublic(key)
                if (token.size != TOKEN_BYTES) null else PairingCode(key, token)
            }
        } catch (_: Exception) {
            null
        }
    }
}
