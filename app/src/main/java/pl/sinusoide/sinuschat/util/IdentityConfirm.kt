package pl.sinusoide.sinuschat.util

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import pl.sinusoide.sinuschat.LockManager
import pl.sinusoide.sinuschat.R
import pl.sinusoide.sinuschat.ScreenLockActivity

class IdentityConfirm(private val activity: FragmentActivity) {

    private var afterPin: (() -> Unit)? = null
    private var onPinCancel: (() -> Unit)? = null

    private val pinConfirm = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val ok = afterPin
        val cancel = onPinCancel
        afterPin = null
        onPinCancel = null
        if (result.resultCode == Activity.RESULT_OK && ok != null) ok() else cancel?.invoke()
    }

    fun confirm(title: String, subtitle: String, onOk: () -> Unit, onCancel: () -> Unit) {
        if (activity.isFinishing) return
        val ctx: Context = activity
        val appPin = LockManager.hasPin(ctx)
        val finger = BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
        val phoneSecure = (ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure
        val askAppPin = {
            afterPin = onOk
            onPinCancel = onCancel
            pinConfirm.launch(Intent(ctx, ScreenLockActivity::class.java)
                .putExtra(ScreenLockActivity.EXTRA_MODE, ScreenLockActivity.MODE_CONFIRM)
                .putExtra(ScreenLockActivity.EXTRA_TITLE, title))
        }
        when {
            finger || (!appPin && phoneSecure) -> {
                val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(ctx),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOk()
                        override fun onAuthenticationError(code: Int, msg: CharSequence) {
                            val cancelled = code == BiometricPrompt.ERROR_USER_CANCELED ||
                                code == BiometricPrompt.ERROR_CANCELED
                            if (appPin && !cancelled) askAppPin() else onCancel()
                        }
                    })
                val info = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                if (appPin) info.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                    .setNegativeButtonText(ctx.getString(R.string.backup_key_auth_pin))
                else info.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                prompt.authenticate(info.build())
            }
            appPin -> askAppPin()
            else -> onOk()
        }
    }
}
