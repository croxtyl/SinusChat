package pl.sinusoide.sinuschat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ConversationAdapter(
    private val onOpen  : (Conversation) -> Unit,
    private val onDelete: (Conversation) -> Unit
) : RecyclerView.Adapter<ConversationAdapter.VH>() {

    private val items = mutableListOf<Conversation>()

    fun setItems(list: List<Conversation>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    fun remove(id: String) {
        val idx = items.indexOfFirst { it.id == id }
        if (idx >= 0) { items.removeAt(idx); notifyItemRemoved(idx) }
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_conversation, parent, false)
        val ctx = parent.context
        val ui = pl.sinusoide.sinuschat.ui.ThemeUi
        val d = ctx.resources.displayMetrics.density
        v.background = android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(
                if (pl.sinusoide.sinuschat.util.ThemeManager.isFlow(ctx) || ui.isDark(ctx)) 0x22FFFFFF else 0x14000000),
            null, android.graphics.drawable.GradientDrawable().apply { cornerRadius = 16 * d; setColor(android.graphics.Color.WHITE) })
        v.findViewById<View>(R.id.iv_conv_icon).background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor((ui.accent(ctx) and 0x00FFFFFF) or 0x1F000000)
        }
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val conv = items[position]
        holder.tvTitle.text = conv.title
        holder.tvDate.text  = ConversationManager.formatDate(conv.timestamp)
        holder.itemView.setOnClickListener { onOpen(conv) }
        holder.btnDelete.setOnClickListener { onDelete(conv) }
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvTitle  : TextView    = v.findViewById(R.id.tv_conv_title)
        val tvDate   : TextView    = v.findViewById(R.id.tv_conv_date)
        val btnDelete: ImageButton = v.findViewById(R.id.btn_conv_delete)
    }
}
