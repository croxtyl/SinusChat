package pl.sinusoide.sinuschat.crypto

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurePrefs {

    private const val TAG = "SecurePrefs"
    private const val ESP_INTERNAL = "__androidx_security_crypto_encrypted_prefs"

    private val cache = HashMap<String, SharedPreferences>()

    @Synchronized
    fun get(context: Context, name: String, plainPrefixes: List<String> = emptyList()): SharedPreferences {
        cache[name]?.let { return it }
        val app = context.applicationContext
        var prefs: SharedPreferences? = null
        var attempt = 0
        while (prefs == null) {
            try {
                prefs = create(app, name)
            } catch (e: Exception) {
                if (isCorrupted(e)) {
                    Log.e(TAG, "Magazyn $name nie do odczytania (inny klucz Keystore) — zakładam nowy")
                    app.deleteSharedPreferences(name)
                    prefs = create(app, name)
                } else {
                    if (++attempt > 2) throw e
                    Log.w(TAG, "Keystore niedostępny ($name), próba $attempt: ${e.message}")
                    try { Thread.sleep(150L * attempt) } catch (_: InterruptedException) {}
                }
            }
        }
        if (plainPrefixes.isNotEmpty()) adoptPlaintext(app, name, prefs, plainPrefixes)
        cache[name] = prefs
        return prefs
    }

    @Synchronized
    fun forgetAll() { cache.clear() }

    @Synchronized
    fun forget(context: Context, name: String) {
        cache.remove(name)
        try { context.applicationContext.deleteSharedPreferences(name) } catch (_: Exception) {}
    }

    private fun create(app: Context, name: String): SharedPreferences =
        EncryptedSharedPreferences.create(
            app, name,
            MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    private fun isCorrupted(e: Throwable): Boolean {
        var t: Throwable? = e
        while (t != null) {
            val n = t.javaClass.simpleName
            if (n == "AEADBadTagException" || n == "InvalidProtocolBufferException") return true
            t = t.cause
        }
        return false
    }

    private fun adoptPlaintext(app: Context, name: String, enc: SharedPreferences, prefixes: List<String>) {
        val raw = app.getSharedPreferences(name, Context.MODE_PRIVATE)
        val plain = raw.all.filterKeys { k ->
            !k.startsWith(ESP_INTERNAL) && prefixes.any { k.startsWith(it) }
        }
        if (plain.isEmpty()) return
        val e = enc.edit()
        for ((k, v) in plain) {
            when (v) {
                is String -> e.putString(k, v)
                is Long -> e.putLong(k, v)
                is Int -> e.putInt(k, v)
                is Boolean -> e.putBoolean(k, v)
                is Float -> e.putFloat(k, v)
                is Set<*> -> e.putStringSet(k, v.filterIsInstance<String>().toSet())
            }
        }
        e.commit()
        val r = raw.edit()
        for (k in plain.keys) r.remove(k)
        r.commit()
        Log.w(TAG, "Przeniesiono ${plain.size} jawnych wpisów do szyfrowanej części ($name)")
    }
}
