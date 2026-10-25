package pl.sinusoide.sinuschat.messenger

import android.util.Base64
import org.json.JSONObject
import pl.sinusoide.sinuschat.util.optStringOrNull

object ContactQr {

    private val KEY = "SinusChatQR_v1_key".toByteArray(Charsets.UTF_8)

    fun encode(hash: String, name: String, profileKey: String? = null): String {
        val json = JSONObject().apply {
            put("h", hash)
            put("n", name)
            if (!profileKey.isNullOrEmpty()) put("k", profileKey)
        }.toString().toByteArray(Charsets.UTF_8)
        val obf = xor(json)
        return Base64.encodeToString(obf, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun decode(token: String): Pair<String, String>? {
        return try {
            val obf = Base64.decode(token, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val json = String(xor(obf), Charsets.UTF_8)
            val obj = JSONObject(json)
            val hash = obj.optStringOrNull("h") ?: return null
            if (!HASH_RE.matches(hash)) return null
            Pair(hash, sanitizeName(obj.optStringOrNull("n")))
        } catch (e: Exception) {
            null
        }
    }

    private val HASH_RE = Regex("^[a-f0-9]{64}$")

    fun remember(ctx: android.content.Context, token: String) {
        val obj = try {
            JSONObject(String(xor(Base64.decode(token.substringBefore('#'),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)), Charsets.UTF_8))
        } catch (_: Exception) { return }
        val hash = obj.optStringOrNull("h")?.takeIf { HASH_RE.matches(it) } ?: return
        val name = (obj.optStringOrNull("n") ?: "").filter { !it.isISOControl() }.trim().take(64)
        if (name.isNotEmpty() && pl.sinusoide.sinuschat.util.ContactCache.realName(ctx, hash) == null)
            pl.sinusoide.sinuschat.util.ContactCache.saveName(ctx, hash, name)
        obj.optStringOrNull("k")?.let { pl.sinusoide.sinuschat.crypto.ProfileCrypto.receiveKey(ctx, hash, it) }
    }

    private fun sanitizeName(raw: String?): String {
        val cleaned = (raw ?: "")
            .filter { !it.isISOControl() }
            .trim()
            .take(64)
        return cleaned.ifEmpty { "Kontakt" }
    }

    private fun xor(data: ByteArray): ByteArray {
        val out = ByteArray(data.size)
        for (i in data.indices) {
            out[i] = (data[i].toInt() xor KEY[i % KEY.size].toInt()).toByte()
        }
        return out
    }
}
