package pl.sinusoide.sinuschat

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import pl.sinusoide.sinuschat.ui.ConfirmSheet
import pl.sinusoide.sinuschat.util.AppExecutor
import pl.sinusoide.sinuschat.util.PopupHelper
import pl.sinusoide.sinuschat.util.StorageUsage
import pl.sinusoide.sinuschat.util.ThemeManager
import java.io.File

class AiModelsActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(pl.sinusoide.sinuschat.util.LocaleManager.wrap(newBase))
    }

    private val dp get() = resources.displayMetrics.density

    private var cBg = 0; private var cSurface = 0; private var cCard = 0
    private var cPrimary = 0; private var cSecondary = 0; private var cAccent = 0
    private var cGreen = 0; private var cRed = 0; private var cPurple = 0; private var cOrange = 0

    private lateinit var listCard: LinearLayout

    private fun attr(id: Int, fallback: Int): Int = pl.sinusoide.sinuschat.ui.ThemeUi.attrOrNull(this, id) ?: fallback

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        cBg = attr(R.attr.sinuschat_background, 0xFF0E1621.toInt())
        cSurface = attr(R.attr.sinuschat_surface, 0xFF17212B.toInt())
        cCard = attr(R.attr.sinuschat_surface_elevated, 0xFF1D2A38.toInt())
        cPrimary = attr(R.attr.sinuschat_text_primary, Color.WHITE)
        cSecondary = attr(R.attr.sinuschat_text_secondary, 0xFF7F91A4.toInt())
        cAccent = pl.sinusoide.sinuschat.ui.ThemeUi.accent(this)
        cGreen = ContextCompat.getColor(this, R.color.sinuschat_success)
        cRed = ContextCompat.getColor(this, R.color.sinuschat_error)
        cPurple = 0xFF9B6BE0.toInt()
        cOrange = 0xFFE67E22.toInt()

        buildUi()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun buildUi() {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(cBg)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = (56 * dp).toInt()
            setBackgroundColor(cSurface)
            elevation = 2 * dp
        }
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_back); setColorFilter(cPrimary)
            val p = (10 * dp).toInt(); setPadding(p, p, p, p)
            background = ContextCompat.getDrawable(this@AiModelsActivity, R.drawable.bg_menu_ripple)
            contentDescription = getString(R.string.back)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt()))
        header.addView(TextView(this).apply {
            text = getString(R.string.ai_models_title); textSize = 20f
            setTextColor(cPrimary); typeface = Typeface.DEFAULT_BOLD
            setPadding((8 * dp).toInt(), 0, 0, 0)
        })
        outer.addView(header)
        ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding((12 * dp).toInt(), bars.top, (12 * dp).toInt(), 0)
            insets
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt(), (28 * dp).toInt())
        }
        val scroll = ScrollView(this).apply { addView(content) }
        outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            v.setPadding(0, 0, 0, insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom); insets
        }

        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = rounded(cCard, 20f)
            setPadding((20 * dp).toInt(), (22 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt())
            addView(ImageView(this@AiModelsActivity).apply {
                setImageResource(R.drawable.ic_sinia)
                setColorFilter(cPurple)
                val p = (16 * dp).toInt(); setPadding(p, p, p, p)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor((cPurple and 0x00FFFFFF) or 0x26000000)
                }
            }, LinearLayout.LayoutParams((64 * dp).toInt(), (64 * dp).toInt()).apply { bottomMargin = (12 * dp).toInt() })
            addView(TextView(this@AiModelsActivity).apply {
                text = getString(R.string.ai_models_hero_title)
                textSize = 17f; setTextColor(cPrimary); typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            })
            addView(TextView(this@AiModelsActivity).apply {
                text = getString(R.string.ai_models_hero_desc)
                textSize = 13f; setTextColor(cSecondary); gravity = Gravity.CENTER
                setLineSpacing(0f, 1.15f)
                setPadding(0, (4 * dp).toInt(), 0, 0)
            })
        })

        content.addView(TextView(this).apply {
            text = getString(R.string.ai_models_section).uppercase()
            textSize = 12f; setTextColor(cAccent); typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
            setPadding((6 * dp).toInt(), (22 * dp).toInt(), 0, (8 * dp).toInt())
        })
        listCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(cCard, 18f)
            clipToOutline = true
        }
        content.addView(listCard)

        setContentView(outer)
    }

    private class Entry(val id: String?, val name: String, val files: List<File>, val downloaded: Boolean, val busy: Boolean) {
        val bytes get() = files.sumOf { it.length() }
    }

    private fun modelBase(name: String): String? {
        val i = name.indexOf(".litertlm")
        return if (i < 0) null else name.substring(0, i + ".litertlm".length)
    }

    private fun entries(): List<Entry> {
        val all = filesDir.listFiles()?.filter { it.isFile } ?: emptyList()
        val recent = System.currentTimeMillis() - 2 * 60_000L
        val known = SiniaOfflineFragment.MODELS.map { it.filename }.toSet()
        val list = SiniaOfflineFragment.MODELS.map { m ->
            val files = all.filter { modelBase(it.name) == m.filename }
            val main = File(filesDir, m.filename)
            val busy = files.any { it.name.endsWith(".part") && it.lastModified() > recent }
            Entry(m.id, getString(m.nameResId), files, main.isFile && main.length() >= m.minValidBytes, busy)
        }.toMutableList()
        val old = all.filter { f -> modelBase(f.name)?.let { it !in known } == true }
        if (old.isNotEmpty()) list.add(Entry(null, getString(R.string.ai_models_old), old, true, false))
        return list
    }

    private fun refresh() {
        listCard.removeAllViews()
        val list = entries()
        list.forEachIndexed { i, e ->
            if (i > 0) listCard.addView(View(this).apply {
                setBackgroundColor(pl.sinusoide.sinuschat.ui.ThemeUi.divider(context))
                layoutParams = LinearLayout.LayoutParams(-1, (1 * dp).toInt().coerceAtLeast(1)).apply {
                    marginStart = (68 * dp).toInt()
                }
            })
            listCard.addView(row(e))
        }
    }

    private fun row(e: Entry): View {
        val hasFiles = e.files.isNotEmpty()
        val partial = hasFiles && !e.downloaded && !e.busy
        val color = when {
            e.downloaded -> cGreen
            e.busy -> cAccent
            partial -> cOrange
            else -> cSecondary
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
        }
        row.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_sinia); setColorFilter(if (hasFiles) cPurple else cSecondary)
            background = GradientDrawable().apply {
                cornerRadius = 12 * dp
                setColor(((if (hasFiles) cPurple else cSecondary) and 0x00FFFFFF) or 0x24000000)
            }
            val p = (9 * dp).toInt(); setPadding(p, p, p, p)
        }, LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt()).apply { marginEnd = (14 * dp).toInt() })
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@AiModelsActivity).apply {
                text = e.name; textSize = 15.5f; setTextColor(cPrimary)
            })
            addView(TextView(this@AiModelsActivity).apply {
                text = when {
                    e.busy -> getString(R.string.ai_model_downloading)
                    e.downloaded -> getString(R.string.ai_model_ready, StorageUsage.formatSize(e.bytes))
                    partial -> getString(R.string.ai_model_partial, StorageUsage.formatSize(e.bytes))
                    else -> getString(R.string.ai_model_missing)
                }
                textSize = 12.5f; setTextColor(color)
                setPadding(0, (2 * dp).toInt(), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = (8 * dp).toInt() })
        if (hasFiles && !e.busy) row.addView(deletePill { confirmDelete(e) })
        return row
    }

    private fun deletePill(onClick: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = 18 * dp
            setColor((cRed and 0x00FFFFFF) or 0x14000000)
            setStroke((1 * dp).toInt().coerceAtLeast(1), (cRed and 0x00FFFFFF) or 0x66000000)
        }
        setPadding((12 * dp).toInt(), (7 * dp).toInt(), (14 * dp).toInt(), (7 * dp).toInt())
        isClickable = true
        contentDescription = getString(R.string.translate_delete)
        setOnClickListener { onClick() }
        addView(ImageView(this@AiModelsActivity).apply {
            setImageResource(R.drawable.ic_delete); setColorFilter(cRed)
        }, LinearLayout.LayoutParams((16 * dp).toInt(), (16 * dp).toInt()).apply { marginEnd = (6 * dp).toInt() })
        addView(TextView(this@AiModelsActivity).apply {
            text = getString(R.string.translate_delete)
            textSize = 13.5f; typeface = Typeface.DEFAULT_BOLD; setTextColor(cRed)
        })
    }

    private fun confirmDelete(e: Entry) {
        ConfirmSheet.show(this,
            icon = R.drawable.ic_delete,
            color = cRed,
            title = getString(R.string.ai_models_delete_title),
            message = getString(R.string.ai_models_delete_desc, e.name, StorageUsage.formatSize(e.bytes)),
            confirm = getString(R.string.translate_delete),
            onConfirm = { delete(e) })
    }

    private fun delete(e: Entry) {
        val app = applicationContext
        AppExecutor.io {
            e.files.forEach { it.delete() }
            if (e.id != null) {
                val prefs = app.getSharedPreferences("sinia_offline", Context.MODE_PRIVATE)
                if (prefs.getString(SiniaOfflineFragment.PREF_MODEL_ID, null) == e.id) {
                    val next = SiniaOfflineFragment.MODELS.firstOrNull { m ->
                        m.id != e.id && File(app.filesDir, m.filename).let { it.isFile && it.length() >= m.minValidBytes }
                    }?.id
                    prefs.edit().apply { if (next == null) remove(SiniaOfflineFragment.PREF_MODEL_ID) else putString(SiniaOfflineFragment.PREF_MODEL_ID, next) }.apply()
                }
            }
            AppExecutor.main {
                if (isFinishing || isDestroyed) return@main
                PopupHelper.showSuccess(this, getString(R.string.ai_models_deleted, e.name))
                refresh()
            }
        }
    }

    private fun rounded(color: Int, radiusDp: Float) = pl.sinusoide.sinuschat.ui.ThemeUi.rounded(this, color, radiusDp)
}
