package pl.sinusoide.sinuschat.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import org.json.JSONObject
import pl.sinusoide.sinuschat.R
import pl.sinusoide.sinuschat.ui.CodeInputView
import pl.sinusoide.sinuschat.ui.IconDrawView
import pl.sinusoide.sinuschat.util.PopupHelper

class VerifyCodeFragment : Fragment() {

    private lateinit var codeInput: CodeInputView
    private lateinit var btnVerify: Button
    private lateinit var btnResend: TextView
    private lateinit var progress: ProgressBar
    private lateinit var errorText: TextView
    private lateinit var infoText: TextView
    private var email: String = ""
    private var verifying = false

    private var verified = false

    private val ticket: String get() = arguments?.getString("ticket").orEmpty()

    private var rejected: String? = null

    private var clipStampAtEntry: Long? = null

    private var reverify: Boolean = false

    companion object {
        private val AUTH_URL get() = pl.sinusoide.sinuschat.AppConfig.AUTH_URL
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_setup_verify, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        email = arguments?.getString("email", "") ?: ""
        reverify = arguments?.getBoolean("reverify", false) ?: false

        codeInput = view.findViewById(R.id.code_input)
        btnVerify = view.findViewById(R.id.btn_verify)
        btnResend = view.findViewById(R.id.btn_resend)
        progress = view.findViewById(R.id.progress)
        errorText = view.findViewById(R.id.error_text)
        infoText = view.findViewById(R.id.verify_subtitle)

        view.findViewById<IconDrawView>(R.id.verify_icon)?.apply {
            val ink = IconDrawView.ink(requireContext())
            setColors(ink, ink)
            setDrawing(IconDrawView.pinSetup(requireContext()))
        }

        infoText.text = getString(R.string.code_sent_to, email)

        val edit = view.findViewById<View>(R.id.edit_email_row)
        if (reverify) {
            edit.visibility = View.GONE
        } else {
            view.findViewById<TextView>(R.id.btn_edit_email).setOnClickListener { editEmail() }
        }

        codeInput.onEdit = { if (errorText.visibility == View.VISIBLE) errorText.visibility = View.INVISIBLE }
        codeInput.onComplete = { verifyCode() }
        codeInput.onFullCode = { verifyCode(it) }

        btnVerify.setOnClickListener {
            if (codeInput.code.length < CodeInputView.LENGTH) {
                codeInput.shake()
                showError(getString(R.string.setup_code_incomplete))
                codeInput.focus()
            } else verifyCode()
        }
        btnResend.setOnClickListener { resendCode() }

        view.viewTreeObserver.addOnWindowFocusChangeListener(windowFocus)
        view.post { if (isAdded && view.hasWindowFocus()) checkClipboard() }

        if (reverify && savedInstanceState == null && email.isNotEmpty()) resendCode()

        listOf(view.findViewById<View>(R.id.verify_title), infoText, edit, codeInput, btnVerify, btnResend)
            .forEachIndexed { i, v ->
                v.alpha = 0f; v.translationY = 30f
                v.animate().alpha(1f).translationY(0f).setDuration(400).setStartDelay(100L + i * 80L).start()
            }
    }

    private val windowFocus = android.view.ViewTreeObserver.OnWindowFocusChangeListener { has ->
        if (has && isResumed) checkClipboard()
    }

    override fun onDestroyView() {
        view?.viewTreeObserver?.removeOnWindowFocusChangeListener(windowFocus)
        super.onDestroyView()
    }

    private fun checkClipboard() {
        if (!isAdded) return
        val cm = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as? android.content.ClipboardManager ?: return
        val stamp = try { cm.primaryClipDescription?.timestamp ?: 0L } catch (_: Throwable) { 0L }
        val entry = clipStampAtEntry
        if (entry == null) {
            clipStampAtEntry = stamp
            return
        }
        if (stamp == entry || codeInput.code.isNotEmpty() || verifying) return
        clipStampAtEntry = stamp
        val clip = try {
            cm.primaryClip?.getItemAt(0)?.coerceToText(requireContext())?.toString().orEmpty()
        } catch (_: Throwable) { "" }
        val code = Regex("""(?<!\d)\d{6}(?!\d)""").find(clip)?.value ?: return
        if (code == rejected) return
        codeInput.typeIn(code)
    }

    private fun editEmail() {
        val fm = parentFragmentManager
        if (fm.backStackEntryCount > 0) {
            fm.popBackStack()
        } else {
            (activity as? SetupActivity)?.navigateTo(EmailInputFragment().apply {
                arguments = Bundle().apply { putString("email", email) }
            }, addToBackStack = false)
        }
    }

    private fun showError(message: String) {
        errorText.text = message
        if (errorText.visibility != View.VISIBLE) {
            errorText.alpha = 0f
            errorText.visibility = View.VISIBLE
            errorText.animate().alpha(1f).setDuration(200).start()
        }
    }

