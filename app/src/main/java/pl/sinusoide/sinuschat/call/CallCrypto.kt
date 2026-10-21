package pl.sinusoide.sinuschat.call

import android.content.Context
import android.util.Log
import org.json.JSONObject
import pl.sinusoide.sinuschat.crypto.E2EManager
import pl.sinusoide.sinuschat.crypto.SecurePrefs
import pl.sinusoide.sinuschat.setup.SetupActivity
import pl.sinusoide.sinuschat.util.AppExecutor
import pl.sinusoide.sinuschat.util.PeerKeyCache
import java.util.concurrent.Executors

object CallCrypto {

    private const val TAG = "CallCrypto"
    private const val PREFS = "call_e2e_peers"
    const val CAP = "call_e2e"
    val SEALED = setOf("call_offer", "call_answer", "call_ice", "call_reoffer", "call_reanswer")

    private val worker = Executors.newSingleThreadExecutor()

    class Channel(val peer: String, val secret: ByteArray?, val sealed: Boolean)

    private fun prefs(ctx: Context) = SecurePrefs.get(ctx.applicationContext, PREFS)

    fun isPinned(ctx: Context, peer: String): Boolean = peer.isNotEmpty() && prefs(ctx).getBoolean(peer, false)

    private fun pin(ctx: Context, peer: String) {
        if (peer.isNotEmpty() && !isPinned(ctx, peer)) prefs(ctx).edit().putBoolean(peer, true).apply()
    }

    fun cached(ctx: Context, peer: String): Channel =
        Channel(peer, E2EManager.getSharedSecretCached(ctx, peer), isPinned(ctx, peer))

    fun prepare(ctx: Context, peer: String): Channel {
        val user = pl.sinusoide.sinuschat.network.ApiClient.getObject("/api/user/$peer", ctx)
        user?.optString("e2e_public_key")?.takeIf { it.isNotEmpty() && it != "null" }?.let { key ->
            if (PeerKeyCache.saveKey(ctx, peer, key))
                pl.sinusoide.sinuschat.crypto.SignalProtocol.onPeerIdentityChanged(ctx, peer)
        }
        if (user?.optBoolean("call_e2e", false) == true) pin(ctx, peer)
        val secret = E2EManager.getSharedSecretCached(ctx, peer)
        val sealed = isPinned(ctx, peer)
        if (sealed && secret == null) Log.w(TAG, "brak klucza rozmówcy — sygnały rozmowy nie zostaną wysłane")
        return Channel(peer, secret, sealed)
    }

    fun seal(ctx: Context, channel: Channel, conversationId: String, type: String, extra: JSONObject): JSONObject? {
        if (!channel.sealed || type !in SEALED) return extra
        val secret = channel.secret ?: return null
        val inner = JSONObject(extra.toString())
            .put("c", conversationId).put("t", type).put("f", SetupActivity.getUserPhoneHash(ctx))
        val enc = E2EManager.encrypt(inner.toString(), secret) ?: return null
        return JSONObject().put("e2e", enc)
    }

    fun open(ctx: Context, msg: JSONObject, deliver: (JSONObject) -> Unit) {
        val type = msg.optString("type")
        if (type !in SEALED) { deliver(msg); return }
        val app = ctx.applicationContext
        worker.execute {
            val opened = try { openBlocking(app, msg) } catch (e: Exception) {
                Log.w(TAG, "open: ${e.message}")
                null
            }
            if (opened != null) AppExecutor.main { deliver(opened) }
        }
    }

    private fun openBlocking(ctx: Context, msg: JSONObject): JSONObject? {
        val type = msg.optString("type")
        val from = msg.optString("fromHash").ifEmpty { msg.optString("senderHash") }
        val enc = msg.optString("e2e")
        if (enc.isEmpty()) {
            if (isPinned(ctx, from)) {
                Log.w(TAG, "odrzucono jawny sygnał $type od rozmówcy, który obsługuje szyfrowanie")
                return null
            }
            return msg
        }
        if (from.isEmpty()) return null
        var text = E2EManager.getSharedSecretCached(ctx, from)?.let { E2EManager.decrypt(enc, it) }
        if (text == null) {
            text = E2EManager.fetchAndCacheKey(ctx, from)
                ?.let { E2EManager.computeSharedSecret(ctx, it) }
                ?.let { E2EManager.decrypt(enc, it) }
        }
        if (text == null) { Log.w(TAG, "nie udało się otworzyć sygnału $type"); return null }
        val inner = JSONObject(text)
        if (inner.optString("c") != msg.optString("conversationId") || inner.optString("t") != type ||
            inner.optString("f") != from) {
            Log.w(TAG, "sygnał $type nie pasuje do rozmowy lub nadawcy")
            return null
        }
        pin(ctx, from)
        val out = JSONObject(msg.toString())
        out.remove("e2e")
        for (k in inner.keys()) if (k != "c" && k != "t" && k != "f") out.put(k, inner.get(k))
        return out
    }
}
