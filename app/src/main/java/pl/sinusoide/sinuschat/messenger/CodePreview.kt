package pl.sinusoide.sinuschat.messenger

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import pl.sinusoide.sinuschat.R
import pl.sinusoide.sinuschat.util.AppExecutor
import pl.sinusoide.sinuschat.util.CodeHighlighter
import pl.sinusoide.sinuschat.util.CodeSnippets
import pl.sinusoide.sinuschat.util.FileIcons
import pl.sinusoide.sinuschat.util.MediaManager
import java.io.File

object CodePreview {

    private const val INLINE_LINES = 18
    private const val INLINE_BYTES = 64 * 1024
    private const val INLINE_LINE_CHARS = 240
    private const val VIEW_BYTES = 512 * 1024
    private const val VIEW_LINES = 4000
    private const val VIEW_LINE_CHARS = 2000
    private const val COPY_BYTES = 1024 * 1024

    class Loaded(val text: CharSequence, val shownLines: Int, val totalLines: Int, val truncated: Boolean, val error: Int)

    private val cache = LruCache<String, Loaded>(24)

    private fun codeBg(dark: Boolean) = if (dark) 0xFF0F1620.toInt() else 0xFFF6F8FA.toInt()
    private fun codeFg(dark: Boolean) = if (dark) 0xFFD6DEEB.toInt() else 0xFF1F2328.toInt()
    private fun codeDim(dark: Boolean) = if (dark) 0xFF7A8794.toInt() else 0xFF6E7781.toInt()

    private fun color(kind: CodeHighlighter.Kind, dark: Boolean): Int = if (dark) when (kind) {
        CodeHighlighter.Kind.KEYWORD -> 0xFFC792EA
        CodeHighlighter.Kind.STRING -> 0xFFC3E88D
        CodeHighlighter.Kind.NUMBER -> 0xFFF78C6C
        CodeHighlighter.Kind.COMMENT -> 0xFF6E7F8F
        CodeHighlighter.Kind.FUNCTION -> 0xFF82AAFF
        CodeHighlighter.Kind.TYPE -> 0xFFFFCB6B
        CodeHighlighter.Kind.ANNOTATION -> 0xFF89DDFF
        CodeHighlighter.Kind.TAG -> 0xFFF07178
        CodeHighlighter.Kind.ATTR -> 0xFFFFCB6B
        CodeHighlighter.Kind.KEY -> 0xFF82AAFF
        CodeHighlighter.Kind.ADDED -> 0xFF7EE787
        CodeHighlighter.Kind.REMOVED -> 0xFFFF7B72
    }.toInt() else when (kind) {
        CodeHighlighter.Kind.KEYWORD -> 0xFFCF222E
        CodeHighlighter.Kind.STRING -> 0xFF0A3069
        CodeHighlighter.Kind.NUMBER -> 0xFF0550AE
        CodeHighlighter.Kind.COMMENT -> 0xFF6E7781
        CodeHighlighter.Kind.FUNCTION -> 0xFF8250DF
        CodeHighlighter.Kind.TYPE -> 0xFF953800
        CodeHighlighter.Kind.ANNOTATION -> 0xFF0550AE
        CodeHighlighter.Kind.TAG -> 0xFF116329
        CodeHighlighter.Kind.ATTR -> 0xFF0550AE
        CodeHighlighter.Kind.KEY -> 0xFF0550AE
        CodeHighlighter.Kind.ADDED -> 0xFF116329
        CodeHighlighter.Kind.REMOVED -> 0xFFCF222E
    }.toInt()

    fun languageLabel(ctx: Context, fileName: String): String =
        CodeHighlighter.language(fileName)?.name?.takeIf { it.isNotEmpty() } ?: ctx.getString(R.string.code_plain)