    private fun requestVerify(code: String): JSONObject {
        val t = ticket
        if (t.isNotEmpty()) {
            try {
                val body = JSONObject().put("email", email).put("code", code).put("ticket", t).toString()
                val (status, text) = post("${pl.sinusoide.sinuschat.AppConfig.API_BASE}/api/auth/verify",
                    body, "application/json; charset=utf-8", 4_000, 6_000)
                if (status == 200) return JSONObject(text)
            } catch (_: Exception) { }
        }
        val form = "action=verify&email=${java.net.URLEncoder.encode(email, "UTF-8")}&code=$code" +
            (if (t.isNotEmpty()) "&ticket=${java.net.URLEncoder.encode(t, "UTF-8")}" else "")
        val (_, text) = post("$AUTH_URL?action=verify", form,
            "application/x-www-form-urlencoded", 15_000, 20_000)
        return JSONObject(text)
    }

    private fun post(url: String, body: String, type: String, connectMs: Int, readMs: Int): Pair<Int, String> {
        val ctx = context?.applicationContext ?: throw java.io.IOException("ekran zamknięty")
        val (status, bytes) = pl.sinusoide.sinuschat.network.ApiClient.rawPost(ctx, url, body.toByteArray(Charsets.UTF_8), type)
            ?: throw java.io.IOException("brak połączenia")
        return status to bytes.toString(Charsets.UTF_8)
    }

    private fun verifyCode(code: String = codeInput.code.trim()) {
        if (code.length != CodeInputView.LENGTH || verifying || verified) return

        verifying = true
        progress.visibility = View.VISIBLE
        btnVerify.isEnabled = false
        codeInput.setLocked(true)
        errorText.visibility = View.INVISIBLE

        Thread {
            try {
                val json = requestVerify(code)

                activity?.runOnUiThread { if (isAdded) codeInput.afterTyping {
                    if (!isAdded) return@afterTyping
                    progress.visibility = View.GONE
                    btnVerify.isEnabled = true
                    verifying = false

                    if (json.getBoolean("success")) {
                        verified = true
                        btnVerify.isEnabled = false
                        val proof = json.optString("authProof")
                        if (proof.isNotEmpty()) {
                            pl.sinusoide.sinuschat.network.ApiClient.saveAuthProof(
                                requireContext(), proof, json.optLong("expiresAt", 0L))
                        }
                        codeInput.showSuccess()
                        codeInput.postDelayed({ if (isAdded) afterVerified() }, 550)
                    } else {
                        rejected = code
                        codeInput.setLocked(false)
                        codeInput.showError()
                        showError(json.optString("error", getString(R.string.invalid_code)))
                        codeInput.postDelayed({ if (isAdded) codeInput.focus() }, 560)
                    }
                } }
            } catch (e: Exception) {
                activity?.runOnUiThread { if (isAdded) codeInput.afterTyping {
                    if (!isAdded) return@afterTyping
                    progress.visibility = View.GONE
                    btnVerify.isEnabled = true
                    verifying = false
                    codeInput.setLocked(false)
                    showError(getString(R.string.error_connection))
                } }
            }
        }.start()
    }

    private fun afterVerified() {
        if (reverify) {
            progress.visibility = View.VISIBLE
            pl.sinusoide.sinuschat.network.ApiClient.autoRegisterIfNeeded(requireContext()) { ok ->
                if (!isAdded) return@autoRegisterIfNeeded
                progress.visibility = View.GONE
                if (ok) {
                    (activity as? SetupActivity)?.goToMain()
                } else {
                    showError(getString(R.string.error_connection))
                }
            }
            return
        }
        val pending = SetupActivity.pendingRegistration(requireContext())
        if (pending != null) {
            progress.visibility = View.VISIBLE
            val app = requireContext().applicationContext
            pl.sinusoide.sinuschat.util.AppExecutor.io {
                val r = AccountRegistration.register(app, pending.email, pending.displayName)
                pl.sinusoide.sinuschat.util.AppExecutor.main {
                    if (!isAdded) return@main
                    progress.visibility = View.GONE
                    if (r is AccountRegistration.Result.Ok) {
                        SetupActivity.clearPendingRegistration(app)
                        pl.sinusoide.sinuschat.util.ProfileSync.pullMissing(app)
                        (activity as? SetupActivity)?.finishSetup()
                    } else showError(getString(R.string.error_server_connect))
                }
            }
            return
        }
        if (SetupActivity.restoreWanted(requireContext())) {
            (activity as? SetupActivity)?.navigateTo(SetupStepFragment.transferFor(email))
            return
        }
        val fragment = ProfileSetupFragment().apply {
            arguments = Bundle().apply { putString("email", email) }
        }
        (activity as? SetupActivity)?.navigateTo(fragment)
    }

    private fun resendCode() {
        Thread {
            try {
                val (_, text) = post("$AUTH_URL?action=send",
                    "action=send&email=${java.net.URLEncoder.encode(email, "UTF-8")}",
                    "application/x-www-form-urlencoded", 15_000, 20_000)
                val json = JSONObject(text)
                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    if (json.optBoolean("success")) {
                        json.optString("ticket").takeIf { it.isNotEmpty() }
                            ?.let { arguments?.putString("ticket", it) }
                        rejected = null
                        PopupHelper.showSuccess(requireActivity(), getString(R.string.code_resent))
                    } else {
                        showError(json.optString("error", getString(R.string.error_sending)))
                    }
                }
            } catch (_: Exception) {
                activity?.runOnUiThread { if (isAdded) showError(getString(R.string.error_connection)) }
            }
        }.start()
    }
}
