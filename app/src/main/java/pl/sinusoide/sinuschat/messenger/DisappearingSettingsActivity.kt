package pl.sinusoide.sinuschat.messenger

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import pl.sinusoide.sinuschat.R
import pl.sinusoide.sinuschat.util.DisappearingMessages
import pl.sinusoide.sinuschat.util.LocaleManager
import androidx.activity.enableEdgeToEdge

class DisappearingSettingsActivity : AppCompatActivity() {
    private val tc by lazy { pl.sinusoide.sinuschat.ui.ThemeColors(this) }
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    private val dp get() = resources.displayMetrics.density
    private var selectedTtl = 0L
    private var selectedTrigger = DisappearingMessages.TRIGGER_READ
    private var customTtl = 0L
    private val radios = ArrayList<RadioButton>()
    private val triggerRadios = ArrayList<RadioButton>()

    private val presets by lazy {
        listOf(
            getString(R.string.disabled)   to 0L,
            "5 sekund"    to 5_000L,
            "30 sekund"   to 30_000L,
            "5 minut"     to 5 * 60_000L,
            "30 minut"    to 30 * 60_000L,
            "1 godzina"   to 60 * 60_000L,
            "8 godzin"    to 8 * 3600_000L,
            getString(R.string.one_day)     to 24 * 3600_000L,
            getString(R.string.one_week)   to 7 * 86400_000L,
            "4 tygodnie"  to 28 * 86400_000L,
            getString(R.string.custom_time) to -1L
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        selectedTtl = intent.getLongExtra("currentTtlMs", 0L)
        selectedTrigger = DisappearingMessages.TRIGGER_READ

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(tc.bg)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, b.top, 0, b.bottom); insets
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding((8*dp).toInt(), (10*dp).toInt(), (12*dp).toInt(), (10*dp).toInt())
        }
        header.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_back); setBackgroundResource(0); setColorFilter(tc.text)
            setPadding((10*dp).toInt(), (10*dp).toInt(), (10*dp).toInt(), (10*dp).toInt())
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams((44*dp).toInt(), (44*dp).toInt()))
        header.addView(TextView(this).apply {
            text = getString(R.string.disappearing_messages); textSize = 20f; setTextColor(tc.text)
            setPadding((8*dp).toInt(), 0, 0, 0)
        })
        root.addView(header)

        root.addView(TextView(this).apply {
            text = getString(R.string.disappearing_desc)
            textSize = 14f; setTextColor(tc.text2)
            setPadding((20*dp).toInt(), (4*dp).toInt(), (20*dp).toInt(), (16*dp).toInt())
        })

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        list.addView(sectionHeader("CZAS"))
        for ((label, ttl) in presets) list.addView(optionRow(label, ttl))
        
        syncRadios()

        root.addView(TextView(this).apply {
            text = getString(R.string.save); textSize = 16f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 26f * dp; setColor(tc.accent)
            }
            setPadding(0, (14*dp).toInt(), 0, (14*dp).toInt())
            setOnClickListener {
                intent.putExtra("selectedTtlMs", selectedTtl)
                intent.putExtra("selectedTrigger", selectedTrigger)
                setResult(RESULT_OK, intent)
                finish()
            }
        }, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins((16*dp).toInt(), (8*dp).toInt(), (16*dp).toInt(), (16*dp).toInt())
        })

        setContentView(root)
    }

    private fun sectionHeader(title: String) = TextView(this).apply {
        text = title; textSize = 12f; setTextColor(tc.accent)
        typeface = Typeface.DEFAULT_BOLD
        setPadding((20*dp).toInt(), (20*dp).toInt(), (20*dp).toInt(), (8*dp).toInt())
    }

    private fun optionRow(label: String, ttl: Long): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding((20*dp).toInt(), (14*dp).toInt(), (20*dp).toInt(), (14*dp).toInt())
            isClickable = true
            setOnClickListener {
                if (ttl == -1L) {
                    DisappearingMessages.showCustomTimePicker(this@DisappearingSettingsActivity) { picked ->
                        customTtl = picked
                        selectedTtl = picked
                        (getChildAt(1) as? TextView)?.text = getString(R.string.custom_time_label, DisappearingMessages.label(this@DisappearingSettingsActivity, picked))
                        syncRadios()
                    }
                } else {
                    selectedTtl = ttl
                    syncRadios()
                }
            }
        }
        val radio = RadioButton(this).apply {
            isClickable = false
            buttonTintList = ColorStateList.valueOf(tc.accent)
        }
        radio.tag = ttl
        radios.add(radio)
        row.addView(radio, LinearLayout.LayoutParams((36*dp).toInt(), -2))
        row.addView(TextView(this).apply {
            text = label; textSize = 16f; setTextColor(tc.text)
            setPadding((8*dp).toInt(), 0, 0, 0)
        })
        return row
    }

    private fun syncRadios() {
        val isPreset = presets.any { it.second == selectedTtl && it.second != -1L }
        for (r in radios) {
            val ttl = r.tag as Long
            r.isChecked = when {
                ttl == -1L -> !isPreset && selectedTtl > 0L
                else -> ttl == selectedTtl
            }
        }
        for (r in triggerRadios) {
            r.isChecked = (r.tag == selectedTrigger)
        }
    }
}
