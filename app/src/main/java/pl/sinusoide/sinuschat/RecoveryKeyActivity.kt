package pl.sinusoide.sinuschat

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import pl.sinusoide.sinuschat.messenger.QrScanActivity
import pl.sinusoide.sinuschat.messenger.StyledQr
import pl.sinusoide.sinuschat.ui.FlowGlass
import pl.sinusoide.sinuschat.ui.ThemeUi
import pl.sinusoide.sinuschat.util.AppExecutor
import pl.sinusoide.sinuschat.util.BackupPrefs
import pl.sinusoide.sinuschat.util.LocaleManager
import pl.sinusoide.sinuschat.util.PopupHelper
import pl.sinusoide.sinuschat.util.RecoveryKey
import pl.sinusoide.sinuschat.util.RecoveryKit
import pl.sinusoide.sinuschat.util.ThemeManager
import androidx.activity.enableEdgeToEdge

class RecoveryKeyActivity : AppCompatActivity() {

    private val dp get() = resources.displayMetrics.density
    private lateinit var content: LinearLayout

    private var current: RecoveryKey? = null
    private var pending: RecoveryKey? = null
    private val shown get() = pending ?: current

    private val fields = ArrayList<AutoCompleteTextView>()
    private var filling = false

    private val savePdf = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val key = shown
        if (uri == null || key == null) return@registerForActivityResult
        val fresh = pending != null
        val created = if (fresh) System.currentTimeMillis() else BackupPrefs.recoveryKeyCreatedMs(this)
        AppExecutor.io {
            val ok = try {
                contentResolver.openOutputStream(uri, "w")!!.use { RecoveryKit.write(this, key, created, it) }
                true
            } catch (e: Throwable) {
                Log.e(TAG, "zapis PDF", e)
                false
            }
            AppExecutor.main {
                when {
                    !ok -> PopupHelper.showError(this, getString(R.string.backup_kit_pdf_failed))
                    fresh -> commit()
                    else -> PopupHelper.showSuccess(this, getString(R.string.backup_kit_pdf_saved))
                }
            }
        }
    }

    private val scanQr = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = result.data?.getStringExtra("qr_result") ?: return@registerForActivityResult
        val key = RecoveryKey.fromQr(this, text)
        if (key == null) {
            if (isAnon) failAnon(FAIL_BAD_QR)
            else PopupHelper.showError(this, getString(R.string.backup_enter_bad_qr))
        } else deliver(key)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val enter = intent.getIntExtra(EXTRA_MODE, MODE_KIT) == MODE_ENTER
        val flow = ThemeManager.isFlow(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(if (flow) Color.TRANSPARENT else ThemeUi.backgroundSolid(this@RecoveryKeyActivity))
        }
        val unlock = intent.getBooleanExtra(EXTRA_UNLOCK, false)
        root.addView(topBar(getString(when {
            unlock -> R.string.lock_forgot
            enter && intent.getBooleanExtra(EXTRA_ANON, false) -> R.string.anon_restore_title
            enter -> R.string.backup_enter_title
            else -> R.string.backup_key_title
        })))
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (4 * dp).toInt(), (16 * dp).toInt(), (28 * dp).toInt())
        }
        root.addView(ScrollView(this).apply { addView(content) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        if (enter) {
            buildEnter()
            return
        }
        current = BackupPrefs.recoveryKey(this)
        pending = savedInstanceState?.getString(STATE_PENDING)?.let { RecoveryKey.fromStorage(it) }
        when {
            current == null && pending == null -> {
                pending = RecoveryKey.generate()
                buildKit()
                askToSave()
            }
            pending != null -> buildKit()
            else -> {
                content.addView(lead(getString(R.string.backup_key_auth_sub)))
                content.post { confirmIdentity { buildKit() } }
            }
        }
    }

    private val identity = pl.sinusoide.sinuschat.util.IdentityConfirm(this)

    private fun confirmIdentity(onOk: () -> Unit) =
        identity.confirm(getString(R.string.backup_key_auth_title), getString(R.string.backup_key_auth_sub),
            onOk = onOk, onCancel = { finish() })

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pending?.let { outState.putString(STATE_PENDING, it.toStorage()) }
    }

    private fun buildKit() {
        content.removeAllViews()
        val key = shown ?: return
        val fresh = pending != null

        content.addView(lead(getString(if (fresh) R.string.backup_key_sub_new else R.string.backup_key_sub_show)))

        val qrCard = card().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((16 * dp).toInt(), (20 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())
        }
        val qrPx = (196 * dp).toInt()
        qrCard.addView(ImageView(this).apply {
            setImageBitmap(StyledQr.generate(key.qrPayload(), qrPx))
            background = ThemeUi.rounded(context, Color.WHITE, 18f)
            val p = (12 * dp).toInt(); setPadding(p, p, p, p)
        }, LinearLayout.LayoutParams(qrPx + (24 * dp).toInt(), qrPx + (24 * dp).toInt()))
        qrCard.addView(TextView(this).apply {
            text = getString(R.string.backup_kit_id, key.shortId)
            textSize = 13f
            setTextColor(subColor())
            gravity = Gravity.CENTER
            setPadding(0, (12 * dp).toInt(), 0, 0)
        })
        content.addView(qrCard)

        content.addView(sectionTitle(getString(R.string.backup_kit_words_label)))
        content.addView(wordsGrid(key.words(this)))

        content.addView(bigButton(getString(if (fresh) R.string.backup_key_save_pdf else R.string.backup_key_save_again)) {
            askToSave()
        }.apply {
            (layoutParams as LinearLayout.LayoutParams).topMargin = (22 * dp).toInt()
        })
        if (!fresh && pl.sinusoide.sinuschat.setup.AnonAccount.isCurrent(this)) {
            content.addView(note(getString(R.string.anon_key_fixed)))
        } else content.addView(link(getString(if (fresh) R.string.backup_key_written else R.string.backup_key_new)) {
            if (fresh) commit() else confirmNewKey()
        })
        content.addView(note(getString(R.string.backup_key_warning)))
    }

    private fun wordsGrid(words: List<String>): View {
        val grid = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (col in 0 until 2) {
            val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            for (row in 0 until 6) {
                val i = col * 6 + row
                column.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = chip()
                    setPadding((10 * dp).toInt(), (11 * dp).toInt(), (10 * dp).toInt(), (11 * dp).toInt())
                    addView(number(i))
                    addView(TextView(this@RecoveryKeyActivity).apply {
                        text = words[i]
                        textSize = 17f
                        setTextColor(onCard())
                        typeface = Typeface.DEFAULT_BOLD
                    })
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (8 * dp).toInt() })
            }
            grid.addView(column, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (col == 0) marginEnd = (4 * dp).toInt() else marginStart = (4 * dp).toInt()
            })
        }
        return grid
    }

    private fun askToSave() {
        val key = shown ?: return
        content.postDelayed({
            if (!isFinishing) savePdf.launch(RecoveryKit.fileName(this, key))
        }, if (pending != null && content.tag == null) 650L else 0L)
        content.tag = true
    }

    private fun commit() {
        val key = pending ?: return
        if (!BackupPrefs.setRecoveryKey(this, key)) {
            PopupHelper.showError(this, getString(R.string.backup_key_store_failed))
            return
        }
        current = key
        pending = null
        setResult(RESULT_OK)
        finish()
    }

    private fun confirmNewKey() {
        val sheet = BottomSheetDialog(this, R.style.Theme_SinusChat_BottomSheet)
        val root = sheetRoot(R.drawable.ic_key, getString(R.string.backup_key_new_title),
            getString(R.string.backup_key_new_desc))
        root.addView(bigButton(getString(R.string.backup_key_new_go)) {
            sheet.dismiss()
            pending = RecoveryKey.generate()
            content.tag = null
            buildKit()
            askToSave()
        })
        root.addView(link(getString(R.string.cancel)) { sheet.dismiss() })
        sheet.setContentView(root)
        FlowGlass.show(sheet)
        sheet.show()
    }

    private fun buildEnter() {
        val unlock = intent.getBooleanExtra(EXTRA_UNLOCK, false)
        if (intent.getBooleanExtra(EXTRA_ANON, false)) {
            val ink = if (ThemeManager.isFlow(this)) Color.WHITE else ThemeUi.textPrimary(this)
            val icon = pl.sinusoide.sinuschat.ui.IconDrawView(this).apply {
                setColors(ink, ink)
                setDrawing(pl.sinusoide.sinuschat.ui.IconDrawView.mail(this@RecoveryKeyActivity))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            content.addView(icon, LinearLayout.LayoutParams(-1, (150 * dp).toInt()))
            icon.postDelayed({ if (!isFinishing) icon.strike(true) }, 1500L)
        }
        content.addView(lead(getString(if (unlock) R.string.lock_forgot_sub else R.string.backup_enter_sub)))
        intent.getByteArrayExtra(EXTRA_EXPECTED_ID)?.let {
            content.addView(note(getString(if (unlock) R.string.lock_forgot_expected else R.string.backup_enter_expected,
                RecoveryKey.formatId(it))).apply {
                setPadding(0, 0, 0, (6 * dp).toInt())
            })
        }

        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = ThemeUi.rounded(context, (ThemeUi.accent(context) and 0x00FFFFFF) or 0x26000000, 26f)
            setPadding(0, (13 * dp).toInt(), 0, (13 * dp).toInt())
            isClickable = true
            setOnClickListener {
                scanQr.launch(Intent(this@RecoveryKeyActivity, QrScanActivity::class.java)
                    .putExtra(EXTRA_UNLOCK, intent.getBooleanExtra(EXTRA_UNLOCK, false)))
            }
            addView(ImageView(this@RecoveryKeyActivity).apply {
                setImageResource(R.drawable.ic_qr_code)
                setColorFilter(ThemeUi.accent(context))
            }, LinearLayout.LayoutParams((22 * dp).toInt(), (22 * dp).toInt()).apply { marginEnd = (10 * dp).toInt() })
            addView(TextView(this@RecoveryKeyActivity).apply {
                text = getString(R.string.backup_enter_scan)
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ThemeUi.accent(context))
            })
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (8 * dp).toInt() })

        content.addView(sectionTitle(getString(R.string.backup_enter_or)))

        val grid = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val columns = List(2) { LinearLayout(this).apply { orientation = LinearLayout.VERTICAL } }
        val words = RecoveryKey.wordList(this)
        for (i in 0 until RecoveryKey.WORDS) {
            val field = wordField(i, words)
            fields.add(field)
            columns[i / 6].addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = chip()
                setPadding((10 * dp).toInt(), (2 * dp).toInt(), (6 * dp).toInt(), (2 * dp).toInt())
                addView(number(i))
                addView(field, LinearLayout.LayoutParams(0, -2, 1f))
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (8 * dp).toInt() })
        }
        for ((col, column) in columns.withIndex())
            grid.addView(column, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (col == 0) marginEnd = (4 * dp).toInt() else marginStart = (4 * dp).toInt()
            })
        content.addView(grid)
        intent.getStringArrayExtra(EXTRA_PREFILL)?.takeIf { it.size == fields.size }?.let { pre ->
            filling = true
            pre.forEachIndexed { i, w -> fields[i].setText(w, false) }
            filling = false
        }

        content.addView(bigButton(getString(R.string.backup_enter_go)) { submit() }.apply {
            (layoutParams as LinearLayout.LayoutParams).topMargin = (22 * dp).toInt()
        })
    }

    private fun wordField(i: Int, words: List<String>) = AutoCompleteTextView(this).apply {
        setAdapter(WordAdapter(words))
        threshold = 1
        textSize = 16.5f
        setTextColor(onCard())
        typeface = Typeface.DEFAULT_BOLD
        background = null
        setPadding((4 * dp).toInt(), (10 * dp).toInt(), (4 * dp).toInt(), (10 * dp).toInt())
        isSingleLine = true
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or
            (if (i == RecoveryKey.WORDS - 1) EditorInfo.IME_ACTION_DONE else EditorInfo.IME_ACTION_NEXT)
        setDropDownBackgroundDrawable(ThemeUi.rounded(context, ThemeUi.backgroundSolid(context), 14f))
        setOnEditorActionListener { _, action, _ ->
            when (action) {
                EditorInfo.IME_ACTION_NEXT -> { advance(i); true }
                EditorInfo.IME_ACTION_DONE -> { submit(); true }
                else -> false
            }
        }
        setOnItemClickListener { _, _, _, _ -> advance(i) }
        setOnFocusChangeListener { _, has -> if (!has) tidy(i) }
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (filling) return
                setTextColor(onCard())
                val tokens = RecoveryKey.tokens(s?.toString().orEmpty())
                if (tokens.size > 1) spread(i, tokens)
                else if (tokens.size == 1 && words.count { it.startsWith(tokens[0]) } == 1 &&
                    words.binarySearch(tokens[0]) >= 0) post { advance(i) }
            }
        })
    }

    private fun spread(from: Int, tokens: List<String>) {
        filling = true
        for ((k, t) in tokens.withIndex()) {
            val f = fields.getOrNull(from + k) ?: break
            f.setText(t, false)
            f.setSelection(t.length)
            f.dismissDropDown()
        }
        filling = false
        val next = fields.indexOfFirst { it.text.isBlank() }
        (if (next >= 0) fields[next] else fields.last()).requestFocus()
    }

    private fun advance(i: Int) {
        val f = fields.getOrNull(i) ?: return
        if (!f.hasFocus()) return
        tidy(i)
        fields.getOrNull(i + 1)?.requestFocus()
    }

    private fun tidy(i: Int) {
        val f = fields.getOrNull(i) ?: return
        val typed = f.text.toString()
        if (typed.isBlank()) return
        val word = RecoveryKey.resolve(this, typed)
        filling = true
        if (word != null && word != typed) f.setText(word, false)
        filling = false
        f.dismissDropDown()
        f.setTextColor(if (word == null) ContextCompat.getColor(this, R.color.sinuschat_error) else onCard())
    }

    private fun submit() {
        for (i in fields.indices) tidy(i)
        val typed = fields.map { it.text.toString() }
        val bad = typed.indexOfFirst { RecoveryKey.resolve(this, it) == null }
        if (bad >= 0) {
            PopupHelper.showError(this, getString(
                if (typed[bad].isBlank()) R.string.backup_enter_missing else R.string.backup_enter_unknown, bad + 1))
            fields[bad].requestFocus()
            return
        }
        val key = RecoveryKey.fromWords(this, typed)
        if (key == null) {
            if (isAnon) failAnon(FAIL_BAD_WORDS, fields.map { it.text.toString() })
            else PopupHelper.showError(this, getString(R.string.backup_enter_bad))
            return
        }
        deliver(key, byWords = true)
    }

    private val isAnon get() = intent.getBooleanExtra(EXTRA_ANON, false)

    private fun failAnon(reason: String, words: List<String>? = null) {
        setResult(RESULT_CANCELED, Intent().putExtra(EXTRA_FAIL, reason)
            .apply { words?.let { putExtra(EXTRA_WORDS, it.toTypedArray()) } })
        finish()
    }

    private fun deliver(key: RecoveryKey, byWords: Boolean = false) {
        val expected = intent.getByteArrayExtra(EXTRA_EXPECTED_ID)
        if (expected != null && !key.matches(expected)) {
            PopupHelper.showError(this, getString(
                if (intent.getBooleanExtra(EXTRA_UNLOCK, false)) R.string.lock_forgot_wrong_key
                else R.string.backup_enter_wrong_key,
                key.shortId, RecoveryKey.formatId(expected)))
            return
        }
        setResult(RESULT_OK, Intent().putExtra(EXTRA_KEY, key.toStorage()).putExtra(EXTRA_BY_WORDS, byWords))
        finish()
    }

    private inner class WordAdapter(words: List<String>) :
        ArrayAdapter<String>(this@RecoveryKeyActivity, 0, words) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            ((convertView as? TextView) ?: TextView(context).apply {
                textSize = 16f
                setTextColor(ThemeUi.textPrimary(context))
                setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
            }).apply { text = getItem(position) }
    }

    private fun onCard(): Int =
        if (ThemeManager.isFlow(this)) Color.WHITE else ThemeUi.textPrimary(this)

    private fun subColor(): Int =
        if (ThemeManager.isFlow(this)) 0xB3FFFFFF.toInt() else ThemeUi.textSecondary(this)

    private fun chip() = ThemeUi.rounded(this,
        if (ThemeManager.isFlow(this)) 0x2E000000 else ThemeUi.card(this), 14f)

    private fun number(i: Int) = TextView(this).apply {
        text = "${i + 1}"
        textSize = 13f
        setTextColor(subColor())
        gravity = Gravity.END
        layoutParams = LinearLayout.LayoutParams((20 * dp).toInt(), -2).apply { marginEnd = (10 * dp).toInt() }
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (14 * dp).toInt() }
        background = if (ThemeManager.isFlow(this@RecoveryKeyActivity))
            pl.sinusoide.sinuschat.ui.GlassDrawable(dp, null, false, 20 * dp, null)
        else GradientDrawable().apply {
            cornerRadius = 20 * dp
            setColor(ThemeUi.card(this@RecoveryKeyActivity))
            setStroke(dp.toInt().coerceAtLeast(1), ThemeUi.border(this@RecoveryKeyActivity))
        }
    }

    private fun lead(text: String) = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(subColor())
        gravity = Gravity.CENTER
        setLineSpacing(0f, 1.25f)
        setPadding((8 * dp).toInt(), (6 * dp).toInt(), (8 * dp).toInt(), (6 * dp).toInt())
    }

    private fun note(text: String) = TextView(this).apply {
        this.text = text
        textSize = 12.5f
        setTextColor(if (ThemeManager.isFlow(this@RecoveryKeyActivity)) 0xAAFFFFFF.toInt() else ThemeUi.textSecondary(context))
        gravity = Gravity.CENTER
        setLineSpacing(0f, 1.2f)
        setPadding((10 * dp).toInt(), (16 * dp).toInt(), (10 * dp).toInt(), 0)
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text.uppercase()
        textSize = 12f
        setTextColor(if (ThemeManager.isFlow(this@RecoveryKeyActivity)) 0xCCFFFFFF.toInt() else ThemeUi.accent(context))
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.06f
        setPadding((6 * dp).toInt(), (22 * dp).toInt(), 0, 0)
    }

    private fun bigButton(text: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        textSize = 16f
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        background = ThemeUi.rounded(context, ThemeUi.accent(context), 26f)
        setPadding(0, (14 * dp).toInt(), 0, (14 * dp).toInt())
        isClickable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    private fun link(text: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        textSize = 14.5f
        setTextColor(ThemeUi.accent(context))
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setPadding(0, (16 * dp).toInt(), 0, (6 * dp).toInt())
        isClickable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    private fun sheetRoot(icon: Int, title: String, desc: String): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((24 * dp).toInt(), (12 * dp).toInt(), (24 * dp).toInt(), (20 * dp).toInt())
        }
        root.addView(View(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = 3 * dp; setColor(ThemeUi.handle(context))
            }
        }, LinearLayout.LayoutParams((36 * dp).toInt(), (4 * dp).toInt())
            .apply { bottomMargin = (20 * dp).toInt() })
        root.addView(ImageView(this).apply {
            setImageResource(icon)
            setColorFilter(ThemeUi.accent(context))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor((ThemeUi.accent(context) and 0x00FFFFFF) or 0x26000000)
            }
            val p = (16 * dp).toInt(); setPadding(p, p, p, p)
        }, LinearLayout.LayoutParams((64 * dp).toInt(), (64 * dp).toInt())
            .apply { bottomMargin = (16 * dp).toInt() })
        root.addView(TextView(this).apply {
            this.text = title; textSize = 20f
            setTextColor(ThemeUi.textPrimary(context))
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            this.text = desc; textSize = 14f
            setTextColor(ThemeUi.textSecondary(context))
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.2f)
            setPadding(0, (8 * dp).toInt(), 0, (16 * dp).toInt())
        })
        return root
    }

    private fun topBar(title: String): View {
        val onBar = if (ThemeManager.isFlow(this)) Color.WHITE else ThemeUi.textPrimary(this)
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        bar.addView(android.widget.ImageButton(this).apply {
            setImageResource(R.drawable.ic_back)
            background = null
            imageTintList = android.content.res.ColorStateList.valueOf(onBar)
            setPadding((9 * dp).toInt(), (9 * dp).toInt(), (9 * dp).toInt(), (9 * dp).toInt())
            contentDescription = getString(R.string.action_back)
            layoutParams = LinearLayout.LayoutParams((42 * dp).toInt(), (42 * dp).toInt())
            setOnClickListener { finish() }
        })
        bar.addView(TextView(this).apply {
            text = title
            setTextColor(onBar); textSize = 17f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                .apply { marginStart = (6 * dp).toInt() }
        })
        FlowGlass.bar(bar, 22f)
        return bar
    }

    companion object {
        private const val TAG = "RecoveryKey"
        private const val STATE_PENDING = "pending"

        const val EXTRA_MODE = "mode"
        const val MODE_KIT = 0
        const val MODE_ENTER = 1
        const val EXTRA_EXPECTED_ID = "expected_id"
        const val EXTRA_KEY = "key"
        const val EXTRA_UNLOCK = "unlock_app"

        fun kitIntent(ctx: Context): Intent =
            Intent(ctx, RecoveryKeyActivity::class.java).putExtra(EXTRA_MODE, MODE_KIT)

        fun enterIntent(ctx: Context, expectedId: ByteArray?): Intent =
            Intent(ctx, RecoveryKeyActivity::class.java)
                .putExtra(EXTRA_MODE, MODE_ENTER)
                .apply { if (expectedId != null) putExtra(EXTRA_EXPECTED_ID, expectedId) }

        const val EXTRA_ANON = "anon_login"

        fun anonLoginIntent(ctx: Context, prefill: List<String>? = null): Intent =
            enterIntent(ctx, null).putExtra(EXTRA_ANON, true)
                .apply { prefill?.let { putExtra(EXTRA_PREFILL, it.toTypedArray()) } }

        const val FAIL_BAD_QR = "bad_qr"
        const val FAIL_BAD_WORDS = "bad_words"
        private const val EXTRA_FAIL = "anon_fail"
        private const val EXTRA_WORDS = "anon_words"
        private const val EXTRA_PREFILL = "anon_prefill"
        private const val EXTRA_BY_WORDS = "by_words"

        fun failureFrom(data: Intent?): String? = data?.getStringExtra(EXTRA_FAIL)
        fun wordsFrom(data: Intent?): List<String>? = data?.getStringArrayExtra(EXTRA_WORDS)?.toList()
        fun enteredByWords(data: Intent?): Boolean = data?.getBooleanExtra(EXTRA_BY_WORDS, false) == true

        fun unlockIntent(ctx: Context, expectedId: ByteArray?): Intent =
            enterIntent(ctx, expectedId).putExtra(EXTRA_UNLOCK, true)

        fun keyFrom(data: Intent?): RecoveryKey? =
            data?.getStringExtra(EXTRA_KEY)?.let { RecoveryKey.fromStorage(it) }
    }
}
