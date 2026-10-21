package pl.sinusoide.sinuschat.util

import android.content.Context
import android.util.Log
import pl.sinusoide.sinuschat.setup.SetupActivity
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom

object DeviceTransfer {

    private const val TAG = "DeviceTransfer"
    private const val PREFIX = "SINUSCHAT-MOVE:1:"
    private const val MAGIC = 0x53434D56
    private const val VERSION = 1
    private const val CLIENT_LABEL = "SinusChat move client"
    private const val STATUS_OK = 0
    private const val STATUS_OTHER_ACCOUNT = 2
    private const val CONNECT_MS = 4000
    private const val HELLO_MS = 10_000
    private const val READ_MS = 60_000
    private const val ACK_MS = 5 * 60_000
    private const val BUF = 64 * 1024

    class OtherAccountException : IOException("Inne konto")

    class NoConnectionException : IOException("Brak połączenia")

    class Account(val email: String, val displayName: String, val authProof: String?)

    class Received(val restore: BackupRestore.Result, val account: Account)

    class Code(val hosts: List<String>, val port: Int, val key: RecoveryKey) {
        fun encode(): String = PREFIX + hosts.joinToString("+") + "/" + port + "/" + key.toStorage()
    }

    fun parse(text: String): Code? {
        val t = text.trim()
        if (!t.startsWith(PREFIX, ignoreCase = true)) return null
        val parts = t.substring(PREFIX.length).split('/')
        if (parts.size != 3) return null
        val hosts = parts[0].split('+').filter { h -> h.split('.').let { it.size == 4 && it.all { p -> p.toIntOrNull() in 0..255 } } }
        val port = parts[1].toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val key = RecoveryKey.fromStorage(parts[2]) ?: return null
        return if (hosts.isEmpty()) null else Code(hosts, port, key)
    }

