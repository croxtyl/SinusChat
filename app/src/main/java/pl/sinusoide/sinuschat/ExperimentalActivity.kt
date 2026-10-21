package pl.sinusoide.sinuschat

import android.content.Intent
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
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.materialswitch.MaterialSwitch
import pl.sinusoide.sinuschat.ui.startWithFade
import pl.sinusoide.sinuschat.util.AppExecutor
import pl.sinusoide.sinuschat.util.ExperimentalFeatures
import pl.sinusoide.sinuschat.util.PopupHelper
import pl.sinusoide.sinuschat.util.StorageUsage
import pl.sinusoide.sinuschat.util.ThemeManager

class ExperimentalActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(pl.sinusoide.sinuschat.util.LocaleManager.wrap(newBase))
    }

    private val dp get() = resources.displayMetrics.density

    private var cBg = 0; private var cSurface = 0; private var cCard = 0
    private var cPrimary = 0; private var cSecondary = 0
    private var cAccent = 0; private var cRed = 0; private var cOrange = 0; private var cPurple = 0

    private lateinit var aiSwitch: MaterialSwitch
    private lateinit var pipSwitch: MaterialSwitch
    private lateinit var bubbleSwitch: MaterialSwitch
    private lateinit var bubbleDesc: TextView
    private lateinit var countPill: TextView
    private lateinit var modelsRow: LinearLayout
    private lateinit var modelsSubtitle: TextView
    private lateinit var modelsValue: TextView
    private var modelsBytes = 0L
    private var restarting = false

    private fun attr(id: Int, fallback: Int): Int {
        val tv = android.util.TypedValue()
        return pl.sinusoide.sinuschat.ui.ThemeUi.attrOrNull(this, id) ?: fallback
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pl.sinusoide.sinuschat.util.SettingsHighlight.apply(this)

        cBg = attr(R.attr.sinuschat_background, 0xFF0E1621.toInt())
        cSurface = attr(R.attr.sinuschat_surface, 0xFF17212B.toInt())
        cCard = attr(R.attr.sinuschat_surface_elevated, 0xFF1D2A38.toInt())
        cPrimary = attr(R.attr.sinuschat_text_primary, Color.WHITE)
        cSecondary = attr(R.attr.sinuschat_text_secondary, 0xFF7F91A4.toInt())
        cAccent = pl.sinusoide.sinuschat.ui.ThemeUi.accent(this)
        cRed = ContextCompat.getColor(this, R.color.sinuschat_error)
        cOrange = 0xFFE67E22.toInt()
        cPurple = 0xFF9B6BE0.toInt()

        buildUi()
    }

    override fun onResume() {
        super.onResume()
        refreshModels()
        refreshBubbles()
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
            background = ContextCompat.getDrawable(this@ExperimentalActivity, R.drawable.bg_menu_ripple)
            contentDescription = getString(R.string.back)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt()))
        header.addView(TextView(this).apply {
            text = getString(R.string.experimental_title); textSize = 20f
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

        content.addView(heroCard())

        content.addView(sectionTitle(getString(R.string.exp_section_messages)))
        content.addView(card().apply { addView(bubbleRow()) })

        content.addView(sectionTitle(getString(R.string.exp_section_calls)))
        content.addView(card().apply { addView(pipRow()) })

        content.addView(sectionTitle(getString(R.string.exp_section_ai)))
        content.addView(card().apply {
            addView(aiRow())
            addView(divider())
            addView(modelsRowView())
        })

        content.addView(TextView(this).apply {
            text = getString(R.string.exp_footer)
            textSize = 13f; setTextColor(cSecondary)
            setLineSpacing(2 * dp, 1f)
            setPadding((6 * dp).toInt(), (18 * dp).toInt(), (6 * dp).toInt(), 0)
        })

        setContentView(outer)
        refreshCount()
    }

    private fun heroCard(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        background = rounded(cCard, 20f)
        setPadding((20 * dp).toInt(), (22 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt())
        addView(ImageView(this@ExperimentalActivity).apply {
            setImageResource(R.drawable.ic_experimental); setColorFilter(cOrange)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor((cOrange and 0x00FFFFFF) or 0x26000000)
            }
            val p = (16 * dp).toInt(); setPadding(p, p, p, p)
        }, LinearLayout.LayoutParams((64 * dp).toInt(), (64 * dp).toInt()).apply {
            bottomMargin = (12 * dp).toInt()
        })
        addView(TextView(this@ExperimentalActivity).apply {
            text = getString(R.string.exp_hero_title); textSize = 17f
            setTextColor(cPrimary); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        })
        addView(TextView(this@ExperimentalActivity).apply {
            text = getString(R.string.experimental_desc); textSize = 13f
            setTextColor(cSecondary); gravity = Gravity.CENTER
            setLineSpacing(0f, 1.15f)
            setPadding(0, (4 * dp).toInt(), 0, (14 * dp).toInt())
        })
        countPill = TextView(this@ExperimentalActivity).apply {
            textSize = 12f; typeface = Typeface.DEFAULT_BOLD
            setPadding((12 * dp).toInt(), (5 * dp).toInt(), (12 * dp).toInt(), (5 * dp).toInt())
        }
        addView(countPill)
    }

    private fun aiRow(): View {
        val sizes = SiniaOfflineFragment.MODELS.map { it.sizeGB }
        fun gb(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else String.format("%.1f", v)

        aiSwitch = MaterialSwitch(this).apply {
            isChecked = ExperimentalFeatures.isAiEnabled(this@ExperimentalActivity)
            thumbTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Color.WHITE, 0xFF8AA0B8.toInt()))
            trackTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(cAccent, 0xFF2A3A4A.toInt()))
            isClickable = false
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * dp).toInt(), (14 * dp).toInt(), (10 * dp).toInt(), (14 * dp).toInt())
            background = ContextCompat.getDrawable(this@ExperimentalActivity, R.drawable.bg_menu_ripple)
            isClickable = true
            setOnClickListener { confirmToggle(!aiSwitch.isChecked) }
            addView(iconTile(R.drawable.ic_sinia, cPurple))
            addView(LinearLayout(this@ExperimentalActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(LinearLayout(this@ExperimentalActivity).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    addView(TextView(this@ExperimentalActivity).apply {
                        text = getString(R.string.exp_ai_title); textSize = 15.5f; setTextColor(cPrimary)
                    })
                    addView(TextView(this@ExperimentalActivity).apply {
                        text = getString(R.string.exp_beta); textSize = 10f
                        typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.08f
                        setTextColor(cOrange)
                        background = GradientDrawable().apply {
                            cornerRadius = 8 * dp
                            setColor((cOrange and 0x00FFFFFF) or 0x26000000)
                        }
                        setPadding((7 * dp).toInt(), (2 * dp).toInt(), (7 * dp).toInt(), (2 * dp).toInt())
                    }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = (8 * dp).toInt() })
                })
                addView(TextView(this@ExperimentalActivity).apply {
                    text = getString(R.string.exp_ai_desc_long, gb(sizes.minOrNull() ?: 0.0), gb(sizes.maxOrNull() ?: 0.0))
                    textSize = 12.5f; setTextColor(cSecondary)
                    setLineSpacing(0f, 1.1f)
                    setPadding(0, (3 * dp).toInt(), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = (8 * dp).toInt() })
            addView(aiSwitch)
        }
    }

    private fun bubbleRow(): View {
        val supported = pl.sinusoide.sinuschat.util.ChatBubbles.isSupported()
        bubbleSwitch = MaterialSwitch(this).apply {
            isChecked = supported && ExperimentalFeatures.isBubblesEnabled(this@ExperimentalActivity)
            thumbTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Color.WHITE, 0xFF8AA0B8.toInt()))
            trackTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(cAccent, 0xFF2A3A4A.toInt()))
            isClickable = false
            isEnabled = supported
        }
        bubbleDesc = TextView(this).apply {
            textSize = 12.5f; setTextColor(cSecondary)
            setLineSpacing(0f, 1.1f)
            setPadding(0, (3 * dp).toInt(), 0, 0)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * dp).toInt(), (14 * dp).toInt(), (10 * dp).toInt(), (14 * dp).toInt())
            background = ContextCompat.getDrawable(this@ExperimentalActivity, R.drawable.bg_menu_ripple)
            isClickable = supported
            alpha = if (supported) 1f else 0.55f
            setOnClickListener {
                val on = !bubbleSwitch.isChecked
                ExperimentalFeatures.setBubblesEnabled(this@ExperimentalActivity, on)
                if (!on) pl.sinusoide.sinuschat.util.ChatBubbles.clear(this@ExperimentalActivity)
                refreshBubbles()
                if (on && !pl.sinusoide.sinuschat.util.ChatBubbles.systemAllows(this@ExperimentalActivity)) {
                    PopupHelper.show(this@ExperimentalActivity, getString(R.string.exp_bubbles_allow))
                    pl.sinusoide.sinuschat.util.ChatBubbles.openSystemSettings(this@ExperimentalActivity)
                }
            }
            addView(iconTile(R.drawable.ic_chat, cPurple))
            addView(LinearLayout(this@ExperimentalActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@ExperimentalActivity).apply {
                    text = getString(R.string.exp_bubbles_title); textSize = 15.5f; setTextColor(cPrimary)
                })
                addView(bubbleDesc)
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = (8 * dp).toInt() })
            addView(bubbleSwitch)
        }
    }

    private fun refreshBubbles() {
        if (!::bubbleDesc.isInitialized) return
        val bubbles = pl.sinusoide.sinuschat.util.ChatBubbles
        val enabled = bubbles.isSupported() && ExperimentalFeatures.isBubblesEnabled(this)
        bubbleSwitch.isChecked = enabled
        val blocked = enabled && !bubbles.systemAllows(this)
        bubbleDesc.text = getString(when {
            !bubbles.isSupported() -> R.string.exp_bubbles_unsupported
            blocked -> R.string.exp_bubbles_blocked
            else -> R.string.exp_bubbles_desc
        })
        bubbleDesc.setTextColor(if (blocked) cOrange else cSecondary)
        if (blocked) bubbleDesc.setOnClickListener { bubbles.openSystemSettings(this) }
        else { bubbleDesc.setOnClickListener(null); bubbleDesc.isClickable = false }
        refreshCount()
    }

    private fun pipRow(): View {
        val supported = ExperimentalFeatures.isPipSupported(this)
        pipSwitch = MaterialSwitch(this).apply {
            isChecked = supported && ExperimentalFeatures.isPipEnabled(this@ExperimentalActivity)
            thumbTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Color.WHITE, 0xFF8AA0B8.toInt()))
            trackTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(cAccent, 0xFF2A3A4A.toInt()))
            isClickable = false
            isEnabled = supported
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * dp).toInt(), (14 * dp).toInt(), (10 * dp).toInt(), (14 * dp).toInt())
            background = ContextCompat.getDrawable(this@ExperimentalActivity, R.drawable.bg_menu_ripple)
            isClickable = supported
            alpha = if (supported) 1f else 0.55f
            setOnClickListener {
                val on = !pipSwitch.isChecked
                pipSwitch.isChecked = on
                ExperimentalFeatures.setPipEnabled(this@ExperimentalActivity, on)
                refreshCount()
            }
            addView(iconTile(R.drawable.ic_call_video_on, cAccent))
            addView(LinearLayout(this@ExperimentalActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@ExperimentalActivity).apply {
                    text = getString(R.string.exp_pip_title); textSize = 15.5f; setTextColor(cPrimary)
                })
                addView(TextView(this@ExperimentalActivity).apply {
                    text = getString(if (supported) R.string.exp_pip_desc else R.string.exp_pip_unsupported)
                    textSize = 12.5f; setTextColor(cSecondary)
                    setLineSpacing(0f, 1.1f)
                    setPadding(0, (3 * dp).toInt(), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = (8 * dp).toInt() })
            addView(pipSwitch)
        }
    }

    private fun modelsRowView(): View {
        modelsSubtitle = TextView(this).apply {
            textSize = 12.5f; setTextColor(cSecondary); setPadding(0, (2 * dp).toInt(), 0, 0)
        }
        modelsValue = TextView(this).apply {
            textSize = 14f; setTextColor(cPrimary); typeface = Typeface.DEFAULT_BOLD
        }
        modelsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * dp).toInt(), (13 * dp).toInt(), (16 * dp).toInt(), (13 * dp).toInt())
            addView(iconTile(R.drawable.ic_storage, cAccent))
            addView(LinearLayout(this@ExperimentalActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@ExperimentalActivity).apply {
                    text = getString(R.string.exp_models_title); textSize = 15.5f; setTextColor(cPrimary)
                })
                addView(modelsSubtitle)
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = (8 * dp).toInt() })
            addView(modelsValue)
            setOnClickListener {
                if (!aiSwitch.isChecked && modelsBytes > 0L) confirmDeleteModels()
            }
        }
        return modelsRow
    }

    private fun refreshCount() {
        val total = 3
        val active = (if (ExperimentalFeatures.isAiEnabled(this)) 1 else 0) +
            (if (ExperimentalFeatures.isPipEnabled(this) && ExperimentalFeatures.isPipSupported(this)) 1 else 0) +
            (if (ExperimentalFeatures.isBubblesEnabled(this) && pl.sinusoide.sinuschat.util.ChatBubbles.isSupported()) 1 else 0)
        val color = if (active > 0) cAccent else cSecondary
        countPill.text = getString(R.string.exp_active_count, active, total)
        countPill.setTextColor(color)
        countPill.background = GradientDrawable().apply {
            cornerRadius = 14 * dp
            setColor((color and 0x00FFFFFF) or 0x24000000)
        }
    }

    private fun refreshModels() {
        AppExecutor.io {
            val bytes = try { ExperimentalFeatures.downloadedModelsBytes(this) } catch (_: Exception) { 0L }
            AppExecutor.main {
                if (isFinishing || isDestroyed) return@main
                modelsBytes = bytes
                val aiOn = aiSwitch.isChecked
                modelsValue.text = if (bytes > 0) StorageUsage.formatSize(bytes) else ""
                modelsSubtitle.text = getString(when {
                    bytes <= 0L -> R.string.exp_models_none
                    aiOn -> R.string.exp_models_turn_off_first
                    else -> R.string.exp_models_tap_delete
                })
                val deletable = !aiOn && bytes > 0L
                modelsRow.isClickable = deletable
                modelsRow.background = if (deletable)
                    ContextCompat.getDrawable(this, R.drawable.bg_menu_ripple) else null
                modelsSubtitle.setTextColor(if (deletable) cRed else cSecondary)
            }
        }
    }

    private fun confirmToggle(enable: Boolean) {
        if (restarting) return
        showSheet(
            icon = R.drawable.ic_sinia,
            color = if (enable) cPurple else cSecondary,
            title = getString(if (enable) R.string.exp_ai_enable_q else R.string.exp_ai_disable_q),
            desc = getString(if (enable) R.string.exp_ai_enable_desc else R.string.exp_ai_disable_desc),
            button = getString(if (enable) R.string.exp_enable else R.string.exp_disable),
            buttonColor = if (enable) cAccent else cRed
        ) {
            restarting = true
            aiSwitch.isChecked = enable
            ExperimentalFeatures.setAiEnabled(this, enable)
            PopupHelper.show(this, getString(R.string.restarting_app))
            aiSwitch.postDelayed({ restartApp() }, 350)
        }
    }

    private fun confirmDeleteModels() {
        showSheet(
            icon = R.drawable.ic_delete,
            color = cRed,
            title = getString(R.string.exp_models_delete_q),
            desc = getString(R.string.exp_models_delete_desc, StorageUsage.formatSize(modelsBytes)),
            button = getString(R.string.delete),
            buttonColor = cRed
        ) {
            AppExecutor.io {
                val freed = ExperimentalFeatures.deleteDownloadedModels(applicationContext)
                AppExecutor.main {
                    if (isFinishing || isDestroyed) return@main
                    PopupHelper.show(this,
                        if (freed > 0) getString(R.string.exp_models_deleted, StorageUsage.formatSize(freed))
                        else getString(R.string.exp_models_busy))
                    refreshModels()
                }
            }
        }
    }

    private fun showSheet(
        icon: Int, color: Int, title: String, desc: String,
        button: String, buttonColor: Int, onConfirm: () -> Unit
    ) {
        val sheet = BottomSheetDialog(this, R.style.Theme_SinusChat_BottomSheet)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((24 * dp).toInt(), (12 * dp).toInt(), (24 * dp).toInt(), (20 * dp).toInt())
        }
        root.addView(View(this).apply { background = rounded(pl.sinusoide.sinuschat.ui.ThemeUi.handle(context), 3f) },
            LinearLayout.LayoutParams((36 * dp).toInt(), (4 * dp).toInt()).apply {
                bottomMargin = (20 * dp).toInt()
            })
        root.addView(ImageView(this).apply {
            setImageResource(icon); setColorFilter(color)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor((color and 0x00FFFFFF) or 0x26000000)
            }
            val p = (16 * dp).toInt(); setPadding(p, p, p, p)
        }, LinearLayout.LayoutParams((64 * dp).toInt(), (64 * dp).toInt()).apply {
            bottomMargin = (16 * dp).toInt()
        })
        root.addView(TextView(this).apply {
            text = title; textSize = 20f; setTextColor(cPrimary)
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = desc; textSize = 14f; setTextColor(cSecondary); gravity = Gravity.CENTER
            setPadding(0, (8 * dp).toInt(), 0, (22 * dp).toInt())
        })
        root.addView(TextView(this).apply {
            text = button; textSize = 16f; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            background = rounded(buttonColor, 26f)
            setPadding(0, (14 * dp).toInt(), 0, (14 * dp).toInt())
            isClickable = true
            setOnClickListener { sheet.dismiss(); onConfirm() }
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(TextView(this).apply {
            text = getString(R.string.cancel)
            textSize = 15f; setTextColor(cSecondary); gravity = Gravity.CENTER
            setPadding(0, (14 * dp).toInt(), 0, (4 * dp).toInt())
            isClickable = true
            setOnClickListener { sheet.dismiss() }
        }, LinearLayout.LayoutParams(-1, -2))
        sheet.setContentView(root)
        pl.sinusoide.sinuschat.ui.FlowGlass.show(sheet)
    }

    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startWithFade(intent)
        finishAffinity()
    }

    private fun rounded(color: Int, radiusDp: Float) = pl.sinusoide.sinuschat.ui.ThemeUi.rounded(this, color, radiusDp)

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(cCard, 18f)
        clipToOutline = true
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(pl.sinusoide.sinuschat.ui.ThemeUi.divider(context))
        layoutParams = LinearLayout.LayoutParams(-1, (1 * dp).toInt().coerceAtLeast(1)).apply {
            marginStart = (68 * dp).toInt()
        }
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text.uppercase()
        textSize = 12f; setTextColor(cAccent); typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.06f
        setPadding((6 * dp).toInt(), (22 * dp).toInt(), 0, (8 * dp).toInt())
    }

    private fun iconTile(icon: Int, color: Int) = ImageView(this).apply {
        setImageResource(icon); setColorFilter(color)
        background = GradientDrawable().apply {
            cornerRadius = 12 * dp
            setColor((color and 0x00FFFFFF) or 0x24000000)
        }
        val p = (9 * dp).toInt(); setPadding(p, p, p, p)
        layoutParams = LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt()).apply {
            marginEnd = (14 * dp).toInt()
        }
    }
}
