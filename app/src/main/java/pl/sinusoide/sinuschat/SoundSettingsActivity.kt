package pl.sinusoide.sinuschat

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider
import pl.sinusoide.sinuschat.ui.ThemeUi
import pl.sinusoide.sinuschat.util.NotificationPrefs
import pl.sinusoide.sinuschat.util.NotificationSound
import pl.sinusoide.sinuschat.util.SoundPrefs
import pl.sinusoide.sinuschat.util.ThemeManager

class SoundSettingsActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(pl.sinusoide.sinuschat.util.LocaleManager.wrap(newBase))
    }

    private val dp get() = resources.displayMetrics.density
    private var cBg = 0; private var cSurface = 0; private var cCard = 0
    private var cPrimary = 0; private var cSecondary = 0; private var cAccent = 0

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var previewRing: Boolean? = null
    private val ringRows = ArrayList<Pair<SoundPrefs.Ring, ImageView>>()
    private val notifRows = ArrayList<Pair<SoundPrefs.Notif, ImageView>>()
    private val outRows = ArrayList<Pair<SoundPrefs.Outgoing, ImageView>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pl.sinusoide.sinuschat.util.SettingsHighlight.apply(this)

        cBg = ThemeUi.attrOrNull(this, R.attr.sinuschat_background) ?: 0xFF0E1621.toInt()
        cSurface = ThemeUi.attrOrNull(this, R.attr.sinuschat_surface) ?: 0xFF17212B.toInt()
        cCard = ThemeUi.attrOrNull(this, R.attr.sinuschat_surface_elevated) ?: 0xFF1D2A38.toInt()
        cPrimary = ThemeUi.attrOrNull(this, R.attr.sinuschat_text_primary) ?: Color.WHITE
        cSecondary = ThemeUi.attrOrNull(this, R.attr.sinuschat_text_secondary) ?: 0xFF7F91A4.toInt()
        cAccent = ThemeUi.accent(this)
        buildUi()
    }

    override fun onPause() {
        super.onPause()
        stopPreview()
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
            background = ContextCompat.getDrawable(this@SoundSettingsActivity, R.drawable.bg_menu_ripple)
            contentDescription = getString(R.string.back)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt()))
        header.addView(TextView(this).apply {
            text = getString(R.string.sound_title); textSize = 20f
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
            setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (40 * dp).toInt())
        }
        val scroll = ScrollView(this).apply { addView(content); clipToPadding = false }
        outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            v.setPadding(0, 0, 0, insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom); insets
        }

        content.addView(sectionTitle(getString(R.string.sound_section_ring)))
        content.addView(card().apply {
            SoundPrefs.Ring.values().forEachIndexed { i, r ->
                if (i > 0) addView(divider())
                val check = ImageView(this@SoundSettingsActivity)
                ringRows += r to check
                addView(optionRow(getString(r.label), if (r == SoundPrefs.Ring.BIT) getString(R.string.sound_default) else null,
                    null, R.drawable.ic_play_voice, check) {
                    SoundPrefs.setRing(this@SoundSettingsActivity, r)
                    refreshChecks()
                    preview(r.raw, RingtoneManager.TYPE_RINGTONE, ringtone = true)
                })
            }
        })
        content.addView(volumeCard(getString(R.string.sound_volume_ring), R.drawable.ic_call_speaker_on,
            R.drawable.ic_call_speaker_off, ringtone = true), spaced())
        content.addView(note(getString(R.string.sound_ring_note)))

        content.addView(sectionTitle(getString(R.string.sound_section_notif)))
        content.addView(card().apply {
            SoundPrefs.Notif.values().forEachIndexed { i, n ->
                if (i > 0) addView(divider())
                val check = ImageView(this@SoundSettingsActivity)
                notifRows += n to check
                addView(optionRow(getString(n.label), if (n == SoundPrefs.Notif.WAVE) getString(R.string.sound_default) else null,
                    null, R.drawable.ic_play_voice, check) {
                    SoundPrefs.setNotif(this@SoundSettingsActivity, n)
                    refreshChecks()
                    preview(n.raw, RingtoneManager.TYPE_NOTIFICATION, ringtone = false)
                })
            }
        })
        content.addView(volumeCard(getString(R.string.sound_volume_notif), R.drawable.ic_notifications,
            R.drawable.ic_notifications_off, ringtone = false), spaced())
        content.addView(note(getString(R.string.sound_volume_note)))

        content.addView(sectionTitle(getString(R.string.sound_section_vibrate)))
        content.addView(card().apply {
            addView(switchRow(R.drawable.ic_vibrations, getString(R.string.sound_vibrate_notif),
                getString(R.string.sound_vibrate_notif_desc), NotificationPrefs.notifVibrate(this@SoundSettingsActivity)) { on ->
                NotificationPrefs.setNotifVibrate(this@SoundSettingsActivity, on)
                if (on) NotificationSound.previewVibration(this@SoundSettingsActivity)
            })
            addView(divider())
            addView(switchRow(R.drawable.ic_vibrations, getString(R.string.sound_vibrate_calls),
                getString(R.string.sound_vibrate_calls_desc), NotificationPrefs.callVibrate(this@SoundSettingsActivity)) { on ->
                NotificationPrefs.setCallVibrate(this@SoundSettingsActivity, on)
                if (on) NotificationSound.previewVibration(this@SoundSettingsActivity)
            })
        })

        content.addView(sectionTitle(getString(R.string.sound_section_outgoing)))
        content.addView(card().apply {
            SoundPrefs.Outgoing.values().forEachIndexed { i, o ->
                if (i > 0) addView(divider())
                val check = ImageView(this@SoundSettingsActivity)
                outRows += o to check
                val icon = if (o == SoundPrefs.Outgoing.TONE) R.drawable.ic_call_speaker_on else R.drawable.ic_call_answer
                addView(optionRow(getString(o.label), if (o == SoundPrefs.Outgoing.TONE) getString(R.string.sound_default) else null,
                    getString(o.desc), icon, check) {
                    stopPreview()
                    SoundPrefs.setOutgoing(this@SoundSettingsActivity, o)
                    refreshChecks()
                    if (o.raw != 0) preview(o.raw, RingtoneManager.TYPE_NOTIFICATION, ringtone = false)
                })
            }
        })

        setContentView(outer)
        refreshChecks()
    }

    private fun refreshChecks() {
        val ring = SoundPrefs.ring(this)
        val notif = SoundPrefs.notif(this)
        val out = SoundPrefs.outgoing(this)
        for ((r, v) in ringRows) v.visibility = if (r == ring) View.VISIBLE else View.INVISIBLE
        for ((n, v) in notifRows) v.visibility = if (n == notif) View.VISIBLE else View.INVISIBLE
        for ((o, v) in outRows) v.visibility = if (o == out) View.VISIBLE else View.INVISIBLE
    }

    private fun preview(raw: Int, systemType: Int, ringtone: Boolean) {
        stopPreview()
        player = SoundPrefs.preview(this, raw, systemType, ringtone)
        previewRing = if (player != null) ringtone else null
        player?.setOnCompletionListener { stopPreview() }
        handler.postDelayed({ stopPreview() }, if (ringtone) 7000L else 4000L)
    }

    private fun previewSelected(ringtone: Boolean) {
        if (ringtone) preview(SoundPrefs.ring(this).raw, RingtoneManager.TYPE_RINGTONE, ringtone = true)
        else preview(SoundPrefs.notif(this).raw, RingtoneManager.TYPE_NOTIFICATION, ringtone = false)
    }

    private fun stopPreview() {
        handler.removeCallbacksAndMessages(null)
        player?.let {
            try { it.stop() } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        player = null
        previewRing = null
    }

    private fun spaced() = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (10 * dp).toInt() }

    private fun volume(ringtone: Boolean) =
        if (ringtone) SoundPrefs.ringVolume(this) else SoundPrefs.notifVolume(this)

    private fun setVolume(ringtone: Boolean, percent: Int) {
        if (ringtone) SoundPrefs.setRingVolume(this, percent) else SoundPrefs.setNotifVolume(this, percent)
        if (previewRing == ringtone) {
            val g = SoundPrefs.gain(percent)
            try { player?.setVolume(g, g) } catch (_: Exception) {}
        }
    }

    private fun volumeCard(title: String, iconOn: Int, iconOff: Int, ringtone: Boolean) =
        card().apply {
            val initial = volume(ringtone)
            val valueText = TextView(this@SoundSettingsActivity).apply {
                text = getString(R.string.sound_volume_value, initial)
                textSize = 14f; setTextColor(cAccent); typeface = Typeface.DEFAULT_BOLD
            }
            val icon = iconBox(if (initial > 0) iconOn else iconOff).apply {
                contentDescription = getString(if (initial > 0) R.string.sound_mute else R.string.sound_unmute)
            }
            val faint = (cAccent and 0x00FFFFFF) or 0x40000000
            val slider = Slider(this@SoundSettingsActivity).apply {
                valueFrom = 0f
                valueTo = 100f
                value = initial.toFloat()
                labelBehavior = LabelFormatter.LABEL_GONE
                contentDescription = title
                haloRadius = 0
                trackStopIndicatorSize = 0
                trackActiveTintList = ColorStateList.valueOf(cAccent)
                trackInactiveTintList = ColorStateList.valueOf(faint)
                thumbTintList = ColorStateList.valueOf(cAccent)
                addOnChangeListener { _, v, fromUser ->
                    val percent = Math.round(v)
                    valueText.text = getString(R.string.sound_volume_value, percent)
                    icon.setImageResource(if (percent > 0) iconOn else iconOff)
                    icon.contentDescription = getString(if (percent > 0) R.string.sound_mute else R.string.sound_unmute)
                    if (fromUser) setVolume(ringtone, percent)
                }
                addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                    override fun onStartTrackingTouch(slider: Slider) {
                        SoundPrefs.setLastVolume(this@SoundSettingsActivity, ringtone, Math.round(slider.value))
                        if (previewRing != ringtone) previewSelected(ringtone)
                    }

                    override fun onStopTrackingTouch(slider: Slider) {
                        SoundPrefs.setLastVolume(this@SoundSettingsActivity, ringtone, Math.round(slider.value))
                        if (previewRing != ringtone && slider.value > 0f) previewSelected(ringtone)
                    }
                })
            }
            icon.setOnClickListener {
                val current = Math.round(slider.value)
                if (current > 0) {
                    SoundPrefs.setLastVolume(this@SoundSettingsActivity, ringtone, current)
                    slider.value = 0f
                    setVolume(ringtone, 0)
                    if (previewRing == ringtone) stopPreview()
                } else {
                    val back = SoundPrefs.lastVolume(this@SoundSettingsActivity, ringtone)
                    slider.value = back.toFloat()
                    setVolume(ringtone, back)
                    previewSelected(ringtone)
                }
            }
            addView(LinearLayout(this@SoundSettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((14 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), 0)
                addView(icon, LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt()).apply {
                    marginEnd = (14 * dp).toInt()
                })
                addView(TextView(this@SoundSettingsActivity).apply {
                    text = title; textSize = 15.5f; setTextColor(cPrimary)
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(valueText)
            })
            addView(slider, LinearLayout.LayoutParams(-1, -2).apply {
                setMargins((8 * dp).toInt(), (2 * dp).toInt(), (8 * dp).toInt(), (6 * dp).toInt())
            })
        }

    private fun switchRow(icon: Int, title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val sw = MaterialSwitch(this).apply {
            isChecked = checked
            thumbTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Color.WHITE, 0xFF8AA0B8.toInt()))
            trackTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(cAccent, 0xFF2A3A4A.toInt()))
            setOnCheckedChangeListener { _, v -> onChange(v) }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt())
            background = ContextCompat.getDrawable(this@SoundSettingsActivity, R.drawable.bg_menu_ripple)
            isClickable = true
            setOnClickListener { sw.toggle() }
            addView(iconBox(icon), LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt()).apply {
                marginEnd = (14 * dp).toInt()
            })
            addView(LinearLayout(this@SoundSettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@SoundSettingsActivity).apply {
                    text = title; textSize = 15.5f; setTextColor(cPrimary)
                })
                addView(TextView(this@SoundSettingsActivity).apply {
                    text = desc; textSize = 12.5f; setTextColor(cSecondary)
                    setPadding(0, (2 * dp).toInt(), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = (8 * dp).toInt() })
            addView(sw)
        }
    }

    private fun iconBox(icon: Int) = ImageView(this).apply {
        setImageResource(icon); setColorFilter(cAccent)
        background = GradientDrawable().apply {
            cornerRadius = 12 * dp
            setColor((cAccent and 0x00FFFFFF) or 0x24000000)
        }
        val p = (10 * dp).toInt(); setPadding(p, p, p, p)
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = ThemeUi.rounded(this@SoundSettingsActivity, cCard, 18f)
        clipToOutline = true
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(ThemeUi.divider(context))
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

    private fun note(text: String) = TextView(this).apply {
        this.text = text
        textSize = 12.5f; setTextColor(cSecondary)
        setLineSpacing(2 * dp, 1f)
        setPadding((6 * dp).toInt(), (10 * dp).toInt(), (6 * dp).toInt(), 0)
    }

    private fun optionRow(title: String, badge: String?, desc: String?, icon: Int, check: ImageView, onClick: () -> Unit) =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
            background = ContextCompat.getDrawable(this@SoundSettingsActivity, R.drawable.bg_menu_ripple)
            isClickable = true
            setOnClickListener { onClick() }
            addView(iconBox(icon), LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt()).apply { marginEnd = (14 * dp).toInt() })
            addView(LinearLayout(this@SoundSettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(LinearLayout(this@SoundSettingsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(TextView(this@SoundSettingsActivity).apply {
                        text = title; textSize = 15.5f; setTextColor(cPrimary)
                    })
                    if (badge != null) addView(TextView(this@SoundSettingsActivity).apply {
                        text = badge; textSize = 11f; typeface = Typeface.DEFAULT_BOLD
                        setTextColor(cAccent)
                        background = GradientDrawable().apply {
                            cornerRadius = 10 * dp
                            setColor((cAccent and 0x00FFFFFF) or 0x24000000)
                        }
                        setPadding((8 * dp).toInt(), (2 * dp).toInt(), (8 * dp).toInt(), (2 * dp).toInt())
                    }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = (8 * dp).toInt() })
                })
                if (desc != null) addView(TextView(this@SoundSettingsActivity).apply {
                    text = desc; textSize = 12.5f; setTextColor(cSecondary)
                    setPadding(0, (2 * dp).toInt(), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, -2, 1f))
            check.setImageResource(R.drawable.ic_check)
            check.setColorFilter(cAccent)
            check.visibility = View.INVISIBLE
            addView(check, LinearLayout.LayoutParams((24 * dp).toInt(), (24 * dp).toInt()).apply { marginStart = (10 * dp).toInt() })
        }
}
