package pl.sinusoide.sinuschat.messenger

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import pl.sinusoide.sinuschat.R
import pl.sinusoide.sinuschat.util.AppExecutor
import pl.sinusoide.sinuschat.util.AvatarHelper
import pl.sinusoide.sinuschat.util.PopupHelper
import androidx.activity.enableEdgeToEdge

class AddGroupMembersActivity : AppCompatActivity() {
    private val tc by lazy { pl.sinusoide.sinuschat.ui.ThemeColors(this) }
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(pl.sinusoide.sinuschat.util.LocaleManager.wrap(newBase))
    }


    private lateinit var groupId: String
    private val existing = HashSet<String>()
    private val selected = LinkedHashMap<String, String>()
    private val allContacts = ArrayList<Pair<String, String>>()
    private lateinit var resultsContainer: LinearLayout
    private lateinit var addButton: TextView
    private val dp get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        groupId = intent.getStringExtra("conversationId") ?: ""
        intent.getStringArrayListExtra("existing")?.let { existing.addAll(it) }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(tc.bg)
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())
            v.setPadding(0, b.top, 0, maxOf(b.bottom, ime.bottom)); insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(root)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding((8*dp).toInt(), (12*dp).toInt(), (12*dp).toInt(), (12*dp).toInt())
        }
        header.addView(android.widget.ImageButton(this).apply {
            setImageResource(R.drawable.ic_back); setBackgroundResource(0); setColorFilter(tc.text)
            setPadding((10*dp).toInt(), (10*dp).toInt(), (10*dp).toInt(), (10*dp).toInt())
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams((44*dp).toInt(), (44*dp).toInt()))
        header.addView(TextView(this).apply {
            text = getString(R.string.add_members); textSize = 19f; setTextColor(tc.text)
            setPadding((8*dp).toInt(), 0, 0, 0)
        })
        root.addView(header)

        val search = EditText(this).apply {
            hint = getString(R.string.search_name_email_hint); setHintTextColor(tc.text2)
            setTextColor(tc.text); textSize = 15f
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 22f * dp; setColor(tc.input)
            }
            setPadding((16*dp).toInt(), (12*dp).toInt(), (16*dp).toInt(), (12*dp).toInt())
            addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) {
                    val q = s?.toString()?.trim() ?: ""
                    currentQuery = q
                    searchHandler.removeCallbacksAndMessages(null)
                    if (q.isEmpty()) {
                        showContacts(allContacts)
                    } else {
                        showContacts(allContacts.filter { it.second.contains(q, ignoreCase = true) })
                        if (PeopleSearch.shouldQueryServer(q)) {
                            searchHandler.postDelayed({ searchUsers(q) }, 450)
                        }
                    }
                }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        root.addView(search, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins((12*dp).toInt(), 0, (12*dp).toInt(), (8*dp).toInt())
        })

        resultsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = android.widget.ScrollView(this).apply { addView(resultsContainer) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        addButton = TextView(this).apply {
            text = getString(R.string.add); textSize = 16f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 26f * dp; setColor(tc.accent)
            }
            setPadding(0, (14*dp).toInt(), 0, (14*dp).toInt())
            visibility = View.GONE
            setOnClickListener { addSelected() }
        }
        root.addView(addButton, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins((16*dp).toInt(), (8*dp).toInt(), (16*dp).toInt(), (16*dp).toInt())
        })

        setContentView(root)
        setupFlowGlass(root, header, search, scroll)
        loadPrivateContacts()
    }

    private fun setupFlowGlass(root: LinearLayout, header: LinearLayout, search: View, scroll: View) {
        if (!pl.sinusoide.sinuschat.util.ThemeManager.isFlow(this)) return
        val glass = pl.sinusoide.sinuschat.ui.FlowGlass
        header.setPadding((8*dp).toInt(), (6*dp).toInt(), (12*dp).toInt(), (6*dp).toInt())
        glass.bar(header, 28f)
        glass.bar(search, 24f, hMarginDp = 12f, vMarginDp = 4f)
        val L = pl.sinusoide.sinuschat.ui.FlowLens
        L.underBars(root, scroll, listOf(
            pl.sinusoide.sinuschat.ui.FlowLens.Lens(header, 28 * dp, magnify = 0.12f, blurDp = 0.8f),
            pl.sinusoide.sinuschat.ui.FlowLens.Lens(search, 24 * dp, magnify = 0.12f, blurDp = 0.8f),
            pl.sinusoide.sinuschat.ui.FlowLens.Lens(addButton, magnify = 0.12f, blurDp = 0.8f)))
    }

    private val searchHandler = android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile private var currentQuery = ""

    private fun loadPrivateContacts() {
        AppExecutor.io {
            val people = PeopleSearch.loadKnownContacts(this, existing.toSet())
            AppExecutor.main {
                allContacts.clear()
                allContacts.addAll(people.map { Pair(it.hash, it.name) })
                if (currentQuery.isEmpty()) showContacts(allContacts)
            }
        }
    }

    private fun showContacts(list: List<Pair<String, String>>) {
        resultsContainer.removeAllViews()
        if (list.isEmpty()) {
            resultsContainer.addView(TextView(this).apply {
                text = getString(R.string.no_contacts_search_email)
                setTextColor(tc.text2); textSize = 14f
                setPadding((16*dp).toInt(), (16*dp).toInt(), (16*dp).toInt(), (16*dp).toInt())
            })
            return
        }
        for ((h, n) in list) {
            val v = userRow(h, n); v.tag = Pair(h, n); resultsContainer.addView(v)
        }
    }

    private fun searchUsers(query: String) {
        AppExecutor.io {
            val results = PeopleSearch.search(this, query, existing.toSet())
            AppExecutor.main {
                if (query != currentQuery) return@main
                for (p in results) {
                    var exists = false
                    for (k in 0 until resultsContainer.childCount) {
                        if ((resultsContainer.getChildAt(k).tag as? Pair<*, *>)?.first == p.hash) { exists = true; break }
                    }
                    if (exists) continue
                    if (resultsContainer.childCount == 1 && resultsContainer.getChildAt(0).tag == null) {
                        resultsContainer.removeAllViews()
                    }
                    val v = userRow(p.hash, p.name); v.tag = Pair(p.hash, p.name); resultsContainer.addView(v)
                }
                if (results.isEmpty() && PeopleSearch.isEmail(query)) {
                    resultsContainer.removeAllViews()
                    resultsContainer.addView(TextView(this).apply {
                        text = getString(R.string.no_account_for_email, query.trim())
                        setTextColor(tc.text2); textSize = 14f
                        setPadding((16*dp).toInt(), (16*dp).toInt(), (16*dp).toInt(), (16*dp).toInt())
                    })
                }
            }
        }
    }

    private fun userRow(hash: String, name: String): View {
        val isSel = selected.containsKey(hash)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding((16*dp).toInt(), (12*dp).toInt(), (16*dp).toInt(), (12*dp).toInt())
            isClickable = true
            setOnClickListener { toggle(hash, name) }
        }
        row.addView(android.widget.ImageView(this).apply {
            setImageBitmap(AvatarHelper.createLetterBitmap(
                AvatarHelper.initialOf(name), AvatarHelper.colorForHash(hash), (44*dp).toInt()))
        }, LinearLayout.LayoutParams((44*dp).toInt(), (44*dp).toInt()).apply { marginEnd = (14*dp).toInt() })
        row.addView(TextView(this).apply {
            text = name; textSize = 16f; setTextColor(tc.text)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val check = android.widget.ImageView(this).apply {
            setImageResource(if (isSel) R.drawable.ic_check else 0)
            setColorFilter(Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                if (isSel) setColor(tc.accent)
                else { setColor(0x00000000); setStroke((2*dp).toInt(), tc.muted) }
            }
            setPadding((4*dp).toInt(), (4*dp).toInt(), (4*dp).toInt(), (4*dp).toInt())
        }
        pl.sinusoide.sinuschat.ui.FlowGlass.check(check, isSel, animate = hash == justToggled)
        row.addView(check, LinearLayout.LayoutParams((28*dp).toInt(), (28*dp).toInt()))
        return row
    }

    private var justToggled: String? = null

    private fun toggle(hash: String, name: String) {
        if (selected.containsKey(hash)) selected.remove(hash) else selected[hash] = name
        justToggled = hash
        addButton.text = if (selected.isEmpty()) getString(R.string.add) else getString(R.string.add_count_format, selected.size)
        addButton.visibility = if (selected.isEmpty()) View.GONE else View.VISIBLE
        val snap = ArrayList<Pair<String, String>>()
        for (i in 0 until resultsContainer.childCount) {
            (resultsContainer.getChildAt(i).tag as? Pair<*, *>)?.let { snap.add(Pair(it.first as String, it.second as String)) }
        }
        resultsContainer.removeAllViews()
        for ((h, n) in snap) { val v = userRow(h, n); v.tag = Pair(h, n); resultsContainer.addView(v) }
        justToggled = null
    }

    private fun addSelected() {
        if (selected.isEmpty()) return
        val hashes = selected.keys.toList()
        var done = 0; var ok = 0
        for (hash in hashes) {
            GroupRepository.addMember(this, groupId, hash) { success ->
                done++; if (success) ok++
                if (done == hashes.size) {
                    PopupHelper.showSuccess(this, getString(R.string.added_count_format, ok))
                    setResult(RESULT_OK); finish()
                }
            }
        }
    }
}