    private fun readHead(file: File, cap: Int): ByteArray? = try {
        file.inputStream().use { input ->
            val buf = ByteArray(minOf(cap.toLong(), file.length()).toInt().coerceAtLeast(0))
            var off = 0
            while (off < buf.size) {
                val n = input.read(buf, off, buf.size - off)
                if (n < 0) break
                off += n
            }
            if (off == buf.size) buf else buf.copyOf(off)
        }
    } catch (_: Exception) { null }

    private fun countLines(file: File, bytes: ByteArray): Int {
        if (file.length() > bytes.size) return -1
        if (bytes.isEmpty()) return 0
        var n = bytes.count { it == '\n'.code.toByte() }
        if (bytes.last() != '\n'.code.toByte()) n++
        return n
    }

    fun load(file: File, fileName: String, inline: Boolean, dark: Boolean): Loaded {
        val cap = if (inline) INLINE_BYTES else VIEW_BYTES
        val bytes = readHead(file, cap) ?: return Loaded("", 0, 0, false, R.string.code_read_failed)
        if (bytes.take(8192).any { it == 0.toByte() }) return Loaded("", 0, 0, false, R.string.code_not_text)
        val all = String(bytes, Charsets.UTF_8).removePrefix("﻿")
        return highlight(all, CodeHighlighter.language(fileName)?.syntax, inline, dark, countLines(file, bytes))
    }

    private fun snippetLoaded(seg: CodeSnippets.Segment, inline: Boolean, dark: Boolean): Loaded =
        highlight(seg.text, seg.lang?.syntax ?: CodeHighlighter.GENERIC, inline, dark, snippetLines(seg))

    private fun snippetLines(seg: CodeSnippets.Segment): Int = seg.text.count { it == '\n' } + 1

