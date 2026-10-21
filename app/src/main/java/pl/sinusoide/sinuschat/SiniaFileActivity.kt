package pl.sinusoide.sinuschat

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import pl.sinusoide.sinuschat.ui.ThemeUi
import pl.sinusoide.sinuschat.util.DownloadsSaver
import pl.sinusoide.sinuschat.util.PopupHelper
import pl.sinusoide.sinuschat.util.ThemeManager
import java.io.File
import androidx.activity.enableEdgeToEdge

class SiniaFileActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PATH   = "file_path"
        const val EXTRA_NAME   = "file_name"
        const val EXTRA_KIND   = "file_kind"
        const val EXTRA_SOURCE = "file_source"
        const val EXTRA_WRAP   = "file_wrap"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val path = intent.getStringExtra(EXTRA_PATH) ?: run { finish(); return }
        val name = intent.getStringExtra(EXTRA_NAME) ?: File(path).name
        val kind = runCatching { SiniaFile.Kind.valueOf(intent.getStringExtra(EXTRA_KIND) ?: "") }
            .getOrDefault(SiniaFile.Kind.CODE)
        val source = intent.getStringExtra(EXTRA_SOURCE).orEmpty()
        val file = File(path)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ThemeUi.backgroundSolid(this@SiniaFileActivity))
        }
        root.addView(topBar(name, file, kind))

        val wrapText = intent.getBooleanExtra(EXTRA_WRAP, false)
        val content: View = when {
            kind == SiniaFile.Kind.HTML -> webView(file.readText())
            kind == SiniaFile.Kind.CODE -> codeView(file.readText(), wrap = wrapText)
            openExternally(file, kind) -> { finish(); return }
            else -> codeView(source.ifEmpty { getString(R.string.sinia_file_no_preview) }, wrap = true)
        }
        root.addView(content, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
    }

    private fun topBar(name: String, file: File, kind: SiniaFile.Kind): View {
        val d = resources.displayMetrics.density
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt())
        }
        bar.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_back)
            background = null
            imageTintList = android.content.res.ColorStateList.valueOf(ThemeUi.textPrimary(context))
            setPadding((9 * d).toInt(), (9 * d).toInt(), (9 * d).toInt(), (9 * d).toInt())
            contentDescription = getString(R.string.action_back)
            layoutParams = LinearLayout.LayoutParams((42 * d).toInt(), (42 * d).toInt())
            setOnClickListener { finish() }
        })
        bar.addView(TextView(this).apply {
            text = name
            setTextColor(ThemeUi.textPrimary(context))
            textSize = 16f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = (6 * d).toInt() }
        })
        bar.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_download)
            background = null
            imageTintList = android.content.res.ColorStateList.valueOf(ThemeUi.accent(context))
            setPadding((9 * d).toInt(), (9 * d).toInt(), (9 * d).toInt(), (9 * d).toInt())
            contentDescription = getString(R.string.sinia_file_save)
            layoutParams = LinearLayout.LayoutParams((42 * d).toInt(), (42 * d).toInt())
            setOnClickListener {
                val saved = DownloadsSaver.save(context, file, name, SiniaFile(name, kind, file.path).mime)
                if (saved != null) PopupHelper.show(this@SiniaFileActivity, getString(R.string.sinia_file_saved, saved))
                else PopupHelper.showError(this@SiniaFileActivity, getString(R.string.sinia_file_save_failed))
            }
        })
        return bar
    }

    private fun webView(html: String): View = WebView(this).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.blockNetworkLoads = true
        loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    private fun codeView(text: String, wrap: Boolean): View {
        val d = resources.displayMetrics.density
        val tv = TextView(this).apply {
            setText(text)
            typeface = Typeface.MONOSPACE
            textSize = 12.5f
            setTextColor(ThemeUi.textPrimary(context))
            setTextIsSelectable(true)
            setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (24 * d).toInt())
        }
        if (wrap) {
            tv.typeface = Typeface.DEFAULT
            tv.textSize = 14f
            return ScrollView(this).apply { addView(tv) }
        }
        val horizontal = HorizontalScrollView(this).apply { addView(tv) }
        return ScrollView(this).apply { addView(horizontal) }
    }

    private fun openExternally(file: File, kind: SiniaFile.Kind): Boolean = try {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, SiniaFile(file.name, kind, file.path).mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (intent.resolveActivity(packageManager) != null) { startActivity(intent); true } else false
    } catch (_: Throwable) { false }
}
