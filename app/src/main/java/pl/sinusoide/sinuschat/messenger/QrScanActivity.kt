package pl.sinusoide.sinuschat.messenger

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import pl.sinusoide.sinuschat.R
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.BarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.google.zxing.BarcodeFormat
import androidx.activity.enableEdgeToEdge

class QrScanActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(pl.sinusoide.sinuschat.util.LocaleManager.wrap(newBase))
    }


    private lateinit var barcodeView: BarcodeView
    private var handled = false
    private var hasCameraPermission = false

    private val cameraPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            barcodeView.resume()
        } else {
            pl.sinusoide.sinuschat.util.PopupHelper.showError(this, getString(R.string.camera_permission_needed))
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val dp = resources.displayMetrics.density

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        barcodeView = BarcodeView(this).apply {
            decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
        }
        root.addView(barcodeView, FrameLayout.LayoutParams(-1, -1))

        val overlay = ScannerOverlay(this)
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((8*dp).toInt(), (8*dp).toInt(), (16*dp).toInt(), (8*dp).toInt())
        }
        topBar.addView(android.widget.ImageButton(this).apply {
            setImageResource(R.drawable.ic_back)
            setBackgroundResource(0); setColorFilter(Color.WHITE)
            setPadding((10*dp).toInt(), (10*dp).toInt(), (10*dp).toInt(), (10*dp).toInt())
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams((44*dp).toInt(), (44*dp).toInt()))
        topBar.addView(TextView(this).apply {
            text = getString(R.string.scan_qr); textSize = 18f; setTextColor(Color.WHITE)
            setPadding((8*dp).toInt(), 0, 0, 0)
        })
        val topWrap = FrameLayout(this).apply { addView(topBar) }
        root.addView(topWrap, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply {
            topMargin = statusBarHeight()
        })

        val hint = TextView(this).apply {
            text = getString(R.string.qr_in_frame)
            textSize = 15f; setTextColor(0xFFDDDDDD.toInt())
            gravity = Gravity.CENTER
        }
        root.addView(hint, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        root.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val box = ScannerOverlay.box(v.width.toFloat(), v.height.toFloat())
            val below = box.bottom + 20 * dp
            hint.translationY = if (below + hint.height <= v.height - 8 * dp) below
                else (v.height - hint.height - 14 * dp).coerceAtLeast(0f)
        }

        setContentView(root)

        barcodeView.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult) {
                if (handled) return
                val text = result.text ?: return
                handled = true
                barcodeView.pause()
                val data = android.content.Intent().putExtra("qr_result", text)
                setResult(RESULT_OK, data)
                finish()
            }
        })

        hasCameraPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else (24 * resources.displayMetrics.density).toInt()
    }

    override fun onResume() {
        super.onResume()
        if (hasCameraPermission) barcodeView.resume()
    }
    override fun onPause() { super.onPause(); barcodeView.pause() }

    private class ScannerOverlay(context: android.content.Context) : View(context) {
        private val scrim = android.graphics.Paint().apply { color = 0xB3000000.toInt() }
        private val clear = android.graphics.Paint().apply {
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
            isAntiAlias = true
        }
        private val corner = android.graphics.Paint().apply {
            color = 0xFF5288C1.toInt()
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 10f
            strokeCap = android.graphics.Paint.Cap.ROUND
            isAntiAlias = true
        }
        override fun onDraw(canvas: android.graphics.Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            val b = box(w, h)
            val boxSize = b.width()
            val left = b.left
            val top = b.top
            val right = b.right
            val bottom = b.bottom
            val r = 28f

            val layer = canvas.saveLayer(0f, 0f, w, h, null)
            canvas.drawRect(0f, 0f, w, h, scrim)
            canvas.drawRoundRect(left, top, right, bottom, r, r, clear)
            canvas.restoreToCount(layer)

            val armLen = boxSize * 0.10f
            val off = 5f
            canvas.drawLine(left - off, top + armLen, left - off, top + r, corner)
            canvas.drawLine(left + r, top - off, left + armLen, top - off, corner)
            canvas.drawLine(right + off, top + armLen, right + off, top + r, corner)
            canvas.drawLine(right - r, top - off, right - armLen, top - off, corner)
            canvas.drawLine(left - off, bottom - armLen, left - off, bottom - r, corner)
            canvas.drawLine(left + r, bottom + off, left + armLen, bottom + off, corner)
            canvas.drawLine(right + off, bottom - armLen, right + off, bottom - r, corner)
            canvas.drawLine(right - r, bottom + off, right - armLen, bottom + off, corner)

        }

        companion object {
            fun box(w: Float, h: Float): android.graphics.RectF {
                val size = if (w > h) h * 0.62f else minOf(w * 0.72f, h * 0.40f)
                val left = (w - size) / 2f
                val top = if (w > h) (h - size) / 2f else h * 0.30f
                return android.graphics.RectF(left, top, left + size, top + size)
            }
        }
    }
}