    fun localAddresses(): List<String> = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .filterNot { n -> listOf("rmnet", "ccmni", "v4-", "tun", "dummy", "ppp").any { n.name.startsWith(it) } }
            .sortedBy { if (it.name.startsWith("wlan")) 0 else 1 }
            .flatMap { n -> n.inetAddresses.toList().filterIsInstance<Inet4Address>()
                .filter { it.isSiteLocalAddress }.map { it.hostAddress ?: "" } }
            .filter { it.isNotEmpty() }
            .distinct()
    } catch (e: Throwable) {
        Log.w(TAG, "adresy lokalne", e)
        emptyList()
    }

    sealed class Result {
        data class Ok(val stats: BackupManager.Stats) : Result()
        object OtherAccount : Result()
        object Unconfirmed : Result()
        object Cancelled : Result()
        data class Failed(val message: String) : Result()
    }

    class Sender(ctx: Context) {
        private val app = ctx.applicationContext
        private val key = RecoveryKey.generate()
        private var server: ServerSocket? = null
        @Volatile private var cancelled = false

        fun open(): Code? {
            val hosts = localAddresses()
            if (hosts.isEmpty()) return null
            val s = ServerSocket(0)
            server = s
            return Code(hosts, s.localPort, key)
        }

        fun serve(onConnected: () -> Unit, onProgress: (Int, Int) -> Unit): Result {
            val s = server ?: return Result.Failed("gniazdo zamknięte")
            val myHash = SetupActivity.getUserPhoneHash(app)
            while (!cancelled) {
                val sock = try { s.accept() } catch (e: IOException) {
                    return if (cancelled) Result.Cancelled else Result.Failed(e.message ?: "accept")
                }
                val out: DataOutputStream
                val inp: DataInputStream
                try {
                    sock.soTimeout = HELLO_MS
                    out = DataOutputStream(BufferedOutputStream(sock.getOutputStream(), BUF))
                    inp = DataInputStream(sock.getInputStream())
                    val challenge = ByteArray(16).also { SecureRandom().nextBytes(it) }
                    out.writeInt(MAGIC)
                    out.writeByte(VERSION)
                    out.write(challenge)
                    out.flush()

                    if (inp.readInt() != MAGIC) { sock.close(); continue }
                    val hashLen = inp.readUnsignedShort()
                    if (hashLen > 256) { sock.close(); continue }
                    val hash = ByteArray(hashLen).also { inp.readFully(it) }
                    val proof = ByteArray(32).also { inp.readFully(it) }
                    if (!MessageDigest.isEqual(proof, key.proof(CLIENT_LABEL, challenge + hash))) {
                        sock.close(); continue
                    }
                    if (myHash.isNotEmpty() && hash.isNotEmpty() && String(hash) != myHash) {
                        out.writeByte(STATUS_OTHER_ACCOUNT); out.flush(); sock.close()
                        return Result.OtherAccount
                    }
                    out.writeByte(STATUS_OK)
                    out.flush()
                } catch (e: IOException) {
                    Log.w(TAG, "przedstawienie: ${e.message}")
                    try { sock.close() } catch (_: Throwable) {}
                    continue
                }
                try {
                    sock.soTimeout = READ_MS
                    onConnected()

                    val stats = BackupManager.create(app, KeepOpen(out), key, withMedia = true, onProgress = onProgress)
                    writeSealed(out, key, accountPackage().toString().toByteArray())
                    sock.shutdownOutput()
                    sock.soTimeout = ACK_MS
                    val ack = try { inp.read() } catch (_: IOException) { -1 }
                    sock.close()
                    return if (ack == 0) Result.Ok(stats) else Result.Unconfirmed
                } catch (e: Throwable) {
                    Log.e(TAG, "wysyłka", e)
                    try { sock.close() } catch (_: Throwable) {}
                    return if (cancelled) Result.Cancelled else Result.Failed(e.message ?: e.javaClass.simpleName)
                }
            }
            return Result.Cancelled
        }

        private fun accountPackage(): org.json.JSONObject {
            val o = org.json.JSONObject()
                .put("email", SetupActivity.getUserEmail(app))
                .put("displayName", SetupActivity.getUserDisplayName(app))
                .put("backup", BackupPrefs.exportForTransfer(app))
                .put("profileKey", pl.sinusoide.sinuschat.crypto.ProfileCrypto.myKeyB64(app))
                .put("lock", pl.sinusoide.sinuschat.LockManager.exportForTransfer(app))
            try {
                pl.sinusoide.sinuschat.network.ApiClient.post("/api/auth/transfer-proof", org.json.JSONObject(), app)
                    ?.let { r ->
                        val proof = r.optString("authProof")
                        if (proof.isNotEmpty()) o.put("authProof", proof).put("authProofExpiresAt", r.optLong("expiresAt"))
                    }
            } catch (e: Throwable) {
                Log.w(TAG, "dowód przeniesienia", e)
            }
            return o
        }

        fun cancel() {
            cancelled = true
            try { server?.close() } catch (_: Throwable) {}
        }
    }

    fun receive(
        ctx: Context,
        code: Code,
        accountHash: String,
        onConnected: () -> Unit,
        onProgress: (Int, Int) -> Unit
    ): Received {
        val sock = connect(code) ?: throw NoConnectionException()
        sock.use {
            sock.soTimeout = READ_MS
            val inp = DataInputStream(BufferedInputStream(sock.getInputStream(), BUF))
            val out = DataOutputStream(sock.getOutputStream())
            if (inp.readInt() != MAGIC) throw IOException("To nie jest SinusChat")
            inp.readUnsignedByte()
            val challenge = ByteArray(16).also { inp.readFully(it) }
            val hash = accountHash.toByteArray()
            out.writeInt(MAGIC)
            out.writeShort(hash.size)
            out.write(hash)
            out.write(code.key.proof(CLIENT_LABEL, challenge + hash))
            out.flush()
            when (inp.readUnsignedByte()) {
                STATUS_OK -> Unit
                STATUS_OTHER_ACCOUNT -> throw OtherAccountException()
                else -> throw IOException("Stary telefon odmówił")
            }
            onConnected()
            val received = try {
                val restored = BackupManager.open(KeepOpenIn(inp), code.key).use { archive ->
                    BackupRestore.restore(ctx, archive, accountHash, onProgress)
                }
                val pkg = org.json.JSONObject(String(readSealed(inp, code.key)))
                Received(restored, applyPackage(ctx, pkg))
            } catch (e: Throwable) {
                try { out.write(1); out.flush() } catch (_: Throwable) {}
                throw e
            }
            out.write(0)
            out.flush()
            return received
        }
    }

    private fun applyPackage(ctx: Context, o: org.json.JSONObject): Account {
        val app = ctx.applicationContext
        o.optJSONObject("backup")?.let { BackupPrefs.importFromTransfer(app, it) }
        o.optString("profileKey").takeIf { it.isNotEmpty() }?.let { pl.sinusoide.sinuschat.crypto.ProfileCrypto.setMyKey(app, it) }
        o.optJSONObject("lock")?.let { pl.sinusoide.sinuschat.LockManager.importFromTransfer(app, it) }
        val proof = o.optString("authProof").takeIf { it.isNotEmpty() }
        if (proof != null)
            pl.sinusoide.sinuschat.network.ApiClient.saveAuthProof(app, proof, o.optLong("authProofExpiresAt"))
        return Account(o.optString("email"), o.optString("displayName"), proof)
    }

    private fun writeSealed(out: OutputStream, key: RecoveryKey, bytes: ByteArray) {
        val header = BackupCrypto.Header.create(0, key)
        out.write(header.bytes())
        BackupCrypto.encryptingStream(KeepOpen(out), key, header).use { it.write(bytes) }
    }

    private fun readSealed(inp: InputStream, key: RecoveryKey): ByteArray {
        val header = BackupCrypto.Header.read(inp)
        return BackupCrypto.decryptingStream(KeepOpenIn(inp), key, header).use { it.readBytes() }
    }

    private fun connect(code: Code): Socket? {
        for (host in code.hosts) {
            val s = Socket()
            try {
                s.connect(InetSocketAddress(host, code.port), CONNECT_MS)
                return s
            } catch (e: IOException) {
                Log.w(TAG, "$host:${code.port} — ${e.message}")
                try { s.close() } catch (_: Throwable) {}
            }
        }
        return null
    }

    private class KeepOpen(out: OutputStream) : FilterOutputStream(out) {
        override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
        override fun close() = out.flush()
    }

    private class KeepOpenIn(inp: InputStream) : FilterInputStream(inp) {
        override fun close() = Unit
    }
}
