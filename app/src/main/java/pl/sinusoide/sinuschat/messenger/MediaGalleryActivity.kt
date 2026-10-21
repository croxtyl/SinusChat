package pl.sinusoide.sinuschat.messenger

import android.animation.ValueAnimator
import android.app.Dialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import pl.sinusoide.sinuschat.R
import pl.sinusoide.sinuschat.ScreenLockActivity
import pl.sinusoide.sinuschat.crypto.DbKeyManager
import pl.sinusoide.sinuschat.data.MessageEntity
import pl.sinusoide.sinuschat.data.SinusChatDatabase
import pl.sinusoide.sinuschat.util.AppExecutor
import pl.sinusoide.sinuschat.util.ContactCache
import pl.sinusoide.sinuschat.util.FileUploader
import pl.sinusoide.sinuschat.util.PopupHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import androidx.activity.enableEdgeToEdge

class MediaGalleryActivity : AppCompatActivity() {
    private val tc by lazy { pl.sinusoide.sinuschat.ui.ThemeColors(this) }
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(pl.sinusoide.sinuschat.util.LocaleManager.wrap(newBase))
    }


    private lateinit var groupId: String
    private var otherUserName: String = ""
    private var kind: String? = null
    private var pendingSave: (() -> Unit)? = null
    private val storagePermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingSave
        pendingSave = null
        if (granted) action?.invoke() else PopupHelper.showError(this, getString(R.string.error_save))
    }
    private val dp get() = resources.displayMetrics.density

    private lateinit var viewPager: ViewPager2
    private lateinit var tabMedia: TextView
    private var glassTabs: pl.sinusoide.sinuschat.ui.SlideNavBar? = null
    private lateinit var tabDocs: TextView
    private lateinit var tabLinks: TextView
    private lateinit var indicator: View

    private val mediaMsgs = ArrayList<MessageEntity>()
    private val docMsgs = ArrayList<MessageEntity>()
    private val linkItems = ArrayList<Pair<MessageEntity, String>>()

    private lateinit var mediaAdapter: GallerySubAdapter
    private lateinit var docsAdapter: GallerySubAdapter
    private lateinit var linksAdapter: GallerySubAdapter

    private var selectionMode = false
    private val selectedIds = HashSet<String>()
    private var selectionTabType = -1
    private lateinit var titleView: TextView
    private lateinit var backButton: ImageButton
    private lateinit var selectionBar: LinearLayout
    private lateinit var selectionCountView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        if (DbKeyManager.needsPinUnlock(this)) {
            ScreenLockActivity.start(this, ScreenLockActivity.MODE_UNLOCK)
            super.onCreate(null)
            finish()
            return
        }

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        groupId = intent.getStringExtra("conversationId") ?: ""
        otherUserName = intent.getStringExtra("otherUserName") ?: ""
        kind = intent.getStringExtra("kind")
        buildUI()
        loadMessages()
    }

    private fun buildUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(tc.bg)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val top = maxOf(b.top, insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars()).top)
            v.setPadding(0, top, 0, 0); insets
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding((8 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
        }
        backButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_back); setBackgroundResource(0); setColorFilter(tc.text)
            setPadding((10 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt())
            setOnClickListener { if (selectionMode) exitSelectionMode() else finish() }
        }
        header.addView(backButton, LinearLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt()))
        titleView = TextView(this).apply {
            text = when {
                kind == "image" -> getString(R.string.storage_kind_images)
                kind == "video" -> getString(R.string.storage_kind_videos)
                kind == "voice" -> getString(R.string.storage_kind_voice)
                kind == "document" -> getString(R.string.storage_kind_docs)
                groupId == "ALL" -> getString(R.string.all_media_title)
                otherUserName.isNotEmpty() -> otherUserName
                else -> getString(R.string.media_links_docs)
            }
            textSize = 19f; setTextColor(tc.text)
            setPadding((8 * dp).toInt(), 0, 0, 0)
        }
        header.addView(titleView, LinearLayout.LayoutParams(0, -2, 1f))

        selectionCountView = TextView(this).apply {
            textSize = 18f; setTextColor(tc.text); setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val selectAllBtn = ImageButton(this).apply {
            setImageResource(R.drawable.ic_check_double); setBackgroundResource(0); setColorFilter(tc.text)
            setPadding((10 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt())
            setOnClickListener { selectAllCurrent() }
        }
        val deleteBtn = ImageButton(this).apply {
            setImageResource(R.drawable.ic_delete); setBackgroundResource(0); setColorFilter(tc.text)
            setPadding((10 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt())
            setOnClickListener { confirmDeleteSelected() }
        }
        selectionBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        selectionBar.addView(selectionCountView, LinearLayout.LayoutParams(0, -2, 1f))
        selectionBar.addView(selectAllBtn, LinearLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt()))
        selectionBar.addView(deleteBtn, LinearLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt()))
        header.addView(selectionBar, LinearLayout.LayoutParams(0, -2, 2f))

        root.addView(header)

        val tabsLayout = RelativeLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            id = View.generateViewId()
        }
        tabMedia = makeTab(getString(R.string.gallery_tab_media), 0)
        tabDocs = makeTab(getString(R.string.gallery_tab_docs), 1)
        tabLinks = makeTab(getString(R.string.gallery_tab_links), 2)
        tabs.addView(tabMedia, LinearLayout.LayoutParams(0, -2, 1f))
        tabs.addView(tabDocs, LinearLayout.LayoutParams(0, -2, 1f))
        tabs.addView(tabLinks, LinearLayout.LayoutParams(0, -2, 1f))

        indicator = View(this).apply {
            setBackgroundResource(R.drawable.bg_nav_pill)
        }
        val indParams = RelativeLayout.LayoutParams(0, (40 * dp).toInt()).apply {
            addRule(RelativeLayout.CENTER_VERTICAL)
        }
        tabsLayout.addView(indicator, indParams)

        tabsLayout.addView(tabs, RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        if (kind != null) {
            root.addView(View(this).apply { setBackgroundColor(tc.input) },
                LinearLayout.LayoutParams(-1, (1 * dp).toInt()))
        } else if (pl.sinusoide.sinuschat.util.ThemeManager.isFlow(this)) {
            glassTabs = pl.sinusoide.sinuschat.ui.SlideNavBar(this).apply {
                setTabs(listOf(
                    0 to getString(R.string.gallery_tab_media),
                    0 to getString(R.string.gallery_tab_docs),
                    0 to getString(R.string.gallery_tab_links)))
                onTabSelected = { idx -> smoothScrollTo(idx) }
            }
            root.addView(glassTabs, LinearLayout.LayoutParams(-1, -2))
        } else {
            root.addView(tabsLayout)
            root.addView(View(this).apply { setBackgroundColor(tc.input) },
                LinearLayout.LayoutParams(-1, (1 * dp).toInt()))
        }

        viewPager = ViewPager2(this).apply {
            adapter = PagerAdapter()
            offscreenPageLimit = 2
            isUserInputEnabled = false
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    updateTabs(position)
                    glassTabs?.setSelected(position)
                }
                override fun onPageScrolled(pos: Int, offset: Float, offsetPx: Int) {
                    val cellW = width / 3
                    if (cellW > 0) {
                        val margin = (10 * dp).toInt()
                        indicator.layoutParams.width = cellW - margin * 2
                        indicator.translationX = (pos + offset) * cellW + margin.toFloat()
                        indicator.requestLayout()
                    }
                }
            })
        }
        root.addView(viewPager, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(root)

        mediaAdapter = GallerySubAdapter(0)
        docsAdapter = GallerySubAdapter(1)
        linksAdapter = GallerySubAdapter(2)

        root.post {
            val cellW = root.width / 3
            if (cellW > 0) {
                val margin = (10 * dp).toInt()
                val lp = indicator.layoutParams
                lp.width = cellW - margin * 2
                indicator.layoutParams = lp
                indicator.translationX = viewPager.currentItem * cellW + margin.toFloat()
            }
        }
        updateTabs(0)
        if (kind == "voice" || kind == "document") viewPager.setCurrentItem(1, false)

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (selectionMode) exitSelectionMode() else finish()
            }
        })
    }

    private fun makeTab(label: String, idx: Int): TextView = TextView(this).apply {
        text = label; textSize = 15f; gravity = Gravity.CENTER
        setTextColor(tc.text2)
        setPadding(0, (14 * dp).toInt(), 0, (14 * dp).toInt())
        isClickable = true
        isSingleLine = true
        setOnClickListener {
            smoothScrollTo(idx)
        }
    }

    private var currentAnimator: ValueAnimator? = null
    private fun smoothScrollTo(idx: Int) {
        if (viewPager.currentItem == idx) return
        currentAnimator?.cancel()
        val startPos = viewPager.currentItem
        val diff = idx - startPos
        var lastVal = 0f
        currentAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 280
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val value = it.animatedValue as Float
                val delta = (value - lastVal) * diff * viewPager.width
                if (viewPager.isFakeDragging || viewPager.beginFakeDrag()) {
                    viewPager.fakeDragBy(-delta)
                }
                lastVal = value
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (viewPager.isFakeDragging) viewPager.endFakeDrag()
                    viewPager.setCurrentItem(idx, false)
                    lastVal = 0f
                }
            })
            start()
        }
    }

    private fun updateTabs(idx: Int) {
        val active = tc.accent
        val inactive = tc.text2
        tabMedia.setTextColor(if (idx == 0) active else inactive)
        tabDocs.setTextColor(if (idx == 1) active else inactive)
        tabLinks.setTextColor(if (idx == 2) active else inactive)
    }

    private fun loadMessages() {
        AppExecutor.io {
            val dao = SinusChatDatabase.getInstance(this).messageDao()
            val msgs = if (groupId == "ALL") dao.getAllSync() else dao.getAllForConversationSync(groupId)
            val media = ArrayList<MessageEntity>()
            val docs = ArrayList<MessageEntity>()
            val links = ArrayList<Pair<MessageEntity, String>>()

            val urlRegex = Regex(
                """(?:https?://|www\.|[a-zA-Z0-9-]+\.)[a-zA-Z0-9-]+\.[a-zA-Z]{2,}(?:/\S*)?""",
                RegexOption.IGNORE_CASE
            )

            for (m in msgs) {
                when {
                    m.type == MessageEntity.TYPE_ATTACHMENT && m.fileId != null -> {
                        if (m.viewOnce) continue

                        val originalName = m.attachmentName ?: ""
                        if (originalName.startsWith("location_")) continue

                        val name = originalName.lowercase()
                        if (isImageOrVideo(name)) media.add(m) else docs.add(m)
                    }
                    m.type == MessageEntity.TYPE_TEXT && !m.isSystemMessage -> {
                        urlRegex.find(m.text)?.let { links.add(Pair(m, it.value)) }
                    }
                }
            }
            val k = kind
            if (k != null) {
                fun local(m: MessageEntity) = pl.sinusoide.sinuschat.util.MediaManager
                    .getMediaFile(this, m.conversationId, m.id, m.attachmentName ?: "").let { it.exists() && it.length() > 0 }
                fun audio(m: MessageEntity) = (m.attachmentName ?: "").substringAfterLast('.', "").lowercase() in AUDIO_EXT
                val keepMedia = media.filter { m ->
                    local(m) && when (k) {
                        "image" -> pl.sinusoide.sinuschat.util.MediaManager.isImage(m.attachmentName ?: "")
                        "video" -> pl.sinusoide.sinuschat.util.MediaManager.isVideo(m.attachmentName ?: "")
                        else -> false
                    }
                }
                val keepDocs = docs.filter { m ->
                    local(m) && when (k) {
                        "voice" -> audio(m)
                        "document" -> !audio(m)
                        else -> false
                    }
                }
                media.clear(); media.addAll(keepMedia)
                docs.clear(); docs.addAll(keepDocs)
                links.clear()
            }
            AppExecutor.main {
                mediaMsgs.clear(); mediaMsgs.addAll(media)
                docMsgs.clear(); docMsgs.addAll(docs)
                linkItems.clear(); linkItems.addAll(links)

                mediaAdapter.notifyDataSetChanged()
                docsAdapter.notifyDataSetChanged()
                linksAdapter.notifyDataSetChanged()
            }
        }
    }

    private val AUDIO_EXT = setOf("ogg", "opus", "m4a", "aac", "mp3", "wav", "amr")

    private fun isImageOrVideo(name: String): Boolean {
        val ext = name.substringAfterLast('.', "")
        return ext in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp",
            "mp4", "mov", "3gp", "mkv", "webm", "avi")
    }

    private inner class PagerAdapter : RecyclerView.Adapter<PagerVH>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PagerVH {
            val rv = RecyclerView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(-1, -1)
                setBackgroundColor(tc.bg)
                clipToPadding = false
            }
            return PagerVH(rv)
        }
        override fun onBindViewHolder(holder: PagerVH, position: Int) {
            val rv = holder.itemView as RecyclerView
            if (rv.adapter == null) {
                rv.layoutManager = if (position == 0) GridLayoutManager(this@MediaGalleryActivity, gridSpan())
                else LinearLayoutManager(this@MediaGalleryActivity)
                rv.adapter = when (position) {
                    0 -> mediaAdapter
                    1 -> docsAdapter
                    else -> linksAdapter
                }
            }
        }
        override fun getItemCount() = 3
    }
    private class PagerVH(v: View) : RecyclerView.ViewHolder(v)

    private fun gridSpan(): Int = maxOf(3, resources.configuration.screenWidthDp / 130)

    private inner class GallerySubAdapter(val type: Int) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount(): Int = when (type) {
            0 -> mediaMsgs.size
            1 -> docMsgs.size
            2 -> linkItems.size
            else -> 0
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (type == 0) {
                val screenW = resources.displayMetrics.widthPixels
                val parentW = if (parent.width > 0) parent.width else screenW
                val sz = parentW / gridSpan()

                val frame = FrameLayout(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(sz, sz)
                    setPadding(1, 1, 1, 1)
                }
                val iv = ImageView(parent.context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(tc.input)
                }
                frame.addView(iv, FrameLayout.LayoutParams(-1, -1))

                val badge = ImageView(parent.context).apply {
                    setColorFilter(Color.WHITE)
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(0x73000000)
                    }
                    val p = (9 * dp).toInt(); setPadding(p, p, p, p)
                    visibility = View.GONE
                }
                frame.addView(badge, FrameLayout.LayoutParams((38 * dp).toInt(), (38 * dp).toInt(), Gravity.CENTER))
                val chip = TextView(parent.context).apply {
                    textSize = 11f; setTextColor(Color.WHITE)
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = 9 * dp; setColor(0x8C000000.toInt())
                    }
                    setPadding((6 * dp).toInt(), (2 * dp).toInt(), (6 * dp).toInt(), (2 * dp).toInt())
                    visibility = View.GONE
                }
                frame.addView(chip, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply {
                    leftMargin = (6 * dp).toInt(); bottomMargin = (6 * dp).toInt()
                })

                val overlay = FrameLayout(parent.context).apply {
                    id = R.id.media_item_selection_overlay
                    layoutParams = FrameLayout.LayoutParams(-1, -1)
                    setBackgroundColor(0x885288C1.toInt())
                    visibility = View.GONE

                    val check = ImageView(parent.context).apply {
                        setImageResource(R.drawable.ic_check)
                        setColorFilter(Color.WHITE)
                        val p = (8 * dp).toInt()
                        setPadding(p, p, p, p)
                    }
                    addView(check, FrameLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt(), Gravity.CENTER))
                }
                frame.addView(overlay)

                MediaVH(frame, iv, overlay, badge, chip)
            } else {
                val outer = LinearLayout(parent.context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = ViewGroup.LayoutParams(-1, -2)
                }
                val row = LinearLayout(parent.context).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
                    isClickable = true

                    val outValue = android.util.TypedValue()
                    parent.context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                    setBackgroundResource(outValue.resourceId)
                }
                val icon = ImageView(parent.context)
                row.addView(icon, LinearLayout.LayoutParams((28 * dp).toInt(), (28 * dp).toInt()).apply { marginEnd = (14 * dp).toInt() })
                val col = LinearLayout(parent.context).apply { orientation = LinearLayout.VERTICAL }
                val t1 = TextView(parent.context).apply { textSize = 15f; setTextColor(tc.text); maxLines = 1 }
                val t2 = TextView(parent.context).apply { textSize = 12f; setTextColor(tc.text2) }
                col.addView(t1); col.addView(t2)
                row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))

                outer.addView(row)

                val divider = View(parent.context).apply {
                    setBackgroundColor(tc.input)
                    layoutParams = LinearLayout.LayoutParams(-1, (1 * dp).toInt()).apply {
                        marginStart = (58 * dp).toInt()
                    }
                }
                outer.addView(divider)

                ListVH(outer, row, icon, t1, t2)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
            if (payloads.contains("selection")) {
                val id = idForPosition(type, position) ?: return
                val selected = selectionMode && selectionTabType == type && selectedIds.contains(id)
                when (type) {
                    0 -> (holder as MediaVH).overlay.visibility = if (selected) View.VISIBLE else View.GONE
                    1, 2 -> {
                        val vh = holder as ListVH
                        vh.row.setBackgroundColor(if (selected) 0x4D5288C1 else Color.TRANSPARENT)
                        vh.icon.setColorFilter(if (selected) Color.WHITE else tc.accent)
                    }
                }
            } else {
                super.onBindViewHolder(holder, position, payloads)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (type) {
                0 -> {
                    val m = mediaMsgs[position]
                    val vh = holder as MediaVH

                    val selected = selectionMode && selectionTabType == 0 && selectedIds.contains(m.id)
                    vh.overlay.visibility = if (selected) View.VISIBLE else View.GONE

                    val localFile = pl.sinusoide.sinuschat.util.MediaManager.getMediaFile(this@MediaGalleryActivity, m.conversationId, m.id, m.attachmentName ?: "")
                    val isVideo = pl.sinusoide.sinuschat.util.MediaManager.isVideo(m.attachmentName ?: "")
                    vh.badge.visibility = View.GONE
                    vh.chip.visibility = View.GONE
                    if (isVideo) {
                        bindVideoTile(vh, m, localFile)
                    } else if (localFile.exists()) {
                        vh.img.colorFilter = null
                        Glide.with(this@MediaGalleryActivity)
                            .load(localFile)
                            .centerCrop()
                            .into(vh.img)
                    } else {
                        vh.img.setImageResource(R.drawable.ic_image)
                        vh.img.setColorFilter(tc.muted)
                        loadMediaBytes(m) { bytes ->
                            if (bytes != null && holder.bindingAdapterPosition == position) {
                                vh.img.colorFilter = null
                                Glide.with(this@MediaGalleryActivity).load(bytes).centerCrop().into(vh.img)
                            }
                        }
                    }

                    vh.itemView.setOnClickListener {
                        if (selectionMode) toggleSelectionAndRefresh(0, m.id, vh) else openMedia(m)
                    }
                    vh.itemView.setOnLongClickListener {
                        if (!selectionMode) enterSelectionMode(0, m.id) else toggleSelectionAndRefresh(0, m.id, vh)
                        true
                    }
                }
                1 -> {
                    val m = docMsgs[position]
                    val vh = holder as ListVH
                    val docName = m.attachmentName ?: "dokument"
                    val isCode = pl.sinusoide.sinuschat.util.CodeHighlighter.isCode(docName)
                    vh.icon.setColorFilter(tc.accent)
                    vh.t1.text = docName
                    val docFile = pl.sinusoide.sinuschat.util.MediaManager.getMediaFile(this@MediaGalleryActivity, m.conversationId, m.id, docName)
                    val localDoc = docFile.takeIf { it.exists() }
                    vh.icon.setImageResource(pl.sinusoide.sinuschat.util.FileIcons.forFile(docName, localDoc))
                    vh.icon.setTag(R.id.msg_doc_icon, m.id)
                    if (localDoc != null && pl.sinusoide.sinuschat.util.FileIcons.needsScan(docName, localDoc)) {
                        val iconView = vh.icon
                        pl.sinusoide.sinuschat.util.AppExecutor.io {
                            pl.sinusoide.sinuschat.util.FileIcons.scan(docName, localDoc)
                            pl.sinusoide.sinuschat.util.AppExecutor.main {
                                if (iconView.getTag(R.id.msg_doc_icon) == m.id) {
                                    iconView.setImageResource(pl.sinusoide.sinuschat.util.FileIcons.forFile(docName, localDoc))
                                }
                            }
                        }
                    }
                    val meta = mutableListOf<String>()
                    if (isCode) meta += CodePreview.languageLabel(this@MediaGalleryActivity, docName)
                    else docName.substringAfterLast('.', "").takeIf { it.isNotEmpty() }?.let { meta += it.uppercase() }
                    if (docFile.exists()) meta += pl.sinusoide.sinuschat.util.MediaManager.formatSize(docFile.length())
                    meta += formatDate(m.timestampMs)
                    vh.t2.text = meta.joinToString("  ·  ")
                    val selected = selectionMode && selectionTabType == 1 && selectedIds.contains(m.id)
                    vh.row.setBackgroundColor(if (selected) 0x4D5288C1 else Color.TRANSPARENT)
                    vh.icon.setColorFilter(if (selected) Color.WHITE else tc.accent)
                    vh.row.setOnClickListener {
                        if (selectionMode) toggleSelectionAndRefresh(1, m.id, vh) else openDocument(m)
                    }
                    vh.row.setOnLongClickListener {
                        if (!selectionMode) enterSelectionMode(1, m.id) else toggleSelectionAndRefresh(1, m.id, vh)
                        true
                    }
                }
                2 -> {
                    val (m, url) = linkItems[position]
                    val vh = holder as ListVH
                    vh.icon.setImageResource(R.drawable.ic_link)
                    vh.icon.setColorFilter(tc.accent)
                    vh.t1.text = url; vh.t1.setTextColor(tc.accent)
                    vh.t2.text = formatDate(m.timestampMs)
                    val selected = selectionMode && selectionTabType == 2 && selectedIds.contains(m.id)
                    vh.row.setBackgroundColor(if (selected) 0x4D5288C1 else Color.TRANSPARENT)
                    vh.icon.setColorFilter(if (selected) Color.WHITE else tc.accent)
                    vh.row.setOnClickListener {
                        if (selectionMode) toggleSelectionAndRefresh(2, m.id, vh) else openLink(url)
                    }
                    vh.row.setOnLongClickListener {
                        if (!selectionMode) enterSelectionMode(2, m.id) else toggleSelectionAndRefresh(2, m.id, vh)
                        true
                    }
                }
            }
        }
    }

    private class MediaVH(v: View, val img: ImageView, val overlay: View, val badge: ImageView, val chip: TextView) : RecyclerView.ViewHolder(v)

    private fun bindVideoTile(vh: MediaVH, m: MessageEntity, localFile: File) {
        val thumbs = pl.sinusoide.sinuschat.util.VideoThumbs
        val info = thumbs.load(this, m.conversationId, m.id)
        val thumb = thumbs.bitmap(this, m.conversationId, m.id)
        val local = localFile.exists() && localFile.length() > 0
        vh.img.colorFilter = null
        Glide.with(this).clear(vh.img)
        when {
            local -> {
                val ph = thumb?.let { android.graphics.drawable.BitmapDrawable(resources, it) }
                val frameAt = if ((info?.durationMs ?: 0L) > 2000L) 1_000_000L else 0L
                Glide.with(this).asBitmap().load(localFile)
                    .apply(com.bumptech.glide.request.RequestOptions.frameOf(frameAt))
                    .placeholder(ph).error(ph).centerCrop().into(vh.img)
            }
            thumb != null -> vh.img.setImageBitmap(thumb)
            else -> {
                vh.img.setImageResource(R.drawable.ic_video)
                vh.img.setColorFilter(tc.muted)
            }
        }
        vh.badge.setImageResource(if (local) R.drawable.ic_play_voice else R.drawable.ic_download)
        vh.badge.visibility = View.VISIBLE
        val parts = mutableListOf<String>()
        info?.durationMs?.takeIf { it > 0 }?.let { parts += thumbs.formatDuration(it) }
        if (!local) info?.sizeBytes?.takeIf { it > 0 }?.let { parts += pl.sinusoide.sinuschat.util.MediaManager.formatSize(it) }
        if (parts.isNotEmpty()) {
            vh.chip.text = parts.joinToString("  ·  ")
            vh.chip.visibility = View.VISIBLE
        }
    }
    private class ListVH(v: View, val row: View, val icon: ImageView, val t1: TextView, val t2: TextView) : RecyclerView.ViewHolder(v)

    private fun currentAdapter(): GallerySubAdapter = when (selectionTabType) {
        0 -> mediaAdapter; 1 -> docsAdapter; 2 -> linksAdapter
        else -> when (viewPager.currentItem) { 1 -> docsAdapter; 2 -> linksAdapter; else -> mediaAdapter }
    }

    private fun idForPosition(type: Int, pos: Int): String? = when (type) {
        0 -> mediaMsgs.getOrNull(pos)?.id
        1 -> docMsgs.getOrNull(pos)?.id
        2 -> linkItems.getOrNull(pos)?.first?.id
        else -> null
    }

    private fun countForType(type: Int): Int = when (type) {
        0 -> mediaMsgs.size; 1 -> docMsgs.size; 2 -> linkItems.size; else -> 0
    }

    private fun enterSelectionMode(type: Int, firstId: String) {
        selectionMode = true
        selectionTabType = type
        selectedIds.clear()
        selectedIds.add(firstId)
        titleView.visibility = View.GONE
        selectionBar.visibility = View.VISIBLE
        backButton.setImageResource(R.drawable.ic_close)
        updateSelectionCount()
        currentAdapter().notifyDataSetChanged()
    }

    private fun exitSelectionMode() {
        val type = selectionTabType
        selectionMode = false
        selectionTabType = -1
        selectedIds.clear()
        titleView.visibility = View.VISIBLE
        selectionBar.visibility = View.GONE
        backButton.setImageResource(R.drawable.ic_back)
        if (type in 0..2) when (type) {
            0 -> mediaAdapter.notifyDataSetChanged()
            1 -> docsAdapter.notifyDataSetChanged()
            2 -> linksAdapter.notifyDataSetChanged()
        }
    }

    private fun toggleSelection(type: Int, id: String) {
        if (type != selectionTabType) return
        if (selectedIds.contains(id)) selectedIds.remove(id) else selectedIds.add(id)
        if (selectedIds.isEmpty()) { exitSelectionMode(); return }
        updateSelectionCount()
    }

    private fun toggleSelectionAndRefresh(type: Int, id: String, vh: RecyclerView.ViewHolder) {
        val wasLast = selectedIds.size == 1 && selectedIds.contains(id)
        toggleSelection(type, id)
        if (!wasLast) {
            currentAdapter().notifyItemChanged(vh.bindingAdapterPosition, "selection")
        }
    }

    private fun selectAllCurrent() {
        val type = selectionTabType
        if (type !in 0..2) return
        selectedIds.clear()
        for (i in 0 until countForType(type)) idForPosition(type, i)?.let { selectedIds.add(it) }
        updateSelectionCount()
        currentAdapter().notifyDataSetChanged()
    }

    private fun updateSelectionCount() {
        selectionCountView.text = getString(R.string.selected_count, selectedIds.size)
    }

    private fun confirmDeleteSelected() {
        if (selectedIds.isEmpty()) return
        val n = selectedIds.size
        AlertDialog.Builder(this, R.style.Theme_SinusChat_Dialog)
            .setTitle(getString(R.string.delete))
            .setMessage(getString(R.string.delete_selected_forever, n))
            .setPositiveButton(getString(R.string.delete)) { _, _ -> deleteSelectedForever() }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun deleteSelectedForever() {
        val ids = selectedIds.toList()
        AppExecutor.io {
            val dao = SinusChatDatabase.getInstance(this).messageDao()
            for (id in ids) {
                val m = dao.getById(id)
                if (m != null) {
                    try { pl.sinusoide.sinuschat.util.MediaManager.deleteMessageMedia(this, m.conversationId, id) } catch (_: Exception) {}
                    dao.deleteById(id)
                }
            }
            AppExecutor.main {
                exitSelectionMode()
                PopupHelper.show(this, getString(R.string.deleted))
                loadMessages()
            }
        }
    }

    private fun openMedia(m: MessageEntity) {
        val idx = mediaMsgs.indexOfFirst { it.id == m.id }.coerceAtLeast(0)
        MediaViewers.showMedia(this, mediaMsgs, idx, { saveToDevice(it) }, { mm, f -> openExternal(mm, f) }, listOf(
            MediaViewers.Action(getString(R.string.show_in_chat_menu)) { item, d -> d.dismiss(); showInChat(item) },
            MediaViewers.Action(getString(R.string.delete)) { item, d -> d.dismiss(); confirmDeleteImage(item) }
        ), onClosed = { mediaAdapter.notifyDataSetChanged() })
    }

    private fun mimeFor(name: String): String =
        android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: guessMimeType(name)

    private fun saveToDevice(m: MessageEntity, confirmed: Boolean = false) {
        if (m.viewOnce) return
        val name = m.attachmentName ?: "plik"
        if (!confirmed) {
            pl.sinusoide.sinuschat.util.SafeEngine.guard(this, pl.sinusoide.sinuschat.util.SafeEngine.Risk.SAVE_FILE, name) {
                saveToDevice(m, confirmed = true)
            }
            return
        }
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q &&
            androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingSave = { saveToDevice(m, confirmed = true) }
            storagePermission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        AttachmentDownloader.fetch(this, m) { f ->
            if (isFinishing || isDestroyed) return@fetch
            if (f == null) { PopupHelper.showError(this, getString(R.string.file_download_failed)); return@fetch }
            val mime = mimeFor(name)
            AppExecutor.io {
                val saved = pl.sinusoide.sinuschat.util.DownloadsSaver.save(this, f, name, mime)
                AppExecutor.main {
                    if (saved != null) PopupHelper.showSuccess(this, getString(R.string.saved_to_folder,
                        pl.sinusoide.sinuschat.util.DownloadsSaver.locationFor(this, mime)))
                    else PopupHelper.showError(this, getString(R.string.error_save))
                }
            }
        }
    }

    private fun openExternal(m: MessageEntity, file: File) {
        val name = m.attachmentName ?: file.name
        pl.sinusoide.sinuschat.util.SafeEngine.guard(this, pl.sinusoide.sinuschat.util.SafeEngine.Risk.OPEN_FILE, name) {
            try {
                val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
                startActivity(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeFor(name))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
            } catch (_: android.content.ActivityNotFoundException) {
                PopupHelper.showError(this, getString(R.string.no_app_for_file))
            } catch (_: Exception) {
                PopupHelper.showError(this, getString(R.string.file_unavailable))
            }
        }
    }

    private fun loadMediaBytes(m: MessageEntity, onResult: (ByteArray?) -> Unit) {
        val name = m.attachmentName ?: ""
        val localFile = pl.sinusoide.sinuschat.util.MediaManager.getMediaFile(this, m.conversationId, m.id, name)
        android.util.Log.d("MediaGallery", "loadMediaBytes: msgId=${m.id}, plik lokalny=${localFile.absolutePath}, istnieje=${localFile.exists()}, rozmiar=${if (localFile.exists()) localFile.length() else -1}")
        if (localFile.exists()) {
            AppExecutor.io {
                val bytes = try { localFile.readBytes() } catch (_: Exception) { null }
                AppExecutor.main { onResult(bytes) }
            }
            return
        }
        android.util.Log.d("MediaGallery", "loadMediaBytes: brak lokalnie, próbuję serwera dla msgId=${m.id}")
        if (m.fileId != null && m.fileKey != null) {
            FileUploader.downloadAndDecryptAsync(this, m.fileId, m.fileKey) { bytes ->
                if (bytes != null) {
                    AppExecutor.io {
                        pl.sinusoide.sinuschat.util.MediaManager.saveMedia(this, m.conversationId, m.id, name, bytes)
                    }
                }
                onResult(bytes)
            }
        } else {
            onResult(null)
        }
    }

    private fun confirmDeleteImage(m: MessageEntity) {
        AlertDialog.Builder(this, R.style.Theme_SinusChat_Dialog)
            .setTitle(getString(R.string.delete_photo))
            .setMessage(getString(R.string.delete_photo_q))
            .setPositiveButton(getString(R.string.delete)) { _, _ -> deleteMedia(m) }
            .setNegativeButton(getString(R.string.cancel), null).show()
    }

    private fun showInChat(m: MessageEntity) {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("conversationId", m.conversationId)
            putExtra("otherUserName", ContactCache.getName(this@MediaGalleryActivity, m.conversationId) ?: "")
            putExtra("scrollToId", m.id)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
    }

    private fun deleteMedia(m: MessageEntity) {
        AppExecutor.io {
            try { pl.sinusoide.sinuschat.util.MediaManager.deleteMessageMedia(this@MediaGalleryActivity, m.conversationId, m.id) } catch (_: Exception) {}
            SinusChatDatabase.getInstance(this@MediaGalleryActivity).messageDao().deleteById(m.id)
            AppExecutor.main {
                mediaMsgs.remove(m); docMsgs.remove(m)
                mediaAdapter.notifyDataSetChanged()
                docsAdapter.notifyDataSetChanged()
                PopupHelper.show(this@MediaGalleryActivity, getString(R.string.deleted))
            }
        }
    }

    private fun openDocument(m: MessageEntity) {
        val docName = m.attachmentName ?: ""
        if (!m.viewOnce && pl.sinusoide.sinuschat.util.CodeHighlighter.isCode(docName)) {
            AttachmentDownloader.fetch(this, m) { f ->
                if (isFinishing || isDestroyed) return@fetch
                if (f == null) { PopupHelper.showError(this, getString(R.string.file_unavailable)); return@fetch }
                docsAdapter.notifyDataSetChanged()
                CodePreview.showViewer(this, f, docName) { saveToDevice(m) }
            }
            return
        }
        if (m.fileId == null || m.fileKey == null) return
        pl.sinusoide.sinuschat.util.SafeEngine.guard(this, pl.sinusoide.sinuschat.util.SafeEngine.Risk.OPEN_FILE, m.attachmentName ?: getString(R.string.safe_this_file)) {
            openDocumentConfirmed(m)
        }
    }

    private fun openDocumentConfirmed(m: MessageEntity) {
        val fileId = m.fileId ?: return
        val fileKey = m.fileKey ?: return
        PopupHelper.show(this, getString(R.string.opening))
        FileUploader.downloadAndDecryptAsync(this, fileId, fileKey) { bytes ->
            if (bytes != null) {
                val name = m.attachmentName ?: "dokument"
                val opened = openWithApp(bytes, name)
                if (!opened) {
                    downloadDocument(m)
                    PopupHelper.show(this, getString(R.string.no_app_for_file))
                }
            }
        }
    }

    private fun downloadDocument(m: MessageEntity) {
        if (m.fileId == null || m.fileKey == null) return
        PopupHelper.show(this, getString(R.string.downloading))
        FileUploader.downloadAndDecryptAsync(this, m.fileId, m.fileKey) { bytes ->
            if (bytes == null) return@downloadAndDecryptAsync
            try {
                val fileName = m.attachmentName ?: "plik"
                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloads, fileName)
                file.writeBytes(bytes)

                PopupHelper.showSuccess(this, getString(R.string.downloaded_filename, fileName))

                val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
                val mimeType = contentResolver.getType(uri) ?: guessMimeType(fileName)

                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                startActivity(Intent.createChooser(intent, getString(R.string.open_word)))
            } catch (e: Exception) {
                android.util.Log.e("MediaGallery", "Error saving/opening: ${e.message}")
            }
        }
    }

    private fun guessMimeType(name: String): String {
        return when (name.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            else -> "application/octet-stream"
        }
    }

    private fun openWithApp(bytes: ByteArray, fileName: String): Boolean {
        return try {
            val file = File(cacheDir, fileName)
            file.writeBytes(bytes)
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val mimeType = contentResolver.getType(uri) ?: "*/*"

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val resInfo = packageManager.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            if (resInfo.isEmpty()) return false

            startActivity(Intent.createChooser(intent, getString(R.string.open_word)))
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun openLink(url: String) {
        var fullUrl = url
        if (!fullUrl.startsWith("http://") && !fullUrl.startsWith("https://")) {
            fullUrl = "https://$fullUrl"
        }
        pl.sinusoide.sinuschat.util.SafeEngine.openLink(this, fullUrl)
    }

    private fun formatDate(ts: Long): String =
        SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ts))
}
