package pl.sinusoide.sinuschat.setup

import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import org.json.JSONObject
import pl.sinusoide.sinuschat.R
import pl.sinusoide.sinuschat.ui.IconDrawView
import pl.sinusoide.sinuschat.ui.ThemeUi
import pl.sinusoide.sinuschat.util.AppExecutor
import pl.sinusoide.sinuschat.util.BackupPrefs
import pl.sinusoide.sinuschat.util.RecoveryKey
import pl.sinusoide.sinuschat.util.RecoveryKit

class EmailInputFragment : Fragment() {

    private lateinit var inputEmail: EditText
    private lateinit var btnNext: Button
    private lateinit var progress: ProgressBar
    private lateinit var errorText: TextView
    private lateinit var titleView: TextView
    private lateinit var subtitleView: TextView
    private var icon: IconDrawView? = null

    companion object {
        private val AUTH_URL get() = pl.sinusoide.sinuschat.AppConfig.AUTH_URL
        private const val STATE_ANON = "anon"
        private const val STATE_ANON_KEY = "anon_key"
        private const val ICON_FULL_DP = 196
        private const val ICON_SMALL_DP = 112
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_setup_email, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        inputEmail = view.findViewById(R.id.input_email)
        btnNext = view.findViewById(R.id.btn_send_code)
        progress = view.findViewById(R.id.progress)
        errorText = view.findViewById(R.id.error_text)
        titleView = view.findViewById(R.id.email_title)
        subtitleView = view.findViewById(R.id.email_subtitle)

        arguments?.getString("email")?.takeIf { it.isNotEmpty() && inputEmail.text.isEmpty() }?.let {
            inputEmail.setText(it)
            inputEmail.setSelection(it.length)
        }

        icon = view.findViewById<IconDrawView>(R.id.email_icon)?.apply {
            val ink = IconDrawView.ink(requireContext())
            setColors(ink, ink)
            setDrawing(IconDrawView.mail(requireContext()))
        }

        inputEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (errorText.visibility == View.VISIBLE) clearInputError()
            }
        })

        btnNext.setOnClickListener { sendCode() }
        addAnonOption(view)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backToEmail)

        val wasAnon = savedInstanceState?.getBoolean(STATE_ANON) ?: anonMode
        val shownKey = savedInstanceState?.getString(STATE_ANON_KEY)?.let { RecoveryKey.fromStorage(it) } ?: anonKey
        anonMode = false
        anonKey = null
        keySaved = false
        wordsBox = null
        if (wasAnon && anonLink != null) {
            enterAnon(animate = false)
            shownKey?.let { showWords(it, animate = false) }
            return
        }

        listOf(titleView, subtitleView, inputEmail, btnNext).forEachIndexed { i, v ->
            v.alpha = 0f; v.translationY = 30f
            v.animate().alpha(1f).translationY(0f).setDuration(400).setStartDelay(100L + i * 80L).start()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_ANON, anonMode)
        anonKey?.let { outState.putString(STATE_ANON_KEY, it.toStorage()) }
    }

    override fun onDestroyView() {
        secure(false)
        super.onDestroyView()
    }

    private var anonLink: TextView? = null
    private var anonMode = false
    private var anonKey: RecoveryKey? = null
    private var keySaved = false
    private var wordsBox: ScrollView? = null

    private val backToEmail = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() { leaveAnon() }
    }

    private val savePdf = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (!isAdded) return@registerForActivityResult
        val key = anonKey ?: return@registerForActivityResult
        if (uri == null) {
            showNote(getString(R.string.anon_pdf_needed), error = true)
            return@registerForActivityResult
        }
        val app = requireContext().applicationContext
        setBusy(true, getString(R.string.anon_saving))
        AnonAccount.creating = true
        AppExecutor.io {
            val ok = try {
                app.contentResolver.openOutputStream(uri, "w")!!.use {
                    RecoveryKit.write(app, key, System.currentTimeMillis(), it)
                }
                true
            } catch (e: Throwable) {
                android.util.Log.e("EmailInput", "zapis PDF", e)
                false
            }
            val stored = ok && BackupPrefs.setRecoveryKey(app, key)
            AppExecutor.main {
                AnonAccount.creating = false
                if (!isAdded) return@main
                when {
                    !ok -> {
                        setBusy(false)
                        showNote(getString(R.string.backup_kit_pdf_failed), error = true)
                    }
                    !stored -> {
                        setBusy(false)
                        showNote(getString(R.string.backup_key_store_failed), error = true)
                    }
                    else -> {
                        keySaved = true
                        createAnon(key)
                    }
                }
            }
        }
    }

    private fun addAnonOption(view: View) {
        if (!arguments?.getString("email").isNullOrEmpty()) return
        if (SetupActivity.restoreWanted(requireContext())) return
        val root = view as? LinearLayout ?: return
        val d = resources.displayMetrics.density
        anonLink = TextView(requireContext()).apply {
            text = getString(R.string.anon_link)
            textSize = 14.5f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setTextColor(ThemeUi.accent(requireContext()))
            val p = (12 * d).toInt(); setPadding(p, p, p, p)
            setOnClickListener { if (anonMode) leaveAnon() else enterAnon() }
        }
        root.addView(anonLink, root.indexOfChild(progress) + 1,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = (4 * d).toInt()
                leftMargin = (28 * d).toInt()
                rightMargin = (28 * d).toInt()
            })
    }

    private fun enterAnon(animate: Boolean = true) {
        if (anonMode) return
        anonMode = true
        backToEmail.isEnabled = true
        clearInputError()
        icon?.strike(true, animate)
        swapText(titleView, getString(R.string.anon_title), animate)
        swapText(subtitleView, getString(R.string.anon_intro), animate)
        swapText(btnNext, getString(R.string.anon_generate), animate)
        swapText(anonLink, getString(R.string.anon_back), animate)
        fadeTo(inputEmail, View.INVISIBLE, animate)
        hideKeyboard()
        btnNext.setOnClickListener { showWords(BackupPrefs.recoveryKey(requireContext()) ?: RecoveryKey.generate()) }
    }

    private fun leaveAnon() {
        if (!anonMode || progress.visibility == View.VISIBLE) return
        anonMode = false
        backToEmail.isEnabled = false
        anonKey = null
        keySaved = false
        secure(false)
        icon?.strike(false)
        wordsBox?.let { box -> (box.parent as? ViewGroup)?.removeView(box) }
        wordsBox = null
        bottomSpacer()?.visibility = View.VISIBLE
        resizeIcon(ICON_FULL_DP)
        inputEmail.visibility = View.VISIBLE
        inputEmail.alpha = 0f
        inputEmail.animate().alpha(1f).setDuration(250).start()
        errorText.visibility = View.INVISIBLE
        swapText(titleView, getString(R.string.enter_email_title))
        swapText(subtitleView, getString(R.string.enter_email_subtitle))
        swapText(btnNext, getString(R.string.action_send_code))
        swapText(anonLink, getString(R.string.anon_link))
        btnNext.isEnabled = true
        btnNext.setOnClickListener { sendCode() }
    }

    private fun showWords(key: RecoveryKey, animate: Boolean = true) {
        val root = view as? LinearLayout ?: return
        val ctx = requireContext()
        val d = resources.displayMetrics.density
        anonKey = key
        keySaved = false
        secure(true)

        swapText(titleView, getString(R.string.anon_words_title), animate)
        swapText(subtitleView, getString(R.string.anon_words_sub), animate)
        swapText(btnNext, getString(R.string.anon_download), animate)
        inputEmail.visibility = View.GONE
        errorText.visibility = View.INVISIBLE
        bottomSpacer()?.visibility = View.GONE
        resizeIcon(ICON_SMALL_DP, animate)

        wordsBox?.let { (it.parent as? ViewGroup)?.removeView(it) }
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((28 * d).toInt(), 0, (28 * d).toInt(), (8 * d).toInt())
        }
        val grid = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val words = key.words(ctx)
        val chipColor = if (ThemeUi.isFlow(ctx)) 0x2E000000 else ThemeUi.card(ctx)
        val onChip = if (ThemeUi.isFlow(ctx)) android.graphics.Color.WHITE else ThemeUi.textPrimary(ctx)
        val sub = if (ThemeUi.isFlow(ctx)) 0xB3FFFFFF.toInt() else ThemeUi.textSecondary(ctx)
        for (col in 0 until 2) {
            val column = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            for (row in 0 until 6) {
                val i = col * 6 + row
                val chip = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = ThemeUi.rounded(ctx, chipColor, 12f)
                    setPadding((10 * d).toInt(), (9 * d).toInt(), (10 * d).toInt(), (9 * d).toInt())
                    addView(TextView(ctx).apply {
                        text = "${i + 1}"
                        textSize = 12f
                        setTextColor(sub)
                        gravity = Gravity.END
                    }, LinearLayout.LayoutParams((18 * d).toInt(), -2).apply { marginEnd = (8 * d).toInt() })
                    addView(TextView(ctx).apply {
                        text = words[i]
                        textSize = 16f
                        setTextColor(onChip)
                        typeface = Typeface.DEFAULT_BOLD
                    })
                }
                if (animate) {
                    chip.alpha = 0f; chip.translationY = 12 * d
                    chip.animate().alpha(1f).translationY(0f).setDuration(260)
                        .setStartDelay(150L + i * 45L).start()
                }
                column.addView(chip, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (7 * d).toInt() })
            }
            grid.addView(column, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (col == 0) marginEnd = (4 * d).toInt() else marginStart = (4 * d).toInt()
            })
        }
        content.addView(grid)
        content.addView(TextView(ctx).apply {
            text = getString(R.string.anon_words_note)
            textSize = 12.5f
            setTextColor(sub)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.2f)
            setPadding(0, (14 * d).toInt(), 0, 0)
        })
        wordsBox = ScrollView(ctx).apply {
            isVerticalScrollBarEnabled = false
            addView(content)
        }
        root.addView(wordsBox, root.indexOfChild(inputEmail),
            LinearLayout.LayoutParams(-1, 0, 1f))

        btnNext.isEnabled = true
        btnNext.setOnClickListener {
            if (keySaved) createAnon(key)
            else savePdf.launch(RecoveryKit.fileName(ctx, key))
        }
    }

    private fun createAnon(key: RecoveryKey) {
        val app = requireContext().applicationContext
        setBusy(true, getString(R.string.anon_creating))
        AppExecutor.io {
            var r = AnonAccount.create(app, key)
            if (r == AnonAccount.Result.Ok) pl.sinusoide.sinuschat.crypto.ProfileCrypto.useAnonKey(app, key)
            if (r != AnonAccount.Result.Ok) {
                val l = AnonAccount.login(app, key)
                if (l == AnonAccount.Result.Ok) {
                    r = l
                    pl.sinusoide.sinuschat.crypto.ProfileCrypto.restoreForAnon(app, key)
                }
            }
            AppExecutor.main {
                if (!isAdded) return@main
                setBusy(false)
                if (r == AnonAccount.Result.Ok) {
                    errorText.visibility = View.INVISIBLE
                    secure(false)
                    (activity as? SetupActivity)?.navigateTo(ProfileSetupFragment().apply {
                        arguments = Bundle().apply { putString("email", AnonAccount.idFor(key)) }
                    })
                } else {
                    showNote(getString(R.string.anon_failed), error = true)
                    if (keySaved) btnNext.text = getString(R.string.anon_retry)
                }
            }
        }
    }

    private fun setBusy(busy: Boolean, message: String? = null) {
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        btnNext.isEnabled = !busy
        anonLink?.isEnabled = !busy
        if (busy && message != null) showNote(message, error = false)
    }

    private fun showNote(message: String, error: Boolean) {
        errorText.text = message
        errorText.setTextColor(if (error) requireContext().getColor(R.color.sinuschat_error)
                               else ThemeUi.textSecondary(requireContext()))
        errorText.visibility = View.VISIBLE
    }

    private fun swapText(v: TextView?, text: String, animate: Boolean = true) {
        v ?: return
        if (!animate) { v.text = text; return }
        v.animate().cancel()
        v.animate().alpha(0f).setDuration(120).withEndAction {
            v.text = text
            v.animate().alpha(1f).setDuration(200).start()
        }.start()
    }

    private fun fadeTo(v: View, visibility: Int, animate: Boolean) {
        if (!animate) { v.visibility = visibility; return }
        v.animate().alpha(0f).setDuration(180).withEndAction {
            v.visibility = visibility
            v.alpha = 1f
        }.start()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun resizeIcon(dp: Int, animate: Boolean = true) {
    }

    private fun bottomSpacer(): View? {
        val root = view as? LinearLayout ?: return null
        return root.getChildAt(root.indexOfChild(btnNext) - 1)
            ?.takeIf { it !is TextView && it !is Button && it !is ScrollView && it !is ProgressBar }
    }

    private fun secure(on: Boolean) {
        val w = activity?.window ?: return
        if (on) w.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else w.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(android.view.inputmethod.InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(inputEmail.windowToken, 0)
        inputEmail.clearFocus()
    }

    private fun showInputError(message: String) {
        val ctx = requireContext()
        val d = resources.displayMetrics.density
        errorText.text = message
        errorText.setTextColor(ctx.getColor(R.color.sinuschat_error))
        if (errorText.visibility != View.VISIBLE) {
            errorText.alpha = 0f
            errorText.visibility = View.VISIBLE
            errorText.animate().alpha(1f).setDuration(200).start()
        }
        (inputEmail.background?.mutate() as? android.graphics.drawable.GradientDrawable)
            ?.setStroke((1.5f * d).toInt(), ctx.getColor(R.color.sinuschat_error))

        inputEmail.animate().cancel()
        inputEmail.alpha = 1f
        inputEmail.translationY = 0f
        android.animation.ObjectAnimator.ofFloat(inputEmail, View.TRANSLATION_X,
            0f, -14f * d, 12f * d, -9f * d, 6f * d, -3f * d, 0f).apply {
            duration = 420
            start()
        }
        inputEmail.performHapticFeedback(
            if (android.os.Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.REJECT
            else android.view.HapticFeedbackConstants.LONG_PRESS)
    }

    private fun clearInputError() {
        errorText.visibility = View.INVISIBLE
        (inputEmail.background?.mutate() as? android.graphics.drawable.GradientDrawable)
            ?.setStroke(resources.displayMetrics.density.toInt().coerceAtLeast(1),
                ThemeUi.border(requireContext()))
    }

    private fun sendCode() {
        val email = inputEmail.text.toString().trim().lowercase()
        if (email.isEmpty()) {
            showInputError(getString(R.string.setup_email_required))
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showInputError(getString(R.string.invalid_email))
            return
        }

        progress.visibility = View.VISIBLE
        btnNext.isEnabled = false
        errorText.visibility = View.INVISIBLE

        val app = requireContext().applicationContext
        Thread {
            try {
                val response = pl.sinusoide.sinuschat.network.ApiClient.rawPost(app, "$AUTH_URL?action=send",
                    "action=send&email=${java.net.URLEncoder.encode(email, "UTF-8")}".toByteArray(),
                    "application/x-www-form-urlencoded")
                    ?.second?.toString(Charsets.UTF_8) ?: throw java.io.IOException("brak połączenia")
                val json = JSONObject(response)

                requireActivity().runOnUiThread {
                    progress.visibility = View.GONE
                    btnNext.isEnabled = true

                    if (json.getBoolean("success")) {
                        val fragment = VerifyCodeFragment().apply {
                            arguments = Bundle().apply {
                                putString("email", email)
                                putString("ticket", json.optString("ticket"))
                            }
                        }
                        (activity as? SetupActivity)?.navigateTo(fragment)
                    } else {
                        showNote(json.optString("error", getString(R.string.error_sending)), error = true)
                    }
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    progress.visibility = View.GONE
                    btnNext.isEnabled = true
                    showNote(getString(R.string.error_connection_reason, e.message ?: ""), error = true)
                }
            }
        }.start()
    }
}
