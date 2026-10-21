package pl.sinusoide.sinuschat

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import pl.sinusoide.sinuschat.call.CallActivity
import pl.sinusoide.sinuschat.crypto.DbKeyManager
import pl.sinusoide.sinuschat.data.CallEntity
import pl.sinusoide.sinuschat.data.CallLogRepository
import pl.sinusoide.sinuschat.data.SinusChatDatabase
import pl.sinusoide.sinuschat.util.AvatarHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallsFragment : Fragment() {

    private val dp by lazy { resources.displayMetrics.density }
    private lateinit var listContainer: LinearLayout
    private lateinit var emptyView: LinearLayout
    private lateinit var emptyTitle: TextView
    private lateinit var emptyDesc: TextView
    private lateinit var emptyIcon: android.widget.ImageView

    private var cBg = 0; private var cSurface = 0; private var cCard = 0
    private var cPrimary = 0; private var cSecondary = 0; private var cAccent = 0
    private val cRed = 0xFFE5484D.toInt()
    private val cGreen = 0xFF3FB97A.toInt()
    private val cOrange = 0xFFE89A3C.toInt()

    private var allCalls: List<CallEntity> = emptyList()
    private var missedOnly = false
    private var firstRender = true

    private lateinit var filterBar: FrameLayout
    private lateinit var filterPill: View
    private lateinit var filterAll: TextView
    private lateinit var filterMissed: TextView

    private fun attr(id: Int, fallback: Int): Int {
        val tv = android.util.TypedValue()
        return pl.sinusoide.sinuschat.ui.ThemeUi.attrOrNull(requireContext(), id) ?: fallback
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        cBg = attr(R.attr.sinuschat_background, 0xFF0E1621.toInt())
        cSurface = attr(R.attr.sinuschat_surface, 0xFF17212B.toInt())
        cCard = attr(R.attr.sinuschat_surface_elevated, 0xFF1D2A38.toInt())
        cPrimary = attr(R.attr.sinuschat_text_primary, android.graphics.Color.WHITE)
        cSecondary = attr(R.attr.sinuschat_text_secondary, 0xFF7F91A4.toInt())
        cAccent = pl.sinusoide.sinuschat.ui.ThemeUi.accent(ctx)

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(cBg)
        }

        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(cSurface)
            elevation = 2 * dp
        }
        header.addView(TextView(ctx).apply {
            text = getString(R.string.calls_title); textSize = 22f
            setTextColor(cPrimary); typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            minHeight = (56 * dp).toInt()
        })
        header.addView(buildFilter(), LinearLayout.LayoutParams(-1, (40 * dp).toInt()).apply {
            bottomMargin = (12 * dp).toInt()
        })
        root.addView(header)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val top = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).top
            v.setPadding((20 * dp).toInt(), top, (20 * dp).toInt(), 0)
            insets
        }

        val frame = FrameLayout(ctx)
        listContainer = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        frame.addView(android.widget.ScrollView(ctx).apply {
            clipToPadding = false
            isVerticalScrollBarEnabled = false
            setPadding(0, (4 * dp).toInt(), 0, (24 * dp).toInt())
            addView(listContainer)
        }, FrameLayout.LayoutParams(-1, -1))

        emptyIcon = android.widget.ImageView(ctx).apply {
            val p = (20 * dp).toInt(); setPadding(p, p, p, p)
        }
        emptyTitle = TextView(ctx).apply {
            textSize = 18f; setTextColor(cPrimary); typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        emptyDesc = TextView(ctx).apply {
            textSize = 14f; setTextColor(cSecondary); gravity = Gravity.CENTER
            setLineSpacing(0f, 1.2f)
            setPadding(0, (6 * dp).toInt(), 0, 0)
        }
        emptyView = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((40 * dp).toInt(), 0, (40 * dp).toInt(), (40 * dp).toInt())
            visibility = View.GONE
            addView(emptyIcon, LinearLayout.LayoutParams((84 * dp).toInt(), (84 * dp).toInt()).apply {
                bottomMargin = (16 * dp).toInt()
            })
            addView(emptyTitle)
            addView(emptyDesc)
        }
        frame.addView(emptyView, FrameLayout.LayoutParams(-1, -1))
        root.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeCalls()
    }

    private fun observeCalls() {
        val ctx = context ?: return
        if (DbKeyManager.isEnabled(ctx) && DbKeyManager.cachedKey() == null) return

        val dao = SinusChatDatabase.getInstance(ctx).callDao()
        dao.observeAll().observe(viewLifecycleOwner) { calls ->
            rawCalls = calls.orEmpty()
            applyLocalNames()
        }
    }

    private var rawCalls: List<CallEntity> = emptyList()

    private fun applyLocalNames() {
        val ctx = context ?: return
        allCalls = rawCalls.map { c ->
            pl.sinusoide.sinuschat.util.ContactCache.getNick(ctx, c.otherUserHash)?.let { c.copy(otherUserName = it) } ?: c
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        if (rawCalls.isNotEmpty()) applyLocalNames()
    }

    private fun buildFilter(): View {
        val ctx = requireContext()
        filterBar = FrameLayout(ctx).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 20 * dp; setColor(pl.sinusoide.sinuschat.ui.ThemeUi.inset(ctx))
            }
            val p = (3 * dp).toInt(); setPadding(p, p, p, p)
        }
        filterPill = View(ctx).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 17 * dp; setColor(cCard)
                setStroke((1 * dp).toInt().coerceAtLeast(1), (cSecondary and 0x00FFFFFF) or 0x33000000)
            }
        }
        filterBar.addView(filterPill, FrameLayout.LayoutParams(0, -1))
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        fun option(text: String, missed: Boolean) = TextView(ctx).apply {
            this.text = text; textSize = 14f; gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            isClickable = true
            setOnClickListener { setFilter(missed, animate = true) }
        }
        filterAll = option(getString(R.string.calls_filter_all), false)
        filterMissed = option(getString(R.string.calls_filter_missed), true)
        row.addView(filterAll, LinearLayout.LayoutParams(0, -1, 1f))
        row.addView(filterMissed, LinearLayout.LayoutParams(0, -1, 1f))
        filterBar.addView(row, FrameLayout.LayoutParams(-1, -1))
        filterBar.addOnLayoutChangeListener { _, l, _, r, _, _, _, _, _ ->
            val inner = (r - l) - filterBar.paddingLeft - filterBar.paddingRight
            val half = inner / 2
            if (filterPill.layoutParams.width != half) {
                filterPill.layoutParams = FrameLayout.LayoutParams(half, -1)
                filterPill.post { filterPill.translationX = if (missedOnly) half.toFloat() else 0f }
            }
        }
        setFilter(false, animate = false)
        return filterBar
    }

    private fun setFilter(missed: Boolean, animate: Boolean) {
        val changed = missed != missedOnly
        missedOnly = missed
        filterAll.setTextColor(if (!missed) cPrimary else cSecondary)
        filterMissed.setTextColor(if (missed) cRed else cSecondary)
        val target = if (missed) filterPill.width.toFloat() else 0f
        if (animate) {
            filterPill.animate().translationX(target).setDuration(260)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.2f)).start()
        } else {
            filterPill.translationX = target
        }
        if (changed && ::listContainer.isInitialized) {
            firstRender = true
            render()
        }
    }

    private fun render() {
        if (!isAdded) return
        val calls = if (missedOnly) allCalls.filter { it.direction == CallLogRepository.MISSED } else allCalls
        listContainer.removeAllViews()

        if (calls.isEmpty()) {
            val color = if (missedOnly) cGreen else cAccent
            emptyIcon.setImageResource(if (missedOnly) R.drawable.ic_check else R.drawable.ic_call_answer)
            emptyIcon.setColorFilter(color)
            emptyIcon.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor((color and 0x00FFFFFF) or 0x24000000)
            }
            emptyTitle.text = getString(if (missedOnly) R.string.calls_missed_empty_title else R.string.calls_empty_title)
            emptyDesc.text = getString(if (missedOnly) R.string.calls_missed_empty_desc else R.string.calls_empty_desc)
            if (emptyView.visibility != View.VISIBLE) {
                emptyView.visibility = View.VISIBLE
                emptyView.alpha = 0f; emptyView.translationY = 12 * dp
                emptyView.animate().alpha(1f).translationY(0f).setDuration(260).start()
            }
            return
        }
        emptyView.visibility = View.GONE

        var lastDay = -1L
        var index = 0
        for (call in calls) {
            val day = dayKey(call.startedAtMs)
            if (day != lastDay) {
                listContainer.addView(dayHeader(call.startedAtMs))
                lastDay = day
            }
            val row = swipeableRow(call)
            listContainer.addView(row)
            if (firstRender && index < 14) {
                row.alpha = 0f; row.translationY = 10 * dp
                row.animate().alpha(1f).translationY(0f).setStartDelay(index * 22L).setDuration(240).start()
            }
            index++
        }
        firstRender = false
    }

    private fun dayKey(ms: Long): Long {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = ms }
        return c.get(java.util.Calendar.YEAR) * 1000L + c.get(java.util.Calendar.DAY_OF_YEAR)
    }

    private fun dayHeader(ms: Long): View {
        val now = System.currentTimeMillis()
        val label = when (dayKey(ms)) {
            dayKey(now) -> getString(R.string.calls_today)
            dayKey(now - 86_400_000L) -> getString(R.string.calls_yesterday)
            else -> {
                val sameYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) ==
                    java.util.Calendar.getInstance().apply { timeInMillis = ms }.get(java.util.Calendar.YEAR)
                SimpleDateFormat(if (sameYear) "EEEE, d MMMM" else "d MMMM yyyy", Locale.getDefault())
                    .format(Date(ms)).replaceFirstChar { it.uppercase() }
            }
        }
        return TextView(requireContext()).apply {
            text = label.uppercase(Locale.getDefault())
            textSize = 12f; letterSpacing = 0.06f
            setTextColor(cAccent); typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding((20 * dp).toInt(), (18 * dp).toInt(), (20 * dp).toInt(), (6 * dp).toInt())
        }
    }

    private fun callRow(call: CallEntity): View {
        val ctx = requireContext()
        val missed = call.direction == CallLogRepository.MISSED
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            setBackgroundColor(cBg)
        }

        val avatarSize = (50 * dp).toInt()
        val frameSize = avatarSize + (4 * dp).toInt()
        val avatarFrame = FrameLayout(ctx).apply { clipChildren = false; clipToPadding = false }
        val letterColor = AvatarHelper.getContactColor(ctx, call.otherUserHash)
        val letterDrawable = android.graphics.drawable.BitmapDrawable(resources,
            AvatarHelper.createLetterBitmap(AvatarHelper.initialOf(call.otherUserName), letterColor, avatarSize))
        val avatar = android.widget.ImageView(ctx).apply {
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            background = androidx.core.content.ContextCompat.getDrawable(ctx, R.drawable.bg_avatar_circle)
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            clipToOutline = true
            setImageDrawable(letterDrawable)
        }
        avatarFrame.addView(avatar, FrameLayout.LayoutParams(avatarSize, avatarSize, Gravity.CENTER))
        val cachedUrl = pl.sinusoide.sinuschat.util.ContactCache.getAvatarUrl(ctx, call.otherUserHash)
        if (!cachedUrl.isNullOrEmpty()) {
            com.bumptech.glide.Glide.with(this).load(cachedUrl).circleCrop()
                .placeholder(letterDrawable).error(letterDrawable)
                .into(avatar)
        }
        val (dirIcon, dirColor, dirLabel) = when (call.direction) {
            CallLogRepository.OUTGOING -> Triple(R.drawable.ic_call_made, cAccent, R.string.call_dir_outgoing)
            CallLogRepository.MISSED -> Triple(R.drawable.ic_call_missed, cRed, R.string.call_dir_missed)
            CallLogRepository.DECLINED -> Triple(R.drawable.ic_call_end, cOrange, R.string.call_dir_declined)
            else -> Triple(R.drawable.ic_call_made, cGreen, R.string.call_dir_incoming)
        }
        avatarFrame.addView(android.widget.ImageView(ctx).apply {
            setImageResource(dirIcon); setColorFilter(android.graphics.Color.WHITE)
            if (call.direction == CallLogRepository.INCOMING) rotation = 180f
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(dirColor); setStroke((2.5f * dp).toInt(), pl.sinusoide.sinuschat.ui.ThemeUi.backgroundSolid(ctx))
            }
            val p = (4 * dp).toInt(); setPadding(p, p, p, p)
        }, FrameLayout.LayoutParams((22 * dp).toInt(), (22 * dp).toInt(), Gravity.BOTTOM or Gravity.END))
        row.addView(avatarFrame, LinearLayout.LayoutParams(frameSize, frameSize).apply {
            marginEnd = (12 * dp).toInt()
        })

        val middle = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        middle.addView(TextView(ctx).apply {
            text = call.otherUserName
            textSize = 16f
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(if (missed) cRed else cPrimary)
            if (missed) typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        val detail = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (3 * dp).toInt(), 0, 0)
        }
        detail.addView(android.widget.ImageView(ctx).apply {
            setImageResource(if (call.isVideo) R.drawable.ic_call_video_on else R.drawable.ic_call_answer)
            setColorFilter(if (missed) cRed else cSecondary)
        }, LinearLayout.LayoutParams((14 * dp).toInt(), (14 * dp).toInt()).apply { marginEnd = (5 * dp).toInt() })
        val durStr = if (call.durationSec > 0)
            " · " + String.format(Locale.getDefault(), "%d:%02d", call.durationSec / 60, call.durationSec % 60) else ""
        detail.addView(TextView(ctx).apply {
            text = getString(dirLabel) + durStr
            textSize = 13f
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(if (missed) cRed else cSecondary)
        })
        middle.addView(detail)
        row.addView(middle, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = (8 * dp).toInt() })

        row.addView(TextView(ctx).apply {
            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(call.startedAtMs))
            textSize = 12.5f; setTextColor(cSecondary)
            setPadding(0, 0, (10 * dp).toInt(), 0)
        })

        val callBtn = android.widget.ImageView(ctx).apply {
            setImageResource(if (call.isVideo) R.drawable.ic_call_video_on else R.drawable.ic_call_answer)
            setColorFilter(cAccent)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor((cAccent and 0x00FFFFFF) or 0x24000000)
            }
            val p = (10 * dp).toInt(); setPadding(p, p, p, p)
            contentDescription = getString(R.string.call_back_cd)
            isClickable = true
            setOnClickListener { v ->
                v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80).withEndAction {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(220)
                        .setInterpolator(android.view.animation.OvershootInterpolator(2.5f)).start()
                }.start()
                callBack(call)
            }
        }
        row.addView(callBtn, LinearLayout.LayoutParams((42 * dp).toInt(), (42 * dp).toInt()))

        return row
    }

    private fun swipeableRow(call: CallEntity): View =
        pl.sinusoide.sinuschat.util.SwipeToDeleteLayout(
            requireContext(), callRow(call),
            getString(R.string.delete), R.drawable.ic_delete
        ) { row -> confirmDeleteCall(call, row) }

    private fun confirmDeleteCall(call: CallEntity, row: pl.sinusoide.sinuschat.util.SwipeToDeleteLayout) {
        val ctx = requireContext()
        val d = dp
        val red = 0xFFE5484D.toInt()
        val secondary = pl.sinusoide.sinuschat.ui.ThemeUi.textSecondary(ctx)
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(ctx, R.style.Theme_SinusChat_BottomSheet)
        var confirmed = false

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((24 * d).toInt(), (12 * d).toInt(), (24 * d).toInt(), (20 * d).toInt())
        }
        root.addView(View(ctx).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 3 * d; setColor(pl.sinusoide.sinuschat.ui.ThemeUi.handle(context))
            }
        }, LinearLayout.LayoutParams((36 * d).toInt(), (4 * d).toInt()).apply {
            bottomMargin = (20 * d).toInt()
        })
        root.addView(android.widget.ImageView(ctx).apply {
            setImageResource(R.drawable.ic_delete); setColorFilter(red)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor((red and 0x00FFFFFF) or 0x26000000)
            }
            val p = (16 * d).toInt(); setPadding(p, p, p, p)
        }, LinearLayout.LayoutParams((64 * d).toInt(), (64 * d).toInt()).apply {
            bottomMargin = (16 * d).toInt()
        })
        root.addView(TextView(ctx).apply {
            text = getString(R.string.delete_call_q)
            textSize = 20f; setTextColor(pl.sinusoide.sinuschat.ui.ThemeUi.textPrimary(context))
            typeface = android.graphics.Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        })
        root.addView(TextView(ctx).apply {
            text = getString(R.string.delete_call_desc)
            textSize = 14f; setTextColor(secondary); gravity = Gravity.CENTER
            setPadding(0, (8 * d).toInt(), 0, (18 * d).toInt())
        })

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 16 * d; setColor(pl.sinusoide.sinuschat.ui.ThemeUi.card(ctx))
            }
            setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())
        }
        val avatarSize = (40 * d).toInt()
        card.addView(android.widget.ImageView(ctx).apply {
            setImageBitmap(AvatarHelper.createLetterBitmap(
                AvatarHelper.initialOf(call.otherUserName),
                AvatarHelper.getContactColor(ctx, call.otherUserHash), avatarSize))
            pl.sinusoide.sinuschat.util.ContactCache.getAvatarUrl(ctx, call.otherUserHash)
                ?.takeIf { it.isNotEmpty() }
                ?.let { url -> com.bumptech.glide.Glide.with(this).load(url).circleCrop().into(this) }
        }, LinearLayout.LayoutParams(avatarSize, avatarSize).apply { marginEnd = (12 * d).toInt() })
        val info = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val missed = call.direction == CallLogRepository.MISSED
        info.addView(TextView(ctx).apply {
            text = call.otherUserName; textSize = 15f
            setTextColor(if (missed) red else pl.sinusoide.sinuschat.ui.ThemeUi.textPrimary(ctx))
        })
        val typeWord = if (call.isVideo) getString(R.string.call_label_video) else getString(R.string.call_label_voice)
        val timeStr = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(call.startedAtMs))
        info.addView(TextView(ctx).apply {
            text = typeWord + " · " + timeStr; textSize = 12.5f; setTextColor(secondary)
        })
        card.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(android.widget.ImageView(ctx).apply {
            setImageResource(if (call.isVideo) R.drawable.ic_call_video_on else R.drawable.ic_call_answer)
            setColorFilter(secondary)
        }, LinearLayout.LayoutParams((20 * d).toInt(), (20 * d).toInt()))
        root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = (22 * d).toInt() })

        root.addView(TextView(ctx).apply {
            text = getString(R.string.delete)
            textSize = 16f; setTextColor(android.graphics.Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 26 * d; setColor(red)
            }
            setPadding(0, (14 * d).toInt(), 0, (14 * d).toInt())
            isClickable = true
            setOnClickListener {
                confirmed = true
                sheet.dismiss()
                row.removeAnimated { deleteCall(call) }
            }
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(TextView(ctx).apply {
            text = getString(R.string.cancel)
            textSize = 15f; setTextColor(secondary); gravity = Gravity.CENTER
            setPadding(0, (14 * d).toInt(), 0, (4 * d).toInt())
            isClickable = true
            setOnClickListener { sheet.dismiss() }
        }, LinearLayout.LayoutParams(-1, -2))

        sheet.setOnDismissListener { if (!confirmed) row.close() }
        sheet.setContentView(root)
        pl.sinusoide.sinuschat.ui.FlowGlass.show(sheet)
    }

    private fun deleteCall(call: CallEntity) {
        val ctx = context?.applicationContext ?: return
        pl.sinusoide.sinuschat.util.AppExecutor.io {
            SinusChatDatabase.getInstance(ctx).callDao().deleteById(call.id)
        }
        activity?.let { pl.sinusoide.sinuschat.util.PopupHelper.show(it, getString(R.string.call_deleted)) }
    }

    override fun onPause() {
        super.onPause()
        pl.sinusoide.sinuschat.util.SwipeToDeleteLayout.closeOpenRow()
    }

    private fun callBack(call: CallEntity) {
        CallActivity.startAsCaller(
            requireContext(), call.conversationId, call.otherUserHash,
            call.otherUserName, call.isVideo
        )
    }
}
