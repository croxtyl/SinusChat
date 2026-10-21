package pl.sinusoide.sinuschat.pclink

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import pl.sinusoide.sinuschat.BuildConfig
import pl.sinusoide.sinuschat.link.LinkAuthorization
import pl.sinusoide.sinuschat.link.LinkChannel
import pl.sinusoide.sinuschat.link.LinkConnection
import pl.sinusoide.sinuschat.link.LinkFrame
import pl.sinusoide.sinuschat.link.LinkHandshake
import pl.sinusoide.sinuschat.link.LinkProtocol
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

object PcLinkServer {

    private const val TAG = "PcLink"
    private const val MAX_PENDING_SOCKETS = 8

    private val main = Handler(Looper.getMainLooper())
    private val control = Executors.newSingleThreadExecutor()
    private val handshakes = Executors.newFixedThreadPool(2)
    private val connections = ConcurrentHashMap<String, LinkConnection>()
    private val listeners = mutableSetOf<() -> Unit>()

    @Volatile private var app: Context? = null
    @Volatile private var wifiAddress: InetAddress? = null
    @Volatile private var server: ServerSocket? = null
    @Volatile private var boundAddress: InetAddress? = null
    private var nsdListener: NsdManager.RegistrationListener? = null
    private var netCallbackRegistered = false

    fun init(ctx: Context) {
        val c = ctx.applicationContext
        if (app == null) app = c
        registerWifiCallback(c)
        refresh()
    }

    fun addListener(l: () -> Unit) { synchronized(listeners) { listeners += l } }

    fun removeListener(l: () -> Unit) { synchronized(listeners) { listeners -= l } }

    private fun notifyChanged() {
        val copy = synchronized(listeners) { listeners.toList() }
        main.post { copy.forEach { it() } }
    }

    fun isOnWifi(): Boolean = wifiAddress != null

    fun isConnected(key: String): Boolean = connections[key]?.isOpen == true

    fun refresh() {
        control.execute { reconcile() }
    }

    fun unpair(key: String) {
        control.execute {
            val ctx = app ?: return@execute
            PcLinkStore.remove(ctx, key)
            connections.remove(key)?.close(LinkFrame.BYE_UNPAIRED)
            reconcile()
            notifyChanged()
        }
    }

    fun shutdown() {
        control.execute {
            connections.values.forEach { it.close(LinkFrame.BYE_CLOSING) }
            connections.clear()
            stopServer()
        }
    }

    private fun shouldRun(ctx: Context): Boolean =
        PcLinkStore.isPairing() || PcLinkStore.pcs(ctx).isNotEmpty()

    private fun reconcile() {
        val ctx = app ?: return
        val addr = wifiAddress
        if (addr == null || !shouldRun(ctx)) {
            if (server != null) stopServer()
            if (addr == null) connections.values.forEach { it.close(LinkFrame.BYE_CLOSING) }
            return
        }
        if (server != null && boundAddress == addr) return
        stopServer()
        startServer(ctx, addr)
    }

    private fun startServer(ctx: Context, addr: InetAddress) {
        val ss = bind(addr, LinkProtocol.DEFAULT_PORT) ?: bind(addr, 0) ?: return
        server = ss
        boundAddress = addr
        Thread({ acceptLoop(ss) }, "pclink-accept").apply { isDaemon = true }.start()
        registerNsd(ctx, ss.localPort)
        Log.d(TAG, "nasłuch ${addr.hostAddress}:${ss.localPort}")
    }

