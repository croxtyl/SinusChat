package pl.sinusoide.sinuschat.messenger

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import pl.sinusoide.sinuschat.AppConfig
import pl.sinusoide.sinuschat.R
import pl.sinusoide.sinuschat.crypto.ProfileCrypto
import pl.sinusoide.sinuschat.network.ApiClient
import pl.sinusoide.sinuschat.util.AppExecutor
import pl.sinusoide.sinuschat.util.LinkLog
import pl.sinusoide.sinuschat.util.NetworkSettings
import pl.sinusoide.sinuschat.util.PrivacySettings
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object InviteManager {

    const val PREFIX = "inv_"
    const val KIND_ONCE = "once"
    const val KIND_MULTI = "multi"

    private val CODE_RE = Regex("^inv_[A-Za-z0-9_-]{20,40}$")

    private val RETRYABLE = setOf("offline", "timeout", "unreachable", "tls", "server", "rate_limited", "app_error", "network", "session")

    private val worker = Executors.newCachedThreadPool()

    data class Invite(val code: String, val kind: String, val createdAt: Long, val expiresAt: Long?, val useCount: Int,
                      val fragment: String = "") {
        val link: String get() = AppConfig.ADD_LINK_BASE + code + fragment
        val shortLink: String get() = AppConfig.ADD_LINK_BASE + code
    }

    sealed class PreviewResult {
        data class Ready(val hash: String?, val name: String?, val color: String?) : PreviewResult()
        data class Failure(val errorCode: String, val http: Int = 0) : PreviewResult()
    }

    private val COLOR_RE = Regex("^#[0-9A-Fa-f]{6}$")

    sealed class RedeemResult {
        data class Success(val hash: String, val name: String, val conversationId: String) : RedeemResult()
        data class Failure(val errorCode: String, val http: Int = 0) : RedeemResult()
    }

    private data class Resp(val status: Int, val json: JSONObject?, val fail: String? = null)

    fun isInviteToken(token: String?): Boolean = token != null && CODE_RE.matches(token.substringBefore('#'))

    fun hasKey(token: String?): Boolean = ProfileCrypto.keyFromFragment(token) != null

    fun isRetryable(errorCode: String): Boolean = errorCode in RETRYABLE

    fun create(context: Context, kind: String, reset: Boolean = false, onDone: (Invite?) -> Unit) {
        val app = context.applicationContext
        AppExecutor.io {
            val body = JSONObject().put("kind", kind).put("reset", reset)
            val (code, json) = post(app, "/api/invites", body)
            val inv = json?.optJSONObject("invite")
            val result = if (code in 200..299 && inv != null) Invite(
                code = inv.optString("code"),
                kind = inv.optString("kind"),
                createdAt = inv.optLong("createdAt"),
                expiresAt = if (inv.isNull("expiresAt")) null else inv.optLong("expiresAt"),
                useCount = inv.optInt("useCount"),
                fragment = ProfileCrypto.linkFragment(app)
            ) else null
            AppExecutor.main { onDone(result) }
        }
    }

    fun preview(context: Context, code: String, onDone: (PreviewResult) -> Unit) {
        if (!isInviteToken(code)) { onDone(PreviewResult.Failure("link_damaged")); return }
        val app = context.applicationContext
        val keyB64 = ProfileCrypto.keyFromFragment(code)
        val cleanCode = code.substringBefore('#')
        work(app, "preview", { e -> PreviewResult.Failure(e) }, onDone) {
            val r = post(app, "/api/invites/preview", JSONObject().put("code", cleanCode))
            val json = r.json
            val errorCode = json?.optString("code")?.takeIf { it.isNotEmpty() }
            when {
                r.status in 200..299 && json?.optBoolean("ok") == true -> {
                    val hash = json.optString("hash").takeIf { it.length == 64 }
                    val raw = json.optString("displayName")
                    val name = hash?.let {
                        runCatching { ProfileCrypto.openNameWithKey(app, it, raw, keyB64) }.getOrNull()
                    }?.filter { !it.isISOControl() }?.trim()?.take(64)?.takeIf { it.isNotEmpty() }
                    if (hash != null && name == null && ProfileCrypto.isSealedName(raw))
                        PreviewResult.Failure(if (keyB64 == null) "link_incomplete" else "link_damaged", r.status)
                    else PreviewResult.Ready(hash, name, json.optString("avatarColor").takeIf { COLOR_RE.matches(it) })
                }
                r.status == 404 && errorCode == null && r.fail == null -> PreviewResult.Ready(null, null, null)
                else -> PreviewResult.Failure(failure(r), r.status)
            }
        }
    }

    fun redeem(context: Context, code: String, onDone: (RedeemResult) -> Unit) {
        if (!isInviteToken(code)) { onDone(RedeemResult.Failure("link_damaged")); return }
        val app = context.applicationContext
        val profileKey = ProfileCrypto.keyFromFragment(code)
        val cleanCode = code.substringBefore('#')
        work(app, "redeem", { e -> RedeemResult.Failure(e) }, onDone) {
            val r = post(app, "/api/invites/redeem", JSONObject().put("code", cleanCode))
            val json = r.json
            if (r.status in 200..299 && json?.optBoolean("ok") == true) {
                val hash = json.optString("hash")
                val convId = json.optString("conversationId")
                if (hash.length != 64 || convId.isEmpty()) return@work RedeemResult.Failure("server", r.status)
                if (profileKey != null) ProfileCrypto.receiveKey(app, hash, profileKey)
                val name = (ProfileCrypto.openName(app, hash, json.optString("displayName")) ?: "")
                    .filter { !it.isISOControl() }.trim().take(64)
                    .ifEmpty { app.getString(R.string.unknown_chat) }
                pl.sinusoide.sinuschat.util.ContactCache.saveName(app, hash, name)
                pl.sinusoide.sinuschat.util.LocalChats.add(app, convId, hash)
                introduce(app, hash)
                RedeemResult.Success(hash, name, convId)
            } else RedeemResult.Failure(failure(r), r.status)
        }
    }

    fun introduce(context: Context, peer: String) {
        val app = context.applicationContext
        val me = pl.sinusoide.sinuschat.setup.SetupActivity.getUserPhoneHash(app)
        if (peer.length != 64 || me.isEmpty() || peer == me) return
        AppExecutor.io {
            MessageRepository.sendProfileKeyBlocking(app, me, peer, direct = true)
            LinkLog.add(app, "intro", "klucz wysłany")
        }
    }

    private fun <T> work(app: Context, step: String, onCrash: (String) -> T, onDone: (T) -> Unit, block: () -> T) {
        worker.execute {
            val result = try {
                block()
            } catch (e: Throwable) {
                android.util.Log.e("QrLink", "$step: ${e.javaClass.simpleName}", e)
                LinkLog.add(app, step, "exception ${e.javaClass.simpleName}")
                onCrash("app_error")
            }
            AppExecutor.main { onDone(result) }
        }
    }

    private fun failure(r: Resp): String = when {
        r.fail != null -> r.fail
        r.status == 401 -> "session"
        r.status == 409 && r.json?.optString("error") == "device_kicked" -> "device_kicked"
        r.status == 429 -> "rate_limited"
        r.status >= 500 -> "server"
        else -> r.json?.optString("code")?.takeIf { it.isNotEmpty() } ?: "invite_invalid"
    }

    fun errorMessage(context: Context, errorCode: String): String = context.getString(when (errorCode) {
        "invite_used" -> R.string.invite_err_used
        "invite_expired" -> R.string.invite_err_expired
        "invite_revoked" -> R.string.invite_err_revoked
        "invite_owner_gone" -> R.string.invite_err_owner_gone
        "invite_own" -> R.string.your_own_code
        "link_empty" -> R.string.invite_err_link_empty
        "link_damaged" -> R.string.invite_err_link_damaged
        "link_incomplete" -> R.string.invite_err_link_incomplete
        "offline" -> R.string.invite_err_offline
        "timeout" -> R.string.invite_err_timeout
        "unreachable" -> R.string.invite_err_unreachable
        "tls" -> R.string.invite_err_tls
        "server" -> R.string.invite_err_server
        "rate_limited" -> R.string.invite_err_rate_limited
        "session" -> R.string.invite_err_session
        "device_kicked" -> R.string.invite_err_device_kicked
        "app_error" -> R.string.invite_err_app
        "network" -> R.string.error_connection
        else -> R.string.invite_err_invalid
    })

    fun errorHint(context: Context, errorCode: String): String = context.getString(when (errorCode) {
        "invite_own" -> R.string.invite_hint_own
        "invite_invalid", "invite_used", "invite_expired", "invite_revoked", "invite_owner_gone" -> R.string.invite_screen_error_desc
        "link_empty", "link_damaged" -> R.string.invite_hint_link_damaged
        "link_incomplete" -> R.string.invite_hint_link_incomplete
        "offline" -> R.string.invite_hint_offline
        "timeout", "unreachable", "network" -> R.string.invite_hint_unreachable
        "tls" -> R.string.invite_hint_tls
        "server" -> R.string.invite_hint_server
        "rate_limited" -> R.string.invite_hint_rate_limited
        "session", "device_kicked" -> R.string.invite_hint_session
        else -> R.string.invite_hint_app
    })

    private fun client(context: Context): okhttp3.OkHttpClient {
        val tor = PrivacySettings.isTorEnabled(context)
        return ApiClient.httpClient(context).newBuilder()
            .connectTimeout(if (tor) 60 else 15, TimeUnit.SECONDS)
            .readTimeout(if (tor) 60 else 20, TimeUnit.SECONDS)
            .callTimeout(if (tor) 120 else 35, TimeUnit.SECONDS)
            .build()
    }

    private fun post(context: Context, path: String, body: JSONObject): Resp = try {
        val req = okhttp3.Request.Builder()
            .url(ApiClient.BASE_URL + path)
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Authorization", "Bearer ${ApiClient.getToken(context) ?: ""}")
            .build()
        client(context).newCall(req).execute().use { resp ->
            Resp(resp.code, resp.body?.string()?.let { runCatching { JSONObject(it) }.getOrNull() })
        }
    } catch (e: Exception) {
        val offline = NetworkSettings.connection(context) == NetworkSettings.Connection.NONE
        val kind = when (e) {
            is java.io.InterruptedIOException -> "timeout"
            is javax.net.ssl.SSLException -> "tls"
            is java.io.IOException -> if (offline) "offline" else "unreachable"
            else -> "app_error"
        }
        android.util.Log.w("QrLink", "$path: ${e.javaClass.simpleName}")
        LinkLog.add(context, "net", "$path ${e.javaClass.simpleName}")
        Resp(-1, null, kind)
    }
}
