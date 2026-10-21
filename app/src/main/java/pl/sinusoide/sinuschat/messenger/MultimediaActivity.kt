package pl.sinusoide.sinuschat.messenger

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import pl.sinusoide.sinuschat.R
import pl.sinusoide.sinuschat.util.MediaManager
import pl.sinusoide.sinuschat.util.PopupHelper

class MultimediaActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(pl.sinusoide.sinuschat.util.LocaleManager.wrap(newBase))
    }


    companion object { private const val TAG = "MultimediaActivity" }

    private var conversationId: String = ""
    private var otherUserName: String = ""

    private lateinit var recycler: RecyclerView
    private lateinit var tvTotalSize: TextView
    private lateinit var tvCount: TextView
    private lateinit var emptyView: View
    private lateinit var adapter: MediaGridAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            pl.sinusoide.sinuschat.util.ThemeManager.applyTheme(this)
            enableEdgeToEdge()
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_multimedia)
            supportActionBar?.hide()

            val statusSpacer = findViewById<View>(R.id.media_status_spacer)
            val navSpacer = findViewById<View>(R.id.media_nav_spacer)
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.media_root)) { _, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                statusSpacer.layoutParams = statusSpacer.layoutParams.apply { height = systemBars.top }
                navSpacer.layoutParams = navSpacer.layoutParams.apply { height = systemBars.bottom }
                insets
            }

            conversationId = intent.getStringExtra("conversationId") ?: ""
            otherUserName = intent.getStringExtra("otherUserName") ?: "Multimedia"

            if (conversationId.isEmpty()) {
                PopupHelper.showError(this, getString(R.string.error_no_conv_id))
                finish()
                return
            }

            findViewById<TextView>(R.id.media_title).text = otherUserName
            findViewById<ImageButton>(R.id.btn_media_back).setOnClickListener { finish() }
            findViewById<ImageButton>(R.id.btn_media_clear_all).setOnClickListener { confirmClearAll() }

            tvTotalSize = findViewById(R.id.tv_media_total_size)
            tvCount = findViewById(R.id.tv_media_count)
            emptyView = findViewById(R.id.media_empty)
            recycler = findViewById(R.id.media_recycler)

            adapter = MediaGridAdapter(
                onClick = { mediaFile -> openMedia(mediaFile) },
                onLongClick = { mediaFile -> confirmDeleteOne(mediaFile) }
            )
            recycler.layoutManager = GridLayoutManager(this, 3)
            recycler.adapter = adapter
            recycler.setHasFixedSize(true)

            loadMedia()
        } catch (e: Exception) {
            Log.e(TAG, "onCreate failed", e)
            PopupHelper.showError(this, getString(R.string.error_opening_media, e.message))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) loadMedia()
    }

    private fun loadMedia() {
        try {
            val files = MediaManager.listMedia(this, conversationId)
            adapter.submitList(files)

            val totalSize = files.sumOf { it.sizeBytes }
            tvTotalSize.text = MediaManager.formatSize(totalSize)
            tvCount.text = resources.getQuantityString(R.plurals.files_plural, files.size, files.size)

            emptyView.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
            recycler.visibility = if (files.isEmpty()) View.GONE else View.VISIBLE
        } catch (e: Exception) {
            Log.e(TAG, "loadMedia failed", e)
            PopupHelper.showError(this, getString(R.string.error_loading_format, e.message))
        }
    }

    private fun openMedia(mediaFile: MediaManager.MediaFile) {
        pl.sinusoide.sinuschat.util.SafeEngine.guard(this, pl.sinusoide.sinuschat.util.SafeEngine.Risk.OPEN_FILE, mediaFile.file.name) { openMediaConfirmed(mediaFile) }
    }

    private fun openMediaConfirmed(mediaFile: MediaManager.MediaFile) {
        try {
            if (!mediaFile.file.exists()) {
                PopupHelper.show(this, getString(R.string.file_not_exist))
                return
            }
            val uri = FileProvider.getUriForFile(
                this, "${packageName}.fileprovider", mediaFile.file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, when {
                    mediaFile.isImage -> "image/*"
                    mediaFile.isVideo -> "video/*"
                    else -> "*/*"
                })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.action_open)))
        } catch (e: Exception) {
            Log.e(TAG, "openMedia failed", e)
            PopupHelper.showError(this, getString(R.string.cant_open_reason, e.message ?: ""))
        }
    }

    private fun confirmDeleteOne(mediaFile: MediaManager.MediaFile) {
        AlertDialog.Builder(this, R.style.Theme_SinusChat_Dialog)
            .setTitle(getString(R.string.delete_file))
            .setMessage(getString(R.string.delete_file_desc_format, mediaFile.name, MediaManager.formatSize(mediaFile.sizeBytes)))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                if (mediaFile.file.delete()) {
                    PopupHelper.show(this, getString(R.string.file_deleted))
                    loadMedia()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun confirmClearAll() {
        val totalSize = MediaManager.getTotalSize(this, conversationId)
        if (totalSize == 0L) {
            PopupHelper.show(this, getString(R.string.no_files_to_delete))
            return
        }
        AlertDialog.Builder(this, R.style.Theme_SinusChat_Dialog)
            .setTitle(getString(R.string.delete_all_media))
            .setMessage(getString(R.string.delete_all_media_desc, MediaManager.formatSize(totalSize)))
            .setPositiveButton(getString(R.string.delete_all_btn)) { _, _ ->
                MediaManager.clearConversationMedia(this, conversationId)
                PopupHelper.show(this, getString(R.string.media_deleted))
                loadMedia()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}

class MediaGridAdapter(
    private val onClick: (MediaManager.MediaFile) -> Unit,
    private val onLongClick: (MediaManager.MediaFile) -> Unit
) : RecyclerView.Adapter<MediaGridAdapter.VH>() {

    private val items = mutableListOf<MediaManager.MediaFile>()
    private val bitmapCache = mutableMapOf<String, Bitmap?>()

    fun submitList(list: List<MediaManager.MediaFile>) {
        val newPaths = list.map { it.file.absolutePath }.toSet()
        bitmapCache.keys.filter { it !in newPaths }.forEach { bitmapCache.remove(it) }

        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class VH(val view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.media_item_image)
        val icon: ImageView = view.findViewById(R.id.media_item_icon)
        val name: TextView = view.findViewById(R.id.media_item_name)
        val size: TextView = view.findViewById(R.id.media_item_size)
        val videoBadge: View = view.findViewById(R.id.media_item_video_badge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_media_grid, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        try {
            val item = items[position]

            when {
                item.isImage -> {
                    holder.image.visibility = View.VISIBLE
                    holder.icon.visibility = View.GONE
                    holder.name.visibility = View.GONE
                    holder.videoBadge.visibility = View.GONE

                    val cached = bitmapCache[item.file.absolutePath]
                    if (cached != null) {
                        holder.image.setImageBitmap(cached)
                    } else {
                        holder.image.setImageDrawable(null)
                        Thread {
                            try {
                                val bmp = MediaManager.loadBitmapFromFile(item.file, 300)
                                bitmapCache[item.file.absolutePath] = bmp
                                holder.image.post {
                                    if (holder.bindingAdapterPosition == position && bmp != null) {
                                        holder.image.setImageBitmap(bmp)
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("MediaGrid", "Failed to load thumb", e)
                            }
                        }.start()
                    }
                }
                item.isVideo -> {
                    holder.image.visibility = View.GONE
                    holder.icon.visibility = View.VISIBLE
                    holder.name.visibility = View.VISIBLE
                    holder.videoBadge.visibility = View.VISIBLE
                    holder.icon.setImageResource(R.drawable.ic_call_video_on)
                    holder.name.text = item.ext.uppercase().ifEmpty { "VID" }
                }
                else -> {
                    holder.image.visibility = View.GONE
                    holder.icon.visibility = View.VISIBLE
                    holder.name.visibility = View.VISIBLE
                    holder.videoBadge.visibility = View.GONE
                    holder.icon.setImageResource(pl.sinusoide.sinuschat.util.FileIcons.forName(item.file.name))
                    holder.name.text = item.ext.uppercase().ifEmpty { "FILE" }
                }
            }

            holder.size.text = MediaManager.formatSize(item.sizeBytes)

            holder.view.setOnClickListener { onClick(item) }
            holder.view.setOnLongClickListener { onLongClick(item); true }
        } catch (e: Exception) {
            android.util.Log.e("MediaGrid", "bind failed at $position", e)
        }
    }
}
