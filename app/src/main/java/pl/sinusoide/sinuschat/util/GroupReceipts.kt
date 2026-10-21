package pl.sinusoide.sinuschat.util

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import pl.sinusoide.sinuschat.crypto.SecurePrefs
import pl.sinusoide.sinuschat.data.SinusChatDatabase
import pl.sinusoide.sinuschat.messenger.MessageRepository
import java.util.UUID

object GroupReceipts {

    const val KIND = "gack"
    const val MAX_MEMBERS = 30
    private const val PREFS = "group_receipts"

    private fun prefs(ctx: Context) = SecurePrefs.get(ctx, PREFS)

    fun isSmall(ctx: Context, groupId: String): Boolean =
        GroupRoster.members(ctx, groupId)?.let { it.size in 2..MAX_MEMBERS } == true

    fun mayBeSmall(ctx: Context, groupId: String): Boolean =
        GroupRoster.members(ctx, groupId)?.let { it.size in 2..MAX_MEMBERS } ?: true

    private val lock = Any()
    private val pending = HashMap<String, LongArray>()
    private var scheduled = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    fun queue(ctx: Context, groupId: String, myHash: String, delivered: Long, read: Long, disappear: Long = 0L) {
        val app = ctx.applicationContext
        synchronized(lock) {
            val cur = pending.getOrPut(groupId) { longArrayOf(0L, 0L, 0L) }
            cur[0] = maxOf(cur[0], delivered)
            cur[1] = maxOf(cur[1], read)
            cur[2] = maxOf(cur[2], disappear)
            if (!scheduled) {
                scheduled = true
                handler.postDelayed({ flush(app, myHash) }, 2_000L)
            }
        }
    }

    private fun flush(ctx: Context, myHash: String) {
        val batch = synchronized(lock) {
            scheduled = false
            pending.toMap().also { pending.clear() }
        }
        AppExecutor.io { for ((g, v) in batch) sendBlocking(ctx, g, myHash, v[0], v[1], v[2]) }
    }

    private fun sendBlocking(ctx: Context, groupId: String, myHash: String, delivered: Long, read: Long, disappear: Long) {
        val dao = try { SinusChatDatabase.getInstance(ctx).messageDao() } catch (_: Exception) { return }
        val p = prefs(ctx)
        var members = GroupRoster.members(ctx, groupId)
        if (members == null) {
            pl.sinusoide.sinuschat.messenger.GroupRepository.sendAccessBlocking(ctx, groupId)
            members = GroupRoster.members(ctx, groupId) ?: return
        }
        if (members.size !in 2..MAX_MEMBERS) return
        val baseD = p.getLong("sd_$groupId", 0L)
        val baseR = p.getLong("sr_$groupId", 0L)
        val marks = (members - myHash).associateWith { a ->
            val r = p.getLong("sr_${groupId}_$a", baseR)
            longArrayOf(p.getLong("sd_${groupId}_$a", baseD), r, p.getLong("sx_${groupId}_$a", r))
        }
        val from = marks.values.minOfOrNull { m ->
            minOf(if (delivered > m[0]) m[0] else Long.MAX_VALUE, if (read > m[1]) m[1] else Long.MAX_VALUE,
                if (disappear > m[2]) m[2] else Long.MAX_VALUE)
        } ?: return
        if (from == Long.MAX_VALUE) return
        val upto = maxOf(delivered, read, disappear)
        val authors = try { dao.getIncomingSenders(groupId, myHash, from, upto) } catch (_: Exception) { emptyList() }
        val e = p.edit()
        for (author in authors) {
            val m = marks[author] ?: continue
            val newD = if (delivered > m[0]) delivered else 0L
            val newR = if (read > m[1]) read else 0L
            val newX = if (disappear > maxOf(m[2], newR)) disappear else 0L
            if (newD == 0L && newR == 0L && newX == 0L) continue
            val payload = JSONObject().put("g", groupId)
            if (newD > 0) payload.put("d", newD)
            if (newR > 0) payload.put("r", newR)
            if (newX > 0) payload.put("x", newX)
            val body = JSONObject()
                .put("conversationId", MessageRepository.conversationId(myHash, author))
                .put("messageId", UUID.randomUUID().toString())
                .put("kind", KIND)
                .put("recipientHash", author)
                .put("payloadB64", Base64.encodeToString(payload.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
            pl.sinusoide.sinuschat.crypto.SealedSender.post(ctx, body)
            if (newD > 0) e.putLong("sd_${groupId}_$author", newD)
            if (newR > 0) e.putLong("sr_${groupId}_$author", newR)
            if (newX > 0 || newR > 0) e.putLong("sx_${groupId}_$author", maxOf(newX, newR))
        }
        e.apply()
    }

    fun onReceipt(ctx: Context, myHash: String, from: String, payload: JSONObject) {
        val groupId = payload.optString("g")
        if (!groupId.startsWith("g_")) return
        var members = GroupRoster.members(ctx, groupId)
        if (members == null || from !in members) {
            pl.sinusoide.sinuschat.messenger.GroupRepository.sendAccessBlocking(ctx, groupId)
            members = GroupRoster.members(ctx, groupId) ?: return
            if (from !in members) return
        }
        val p = prefs(ctx)
        val state = runCatching { JSONObject(p.getString("m_$groupId", "{}") ?: "{}") }.getOrElse { JSONObject() }
        val cur = state.optJSONArray(from)
        val d = maxOf(cur?.optLong(0) ?: 0L, payload.optLong("d", 0L))
        val r = maxOf(cur?.optLong(1) ?: 0L, payload.optLong("r", 0L))
        val x = maxOf(cur?.optLong(2) ?: 0L, payload.optLong("x", 0L), r)
        state.put(from, org.json.JSONArray().put(maxOf(d, r, x)).put(r).put(x))
        val others = members - myHash
        for (k in state.keys().asSequence().toList()) if (k !in others) state.remove(k)
        p.edit().putString("m_$groupId", state.toString()).apply()
        if (others.isEmpty() || others.any { !state.has(it) }) return
        val minD = others.minOf { state.optJSONArray(it)?.optLong(0) ?: 0L }
        val minR = others.minOf { state.optJSONArray(it)?.optLong(1) ?: 0L }
        val minX = others.minOf { state.optJSONArray(it)?.let { a -> maxOf(a.optLong(2), a.optLong(1)) } ?: 0L }
        if (minD > 0) MessageRepository.applyDeliveryAck(ctx, groupId, myHash, minD)
        if (minR > 0) MessageRepository.applyReadAck(ctx, groupId, myHash, minR)
        if (minX > minR) MessageRepository.applyDisappearAck(ctx, groupId, myHash, minX)
    }

    fun forget(ctx: Context, groupId: String) {
        try {
            val p = prefs(ctx)
            val e = p.edit().remove("m_$groupId").remove("sd_$groupId").remove("sr_$groupId")
            for (k in p.all.keys) if (k.startsWith("sd_${groupId}_") || k.startsWith("sr_${groupId}_") ||
                k.startsWith("sx_${groupId}_")) e.remove(k)
            e.apply()
        } catch (_: Exception) {}
    }
}
