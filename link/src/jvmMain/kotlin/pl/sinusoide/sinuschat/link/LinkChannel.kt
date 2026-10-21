package pl.sinusoide.sinuschat.link

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

class LinkChannel internal constructor(
    private val input: DataInputStream,
    private val output: DataOutputStream,
    sendKey: ByteArray,
    receiveKey: ByteArray,
    val peerKey: ByteArray,
    val mode: Int
) {
    private val sendCipher = LinkCrypto.FrameCipher(sendKey)
    private val receiveCipher = LinkCrypto.FrameCipher(receiveKey)
    private val sendLock = Any()

    val peerId: String get() = LinkCrypto.fingerprint(peerKey)

    fun write(frame: LinkFrame) {
        val plain = LinkFrame.encode(frame).toByteArray(Charsets.UTF_8)
        if (plain.size > LinkProtocol.MAX_FRAME_BYTES) throw IOException("frame too large")
        synchronized(sendLock) {
            val sealed = sendCipher.seal(plain)
            output.writeInt(sealed.size)
            output.write(sealed)
            output.flush()
        }
    }

    fun read(): LinkFrame? {
        val n = input.readInt()
        if (n < 16 || n > LinkProtocol.MAX_FRAME_BYTES + 16) throw IOException("bad frame length")
        val sealed = ByteArray(n).also { input.readFully(it) }
        val plain = try {
            receiveCipher.open(sealed)
        } catch (e: Exception) {
            throw IOException("frame authentication failed", e)
        }
        return LinkFrame.decode(String(plain, Charsets.UTF_8))
    }
}
