package pl.sinusoide.sinuschat.messenger

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import pl.sinusoide.sinuschat.R
import pl.sinusoide.sinuschat.util.AvatarHelper
import pl.sinusoide.sinuschat.util.MuteSettings
import pl.sinusoide.sinuschat.util.BlockList
import pl.sinusoide.sinuschat.util.PrivacySettings
import java.text.SimpleDateFormat
import java.util.*

class ConversationListAdapter(
    private val myPhoneHash: String,
    private val onClick: (ConversationItem) -> Unit,
    private val onLongClick: ((ConversationItem, View) -> Unit)? = null
) : ListAdapter<ConversationItem, ConversationListAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ConversationItem>() {
            override fun areItemsTheSame(a: ConversationItem, b: ConversationItem) = a.id == b.id
            override fun areContentsTheSame(a: ConversationItem, b: ConversationItem) = a == b
        }
    }

    private var allItems: List<ConversationItem> = emptyList()

    val allConversations: List<ConversationItem> get() = allItems

    var onFiltered: ((shown: Int) -> Unit)? = null

    var pins: () -> Map<String, Long> = { emptyMap() }

    private fun ordered(list: List<ConversationItem>): List<ConversationItem> {
        val p = pins()
        val marked = list.map { if ((it.id in p) != it.isPinned) it.copy(isPinned = it.id in p) else it }
        if (p.isEmpty()) return marked
        val (top, rest) = marked.partition { it.isPinned }
        return top.sortedByDescending { p[it.id] ?: 0L } + rest
    }

    var query: String = ""
        set(value) {
            field = value.trim()
            super.submitList(applyQuery(allItems)) { onFiltered?.invoke(currentList.size) }
        }

    override fun submitList(list: List<ConversationItem>?) = submitList(list, null)

    override fun submitList(list: List<ConversationItem>?, commitCallback: Runnable?) {
        allItems = ordered(list.orEmpty())
        super.submitList(applyQuery(allItems)) {
            if (query.isNotEmpty()) onFiltered?.invoke(currentList.size)
            commitCallback?.run()
        }
    }

    private fun applyQuery(items: List<ConversationItem>): List<ConversationItem> {
        if (query.isEmpty()) return items
        val q = fold(query)
        return items.filter { fold(it.otherUserName).contains(q) }
    }

    private fun fold(s: String): String =
        java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace('ł', 'l')

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val avatarLetter: TextView = view.findViewById(R.id.conv_avatar_letter)
        val avatarImage: ImageView = view.findViewById(R.id.conv_avatar_image)
        val onlineIndicator: View? = view.findViewById(R.id.conv_online_indicator)
        val name: TextView = view.findViewById(R.id.conv_name)
        val lastMsg: TextView = view.findViewById(R.id.conv_last_message)
        val lockIcon: ImageView = view.findViewById(R.id.conv_lock_icon)
        val time: TextView = view.findViewById(R.id.conv_time)
        val badge: TextView = view.findViewById(R.id.conv_unread_badge)
        val muteIcon: ImageView = view.findViewById(R.id.conv_mute_icon)
        val pinIcon: ImageView? = view.findViewById(R.id.conv_pin_icon)
    }

    private var colorsResolved = false
    private var cPrimary = 0
    private var cSecondary = 0
    private var cMuted = 0
    private var cAccent = 0
    private var cBackground = 0

    private fun resolveColors(ctx: android.content.Context) {
        if (colorsResolved) return
        fun attr(id: Int, fallback: Int): Int {
            val tv = android.util.TypedValue()
            return pl.sinusoide.sinuschat.ui.ThemeUi.attrOrNull(ctx, id) ?: fallback
        }
        cPrimary = attr(R.attr.sinuschat_text_primary, Color.WHITE)
        cSecondary = attr(R.attr.sinuschat_text_secondary, Color.parseColor("#7F91A4"))
        cMuted = attr(R.attr.sinuschat_text_muted, Color.parseColor("#5E6D7E"))
        cBackground = attr(R.attr.sinuschat_background_solid, Color.parseColor("#0E1621"))
        cAccent = pl.sinusoide.sinuschat.ui.ThemeUi.accent(ctx)
        colorsResolved = true
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        resolveColors(parent.context)
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_conversation_e2e, parent, false)
        val vh = VH(view)
        val d = parent.resources.displayMetrics.density
        vh.onlineIndicator?.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(Color.parseColor("#3FB97A"))
            setStroke((2.5f * d).toInt(), cBackground)
        }
        return vh
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val ctx = holder.itemView.context

        val color = AvatarHelper.getContactColor(ctx, item.otherUserHash)
        val img = holder.avatarImage
        holder.avatarLetter.visibility = View.GONE
        img.visibility = View.VISIBLE
        img.clipToOutline = true

        Glide.with(ctx).clear(img)
        img.setImageDrawable(null)

        if (item.isGroup) {
            AvatarHelper.loadGroupAvatar(ctx, img, item.otherUserName, item.groupColor, item.avatarUrl)
        } else if (!item.avatarUrl.isNullOrEmpty()) {
            val fallback = BitmapDrawable(
                ctx.resources,
                AvatarHelper.createLetterBitmap(
                    AvatarHelper.initialOf(item.otherUserName),
                    color,
                    (44 * ctx.resources.displayMetrics.density).toInt()
                )
            )
            Glide.with(ctx)
                .load(item.avatarUrl)
                .signature(ObjectKey(item.avatarUrl))
                .circleCrop()
                .placeholder(fallback)
                .error(fallback)
                .into(img)
        } else {
            AvatarHelper.setLetterAvatar(ctx, img, item.otherUserName, color)
        }

        holder.name.text = item.otherUserName
        holder.onlineIndicator?.visibility = if (item.isOnline && !item.isGroup && !BlockList.isCutOff(ctx, item.otherUserHash)) View.VISIBLE else View.GONE

        val hidePreview = PrivacySettings.shouldHidePreview(ctx)
        
        if (hidePreview) {
            holder.lockIcon.visibility = View.VISIBLE
            holder.lastMsg.text = ctx.getString(R.string.locked_release).split(" — ").first()
            holder.time.visibility = View.INVISIBLE
        } else {
            holder.lockIcon.visibility = View.GONE
            
            holder.lastMsg.text = item.lastMessage
            
            val hasTime = item.lastMessageAt.seconds > 0L
            holder.time.visibility = if (hasTime && item.lastMessage.isNotEmpty()) View.VISIBLE else View.INVISIBLE
            holder.time.text = formatTime(item.lastMessageAt.toDate(), ctx)
        }

        val muted = MuteSettings.isMuted(ctx, item.id)
        holder.muteIcon.visibility = if (muted) View.VISIBLE else View.GONE
        holder.pinIcon?.visibility = if (item.isPinned) View.VISIBLE else View.GONE

        if (item.unreadCount > 0) {
            holder.badge.visibility = View.VISIBLE
            holder.badge.text = if (item.unreadCount > 99) "99+" else item.unreadCount.toString()
            holder.badge.background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 10 * ctx.resources.displayMetrics.density
                setColor(if (muted) cMuted else cAccent)
            }
            holder.name.setTypeface(null, Typeface.BOLD)
            holder.lastMsg.setTypeface(null, if (muted) Typeface.NORMAL else Typeface.BOLD)
            holder.lastMsg.setTextColor(if (muted) cSecondary else cPrimary)
            holder.time.setTextColor(if (muted) cMuted else cAccent)
        } else {
            holder.badge.visibility = View.GONE
            holder.name.setTypeface(null, Typeface.NORMAL)
            holder.lastMsg.setTypeface(null, Typeface.NORMAL)
            holder.lastMsg.setTextColor(cSecondary)
            holder.time.setTextColor(cMuted)
        }

        holder.itemView.setOnClickListener { onClick(item) }

        holder.itemView.setOnLongClickListener { view ->
            onLongClick?.invoke(item, view)
            true
        }
    }

    private var yesterdayLabel: String? = null

    private fun formatTime(date: Date, ctx: android.content.Context): String {
        val msg = Calendar.getInstance().apply { time = date }
        val today = Calendar.getInstance()
        fun startOfDay(c: Calendar) = (c.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val days = ((startOfDay(today) - startOfDay(msg)) / 86_400_000L).toInt()
        return when {
            days <= 0 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
            days == 1 -> yesterdayLabel ?: ctx.getString(R.string.calls_yesterday).also { yesterdayLabel = it }
            days < 7 -> SimpleDateFormat("EEE", Locale.getDefault()).format(date)
                .replaceFirstChar { it.uppercase() }
            today.get(Calendar.YEAR) == msg.get(Calendar.YEAR) ->
                SimpleDateFormat("d MMM", Locale.getDefault()).format(date)
            else -> SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(date)
        }
    }
}
