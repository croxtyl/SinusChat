package pl.sinusoide.sinuschat.call

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import pl.sinusoide.sinuschat.crypto.GroupMeta
import pl.sinusoide.sinuschat.setup.SetupActivity
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object WalkieTalkie {

    private const val TAG = "Walkie"
    const val SERVICE_TYPE = "_sinuswt._udp."
    private const val SAMPLE_RATE = 16_000
    private const val FRAME_BYTES = 640
    private const val T_HELLO: Byte = 1
    private const val T_AUDIO: Byte = 2
    private const val T_BYE: Byte = 3
    private const val PEER_TIMEOUT_MS = 8_000L

    class Keys(val audio: ByteArray, val tag: String)

    @Volatile var session: Session? = null
        private set

    private fun hmac(key: ByteArray, label: String): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(label.toByteArray(Charsets.UTF_8))

    fun keys(context: Context, groupId: String): Keys? {
        val pk = GroupMeta.profileKey(context, groupId) ?: return null
        val tag = hmac(pk, "sinuschat-walkie-tag-v1").joinToString("") { "%02x".format(it) }.take(12)
        return Keys(hmac(pk, "sinuschat-walkie-audio-v1"), tag)
    }

    fun start(context: Context, groupId: String, title: String): Session? {
        session?.let { if (it.groupId == groupId && !it.stopped) return it; it.stop() }
        val k = keys(context, groupId) ?: return null
        val s = Session(context.applicationContext, groupId, title, k)
        session = s
        if (!s.begin()) { session = null; return null }
        return s
    }

    private fun multicastLock(context: Context): WifiManager.MulticastLock? = try {
        (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.createMulticastLock("sinuschat-walkie")?.apply { setReferenceCounted(false); acquire() }
    } catch (_: Exception) { null }

    class Peer(val id: String) {
        @Volatile var hash: String = ""
        @Volatile var seen = 0L
        @Volatile var talkingAt = 0L
        var player: AudioTrack? = null
    }

    class Session(val app: Context, val groupId: String, val title: String, private val keys: Keys) {
        val me: String = SetupActivity.getUserPhoneHash(app)
        private val myId = me.take(16)
        private val main = Handler(Looper.getMainLooper())
        private var socket: DatagramSocket? = null
        private val endpoints = ConcurrentHashMap<String, InetSocketAddress>()
        val peers = ConcurrentHashMap<String, Peer>()
        private val talking = AtomicBoolean(false)
        private val random = SecureRandom()
        private var seq = 0
        private var nsd: NsdManager? = null
        private var myServiceName: String? = null
        private var lock: WifiManager.MulticastLock? = null
        private val listeners = CopyOnWriteArraySet<() -> Unit>()
        private val cipher = ThreadLocal.withInitial { Cipher.getInstance("AES/GCM/NoPadding") }
        private val resolveQueue = java.util.ArrayDeque<NsdServiceInfo>()
        private var resolving = false
        @Volatile var stopped = false
            private set
        val isTalking get() = talking.get()
        val serviceName get() = myServiceName
        @Volatile var speakerOn = true
            private set
        private var savedMode: Int? = null

        fun setSpeaker(on: Boolean) {
            if (speakerOn == on || stopped) return
            speakerOn = on
            applyRoute()
            peers.values.forEach { p -> p.player?.let { try { it.release() } catch (_: Exception) {} }; p.player = null }
            changed()
        }

        private fun applyRoute() {
            val am = app.getSystemService(android.media.AudioManager::class.java) ?: return
            try {
                if (speakerOn) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) am.clearCommunicationDevice()
                    else {
                        @Suppress("DEPRECATION")
                        am.isSpeakerphoneOn = false
                    }
                    savedMode?.let { am.mode = it }
                    savedMode = null
                } else {
                    if (savedMode == null) savedMode = am.mode
                    am.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        am.availableCommunicationDevices
                            .firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                            ?.let { am.setCommunicationDevice(it) }
                    } else {
                        @Suppress("DEPRECATION")
                        am.isSpeakerphoneOn = false
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "route: ${e.message}")
            }
        }

        fun addListener(l: () -> Unit) { listeners.add(l) }
        fun removeListener(l: () -> Unit) { listeners.remove(l) }
        private fun changed() { main.post { listeners.forEach { it() } } }

        private val registration = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) { myServiceName = info.serviceName }
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) { Log.w(TAG, "register $code") }
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) {}
        }

        private val discovery = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onStartDiscoveryFailed(type: String, code: Int) { Log.w(TAG, "discovery $code") }
            override fun onStopDiscoveryFailed(type: String, code: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                val name = info.serviceName ?: return
                if (!name.startsWith("sw-${keys.tag}-") || name == myServiceName) return
                main.post { resolveQueue.add(info); nextResolve() }
            }
            override fun onServiceLost(info: NsdServiceInfo) {}
        }

        @Suppress("DEPRECATION")
        private fun nextResolve() {
            if (resolving || stopped) return
            val info = resolveQueue.poll() ?: return
            resolving = true
            try {
                nsd?.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(i: NsdServiceInfo, code: Int) { main.post { resolving = false; nextResolve() } }
                    override fun onServiceResolved(i: NsdServiceInfo) {
                        val host = i.host
                        if (host != null && i.port > 0) {
                            endpoints["${host.hostAddress}:${i.port}"] = InetSocketAddress(host, i.port)
                            sendHello()
                        }
                        main.post { resolving = false; nextResolve() }
                    }
                })
            } catch (e: Exception) {
                resolving = false
            }
        }

        fun begin(): Boolean {
            return try {
                val s = DatagramSocket(null).apply { reuseAddress = true; bind(InetSocketAddress(0)) }
                socket = s
                lock = multicastLock(app)
                nsd = app.getSystemService(NsdManager::class.java)
                val info = NsdServiceInfo().apply {
                    serviceName = "sw-${keys.tag}-" + Integer.toHexString(random.nextInt(0xFFFF))
                    serviceType = SERVICE_TYPE
                    port = s.localPort
                }
                nsd?.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration)
                nsd?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery)
                Thread({ receiveLoop(s) }, "walkie-rx").start()
                OngoingCallService.start(app, title, false)
                OngoingCallService.onHangUp = { main.post { stop() } }
                main.post(helloTick)
                true
            } catch (e: Exception) {
                Log.w(TAG, "begin: ${e.message}")
                stop()
                false
            }
        }

        private val helloTick = object : Runnable {
            override fun run() {
                if (stopped) return
                sendHello()
                val now = System.currentTimeMillis()
                var removed = false
                for ((id, p) in peers) if (now - p.seen > PEER_TIMEOUT_MS) {
                    peers.remove(id); p.player?.let { try { it.release() } catch (_: Exception) {} }; removed = true
                }
                if (removed) changed()
                main.postDelayed(this, 2_000L)
            }
        }

        private fun packet(type: Byte, data: ByteArray): ByteArray? = try {
            val header = byteArrayOf('S'.code.toByte(), 'W'.code.toByte(), 1, type)
            val nonce = ByteArray(12).also { random.nextBytes(it) }
            val idBytes = myId.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val s = seq++
            val plain = idBytes + byteArrayOf((s ushr 24).toByte(), (s ushr 16).toByte(), (s ushr 8).toByte(), s.toByte()) + data
            val c = cipher.get()!!
            c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keys.audio, "AES"), GCMParameterSpec(128, nonce))
            c.updateAAD(header)
            header + nonce + c.doFinal(plain)
        } catch (_: Exception) { null }

        private fun broadcast(bytes: ByteArray) {
            val s = socket ?: return
            val targets = HashMap<String, InetSocketAddress>(endpoints)
            for (addr in targets.values) {
                try { s.send(DatagramPacket(bytes, bytes.size, addr)) } catch (_: Exception) {}
            }
        }

        private fun sendHello() {
            if (stopped) return
            val bytes = packet(T_HELLO, me.toByteArray(Charsets.US_ASCII)) ?: return
            Thread { broadcast(bytes) }.start()
        }

        private fun receiveLoop(s: DatagramSocket) {
            val buf = ByteArray(4096)
            while (!stopped) {
                val pkt = DatagramPacket(buf, buf.size)
                try { s.receive(pkt) } catch (_: Exception) { if (stopped) return else continue }
                val len = pkt.length
                if (len < 4 + 12 + 16 + 12) continue
                if (buf[0] != 'S'.code.toByte() || buf[1] != 'W'.code.toByte() || buf[2] != 1.toByte()) continue
                val type = buf[3]
                val plain = try {
                    val c = cipher.get()!!
                    c.init(Cipher.DECRYPT_MODE, SecretKeySpec(keys.audio, "AES"), GCMParameterSpec(128, buf, 4, 12))
                    c.updateAAD(buf, 0, 4)
                    c.doFinal(buf, 16, len - 16)
                } catch (_: Exception) { continue }
                if (plain.size < 12) continue
                val id = plain.copyOfRange(0, 8).joinToString("") { "%02x".format(it) }
                if (id == myId) continue
                val source = pkt.socketAddress as? InetSocketAddress ?: continue
                endpoints["${source.address.hostAddress}:${source.port}"] = source
                val data = plain.copyOfRange(12, plain.size)
                val now = System.currentTimeMillis()
                when (type) {
                    T_HELLO -> {
                        val known = peers[id]
                        val peer = known ?: Peer(id).also { peers[id] = it }
                        peer.seen = now
                        val hash = String(data, Charsets.US_ASCII)
                        if (hash.length == 64 && hash.startsWith(id)) peer.hash = hash
                        if (known == null) { changed(); sendHello() }
                    }
                    T_AUDIO -> {
                        val peer = peers[id] ?: Peer(id).also { peers[id] = it }
                        peer.seen = now
                        val wasTalking = now - peer.talkingAt < 600
                        peer.talkingAt = now
                        if (!talking.get()) play(peer, data)
                        if (!wasTalking) changed()
                    }
                    T_BYE -> {
                        peers.remove(id)?.player?.let { try { it.release() } catch (_: Exception) {} }
                        changed()
                    }
                }
            }
        }

        private fun play(peer: Peer, pcm: ByteArray) {
            val track = peer.player ?: try {
                val min = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(if (speakerOn) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setBufferSizeInBytes(maxOf(min, FRAME_BYTES * 10))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build().also { it.play(); peer.player = it }
            } catch (_: Exception) { return }
            try { track.write(pcm, 0, pcm.size, AudioTrack.WRITE_NON_BLOCKING) } catch (_: Exception) {}
        }

        @SuppressLint("MissingPermission")
        fun startTalking(): Boolean {
            if (stopped || !talking.compareAndSet(false, true)) return false
            changed()
            Thread({
                val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val rec = try {
                    AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, FRAME_BYTES * 4))
                } catch (_: Exception) { null }
                if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
                    talking.set(false); changed(); rec?.release(); return@Thread
                }
                val frame = ByteArray(FRAME_BYTES)
                try {
                    rec.startRecording()
                    while (talking.get() && !stopped) {
                        var read = 0
                        while (read < FRAME_BYTES && talking.get()) {
                            val n = rec.read(frame, read, FRAME_BYTES - read)
                            if (n <= 0) break
                            read += n
                        }
                        if (read == FRAME_BYTES) packet(T_AUDIO, frame)?.let { broadcast(it) }
                    }
                } catch (_: Exception) {
                } finally {
                    try { rec.stop() } catch (_: Exception) {}
                    rec.release()
                }
            }, "walkie-tx").start()
            return true
        }

        fun stopTalking() {
            if (talking.compareAndSet(true, false)) changed()
        }

        fun stop() {
            if (stopped) return
            stopped = true
            talking.set(false)
            main.removeCallbacks(helloTick)
            packet(T_BYE, ByteArray(0))?.let { bye -> Thread { broadcast(bye); try { socket?.close() } catch (_: Exception) {} }.start() }
                ?: try { socket?.close() } catch (_: Exception) {}
            try { nsd?.unregisterService(registration) } catch (_: Exception) {}
            try { nsd?.stopServiceDiscovery(discovery) } catch (_: Exception) {}
            peers.values.forEach { p -> p.player?.let { try { it.release() } catch (_: Exception) {} } }
            peers.clear()
            if (!speakerOn) { speakerOn = true; applyRoute() }
            try { lock?.release() } catch (_: Exception) {}
            OngoingCallService.stop(app)
            if (session === this) session = null
            changed()
        }
    }

    object Background {
        private const val SCAN_MS = 20_000L
        private const val PERIOD_MS = 60_000L
        private const val QUIET_MS = 10 * 60_000L
        private val handler = Handler(Looper.getMainLooper())
        private var scanner: Scanner? = null
        private var active = false
        private var app: Context? = null
        private val notified = HashMap<String, Long>()

        fun update(context: Context, onWifi: Boolean) {
            app = context.applicationContext
            handler.post {
                if (onWifi && !active) { active = true; handler.post(cycle) }
                else if (!onWifi && active) stopNow()
            }
        }

        fun stop() { handler.post { stopNow() } }

        private fun stopNow() {
            active = false
            handler.removeCallbacks(cycle)
            handler.removeCallbacks(endScan)
            scanner?.stop()
            scanner = null
        }

        private val endScan = Runnable { scanner?.stop(); scanner = null }

        private val cycle = object : Runnable {
            override fun run() {
                if (!active) return
                val ctx = app ?: return
                if (scanner == null && session == null) {
                    val tags = groupTags(ctx)
                    if (tags.isNotEmpty()) {
                        scanner = Scanner(ctx, tags) { found -> handler.post { found.forEach { onFound(ctx, it) } } }.also { it.start() }
                        handler.postDelayed(endScan, SCAN_MS)
                    }
                }
                handler.postDelayed(this, PERIOD_MS)
            }
        }

        private fun groupTags(context: Context): Map<String, String> {
            val arr = pl.sinusoide.sinuschat.util.ConversationCache.load(context) ?: return emptyMap()
            val out = HashMap<String, String>()
            for (i in 0 until arr.length()) {
                val gid = arr.optJSONObject(i)?.optString("id") ?: continue
                if (!gid.startsWith("g_")) continue
                keys(context, gid)?.let { out[it.tag] = gid }
            }
            return out
        }

        private fun onFound(context: Context, groupId: String) {
            if (session?.groupId == groupId) return
            val now = System.currentTimeMillis()
            if (now - (notified[groupId] ?: 0L) < QUIET_MS) return
            notified[groupId] = now
            if (!pl.sinusoide.sinuschat.util.NotificationPrefs.allowMessage(context, groupId)) return
            val title = GroupCallRing.groupTitle(context, groupId)
            val open = android.app.PendingIntent.getActivity(context, ("walkie$groupId").hashCode(),
                WalkieTalkieActivity.intent(context, groupId).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
            val notif = androidx.core.app.NotificationCompat.Builder(context,
                    pl.sinusoide.sinuschat.util.MessageNotifier.channelId(context))
                .setSmallIcon(pl.sinusoide.sinuschat.R.drawable.ic_stat_name)
                .setContentTitle(context.getString(pl.sinusoide.sinuschat.R.string.walkie_ring_title, title))
                .setContentText(context.getString(pl.sinusoide.sinuschat.R.string.walkie_ring_text))
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_CALL)
                .setAutoCancel(true)
                .setTimeoutAfter(5 * 60_000L)
                .setColor(0xFF5288C1.toInt())
                .setContentIntent(open)
                .addAction(pl.sinusoide.sinuschat.R.drawable.ic_walkie_talkie,
                    context.getString(pl.sinusoide.sinuschat.R.string.gcall_join), open)
                .setSilent(pl.sinusoide.sinuschat.util.NotificationPrefs.silentMessage(context))
                .build()
            try {
                androidx.core.app.NotificationManagerCompat.from(context).notify(("walkie$groupId").hashCode(), notif)
                pl.sinusoide.sinuschat.util.NotificationSound.alert(context, false)
            } catch (_: SecurityException) {}
        }
    }

    class Scanner(private val context: Context, private val tags: Map<String, String>, private val onChange: (Set<String>) -> Unit) {
        private val found = ConcurrentHashMap<String, String>()
        private var nsd: NsdManager? = null
        private var lock: WifiManager.MulticastLock? = null
        private var running = false
        private val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onStartDiscoveryFailed(type: String, code: Int) {}
            override fun onStopDiscoveryFailed(type: String, code: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                val name = info.serviceName ?: return
                val tag = name.removePrefix("sw-").substringBefore("-")
                val gid = tags[tag] ?: return
                if (name == session?.serviceName) return
                found[name] = gid
                onChange(found.values.toSet())
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                if (found.remove(info.serviceName ?: return) != null) onChange(found.values.toSet())
            }
        }

        fun start() {
            if (running || tags.isEmpty()) return
            running = true
            lock = multicastLock(context)
            nsd = context.applicationContext.getSystemService(NsdManager::class.java)
            try { nsd?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) } catch (_: Exception) { running = false }
        }

        fun stop() {
            if (!running) return
            running = false
            try { nsd?.stopServiceDiscovery(listener) } catch (_: Exception) {}
            try { lock?.release() } catch (_: Exception) {}
            found.clear()
        }
    }
}
