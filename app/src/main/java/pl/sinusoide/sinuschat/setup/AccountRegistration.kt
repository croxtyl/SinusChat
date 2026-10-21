package pl.sinusoide.sinuschat.setup

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import pl.sinusoide.sinuschat.crypto.E2EManager
import pl.sinusoide.sinuschat.crypto.ProfileCrypto
import pl.sinusoide.sinuschat.messenger.MessageRepository
import pl.sinusoide.sinuschat.network.ApiClient
import pl.sinusoide.sinuschat.util.AppExecutor
import pl.sinusoide.sinuschat.util.AutoDelete
import pl.sinusoide.sinuschat.util.AvatarHelper
import pl.sinusoide.sinuschat.util.BackupPrefs
import pl.sinusoide.sinuschat.util.ContactCache
import pl.sinusoide.sinuschat.util.DeviceId
import pl.sinusoide.sinuschat.util.PrivacySettings

object AccountRegistration {

    sealed class Result {
        object Ok : Result()
        object NeedsCode : Result()
        object Failed : Result()
    }

    fun register(ctx: Context, email: String, displayName: String, remoteAvatarUrl: String? = null): Result {
        val app = ctx.applicationContext
        val emailHash = SetupActivity.accountHashOf(email)
        val anon = AnonAccount.isAnonId(email)
        if (anon && ApiClient.getAuthProof(app) == null) {
            BackupPrefs.recoveryKey(app)
                ?.takeIf { AnonAccount.accountHash(it) == emailHash }
                ?.let { AnonAccount.login(app, it) }
        }
        val authProof = ApiClient.getAuthProof(app)
            ?: return if (anon) Result.Failed else Result.NeedsCode
        val name = displayName.ifBlank {
            ProfileCrypto.openOwnName(app, ApiClient.getObject("/api/user/$emailHash", app, mapOf("X-Auth-Proof" to authProof))
                ?.optString("display_name")) ?: ""
        }

        val keyPair = E2EManager.generateKeys(app)
        val publicKey = Base64.encodeToString(keyPair.publicKey.serialize(), Base64.NO_WRAP)
        val body = JSONObject().apply {
            put("emailHash", emailHash)
            if (name.isNotBlank()) put("displayName", ProfileCrypto.sealName(app, name))
            put("e2ePublicKey", publicKey)
            put("keyUpdatedAt", System.currentTimeMillis())
            if (remoteAvatarUrl != null) {
                put("avatarUrl", remoteAvatarUrl)
                put("avatarUpdatedAt", System.currentTimeMillis())
            }
            put("avatarColor", AvatarHelper.getMyColor(app))
            put("deviceId", DeviceId.get(app))
            put("authProof", authProof)
        }
        val result = ApiClient.post("/api/register", body) ?: return Result.Failed
        val token = result.optString("token")
        if (token.isEmpty()) {
            return if (result.optString("code") == "email_verification_required") Result.NeedsCode else Result.Failed
        }
        ApiClient.saveToken(app, token)
        if (remoteAvatarUrl != null) ContactCache.saveAvatarUrl(app, emailHash, remoteAvatarUrl)
        PrivacySettings.syncPrivacyWithServer(app)
        SetupActivity.markSetupDone(app, emailHash, name, email)
        AppExecutor.io {
            MessageRepository.sendKeyHintsBlocking(app, emailHash)
        }
        if (result.has("autoDeleteAfterSec") && !result.isNull("autoDeleteAfterSec")) {
            AutoDelete.adopt(app, result.optLong("autoDeleteAfterSec", 0L))
        }
        return Result.Ok
    }
}