    private fun highlight(source: String, syntax: CodeHighlighter.Syntax?, inline: Boolean, dark: Boolean, total: Int): Loaded {
        val maxLines = if (inline) INLINE_LINES else VIEW_LINES
        val maxChars = if (inline) INLINE_LINE_CHARS else VIEW_LINE_CHARS
        val all = source.replace("\r\n", "\n").replace('\r', '\n')
        val lines = all.split('\n').let { if (it.size > 1 && it.last().isEmpty()) it.dropLast(1) else it }
        val shown = lines.take(maxLines).map { l ->
            val t = l.replace("\t", "    ")
            if (t.length > maxChars) t.take(maxChars) + "…" else t
        }
        val text = shown.joinToString("\n")
        val sb = SpannableStringBuilder(text)
        if (syntax != null) {
            for (t in CodeHighlighter.tokenize(text, syntax)) {
                val s = t.start.coerceIn(0, text.length)
                val e = t.end.coerceIn(s, text.length)
                if (e <= s) continue
                sb.setSpan(ForegroundColorSpan(color(t.kind, dark)), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (t.kind == CodeHighlighter.Kind.COMMENT) sb.setSpan(StyleSpan(Typeface.ITALIC), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        val truncated = total < 0 || lines.size > shown.size
        return Loaded(sb, shown.size, total, truncated, 0)
    }

    private fun copyFile(ctx: Context, file: File, fileName: String) {
        val app = ctx.applicationContext
        AppExecutor.io {
            val bytes = readHead(file, COPY_BYTES)
            AppExecutor.main {
                if (bytes == null || bytes.take(8192).any { it == 0.toByte() }) {
                    Toast.makeText(app, R.string.code_read_failed, Toast.LENGTH_SHORT).show()
                    return@main
                }
                val cm = app.getSystemService(ClipboardManager::class.java) ?: return@main
                cm.setPrimaryClip(ClipData.newPlainText(fileName, String(bytes, Charsets.UTF_8)))
                if (Build.VERSION.SDK_INT < 33) Toast.makeText(app, R.string.copied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun copyText(ctx: Context, text: String) {
        val app = ctx.applicationContext
        val cm = app.getSystemService(ClipboardManager::class.java) ?: return
        cm.setPrimaryClip(ClipData.newPlainText(app.getString(R.string.code_snippet), text))
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(app, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    fun snippetLabel(ctx: Context, seg: CodeSnippets.Segment): String = when {
        seg.lang == null -> ctx.getString(R.string.code_generic)
        seg.lang.name.isEmpty() -> ctx.getString(R.string.code_plain)
        else -> seg.lang.name
    }

    private fun linesLabel(ctx: Context, file: File, loaded: Loaded): String =
        if (loaded.totalLines >= 0) ctx.resources.getQuantityString(R.plurals.code_lines, loaded.totalLines, loaded.totalLines)
        else MediaManager.formatSize(file.length())

    private fun iconButton(ctx: Context, icon: Int, tint: Int, desc: String, sizeDp: Int, padDp: Int, onClick: () -> Unit) =
        ImageView(ctx).apply {
            val d = ctx.resources.displayMetrics.density
            setImageResource(icon); setColorFilter(tint)
            val p = (padDp * d).toInt(); setPadding(p, p, p, p)
            contentDescription = desc
            isClickable = true
            background = android.util.TypedValue().let { tv ->
                ctx.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)
                androidx.core.content.ContextCompat.getDrawable(ctx, tv.resourceId)
            }
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams((sizeDp * d).toInt(), (sizeDp * d).toInt())
        }

    private class Card(val icon: ImageView, val code: TextView, val footer: TextView)

    private fun buildCard(
        card: LinearLayout, label: String, icon: Int, dark: Boolean,
        onCopy: () -> Unit, onOpen: () -> Unit, onLongPress: () -> Unit
    ): Card {
        val ctx = card.context
        val d = ctx.resources.displayMetrics.density
        card.orientation = LinearLayout.VERTICAL
        card.background = GradientDrawable().apply { cornerRadius = 12 * d; setColor(codeBg(dark)) }
        card.setPadding((10 * d).toInt(), (6 * d).toInt(), (6 * d).toInt(), (8 * d).toInt())

        val dim = codeDim(dark)
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val iconView = ImageView(ctx).apply {
            setImageResource(icon); setColorFilter(dim)
        }
        header.addView(iconView, LinearLayout.LayoutParams((14 * d).toInt(), (14 * d).toInt()).apply { marginEnd = (6 * d).toInt() })
        header.addView(TextView(ctx).apply {
            text = label
            textSize = 11.5f; setTextColor(dim); typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(iconButton(ctx, R.drawable.ic_copy, dim, ctx.getString(R.string.code_copy), 30, 7) { onCopy() })
        card.addView(header, LinearLayout.LayoutParams(-1, -2))

        val code = TextView(ctx).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setTextColor(codeFg(dark))
            setLineSpacing(0f, 1.15f)
            setPadding(0, (2 * d).toInt(), (8 * d).toInt(), (2 * d).toInt())
            isClickable = true
            setOnClickListener { onOpen() }
            setOnLongClickListener { onLongPress(); true }
        }
        val hsv = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(code, ViewGroup.LayoutParams(-2, -2))
        }
        card.addView(hsv, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (2 * d).toInt() })

        val footer = TextView(ctx).apply {
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(pl.sinusoide.sinuschat.ui.ThemeUi.accent(ctx))
            setPadding(0, (8 * d).toInt(), 0, (2 * d).toInt())
            visibility = View.GONE
            isClickable = true
            setOnClickListener { onOpen() }
        }
        card.addView(footer, LinearLayout.LayoutParams(-2, -2))
        return Card(iconView, code, footer)
    }

    fun fillSnippets(
        box: LinearLayout, key: String, segments: List<CodeSnippets.Segment>, dark: Boolean, width: Int,
        textView: (String) -> View, onOpen: (CodeSnippets.Segment) -> Unit, onLongPress: () -> Unit
    ) {
        val ctx = box.context
        val d = ctx.resources.displayMetrics.density
        stopAnim(box)
        box.animate().cancel()
        box.alpha = 1f
        box.removeAllViews()
        box.tag = key
        box.orientation = LinearLayout.VERTICAL
        box.background = null
        box.setPadding(0, 0, 0, 0)
        box.visibility = View.VISIBLE
        box.layoutParams = box.layoutParams.apply { this.width = width; height = ViewGroup.LayoutParams.WRAP_CONTENT }
        segments.forEachIndexed { i, seg ->
            val lp = LinearLayout.LayoutParams(-1, -2).apply { if (i > 0) topMargin = (6 * d).toInt() }
            if (!seg.code) {
                box.addView(textView(seg.text), lp)
                return@forEachIndexed
            }
            val card = LinearLayout(ctx)
            val c = buildCard(card, snippetLabel(ctx, seg), FileIcons.forSnippet(seg), dark, { copyText(ctx, seg.text) }, { onOpen(seg) }, onLongPress)
            val cacheKey = "s|$key|$i|$dark|${seg.text.hashCode()}"
            val loaded = cache.get(cacheKey) ?: snippetLoaded(seg, true, dark).also { cache.put(cacheKey, it) }
            c.code.text = loaded.text
            if (loaded.truncated) {
                val lines = snippetLines(seg)
                c.footer.text = ctx.getString(R.string.code_show_all_snippet) + "  ·  " +
                    ctx.resources.getQuantityString(R.plurals.code_lines, lines, lines)
                c.footer.visibility = View.VISIBLE
            }
            box.addView(card, lp)
        }
    }

    fun fill(
        box: LinearLayout, key: String, file: File, fileName: String, dark: Boolean, animate: Boolean,
        onOpen: () -> Unit, onLongPress: () -> Unit
    ) {
        val ctx = box.context
        stopAnim(box)
        box.removeAllViews()
        box.tag = key
        box.visibility = View.VISIBLE
        val dim = codeDim(dark)
        val card = buildCard(box, languageLabel(ctx, fileName), FileIcons.forFile(fileName, file), dark, { copyFile(ctx, file, fileName) }, onOpen, onLongPress)
        val code = card.code
        val footer = card.footer

        fun show(loaded: Loaded) {
            if (box.tag != key) return
            card.icon.setImageResource(FileIcons.forFile(fileName, file))
            if (loaded.error != 0) {
                code.text = ctx.getString(loaded.error)
                code.setTextColor(dim)
                code.typeface = Typeface.DEFAULT
            } else {
                code.text = loaded.text
                if (loaded.truncated) {
                    footer.text = ctx.getString(R.string.code_show_all) + "  ·  " + linesLabel(ctx, file, loaded)
                    footer.visibility = View.VISIBLE
                }
            }
            if (animate) expand(box)
        }

        val cacheKey = "$key|${dark}|${file.length()}"
        val hit = cache.get(cacheKey)
        if (animate) {
            box.layoutParams = box.layoutParams.apply { height = 0 }
        } else {
            box.layoutParams = box.layoutParams.apply { height = ViewGroup.LayoutParams.WRAP_CONTENT }
        }
        if (hit != null) { show(hit); return }
        AppExecutor.io {
            FileIcons.scan(fileName, file)
            val loaded = load(file, fileName, inline = true, dark = dark)
            if (loaded.error == 0) cache.put(cacheKey, loaded)
            AppExecutor.main { show(loaded) }
        }
    }

    private fun anim(box: View): ValueAnimator? = box.getTag(R.id.msg_code_box) as? ValueAnimator

    private fun stopAnim(box: View) {
        val a = anim(box)
        box.setTag(R.id.msg_code_box, null)
        a?.cancel()
    }

    private fun expand(box: View) {
        stopAnim(box)
        val lp = box.layoutParams
        val width = if (lp.width > 0) lp.width else (box.parent as? View)?.width ?: 0
        box.measure(
            View.MeasureSpec.makeMeasureSpec(width.coerceAtLeast(1), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val target = box.measuredHeight
        val a = ValueAnimator.ofInt(0, target)
        a.duration = 260
        a.interpolator = DecelerateInterpolator()
        a.addUpdateListener { if (anim(box) === a) box.layoutParams = box.layoutParams.apply { height = it.animatedValue as Int } }
        a.doOnEnd {
            if (anim(box) === a) {
                box.setTag(R.id.msg_code_box, null)
                box.layoutParams = box.layoutParams.apply { height = ViewGroup.LayoutParams.WRAP_CONTENT }
            }
        }
        box.setTag(R.id.msg_code_box, a)
        a.start()
        box.alpha = 0f
        box.animate().alpha(1f).setDuration(220).start()
    }

    fun collapse(box: LinearLayout) {
        stopAnim(box)
        val start = box.height
        box.tag = null
        if (start <= 0) { reset(box); return }
        val a = ValueAnimator.ofInt(start, 0)
        a.duration = 200
        a.interpolator = DecelerateInterpolator()
        a.addUpdateListener { if (anim(box) === a) box.layoutParams = box.layoutParams.apply { height = it.animatedValue as Int } }
        a.doOnEnd { if (anim(box) === a) reset(box) }
        box.setTag(R.id.msg_code_box, a)
        a.start()
    }

    fun reset(box: LinearLayout) {
        stopAnim(box)
        box.animate().cancel()
        box.alpha = 1f
        box.removeAllViews()
        box.tag = null
        box.visibility = View.GONE
        val d = box.resources.displayMetrics.density
        box.layoutParams = box.layoutParams.apply {
            width = (260 * d).toInt()
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
    }

    private fun ValueAnimator.doOnEnd(block: () -> Unit) {
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) = block()
        })
    }

    fun showViewer(activity: Activity, file: File, fileName: String, onSave: (() -> Unit)? = null) {
        val size = MediaManager.formatSize(file.length())
        viewer(activity, fileName, languageLabel(activity, fileName) + "  ·  " + size, onSave,
            load = { dark -> load(file, fileName, inline = false, dark = dark) },
            subtitle = { loaded ->
                if (loaded.totalLines < 0) null
                else languageLabel(activity, fileName) + "  ·  " +
                    activity.resources.getQuantityString(R.plurals.code_lines, loaded.totalLines, loaded.totalLines) +
                    "  ·  " + size
            },
            copy = { copyFile(activity, file, fileName) })
    }

    fun showSnippet(activity: Activity, seg: CodeSnippets.Segment) {
        val lines = snippetLines(seg)
        viewer(activity, activity.getString(R.string.code_snippet),
            snippetLabel(activity, seg) + "  ·  " + activity.resources.getQuantityString(R.plurals.code_lines, lines, lines), null,
            load = { dark -> snippetLoaded(seg, false, dark) },
            subtitle = { null },
            copy = { copyText(activity, seg.text) })
    }

    private fun viewer(
        activity: Activity, title: String, subtitleText: String, onSave: (() -> Unit)?,
        load: (Boolean) -> Loaded, subtitle: (Loaded) -> String?, copy: () -> Unit
    ) {
        val dark = pl.sinusoide.sinuschat.ui.ThemeUi.isDark(activity)
        val d = activity.resources.displayMetrics.density
        val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar)
        pl.sinusoide.sinuschat.util.PopupHelper.trackOverlay(dialog)
        dialog.window?.let { w ->
            w.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            WindowCompat.setDecorFitsSystemWindows(w, false)
            if (Build.VERSION.SDK_INT >= 30) {
                w.attributes = w.attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }
            }
            w.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(codeBg(dark)))
            WindowCompat.getInsetsController(w, w.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
        val fg = codeFg(dark)
        val dim = codeDim(dark)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(codeBg(dark))
        }
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((6 * d).toInt(), (8 * d).toInt(), (6 * d).toInt(), (8 * d).toInt())
        }
        bar.addView(iconButton(activity, R.drawable.ic_back, fg, activity.getString(R.string.back), 44, 10) { dialog.dismiss() })
        val titles = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(TextView(activity).apply {
            text = title
            textSize = 16f; setTextColor(fg); typeface = Typeface.DEFAULT_BOLD
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        })
        val subtitleView = TextView(activity).apply {
            text = subtitleText
            textSize = 12f; setTextColor(dim)
            maxLines = 1
        }
        titles.addView(subtitleView)
        bar.addView(titles, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = (6 * d).toInt() })
        if (onSave != null) {
            bar.addView(iconButton(activity, R.drawable.ic_download, fg, activity.getString(R.string.save_to_device), 44, 11) { onSave() })
        }
        bar.addView(iconButton(activity, R.drawable.ic_copy, fg, activity.getString(R.string.code_copy), 44, 11) { copy() })
        root.addView(bar, LinearLayout.LayoutParams(-1, -2))
        root.addView(View(activity).apply { setBackgroundColor(if (dark) 0x22FFFFFF else 0x1F000000) },
            LinearLayout.LayoutParams(-1, d.toInt().coerceAtLeast(1)))

        val note = TextView(activity).apply {
            text = activity.getString(R.string.code_truncated)
            textSize = 12.5f; setTextColor(dim)
            setPadding((16 * d).toInt(), (8 * d).toInt(), (16 * d).toInt(), 0)
            visibility = View.GONE
        }
        root.addView(note)

        val body = android.widget.FrameLayout(activity)
        val progress = ProgressBar(activity).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(pl.sinusoide.sinuschat.ui.ThemeUi.accent(activity))
        }
        body.addView(progress, android.widget.FrameLayout.LayoutParams((36 * d).toInt(), (36 * d).toInt(), Gravity.CENTER))
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            bar.setPadding(b.left + (6 * d).toInt(), b.top + (8 * d).toInt(), b.right + (6 * d).toInt(), (8 * d).toInt())
            note.setPadding(b.left + (16 * d).toInt(), (8 * d).toInt(), b.right + (16 * d).toInt(), 0)
            body.setPadding(b.left, 0, b.right, b.bottom)
            WindowInsetsCompat.CONSUMED
        }

        dialog.setContentView(root)
        dialog.show()

        AppExecutor.io {
            val loaded = load(dark)
            AppExecutor.main {
                if (!dialog.isShowing) return@main
                body.removeAllViews()
                if (loaded.error != 0) {
                    body.addView(TextView(activity).apply {
                        text = activity.getString(loaded.error)
                        textSize = 14f; setTextColor(dim); gravity = Gravity.CENTER
                    }, android.widget.FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
                    return@main
                }
                subtitle(loaded)?.let { subtitleView.text = it }
                if (loaded.truncated) note.visibility = View.VISIBLE
                val numbers = TextView(activity).apply {
                    typeface = Typeface.MONOSPACE
                    textSize = 13f
                    setTextColor(dim)
                    gravity = Gravity.END
                    setLineSpacing(0f, 1.2f)
                    text = (1..loaded.shownLines.coerceAtLeast(1)).joinToString("\n")
                    setPadding((12 * d).toInt(), (10 * d).toInt(), (10 * d).toInt(), (24 * d).toInt())
                }
                val code = TextView(activity).apply {
                    typeface = Typeface.MONOSPACE
                    textSize = 13f
                    setTextColor(fg)
                    setLineSpacing(0f, 1.2f)
                    text = loaded.text
                    setTextIsSelectable(true)
                    setPadding((4 * d).toInt(), (10 * d).toInt(), (24 * d).toInt(), (24 * d).toInt())
                }
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(numbers, LinearLayout.LayoutParams(-2, -2))
                    addView(code, LinearLayout.LayoutParams(-2, -2))
                }
                val hsv = HorizontalScrollView(activity).apply {
                    isFillViewport = true
                    addView(row, ViewGroup.LayoutParams(-2, -2))
                }
                body.addView(ScrollView(activity).apply {
                    isFillViewport = true
                    addView(hsv, ViewGroup.LayoutParams(-1, -2))
                }, android.widget.FrameLayout.LayoutParams(-1, -1))
            }
        }
    }
}
