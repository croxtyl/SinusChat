package pl.sinusoide.sinuschat.link

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.Socket

class LinkRejected(val code: Int) : IOException("link rejected: $code")

class LinkHandshakeException(message: String) : IOException(message)

sealed class LinkMode(val code: Int) {
    class Pair(val token: ByteArray) : LinkMode(LinkHandshake.MODE_PAIR)
    class Resume(val phoneKey: ByteArray) : LinkMode(LinkHandshake.MODE_RESUME)
}

sealed class LinkAuthorization {
    class Accept(val psk: ByteArray) : LinkAuthorization()
    class Reject(val code: Int) : LinkAuthorization()
}

object LinkHandshake {
    const val MODE_PAIR = 1
    const val MODE_RESUME = 2

    const val OK = 0
    const val REJECT_UNKNOWN_DEVICE = 1
    const val REJECT_NOT_PAIRING = 2
    const val REJECT_VERSION = 3

    private const val MAX_KEY_BYTES = 256
    private const val CONFIRM_BYTES = 32
    private val MAGIC = "SCL1".toByteArray()
    private val LABEL = "SinusChatLink/1".toByteArray()
    private val INFO = "sinuschat-link-v1 keys".toByteArray()
    private val PHONE = "phone".toByteArray()
    private val PC = "pc".toByteArray()

    private class Keys(val c2s: ByteArray, val s2c: ByteArray, val confirm: ByteArray)

    private fun derive(th: ByteArray, ikm: ByteArray): Keys {
        val okm = LinkCrypto.hkdf(th, ikm, INFO, 96)
        return Keys(okm.copyOfRange(0, 32), okm.copyOfRange(32, 64), okm.copyOfRange(64, 96))
    }

    private fun transcript(mode: Int, sPc: ByteArray, ePc: ByteArray, sPh: ByteArray, ePh: ByteArray): ByteArray =
        LinkCrypto.sha256(LinkCrypto.lengthPrefixed(LABEL, byteArrayOf(mode.toByte()), sPc, ePc, sPh, ePh))

    private fun writeBlob(out: DataOutputStream, data: ByteArray) {
        out.writeShort(data.size)
        out.write(data)
    }

    private fun readBlob(input: DataInputStream): ByteArray {
        val n = input.readUnsignedShort()
        if (n == 0 || n > MAX_KEY_BYTES) throw LinkHandshakeException("bad key length")
        return ByteArray(n).also { input.readFully(it) }
    }

    private fun streams(socket: Socket) =
        DataInputStream(BufferedInputStream(socket.getInputStream())) to
            DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

    fun connect(socket: Socket, identity: LinkIdentity, mode: LinkMode): LinkChannel {
        socket.soTimeout = LinkProtocol.HANDSHAKE_TIMEOUT_MS
        val (input, output) = streams(socket)
        val eph = LinkCrypto.generateKeyPair()
        val ePc = eph.public.encoded

        output.write(MAGIC)
        output.writeByte(mode.code)
        writeBlob(output, identity.publicBytes)
        writeBlob(output, ePc)
        output.flush()

        val status = input.readUnsignedByte()
        if (status != OK) throw LinkRejected(status)
        val sPh = readBlob(input)
        val ePh = readBlob(input)
        val confirmPh = ByteArray(CONFIRM_BYTES).also { input.readFully(it) }

        if (mode is LinkMode.Resume && !LinkCrypto.equal(sPh, mode.phoneKey))
            throw LinkHandshakeException("unexpected phone key")
        val sPhKey = LinkCrypto.decodePublic(sPh)
        val ePhKey = LinkCrypto.decodePublic(ePh)

        val th = transcript(mode.code, identity.publicBytes, ePc, sPh, ePh)
        val psk = when (mode) {
            is LinkMode.Pair -> mode.token
            is LinkMode.Resume -> ByteArray(0)
        }
        val ikm = LinkCrypto.ecdh(eph.private, ePhKey) +
            LinkCrypto.ecdh(identity.privateKey, ePhKey) +
            LinkCrypto.ecdh(eph.private, sPhKey) +
            psk
        val keys = derive(th, ikm)
        if (!LinkCrypto.equal(confirmPh, LinkCrypto.hmac(keys.confirm, PHONE, th)))
            throw LinkHandshakeException("phone confirmation failed")

        output.write(LinkCrypto.hmac(keys.confirm, PC, th))
        output.flush()
        socket.soTimeout = LinkProtocol.READ_TIMEOUT_MS
        return LinkChannel(input, output, keys.c2s, keys.s2c, sPh, mode.code)
    }

    fun accept(
        socket: Socket,
        identity: LinkIdentity,
        authorize: (mode: Int, pcKey: ByteArray) -> LinkAuthorization
    ): LinkChannel {
        socket.soTimeout = LinkProtocol.HANDSHAKE_TIMEOUT_MS
        val (input, output) = streams(socket)

        val magic = ByteArray(MAGIC.size).also { input.readFully(it) }
        if (!magic.contentEquals(MAGIC)) {
            output.writeByte(REJECT_VERSION)
            output.flush()
            throw LinkRejected(REJECT_VERSION)
        }
        val mode = input.readUnsignedByte()
        val sPc = readBlob(input)
        val ePc = readBlob(input)

        val auth = if (mode == MODE_PAIR || mode == MODE_RESUME) authorize(mode, sPc)
        else LinkAuthorization.Reject(REJECT_VERSION)
        if (auth is LinkAuthorization.Reject) {
            output.writeByte(auth.code)
            output.flush()
            throw LinkRejected(auth.code)
        }
        val psk = (auth as LinkAuthorization.Accept).psk

        val sPcKey = LinkCrypto.decodePublic(sPc)
        val ePcKey = LinkCrypto.decodePublic(ePc)
        val eph = LinkCrypto.generateKeyPair()
        val ePh = eph.public.encoded

        val th = transcript(mode, sPc, ePc, identity.publicBytes, ePh)
        val ikm = LinkCrypto.ecdh(eph.private, ePcKey) +
            LinkCrypto.ecdh(eph.private, sPcKey) +
            LinkCrypto.ecdh(identity.privateKey, ePcKey) +
            psk
        val keys = derive(th, ikm)

        output.writeByte(OK)
        writeBlob(output, identity.publicBytes)
        writeBlob(output, ePh)
        output.write(LinkCrypto.hmac(keys.confirm, PHONE, th))
        output.flush()

        val confirmPc = ByteArray(CONFIRM_BYTES).also { input.readFully(it) }
        if (!LinkCrypto.equal(confirmPc, LinkCrypto.hmac(keys.confirm, PC, th)))
            throw LinkHandshakeException("pc confirmation failed")

        socket.soTimeout = LinkProtocol.READ_TIMEOUT_MS
        return LinkChannel(input, output, keys.s2c, keys.c2s, sPc, mode)
    }
}
