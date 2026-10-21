package pl.sinusoide.sinuschat.util

import android.content.Context
import java.math.BigInteger
import java.security.MessageDigest

object SecurityUtils {

    private const val PREFS  = "sinuschat_key_tracker"
    private const val PREFIX = "ekey_"

    fun storeContactKey(context: Context, contactHash: String, publicKeyBase64: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(PREFIX + contactHash, publicKeyBase64)
            .apply()
    }

    fun getStoredPublicKey(context: Context, contactHash: String): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PREFIX + contactHash, null)

    fun hasKeyChanged(context: Context, contactHash: String, currentKey: String): Boolean {
        val stored = getStoredPublicKey(context, contactHash)
        if (stored == null) {
            storeContactKey(context, contactHash, currentKey)
            return false
        }
        return stored != currentKey
    }

    fun generateSafetyNumber(myPublicKey: String, otherPublicKey: String): List<String> {
        val first  = if (myPublicKey <= otherPublicKey) myPublicKey  else otherPublicKey
        val second = if (myPublicKey <= otherPublicKey) otherPublicKey else myPublicKey

        val input  = (first + ":" + second).toByteArray(Charsets.UTF_8)
        val hash   = MessageDigest.getInstance("SHA-256").digest(input)

        val digits = BigInteger(1, hash).toString().padStart(80, '0').take(60)

        val groups = (0 until 12).map { i -> digits.substring(i * 5, (i + 1) * 5) }
        return groups.chunked(4).map { row -> row.joinToString("  ") }
    }
}
