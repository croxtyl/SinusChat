package pl.sinusoide.sinuschat.messenger

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import pl.sinusoide.sinuschat.R
import pl.sinusoide.sinuschat.util.MuteSettings
import androidx.activity.enableEdgeToEdge

class MuteSettingsActivity : AppCompatActivity() {
    private val tc by lazy { pl.sinusoide.sinuschat.ui.ThemeColors(this) }
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(pl.sinusoide.sinuschat.util.LocaleManager.wrap(newBase))
    }

    private val dp get() = resources.displayMetrics.density
    private var selectedUntil = 0L
    private val radios = ArrayList<RadioButton>()

    private val presets by lazy {
        listOf(
            getString(R.string.disabled)   to 0L,
            getString(R.string.mute_1h)    to 3600_000L,
            getString(R.string.mute_8h)    to 8 * 3600_000L,
            getString(R.string.one_day)    to 24 * 3600_000L,
            getString(R.string.one_week)   to 7 * 86400_000L,
            getString(R.string.mute_always) to Long.MAX_VALUE,
            getString(R.string.mute_custom) to -1L
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        selectedUntil = intent.getLongExtra("currentMutedUntil", 0L)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(tc.bg)
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
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
            text = getString(R.string.mute_notifications); textSize = 20f; setTextColor(tc.text)
            setPadding((8*dp).toInt(), 0, 0, 0)
        })
        root.addView(header)

        root.addView(TextView(this).apply {
            text = getString(R.string.mute_desc)
            textSize = 14f; setTextColor(tc.text2)
            setPadding((20*dp).toInt(), (4*dp).toInt(), (20*dp).toInt(), (16*dp).toInt())
        })

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        for ((label, duration) in presets) list.addView(optionRow(label, duration))
        syncRadios()

        root.addView(TextView(this).apply {
            text = getString(R.string.save); textSize = 16f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 26f * dp; setColor(tc.accent)
            }
            setPadding(0, (14*dp).toInt(), 0, (14*dp).toInt())
            setOnClickListener {
                val res = intent
                res.putExtra("selectedMutedUntil", selectedUntil)
                res.putExtra("conversationId", intent.getStringExtra("conversationId"))
                setResult(RESULT_OK, res)
                finish()
            }
        }, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins((16*dp).toInt(), (8*dp).toInt(), (16*dp).toInt(), (16*dp).toInt())
        })

        setContentView(root)
    }

    private fun optionRow(label: String, duration: Long): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding((20*dp).toInt(), (14*dp).toInt(), (20*dp).toInt(), (14*dp).toInt())
            isClickable = true
            setOnClickListener {
                if (duration == -1L) {
                    MuteSettings.showCustomMutePicker(this@MuteSettingsActivity) { pickedUntil ->
                        selectedUntil = pickedUntil
                        syncRadios()
                    }
                } else {
                    selectedUntil = if (duration == 0L || duration == Long.MAX_VALUE) duration 
                                    else System.currentTimeMillis() + duration
                    syncRadios()
                }
            }
        }
        val radio = RadioButton(this).apply {
            isClickable = false
            buttonTintList = android.content.res.ColorStateList.valueOf(tc.accent)
        }
        radio.tag = duration
        radios.add(radio)
        row.addView(radio, LinearLayout.LayoutParams((36*dp).toInt(), -2))
        row.addView(TextView(this).apply {
            text = label; textSize = 16f; setTextColor(tc.text)
            setPadding((8*dp).toInt(), 0, 0, 0)
        })
        return row
    }

    private fun syncRadios() {
        val now = System.currentTimeMillis()
        for (r in radios) {
            val duration = r.tag as Long
            r.isChecked = when {
                duration == -1L -> {
                    presets.none { it.second != -1L && isCloseEnough(selectedUntil, it.second, now) } && selectedUntil > now
                }
                else -> isCloseEnough(selectedUntil, duration, now)
            }
        }
    }

    private fun isCloseEnough(selected: Long, duration: Long, now: Long): Boolean {
        if (duration == 0L) return selected == 0L || selected < now
        if (duration == Long.MAX_VALUE) return selected == Long.MAX_VALUE
        val expected = now + duration
        return Math.abs(selected - expected) < 30_000L
    }
}
