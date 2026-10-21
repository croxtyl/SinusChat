package pl.sinusoide.sinuschat

import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.VH>() {

    private val messages = mutableListOf<ChatMessage>()

    var onFilePreview: ((SiniaFile) -> Unit)? = null
    var onFileSave: ((SiniaFile) -> Unit)? = null

    fun add(msg: ChatMessage) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    fun updateLast(
        text: String, isStreaming: Boolean = false,
        thinking: String? = null, file: SiniaFile? = null
    ) {
        if (messages.isEmpty()) return
        val last = messages.last()
        messages[messages.size - 1] = last.copy(
            text = text, isStreaming = isStreaming,
            thinking = thinking ?: last.thinking, file = file ?: last.file
        )
        notifyItemChanged(messages.size - 1)
    }

    fun clearAll() {
        messages.clear()
        notifyDataSetChanged()
    }

    fun getAll(): List<ChatMessage> = messages.toList()

    fun removeLastIfStreaming() {
        if (messages.isNotEmpty() && messages.last().isStreaming) {
            messages.removeAt(messages.size - 1)
            notifyItemRemoved(messages.size)
        }
    }

    override fun getItemCount() = messages.size
    override fun getItemViewType(pos: Int) =
        if (messages[pos].role == ChatMessage.Role.USER) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = if (viewType == 0) R.layout.item_chat_user else R.layout.item_chat_sinia
        return VH(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(messages[position])
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvText  : TextView  = view.findViewById(R.id.tv_msg_text)
        private val ivImage : ImageView? = view.findViewById(R.id.iv_msg_image)
        private val ivEmoji : ImageView? = view.findViewById(R.id.iv_sinia_emoji)
        private val dots     : pl.sinusoide.sinuschat.ui.ThinkingDotsView? = view.findViewById(R.id.thinking_dots)
        private val thinkBox : View?     = view.findViewById(R.id.layout_thinking)
        private val thinkLbl : TextView? = view.findViewById(R.id.tv_thinking_label)
        private val thinkTxt : TextView? = view.findViewById(R.id.tv_thinking_text)
        private val fileCard : View?     = view.findViewById(R.id.file_card)
        private val fileIcon : ImageView? = view.findViewById(R.id.iv_file_icon)
        private val fileName : TextView? = view.findViewById(R.id.tv_file_name)
        private val fileType : TextView? = view.findViewById(R.id.tv_file_type)

        fun bind(msg: ChatMessage) {
            val thinking = msg.isStreaming && msg.text.isBlank()
            thinkBox?.visibility = if (thinking) View.VISIBLE else View.GONE
            tvText.visibility    = if (thinking) View.GONE else View.VISIBLE
            val thought = if (thinking) tailOf(msg.thinking) else ""
            thinkLbl?.visibility = if (thought.isNotBlank()) View.VISIBLE else View.GONE
            thinkTxt?.visibility = if (thought.isNotBlank()) View.VISIBLE else View.GONE
            thinkTxt?.text = thought
            val shown = if (msg.isStreaming) msg.text + "▌" else msg.text
            tvText.text = pl.sinusoide.sinuschat.ui.MathText.format(shown)
            if (!thinking && msg.text.isBlank() && msg.file != null) tvText.visibility = View.GONE
            styleBubble(msg.role == ChatMessage.Role.USER)
            bindFile(msg.file, thinking)

            ivImage?.let { iv ->
                if (msg.image != null) {
                    iv.visibility = View.VISIBLE
                    iv.setImageBitmap(msg.image)
                } else {
                    iv.visibility = View.GONE
                }
            }

            ivEmoji?.let { iv ->
                val ctx = iv.context
                val tint = when (msg.emotion) {
                    ChatMessage.Emotion.LOVE -> 0xFFFF4F8B.toInt()
                    ChatMessage.Emotion.SAD  -> pl.sinusoide.sinuschat.ui.ThemeUi.textSecondary(ctx)
                    else -> pl.sinusoide.sinuschat.ui.ThemeUi.accent(ctx)
                }
                iv.setColorFilter(tint, PorterDuff.Mode.SRC_IN)
                iv.background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor((tint and 0x00FFFFFF) or 0x26000000)
                }
            }
        }

        private fun bindFile(file: SiniaFile?, thinking: Boolean) {
            val card = fileCard ?: return
            if (file == null || thinking) { card.visibility = View.GONE; return }
            val ctx = card.context
            val ui = pl.sinusoide.sinuschat.ui.ThemeUi
            val flow = pl.sinusoide.sinuschat.util.ThemeManager.isFlow(ctx)
            val d = ctx.resources.displayMetrics.density
            val accent = ui.accent(ctx)
            val onCard = if (flow) android.graphics.Color.WHITE else ui.textPrimary(ctx)

            card.visibility = View.VISIBLE
            card.background = if (flow)
                pl.sinusoide.sinuschat.ui.GlassDrawable(d, null, false, 18 * d, null, emphasis = false)
            else android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 18 * d; setColor(ui.card(ctx))
                setStroke(d.toInt().coerceAtLeast(1), ui.border(ctx))
            }

            val (iconRes, typeRes) = when (file.kind) {
                SiniaFile.Kind.HTML -> R.drawable.ic_code to R.string.sinia_file_kind_html
                SiniaFile.Kind.DOCX -> R.drawable.ic_document to R.string.sinia_file_kind_docx
                SiniaFile.Kind.XLSX -> R.drawable.ic_table to R.string.sinia_file_kind_xlsx
                SiniaFile.Kind.PPTX -> R.drawable.ic_slides to R.string.sinia_file_kind_pptx
                else -> R.drawable.ic_code to R.string.sinia_file_kind_code
            }
            fileIcon?.apply {
                setImageResource(iconRes)
                imageTintList = android.content.res.ColorStateList.valueOf(onCard)
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor((accent and 0x00FFFFFF) or 0x40000000)
                }
            }
            fileName?.apply { text = file.name; setTextColor(onCard) }
            fileType?.apply {
                text = ctx.getString(typeRes)
                setTextColor(if (flow) 0xB3FFFFFF.toInt() else ui.textSecondary(ctx))
            }

            val preview = card.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_file_preview)
            val save = card.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_file_save)
            for ((btn, filled) in listOf(preview to true, save to false)) {
                btn.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    if (filled) accent else ((accent and 0x00FFFFFF) or 0x33000000))
                val fg = if (filled) (if (flow) android.graphics.Color.WHITE else ui.onAccent(ctx)) else onCard
                btn.setTextColor(fg)
                btn.iconTint = android.content.res.ColorStateList.valueOf(fg)
            }
            preview.setOnClickListener { onFilePreview?.invoke(file) }
            save.setOnClickListener { onFileSave?.invoke(file) }
        }

        private fun styleBubble(mine: Boolean) {
            val ctx = tvText.context
            val ui = pl.sinusoide.sinuschat.ui.ThemeUi
            val d = ctx.resources.displayMetrics.density
            val big = 18 * d; val small = 5 * d
            if (pl.sinusoide.sinuschat.util.ThemeManager.isFlow(ctx)) {
                tvText.background = pl.sinusoide.sinuschat.ui.FlowGlass.bubble(ctx, mine)
                tvText.setTextColor(android.graphics.Color.WHITE)
                thinkBox?.background = pl.sinusoide.sinuschat.ui.FlowGlass.bubble(ctx, mine)
                dots?.dotColor = 0x8CFFFFFF.toInt()
                dots?.highlightColor = android.graphics.Color.WHITE
                thinkLbl?.setTextColor(android.graphics.Color.WHITE)
                thinkTxt?.setTextColor(0xB3FFFFFF.toInt())
                return
            }
            fun bubble() = android.graphics.drawable.GradientDrawable().apply {
                if (mine) {
                    cornerRadii = floatArrayOf(big, big, small, small, big, big, big, big)
                    setColor(ui.accent(ctx))
                } else {
                    cornerRadii = floatArrayOf(small, small, big, big, big, big, big, big)
                    setColor(ui.card(ctx))
                    setStroke(d.toInt().coerceAtLeast(1), ui.border(ctx))
                }
            }
            tvText.background = bubble()
            tvText.setTextColor(if (mine) ui.onAccent(ctx) else ui.textPrimary(ctx))
            thinkBox?.background = bubble()
            dots?.dotColor = ui.textMuted(ctx)
            dots?.highlightColor = ui.accent(ctx)
            thinkLbl?.setTextColor(ui.accent(ctx))
            thinkTxt?.setTextColor(ui.textSecondary(ctx))
        }

        private fun tailOf(thinking: String): String {
            val clean = thinking.replace(Regex("""\s+"""), " ").trim()
            if (clean.length <= 160) return clean
            val cut = clean.takeLast(160)
            val dot = cut.indexOfFirst { it == '.' || it == '!' || it == '?' }
            return if (dot in 0..60) cut.substring(dot + 1).trim() else cut.trimStart()
        }
    }
}
