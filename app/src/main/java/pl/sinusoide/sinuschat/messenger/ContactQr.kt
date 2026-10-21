package pl.sinusoide.sinuschat.messenger

import android.util.Base64
import org.json.JSONObject
import pl.sinusoide.sinuschat.util.optStringOrNull

object ContactQr {

    private val KEY = "SinusChatQR_v1_key".toByteArray(Charsets.UTF_8)

    fun encode(hash: String, name: String): String {
        val json = JSONObject().apply {
            put("h", hash)
            put("n", name)
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
