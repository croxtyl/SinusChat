package pl.sinusoide.sinuschat.crypto

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import java.security.SecureRandom

object GroupMeta {

    private const val PREFS = "group_profile_keys"
    private const val PREFIX = "e1:"
    private const val KEY_SIZE = 32

    private fun prefs(ctx: Context) = SecurePrefs.get(ctx, PREFS)

    fun profileKey(ctx: Context, groupId: String): ByteArray? =
        try {
            prefs(ctx).getString(groupId, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
                ?.takeIf { it.size == KEY_SIZE }
        } catch (_: Exception) { null }

    fun saveProfileKey(ctx: Context, groupId: String, key: ByteArray) {
        if (key.size != KEY_SIZE || groupId.isEmpty()) return
        prefs(ctx).edit().putString(groupId, Base64.encodeToString(key, Base64.NO_WRAP)).apply()
    }

    fun receiveProfileKey(ctx: Context, groupId: String, key: ByteArray) {
        if (profileKey(ctx, groupId) == null) saveProfileKey(ctx, groupId, key)
    }

    fun forget(ctx: Context, groupId: String) {
        try { prefs(ctx).edit().remove(groupId).apply() } catch (_: Exception) {}
    }

    fun newKey(): ByteArray = ByteArray(KEY_SIZE).also { SecureRandom().nextBytes(it) }

    fun isSealed(raw: String?) = raw != null && raw.startsWith(PREFIX)

    fun seal(key: ByteArray, plain: String): String =
        PREFIX + (E2EManager.encrypt(plain, key) ?: throw IllegalStateException("Szyfrowanie nazwy nieudane"))

    fun sealIfKeyed(ctx: Context, groupId: String, plain: String): String =
        profileKey(ctx, groupId)?.let { seal(it, plain) } ?: plain

    fun openOrNull(ctx: Context, groupId: String, raw: String?): String? = openWith(profileKey(ctx, groupId), raw)

    fun openWith(key: ByteArray?, raw: String?): String? {
        if (raw == null) return null
        if (!raw.startsWith(PREFIX)) return raw
        return key?.let { E2EManager.decrypt(raw.removePrefix(PREFIX), it) }
    }

    fun open(ctx: Context, groupId: String, raw: String?, fallback: String): String =
        openOrNull(ctx, groupId, raw)?.takeIf { it.isNotEmpty() } ?: fallback

    fun openConversations(ctx: Context, arr: JSONArray) {
        val fallback = ctx.getString(pl.sinusoide.sinuschat.R.string.group)
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (!id.startsWith("g_")) continue
            val raw = o.optString("group_name", "")
            if (isSealed(raw)) o.put("group_name", open(ctx, id, raw, fallback))
        }
    }

    fun linkFragment(ctx: Context, groupId: String): String =
        profileKey(ctx, groupId)?.let {
            "#k=" + Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        } ?: ""

    fun keyFromFragment(fragment: String?): ByteArray? {
        val v = fragment?.substringAfter("k=", "")?.substringBefore('&')?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            Base64.decode(v, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING).takeIf { it.size == KEY_SIZE }
        } catch (_: Exception) { null }
    }
}
