package pl.sinusoide.sinuschat.link

import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

class LinkConnection(
    private val socket: Socket,
    val channel: LinkChannel,
    private val listener: Listener
) {
    interface Listener {
        fun onFrame(connection: LinkConnection, frame: LinkFrame)
        fun onClosed(connection: LinkConnection, error: Throwable?)
    }

    private val closed = AtomicBoolean(false)
    private val closing = AtomicBoolean(false)
    private val pingId = AtomicLong()
    @Volatile private var pinger: Thread? = null

    val peerKey: ByteArray get() = channel.peerKey
    val peerId: String get() = channel.peerId
    val isOpen: Boolean get() = !closed.get()
    val remoteAddress: String get() = socket.inetAddress?.hostAddress ?: ""

    fun start() {
        thread(isDaemon = true, name = "link-read") { readLoop() }
        pinger = thread(isDaemon = true, name = "link-ping") { pingLoop() }
    }

    private fun readLoop() {
        var error: Throwable? = null
        try {
            while (!closed.get()) {
                when (val frame = channel.read() ?: continue) {
                    is LinkFrame.Ping -> send(LinkFrame.Pong(frame.id))
                    is LinkFrame.Pong -> Unit
                    else -> listener.onFrame(this, frame)
                }
            }
        } catch (e: Throwable) {
            error = e
        }
        finish(error)
    }

    private fun pingLoop() {
        try {
            while (!closed.get()) {
                Thread.sleep(LinkProtocol.PING_INTERVAL_MS)
                if (!send(LinkFrame.Ping(pingId.incrementAndGet()))) break
            }
        } catch (_: InterruptedException) {
        }
    }

    fun send(frame: LinkFrame): Boolean {
        if (closed.get()) return false
        return try {
            channel.write(frame)
            true
        } catch (e: Exception) {
            finish(e)
            false
        }
    }

    fun close(reason: String? = null) {
        if (closed.get()) return
        closing.set(true)
        if (reason != null) runCatching { channel.write(LinkFrame.Bye(reason)) }
        finish(null)
    }

    private fun finish(error: Throwable?) {
        if (!closed.compareAndSet(false, true)) return
        runCatching { socket.close() }
        pinger?.interrupt()
        listener.onClosed(this, if (closing.get()) null else error)
    }
}
