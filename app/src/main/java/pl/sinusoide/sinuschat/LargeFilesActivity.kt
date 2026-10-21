package pl.sinusoide.sinuschat

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import pl.sinusoide.sinuschat.util.AppExecutor
import pl.sinusoide.sinuschat.util.ContactCache
import pl.sinusoide.sinuschat.util.PopupHelper
import pl.sinusoide.sinuschat.util.StorageUsage
import pl.sinusoide.sinuschat.util.ThemeManager
import androidx.activity.enableEdgeToEdge

class LargeFilesActivity : AppCompatActivity() {
    private val tc by lazy { pl.sinusoide.sinuschat.ui.ThemeColors(this) }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(pl.sinusoide.sinuschat.util.LocaleManager.wrap(newBase))
    }

    private val items = ArrayList<StorageUsage.LargeFile>()
    private lateinit var adapter: LargeAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var titleView: TextView
    private lateinit var selBar: LinearLayout
    private lateinit var selCount: TextView

    private var selectionMode = false
    private val selected = HashSet<String>()

    private val dp get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(buildUI())
        loadFiles()
    }

    private fun buildUI(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(tc.bg)
            fitsSystemWindows = true
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding((8 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
        }
        header.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_back); setBackgroundResource(0); setColorFilter(tc.text)
            setPadding((10 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt())
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt()))
        titleView = TextView(this).apply {
            text = getString(R.string.large_files_title); textSize = 19f; setTextColor(tc.text)
            setPadding((8 * dp).toInt(), 0, 0, 0)
        }
        header.addView(titleView, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(header)

        selBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(tc.accent)
            setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
            visibility = View.GONE
        }
        selBar.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_back); setBackgroundResource(0); setColorFilter(Color.WHITE)
            setPadding((10 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt())
            setOnClickListener { exitSelection() }
        }, LinearLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt()))
        selCount = TextView(this).apply { text = "0"; textSize = 18f; setTextColor(Color.WHITE)
            setPadding((8 * dp).toInt(), 0, 0, 0) }
        selBar.addView(selCount, LinearLayout.LayoutParams(0, -2, 1f))
        selBar.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_select_all); setBackgroundResource(0); setColorFilter(Color.WHITE)
            setPadding((10 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt())
            setOnClickListener { selectAll() }
        }, LinearLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt()))
        selBar.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_delete); setBackgroundResource(0); setColorFilter(Color.WHITE)
            setPadding((10 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt())
            setOnClickListener { confirmDelete() }
        }, LinearLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt()))
        root.addView(selBar)

        recycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@LargeFilesActivity)
        }
        adapter = LargeAdapter()
        recycler.adapter = adapter
        root.addView(recycler, LinearLayout.LayoutParams(-1, 0, 1f))

        emptyView = TextView(this).apply {
            text = getString(R.string.no_large_files_desc)
            setTextColor(tc.text2); textSize = 15f; gravity = Gravity.CENTER
            setPadding((32 * dp).toInt(), (48 * dp).toInt(), (32 * dp).toInt(), 0)
            visibility = View.GONE
        }
        root.addView(emptyView)

        return root
    }

    private fun loadFiles() {
        AppExecutor.io {
            val list = StorageUsage.filesLargerThan(this)
            AppExecutor.main {
                items.clear(); items.addAll(list)
                adapter.notifyDataSetChanged()
                emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                updateTitle()
            }
        }
    }

    private fun updateTitle() {
        val sum = items.sumOf { it.bytes }
        titleView.text = if (items.isEmpty()) getString(R.string.large_files_title)
            else getString(R.string.large_files_title_format, StorageUsage.formatSize(sum))
    }

    private fun enterSelection(path: String) {
        selectionMode = true
        selected.clear(); selected.add(path)
        selBar.visibility = View.VISIBLE
        selCount.text = "1"
        adapter.notifyDataSetChanged()
    }

    private fun exitSelection() {
        selectionMode = false
        selected.clear()
        selBar.visibility = View.GONE
        adapter.notifyDataSetChanged()
    }

    private fun toggle(path: String) {
        if (selected.contains(path)) selected.remove(path) else selected.add(path)
        if (selected.isEmpty()) { exitSelection(); return }
        selCount.text = selected.size.toString()
        adapter.notifyDataSetChanged()
    }

    private fun selectAll() {
        items.forEach { selected.add(it.file.absolutePath) }
        selCount.text = selected.size.toString()
        adapter.notifyDataSetChanged()
    }

    private fun confirmDelete() {
        val n = selected.size
        if (n == 0) return
        val freed = items.filter { selected.contains(it.file.absolutePath) }.sumOf { it.bytes }
        val filePlural = resources.getQuantityString(R.plurals.files_plural, n)
        androidx.appcompat.app.AlertDialog.Builder(this, R.style.Theme_SinusChat_Dialog)
            .setTitle(getString(R.string.delete_from_device_q))
            .setMessage(getString(R.string.delete_large_files_desc, n, filePlural, StorageUsage.formatSize(freed)))
            .setPositiveButton(getString(R.string.delete_forever)) { _, _ -> doDelete() }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun doDelete() {
        val paths = selected.toList()
        AppExecutor.io {
            var freed = 0L
            for (it in items.filter { paths.contains(it.file.absolutePath) }) {
                try { if (it.file.exists()) { freed += it.bytes; it.file.delete() } } catch (_: Exception) {}
            }
            AppExecutor.main {
                items.removeAll { paths.contains(it.file.absolutePath) }
                PopupHelper.show(this, getString(R.string.freed_format, StorageUsage.formatSize(freed)))
                exitSelection()
                adapter.notifyDataSetChanged()
                emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                updateTitle()
            }
        }
    }

    private inner class VH(v: View, val icon: ImageView, val name: TextView,
                           val sub: TextView, val check: ImageView) : RecyclerView.ViewHolder(v)

    private inner class LargeAdapter : RecyclerView.Adapter<VH>() {
        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
                isClickable = true
            }
            val icon = ImageView(parent.context)
            row.addView(icon, LinearLayout.LayoutParams((30 * dp).toInt(), (30 * dp).toInt()).apply {
                marginEnd = (14 * dp).toInt() })
            val col = LinearLayout(parent.context).apply { orientation = LinearLayout.VERTICAL }
            val name = TextView(parent.context).apply { textSize = 15f; setTextColor(tc.text); maxLines = 1 }
            val sub = TextView(parent.context).apply { textSize = 12f; setTextColor(tc.text2) }
            col.addView(name); col.addView(sub)
            row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
            val check = ImageView(parent.context).apply {
                setImageResource(R.drawable.ic_check); setColorFilter(tc.accent)
                setBackgroundResource(R.drawable.bg_check_circle)
                val p = (3 * dp).toInt(); setPadding(p, p, p, p); visibility = View.GONE
                pl.sinusoide.sinuschat.ui.FlowGlass.check(this, true)
            }
            row.addView(check, LinearLayout.LayoutParams((24 * dp).toInt(), (24 * dp).toInt()))
            return VH(row, icon, name, sub, check)
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val lf = items[position]
            val fileName = lf.file.name
            val isImage = fileName.substringAfterLast('.', "").lowercase() in
                listOf("jpg","jpeg","png","gif","webp","bmp")
            h.icon.setImageResource(if (isImage) R.drawable.ic_image else pl.sinusoide.sinuschat.util.FileIcons.forName(fileName))
            h.icon.setColorFilter(tc.accent)
            h.name.text = fileName
            val chatName = ContactCache.getName(this@LargeFilesActivity, lf.conversationId) ?: getString(R.string.chats_title)
            h.sub.text = "$chatName · ${StorageUsage.formatSize(lf.bytes)}"

            val path = lf.file.absolutePath
            val sel = selectionMode && selected.contains(path)
            h.check.visibility = if (sel) View.VISIBLE else View.GONE
            h.itemView.setBackgroundColor(if (sel) (tc.accent and 0x00FFFFFF) or 0x40000000 else Color.TRANSPARENT)

            h.itemView.setOnClickListener {
                if (selectionMode) toggle(path)
            }
            h.itemView.setOnLongClickListener {
                if (!selectionMode) enterSelection(path) else toggle(path)
                true
            }
        }
    }
}
