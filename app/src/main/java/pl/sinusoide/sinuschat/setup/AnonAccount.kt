package pl.sinusoide.sinuschat.setup

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.crypto.tink.subtle.Ed25519Sign
import org.json.JSONObject
import pl.sinusoide.sinuschat.network.ApiClient
import pl.sinusoide.sinuschat.util.BackupPrefs
import pl.sinusoide.sinuschat.util.RecoveryKey
import java.security.MessageDigest

object AnonAccount {

    private const val TAG = "AnonAccount"
    const val PREFIX = "anon:"

    private const val ID_LABEL = "SinusChat account id v1"
    private const val LOGIN_LABEL = "SinusChat login key v1"

    fun isAnonId(id: String?) = id != null && id.startsWith(PREFIX)

    @Volatile var creating = false

    fun idFor(rk: RecoveryKey) = PREFIX + accountHash(rk)

    fun accountHash(rk: RecoveryKey): String = hex(rk.proof(ID_LABEL, ByteArray(0)))

    private fun loginSeed(rk: RecoveryKey): ByteArray = rk.proof(LOGIN_LABEL, ByteArray(0))

    fun loginPublicKey(rk: RecoveryKey): String =
        Base64.encodeToString(Ed25519Sign.KeyPair.newKeyPairFromSeed(loginSeed(rk)).publicKey, Base64.NO_WRAP)

    fun sign(rk: RecoveryKey, message: String): String =
        Base64.encodeToString(Ed25519Sign(loginSeed(rk)).sign(message.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)

    fun isCurrent(ctx: Context) = isAnonId(SetupActivity.getUserEmail(ctx))

    sealed class Result {
        object Ok : Result()
        object NoAccount : Result()
        object Busy : Result()
        object Exists : Result()
        object Failed : Result()
    }

    private fun postCode(app: Context, path: String, body: JSONObject): Pair<Int, JSONObject?> {
        val r = ApiClient.rawPost(app, ApiClient.BASE_URL + path, body.toString().toByteArray(Charsets.UTF_8),
            "application/json; charset=utf-8") ?: return -1 to null
        return r.first to runCatching { JSONObject(r.second.toString(Charsets.UTF_8)) }.getOrNull()
    }

    private fun failure(code: Int): Result = when (code) {
        401, 404 -> Result.NoAccount
        429 -> Result.Busy
        else -> Result.Failed
    }

    fun create(ctx: Context, rk: RecoveryKey): Result {
        val app = ctx.applicationContext
        return try {
            val ch = ApiClient.post("/api/anon/challenge", JSONObject()) ?: return Result.Failed
            val challenge = ch.optString("challenge").ifEmpty { return Result.Failed }
            val bits = ch.optInt("bits", 20).coerceIn(8, 28)
            val nonce = solve(challenge, bits)
            val resp = ApiClient.post("/api/anon/register", JSONObject()
                .put("accountHash", accountHash(rk))
                .put("loginKey", loginPublicKey(rk))
                .put("challenge", challenge)
                .put("nonce", nonce.toString())) ?: return Result.Failed
            if (resp.optString("code") == "exists") return Result.Exists
            if (!saveProof(app, resp)) return Result.Failed
            Result.Ok
        } catch (e: Exception) { Log.w(TAG, "Zakładanie konta: ${e.message}"); Result.Failed }
    }

    fun login(ctx: Context, rk: RecoveryKey): Result {
        val app = ctx.applicationContext
        return try {
            val hash = accountHash(rk)
            val (c1, ch) = postCode(app, "/api/anon/login-challenge", JSONObject().put("accountHash", hash))
            if (c1 !in 200..299) return failure(c1)
            val challenge = ch?.optString("challenge").orEmpty().ifEmpty { return Result.Failed }
            val (c2, resp) = postCode(app, "/api/anon/login", JSONObject()
                .put("accountHash", hash)
                .put("challenge", challenge)
                .put("signature", sign(rk, "SinusChat login v1|$hash|$challenge")))
            if (c2 !in 200..299 || resp == null) return failure(c2)
            if (!saveProof(app, resp)) return Result.Failed
            Result.Ok
        } catch (e: Exception) { Log.w(TAG, "Logowanie kluczem: ${e.message}"); Result.Failed }
    }

    fun refreshProof(ctx: Context): Boolean {
        if (!isCurrent(ctx)) return false
        val rk = BackupPrefs.recoveryKey(ctx) ?: return false
        if (accountHash(rk) != SetupActivity.getUserPhoneHash(ctx)) return false
        return login(ctx, rk) == Result.Ok
    }

    private fun saveProof(ctx: Context, resp: JSONObject): Boolean {
        val proof = resp.optString("authProof").ifEmpty { return false }
        ApiClient.saveAuthProof(ctx, proof, resp.optLong("expiresAt", System.currentTimeMillis() + 10 * 60_000))
        return true
    }

    internal fun solve(challenge: String, bits: Int): Long {
        val md = MessageDigest.getInstance("SHA-256")
        val prefix = "$challenge:".toByteArray(Charsets.UTF_8)
        var n = 0L
        while (true) {
            md.update(prefix)
            val d = md.digest(n.toString().toByteArray(Charsets.UTF_8))
            if (zeroBits(d) >= bits) return n
            n++
        }
    }

    internal fun zeroBits(d: ByteArray): Int {
        var z = 0
        for (b in d) {
            val v = b.toInt() and 0xFF
            if (v == 0) { z += 8; continue }
            return z + Integer.numberOfLeadingZeros(v) - 24
        }
        return z
    }

    private fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }
}