    private fun bind(addr: InetAddress, port: Int): ServerSocket? = try {
        ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(addr, port), MAX_PENDING_SOCKETS)
        }
    } catch (e: Exception) {
        Log.w(TAG, "bind $port: ${e.message}")
        null
    }

    private fun stopServer() {
        unregisterNsd()
        runCatching { server?.close() }
        server = null
        boundAddress = null
    }

    private fun acceptLoop(ss: ServerSocket) {
        while (!ss.isClosed) {
            val socket = try { ss.accept() } catch (_: Exception) { break }
            handshakes.execute { handle(socket) }
        }
    }

    private fun handle(socket: Socket) {
        val ctx = app ?: run { runCatching { socket.close() }; return }
        try {
            socket.tcpNoDelay = true
            val channel = LinkHandshake.accept(socket, PcLinkStore.identity(ctx)) { mode, pcKey ->
                authorize(ctx, mode, pcKey)
            }
            onAuthenticated(ctx, socket, channel)
        } catch (e: Exception) {
            Log.d(TAG, "odrzucono połączenie: ${e.message}")
            runCatching { socket.close() }
        }
    }

    private fun authorize(ctx: Context, mode: Int, pcKey: ByteArray): LinkAuthorization = when (mode) {
        LinkHandshake.MODE_PAIR ->
            PcLinkStore.pairingToken(pcKey)?.let { LinkAuthorization.Accept(it) }
                ?: LinkAuthorization.Reject(LinkHandshake.REJECT_NOT_PAIRING)
        LinkHandshake.MODE_RESUME ->
            if (PcLinkStore.find(ctx, pcKey) != null) LinkAuthorization.Accept(ByteArray(0))
            else LinkAuthorization.Reject(LinkHandshake.REJECT_UNKNOWN_DEVICE)
        else -> LinkAuthorization.Reject(LinkHandshake.REJECT_VERSION)
    }

    private fun onAuthenticated(ctx: Context, socket: Socket, channel: LinkChannel) {
        val key = PcLinkStore.encode(channel.peerKey)
        val now = System.currentTimeMillis()
        if (channel.mode == LinkHandshake.MODE_PAIR) {
            PcLinkStore.pairingDone(channel.peerKey)
            val old = PcLinkStore.find(ctx, channel.peerKey)
            PcLinkStore.upsert(ctx, PcLinkStore.Pc(key, old?.name ?: "", old?.pairedAt ?: now, now, old?.app ?: ""))
        } else {
            PcLinkStore.update(ctx, key, lastSeen = now)
        }
        val conn = LinkConnection(socket, channel, connectionListener)
        connections.put(key, conn)?.close(LinkFrame.BYE_CLOSING)
        conn.start()
        conn.send(LinkFrame.Hello(phoneName(ctx), "SinusChat Android ${BuildConfig.VERSION_NAME}"))
        PcLinkHub.onConnected(ctx, conn)
        Log.d(TAG, "komputer połączony ${channel.peerId}")
        notifyChanged()
    }

    private val connectionListener = object : LinkConnection.Listener {
        override fun onFrame(connection: LinkConnection, frame: LinkFrame) {
            val ctx = app ?: return
            val key = PcLinkStore.encode(connection.peerKey)
            when (frame) {
                is LinkFrame.Hello -> {
                    PcLinkStore.update(ctx, key, name = frame.name.trim().take(64), app = frame.app.trim().take(64))
                    notifyChanged()
                }
                is LinkFrame.Bye -> {
                    if (frame.reason == LinkFrame.BYE_UNPAIRED) {
                        PcLinkStore.remove(ctx, key)
                        refresh()
                    }
                    connection.close()
                }
                else -> PcLinkHub.onFrame(connection, frame)
            }
        }

        override fun onClosed(connection: LinkConnection, error: Throwable?) {
            PcLinkHub.onDisconnected(connection)
            val ctx = app ?: return
            val key = PcLinkStore.encode(connection.peerKey)
            if (connections.remove(key, connection)) {
                PcLinkStore.update(ctx, key, lastSeen = System.currentTimeMillis())
            }
            notifyChanged()
        }
    }

    private fun phoneName(ctx: Context): String =
        runCatching { Settings.Global.getString(ctx.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: Build.MODEL

    private fun registerNsd(ctx: Context, port: Int) {
        val nsd = ctx.getSystemService(NsdManager::class.java) ?: return
        val info = NsdServiceInfo().apply {
            serviceName = "SinusChat"
            serviceType = LinkProtocol.SERVICE_TYPE
            setPort(port)
            setAttribute(LinkProtocol.TXT_ID, PcLinkStore.identity(ctx).id)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) { Log.d(TAG, "NSD: ${info.serviceName}") }
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) { Log.w(TAG, "NSD błąd $code") }
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) {}
        }
        try {
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
            nsdListener = listener
        } catch (e: Exception) {
            Log.w(TAG, "NSD: ${e.message}")
        }
    }

    private fun unregisterNsd() {
        val l = nsdListener ?: return
        nsdListener = null
        val ctx = app ?: return
        runCatching { ctx.getSystemService(NsdManager::class.java)?.unregisterService(l) }
    }

    private fun ipv4(props: LinkProperties?): InetAddress? =
        props?.linkAddresses?.map { it.address }?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }

    private fun registerWifiCallback(ctx: Context) {
        if (netCallbackRegistered) return
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        try {
            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                private var current: Network? = null

                override fun onAvailable(network: Network) {
                    current = network
                    setWifi(ipv4(cm.getLinkProperties(network)))
                }

                override fun onLinkPropertiesChanged(network: Network, props: LinkProperties) {
                    current = network
                    setWifi(ipv4(props))
                }

                override fun onLost(network: Network) {
                    if (current == network) {
                        current = null
                        setWifi(null)
                    }
                }
            })
            netCallbackRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "wifi callback: ${e.message}")
        }
    }

    private fun setWifi(addr: InetAddress?) {
        if (wifiAddress == addr) return
        wifiAddress = addr
        refresh()
        notifyChanged()
    }
}
