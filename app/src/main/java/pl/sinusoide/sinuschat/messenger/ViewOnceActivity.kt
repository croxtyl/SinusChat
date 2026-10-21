package pl.sinusoide.sinuschat.messenger

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import pl.sinusoide.sinuschat.R
import pl.sinusoide.sinuschat.util.AppExecutor
import pl.sinusoide.sinuschat.util.FileUploader
import pl.sinusoide.sinuschat.util.MediaManager
import pl.sinusoide.sinuschat.util.ZoomableImageView
import java.io.File
import androidx.activity.enableEdgeToEdge

class ViewOnceActivity : AppCompatActivity() {

    private var conversationId = ""
    private var messageId = ""
    private var fileName = ""

    private var tempFile: File? = null

    private var contentShown = false

    private lateinit var root: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try { setRecentsScreenshotEnabled(false) } catch (_: Throwable) {}
        }

        conversationId = intent.getStringExtra(EXTRA_CONV_ID).orEmpty()
        messageId      = intent.getStringExtra(EXTRA_MSG_ID).orEmpty()
        fileName       = intent.getStringExtra(EXTRA_FILE_NAME).orEmpty().ifEmpty { "plik" }
        val fileId     = intent.getStringExtra(EXTRA_FILE_ID).orEmpty()
        val fileKey    = intent.getStringExtra(EXTRA_FILE_KEY).orEmpty()

        root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(-1, -1)
        }
        setContentView(root)
        showLoading()

        if (conversationId.isEmpty() || messageId.isEmpty() || fileId.isEmpty() || fileKey.isEmpty()) {
            showMessage(getString(R.string.view_once_unavailable)); return
        }

        val app = applicationContext
        AppExecutor.io {
            val bytes = FileUploader.downloadAndDecryptBlocking(app, fileId, fileKey) { p -> loadProgress = p }
            AppExecutor.main {
                if (isFinishing || isDestroyed) return@main
                if (bytes == null || bytes.isEmpty()) {
                    showMessage(getString(R.string.file_download_failed))
                    return@main
                }
                render(bytes)
            }
        }
    }

    private fun render(bytes: ByteArray) {
        val tmp = File(cacheDir, "vo_${System.currentTimeMillis()}_${sanitize(fileName)}")
        val ok = try { tmp.writeBytes(bytes); true } catch (_: Exception) { false }
        if (!ok) { showMessage(getString(R.string.view_once_unavailable)); return }
        tempFile = tmp

        when {
            MediaManager.isImage(fileName) -> showImage(bytes)
            MediaManager.isVideo(fileName) -> showVideo(tmp)
            else -> {
                showMessage(getString(R.string.view_once_unsupported, fileName))
            }
        }
    }

    private fun showImage(bytes: ByteArray) {
        val bmp = MediaManager.loadBitmapFromBytes(bytes)
        if (bmp == null) { showMessage(getString(R.string.view_once_unavailable)); return }
        setContent(ZoomableImageView(this).apply {
            setImageBitmap(bmp)
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(-1, -1)
        })
    }

    private fun showVideo(file: File) {
        val vv = android.widget.VideoView(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -2, Gravity.CENTER)
            setVideoPath(file.absolutePath)
            setOnPreparedListener { mp ->
                mp.isLooping = false
                start()
                consume()
            }
            setOnErrorListener { _, _, _ ->
                showMessage(getString(R.string.view_once_unavailable)); true
            }
        }
        vv.setMediaController(android.widget.MediaController(this).apply { setAnchorView(vv) })
        attach(vv)
    }

    @Volatile private var loadProgress: Float? = null

    private fun showLoading() {
        root.removeAllViews()
        val size = (76 * resources.displayMetrics.density).toInt()
        root.addView(pl.sinusoide.sinuschat.ui.SineProgressRing(this).apply {
            color = Color.WHITE
            disc = 0
            showCancel = false
            isClickable = false
            progress = { loadProgress }
        }, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
    }

    private fun showMessage(msg: String) {
        val dp = resources.displayMetrics.density
        root.removeAllViews()
        root.addView(TextView(this).apply {
            text = msg
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding((24 * dp).toInt(), 0, (24 * dp).toInt(), 0)
            layoutParams = FrameLayout.LayoutParams(-1, -2, Gravity.CENTER)
        })
    }

    private fun setContent(v: View) {
        attach(v)
        consume()
    }

    private fun attach(v: View) {
        root.removeAllViews()
        root.addView(v)
    }

    private fun consume() {
        if (contentShown) return
        contentShown = true

        val ctx = applicationContext
        val convId = conversationId
        val msgId = messageId
        AppExecutor.io {
            ViewOnceManager.markOpenedLocally(ctx, convId, msgId)
            ViewOnceManager.notifySenderOpened(ctx, convId, msgId)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        try { tempFile?.delete() } catch (_: Exception) {}
        tempFile = null
    }

    private fun sanitize(name: String) = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(64)

    companion object {
        const val EXTRA_CONV_ID   = "conversationId"
        const val EXTRA_MSG_ID    = "messageId"
        const val EXTRA_FILE_ID   = "fileId"
        const val EXTRA_FILE_KEY  = "fileKey"
        const val EXTRA_FILE_NAME = "fileName"
    }
}
