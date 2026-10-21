package pl.sinusoide.sinuschat.util

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.exifinterface.media.ExifInterface
import pl.sinusoide.sinuschat.R
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

class CropActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    companion object {
        const val EXTRA_URI = "crop_uri"
        const val EXTRA_OUTPUT = "crop_output"
        private const val OUTPUT_SIZE = 512
        private const val MAX_SIDE = 2560
    }

    private lateinit var crop: CropView
    private lateinit var ruler: AngleRuler
    private lateinit var angleLabel: TextView
    private lateinit var resetBtn: TextView
    private lateinit var doneBtn: LinearLayout
    private lateinit var hint: TextView
    private var saving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))

        val uri = IntentCompat.getParcelableExtra(intent, EXTRA_URI, Uri::class.java)
        if (uri == null) { finish(); return }

        val dp = resources.displayMetrics.density
        val accent = pl.sinusoide.sinuschat.ui.ThemeUi.accent(this)

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        crop = CropView(this)
        root.addView(crop, FrameLayout.LayoutParams(-1, -1))

        val progress = ProgressBar(this).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(accent)
        }
        root.addView(progress, FrameLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt(), Gravity.CENTER))

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xCC000000.toInt(), 0x00000000))
        }
        topBar.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_back); setColorFilter(Color.WHITE)
            val p = (10 * dp).toInt(); setPadding(p, p, p, p)
            background = rippleBorderless()
            contentDescription = getString(R.string.cancel)
            setOnClickListener { setResult(Activity.RESULT_CANCELED); finish() }
        }, LinearLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt()))
        topBar.addView(TextView(this).apply {
            text = getString(R.string.crop_title)
            textSize = 17f; setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            maxLines = 1
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = (6 * dp).toInt() })
        resetBtn = TextView(this).apply {
            text = getString(R.string.crop_reset)
            textSize = 15f; setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val ph = (14 * dp).toInt(); val pv = (8 * dp).toInt(); setPadding(ph, pv, ph, pv)
            background = rippleBorderless()
            alpha = 0f
            isEnabled = false
            setOnClickListener { crop.reset() }
        }
        topBar.addView(resetBtn)
        root.addView(topBar, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        hint = TextView(this).apply {
            text = getString(R.string.crop_hint)
            textSize = 13f; setTextColor(0xB3FFFFFF.toInt())
            gravity = Gravity.CENTER
            alpha = 0f
        }
        root.addView(hint, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0xE6000000.toInt(), 0x00000000))
        }
        angleLabel = TextView(this).apply {
            text = "0°"
            textSize = 13f; setTextColor(0x99FFFFFF.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            fontFeatureSettings = "tnum"
        }
        bottom.addView(angleLabel, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = (2 * dp).toInt() })
        ruler = AngleRuler(this, accent).apply { contentDescription = getString(R.string.crop_straighten) }
        bottom.addView(ruler, LinearLayout.LayoutParams(-1, (44 * dp).toInt()))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * dp).toInt(), (10 * dp).toInt(), (16 * dp).toInt(), 0)
        }
        actions.addView(chip(getString(R.string.rotate)) { crop.rotate90() })
        actions.addView(chip(getString(R.string.crop_flip)) { crop.flip() },
            LinearLayout.LayoutParams(-2, -2).apply { marginStart = (8 * dp).toInt() })
        actions.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        doneBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val ph = (18 * dp).toInt(); val pv = (11 * dp).toInt(); setPadding(ph - (4 * dp).toInt(), pv, ph, pv)
            background = GradientDrawable().apply { cornerRadius = 24 * dp; setColor(accent) }
            elevation = 4 * dp
            isClickable = true
            isEnabled = false
            alpha = 0.5f
            contentDescription = getString(R.string.done)
            addView(ImageView(this@CropActivity).apply {
                setImageResource(R.drawable.ic_check); setColorFilter(Color.WHITE)
            }, LinearLayout.LayoutParams((20 * dp).toInt(), (20 * dp).toInt()).apply { marginEnd = (6 * dp).toInt() })
            addView(TextView(this@CropActivity).apply {
                text = getString(R.string.done)
                textSize = 15f; setTextColor(Color.WHITE)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            })
            setOnClickListener { save() }
        }
        actions.addView(doneBtn)
        bottom.addView(actions, LinearLayout.LayoutParams(-1, -2))
        root.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            topBar.setPadding(b.left + (4 * dp).toInt(), b.top + (6 * dp).toInt(), b.right + (8 * dp).toInt(), (18 * dp).toInt())
            bottom.setPadding(b.left, (22 * dp).toInt(), b.right, b.bottom + (14 * dp).toInt())
            crop.sideInsets(b.left, b.right)
            insets
        }
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val top = topBar.bottom - (10 * dp).toInt()
            (hint.layoutParams as FrameLayout.LayoutParams).let {
                if (it.topMargin != top) { it.topMargin = top; hint.layoutParams = it }
            }
            crop.frame(topBar.bottom + hint.height, bottom.height - (14 * dp).toInt())
        }

        crop.onChanged = { refreshReset() }
        crop.onInteract = { if (hint.alpha > 0f) hint.animate().alpha(0f).setDuration(200).start() }
        crop.onStraighten = { deg -> ruler.setValue(deg); showAngle(deg) }
        ruler.onChange = { deg -> crop.setStraighten(deg); showAngle(deg) }
        ruler.onTouching = { active -> crop.showGrid(active); if (active) crop.onInteract?.invoke() }

        setContentView(root)

        AppExecutor.io {
            val bmp = decode(uri)
            AppExecutor.main {
                if (isFinishing || isDestroyed) return@main
                progress.visibility = View.GONE
                if (bmp == null) {
                    Toast.makeText(applicationContext, R.string.crop_open_failed, Toast.LENGTH_SHORT).show()
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                    return@main
                }
                crop.setBitmap(bmp)
                doneBtn.isEnabled = true
                doneBtn.animate().alpha(1f).setDuration(200).start()
                hint.animate().alpha(1f).setStartDelay(250).setDuration(300).start()
            }
        }
    }

    private fun rippleBorderless() = android.util.TypedValue().let { tv ->
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)
        androidx.core.content.ContextCompat.getDrawable(this, tv.resourceId)
    }

    private fun chip(label: String, onClick: () -> Unit) = TextView(this).apply {
        val dp = resources.displayMetrics.density
        text = label
        textSize = 14f; setTextColor(Color.WHITE)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        val ph = (16 * dp).toInt(); val pv = (10 * dp).toInt(); setPadding(ph, pv, ph, pv)
        background = android.graphics.drawable.RippleDrawable(
            ColorStateList.valueOf(0x40FFFFFF),
            GradientDrawable().apply { cornerRadius = 22 * dp; setColor(0x26FFFFFF) }, null)
        setOnClickListener { onClick() }
    }

    private fun showAngle(deg: Float) {
        val v = deg.roundToInt()
        angleLabel.text = "$v°"
        angleLabel.setTextColor(if (v == 0) 0x99FFFFFF.toInt() else Color.WHITE)
    }

    private fun refreshReset() {
        val modified = crop.isModified()
        if (resetBtn.isEnabled == modified) return
        resetBtn.isEnabled = modified
        resetBtn.animate().alpha(if (modified) 1f else 0f).setDuration(180).start()
    }

    private fun save() {
        if (saving || !crop.hasBitmap()) return
        saving = true
        doneBtn.isEnabled = false
        val job = crop.snapshot() ?: run { saving = false; doneBtn.isEnabled = true; return }
        AppExecutor.io {
            val file = File(cacheDir, "cropped_avatar_${System.currentTimeMillis()}.jpg")
            val error = try {
                val out = job(OUTPUT_SIZE)
                try {
                    FileOutputStream(file).use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                } finally { out.recycle() }
                null
            } catch (e: Throwable) { e.message ?: "" }
            AppExecutor.main {
                if (isFinishing || isDestroyed) return@main
                if (error == null) {
                    setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_OUTPUT, Uri.fromFile(file).toString()))
                    finish()
                } else {
                    saving = false
                    doneBtn.isEnabled = true
                    PopupHelper.showError(this, getString(R.string.error_save_reason, error))
                }
            }
        }
    }

    private fun decode(uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                val src = ImageDecoder.createSource(contentResolver, uri)
                return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    var sample = 1
                    while (maxOf(info.size.width, info.size.height) / sample > MAX_SIDE) sample *= 2
                    decoder.setTargetSampleSize(sample)
                }
            } catch (_: Throwable) {}
        }
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
            val bmp = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return null
            val orientation = try {
                contentResolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (_: Throwable) { ExifInterface.ORIENTATION_NORMAL }
            val m = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(-90f); m.postScale(-1f, 1f) }
            }
            if (m.isIdentity) bmp
            else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true).also { if (it !== bmp) bmp.recycle() }
        } catch (_: Throwable) { null }
    }

    private class CropView(context: Context) : View(context) {

        var onChanged: (() -> Unit)? = null
        var onInteract: (() -> Unit)? = null
        var onStraighten: ((Float) -> Unit)? = null

        private val dp = resources.displayMetrics.density
        private var bmp: Bitmap? = null
        private var base = 0f
        private var straight = 0f
        private var flipped = false
        private var z = 1f
        private var tx = 0f
        private var ty = 0f
        private var mirror = 1f
        private var appear = 1f
        private var gridAlpha = 0f

        private var areaTop = 0
        private var areaBottom = 0
        private var padLeft = 0
        private var padRight = 0
        private var cx = 0f
        private var cy = 0f
        private var r = 0f

        private val zMax = 8f
        private val m = Matrix()
        private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xB3000000.toInt() }
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.5f * dp; color = 0xE6FFFFFF.toInt()
        }
        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f * dp; color = Color.WHITE }
        private val dimPath = Path()
        private val circlePath = Path()

        private var anim: ValueAnimator? = null
        private var busy = false
        private var gridAnim: ValueAnimator? = null
        private var gridWanted = false

        private var gesture = false
        private var rawZ = 1f
        private var rawTx = 0f
        private var rawTy = 0f
        private var lastFx = 0f
        private var lastFy = 0f

        fun hasBitmap() = bmp != null

        fun setBitmap(b: Bitmap) {
            bmp = b
            base = 0f; straight = 0f; flipped = false; z = 1f; tx = 0f; ty = 0f
            appear = 0f
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 320
                interpolator = DecelerateInterpolator()
                addUpdateListener { appear = it.animatedValue as Float; invalidate() }
                start()
            }
            onChanged?.invoke()
        }

        fun sideInsets(left: Int, right: Int) {
            if (padLeft == left && padRight == right) return
            padLeft = left; padRight = right
            layoutCircle()
        }

        fun frame(top: Int, bottom: Int) {
            if (areaTop == top && areaBottom == bottom) return
            areaTop = top; areaBottom = bottom
            layoutCircle()
        }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            super.onSizeChanged(w, h, ow, oh)
            layoutCircle()
        }

        private fun layoutCircle() {
            if (width == 0 || height == 0) return
            val availW = (width - padLeft - padRight).toFloat()
            val availH = (height - areaTop - areaBottom).toFloat().coerceAtLeast(height * 0.4f)
            val newR = (minOf(availW, availH) / 2f - 20 * dp).coerceAtLeast(60 * dp)
            if (r > 0f) { tx *= newR / r; ty *= newR / r }
            r = newR
            cx = padLeft + availW / 2f
            cy = areaTop + ((height - areaTop - areaBottom).toFloat().coerceAtLeast(2 * newR + 40 * dp)) / 2f
            if (cy + r > height) cy = height / 2f
            dimPath.reset()
            dimPath.fillType = Path.FillType.EVEN_ODD
            dimPath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            dimPath.addCircle(cx, cy, r, Path.Direction.CW)
            circlePath.reset()
            circlePath.addCircle(cx, cy, r, Path.Direction.CW)
            clampNow()
            invalidate()
        }

        private fun theta() = base + straight

        private fun minScale(thetaDeg: Float): Float {
            val b = bmp ?: return 1f
            val rad = Math.toRadians(thetaDeg.toDouble())
            val k = r * (abs(cos(rad)) + abs(sin(rad))).toFloat()
            return 2f * k / minOf(b.width, b.height)
        }

        private fun scaleOf(zoom: Float, thetaDeg: Float) = zoom * minScale(thetaDeg)

        private fun clampT(x: Float, y: Float, zoom: Float, thetaDeg: Float, give: Float = 0f): Pair<Float, Float> {
            val b = bmp ?: return 0f to 0f
            val rad = Math.toRadians(thetaDeg.toDouble())
            val c = cos(rad).toFloat(); val s = sin(rad).toFloat()
            val k = r * (abs(c) + abs(s))
            val sc = scaleOf(zoom, thetaDeg)
            val ex = (b.width * sc / 2f - k).coerceAtLeast(0f)
            val ey = (b.height * sc / 2f - k).coerceAtLeast(0f)
            val ux = x * c + y * s
            val uy = -x * s + y * c
            val cux = rubber(ux, -ex, ex, give)
            val cuy = rubber(uy, -ey, ey, give)
            return (cux * c - cuy * s) to (cux * s + cuy * c)
        }

        private fun rubber(v: Float, lo: Float, hi: Float, give: Float): Float = when {
            v < lo -> lo - (lo - v) * give
            v > hi -> hi + (v - hi) * give
            else -> v
        }

        private fun clampNow() {
            z = z.coerceIn(1f, zMax)
            val (nx, ny) = clampT(tx, ty, z, theta())
            tx = nx; ty = ny
        }

        private fun centerLocal(thetaDeg: Float, sc: Float): Pair<Float, Float> {
            val rad = Math.toRadians(thetaDeg.toDouble())
            val c = cos(rad).toFloat(); val s = sin(rad).toFloat()
            val x = -tx; val y = -ty
            return ((x * c + y * s) / sc) to ((-x * s + y * c) / sc)
        }

        private fun placeCenter(px: Float, py: Float, thetaDeg: Float, sc: Float) {
            val rad = Math.toRadians(thetaDeg.toDouble())
            val c = cos(rad).toFloat(); val s = sin(rad).toFloat()
            val x = px * sc; val y = py * sc
            tx = -(x * c - y * s)
            ty = -(x * s + y * c)
        }

        private fun buildMatrix(out: Matrix) {
            val b = bmp ?: return
            out.reset()
            out.postTranslate(-b.width / 2f, -b.height / 2f)
            if (flipped) out.postScale(-1f, 1f)
            out.postRotate(theta())
            val sc = scaleOf(z, theta())
            out.postScale(sc, sc)
            out.postTranslate(cx + tx, cy + ty)
        }

        fun isModified(): Boolean =
            abs(z - 1f) > 0.01f || abs(straight) > 0.05f || flipped ||
                ((base % 360f) + 360f) % 360f > 0.5f || abs(tx) > 1f || abs(ty) > 1f

        fun setStraighten(deg: Float) {
            if (bmp == null || busy) return
            anim?.cancel()
            val old = theta()
            val (px, py) = centerLocal(old, scaleOf(z, old))
            straight = deg.coerceIn(-45f, 45f)
            val nt = theta()
            placeCenter(px, py, nt, scaleOf(z, nt))
            clampNow()
            invalidate()
            onChanged?.invoke()
        }

        fun showGrid(show: Boolean) {
            if (gridWanted == show) return
            gridWanted = show
            gridAnim?.cancel()
            gridAnim = ValueAnimator.ofFloat(gridAlpha, if (show) 1f else 0f).apply {
                duration = if (show) 150 else 380
                startDelay = if (show) 0 else 250
                addUpdateListener { gridAlpha = it.animatedValue as Float; invalidate() }
                start()
            }
        }

        private fun run(duration: Long, interp: android.animation.TimeInterpolator, step: (Float) -> Unit, lock: Boolean = false, end: () -> Unit = {}) {
            anim?.cancel()
            busy = lock
            val a = ValueAnimator.ofFloat(0f, 1f)
            a.duration = duration
            a.interpolator = interp
            a.addUpdateListener { step(it.animatedValue as Float); invalidate() }
            a.addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: android.animation.Animator) { cancelled = true }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (anim === a) { anim = null; busy = false }
                    if (!cancelled) end()
                    invalidate()
                    onChanged?.invoke()
                }
            })
            anim = a
            a.start()
        }

        fun rotate90() {
            if (bmp == null || busy) return
            if (anim != null) { anim?.cancel(); clampNow() }
            onInteract?.invoke()
            val from = base
            val th0 = theta()
            val (px, py) = centerLocal(th0, scaleOf(z, th0))
            run(260, DecelerateInterpolator(), { f ->
                base = from - 90f * f
                val th = theta()
                placeCenter(px, py, th, scaleOf(z, th))
                val (nx, ny) = clampT(tx, ty, z, th)
                tx = nx; ty = ny
            }, lock = true) {
                base = ((from - 90f) % 360f + 360f) % 360f
                clampNow()
            }
        }

        fun flip() {
            if (bmp == null || busy) return
            if (anim != null) { anim?.cancel(); clampNow() }
            onInteract?.invoke()
            run(320, AccelerateDecelerateInterpolator(), { f -> mirror = 1f - 2f * f }, lock = true) {
                mirror = 1f
                flipped = !flipped
                base = ((-base) % 360f + 360f) % 360f
                straight = -straight
                tx = -tx
                clampNow()
                onStraighten?.invoke(straight)
            }
        }

        fun reset() {
            if (bmp == null) return
            anim?.cancel()
            mirror = 1f
            val z0 = z; val tx0 = tx; val ty0 = ty; val s0 = straight
            var b0 = ((base % 360f) + 360f) % 360f
            if (b0 > 180f) b0 -= 360f
            if (flipped) { flipped = false; b0 = -b0 }
            run(300, DecelerateInterpolator(), { f ->
                z = z0 + (1f - z0) * f
                tx = tx0 * (1f - f); ty = ty0 * (1f - f)
                straight = s0 * (1f - f)
                base = b0 * (1f - f)
                onStraighten?.invoke(straight)
            }, lock = true) {
                z = 1f; tx = 0f; ty = 0f; straight = 0f; base = 0f
                onStraighten?.invoke(0f)
            }
        }

        fun snapshot(): ((Int) -> Bitmap)? {
            val b = bmp ?: return null
            val mm = Matrix().also { buildMatrix(it) }
            val left = cx - r; val top = cy - r; val side = 2f * r
            return { size ->
                val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val c = Canvas(out)
                c.drawColor(Color.BLACK)
                c.scale(size / side, size / side)
                c.translate(-left, -top)
                c.drawBitmap(b, mm, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG))
                out
            }
        }

        private val scaleDetector: ScaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                val f = d.scaleFactor
                if (f <= 0f || f.isNaN()) return true
                val newRaw = (rawZ * f).coerceIn(0.5f, zMax * 1.6f)
                val ratio = newRaw / rawZ
                rawZ = newRaw
                val fx = d.focusX - cx; val fy = d.focusY - cy
                rawTx = fx * (1f - ratio) + rawTx * ratio
                rawTy = fy * (1f - ratio) + rawTy * ratio
                applyRaw()
                return true
            }
        }).apply { isQuickScaleEnabled = false }

        private val tapDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (bmp == null) return true
                val z0 = z; val tx0 = tx; val ty0 = ty
                val targetZ: Float
                var ntx: Float; var nty: Float
                if (z > 1.25f) {
                    targetZ = 1f
                    ntx = tx0 / z0; nty = ty0 / z0
                } else {
                    targetZ = 2.5f
                    val ratio = targetZ / z0
                    val fx = e.x - cx; val fy = e.y - cy
                    ntx = fx * (1f - ratio) + tx0 * ratio
                    nty = fy * (1f - ratio) + ty0 * ratio
                }
                val (cxT, cyT) = clampT(ntx, nty, targetZ, theta())
                ntx = cxT; nty = cyT
                run(260, DecelerateInterpolator(), { f ->
                    z = z0 + (targetZ - z0) * f
                    tx = tx0 + (ntx - tx0) * f
                    ty = ty0 + (nty - ty0) * f
                })
                return true
            }
        })

        private fun applyRaw() {
            z = rubber(rawZ, 1f, zMax, 0.35f).coerceAtLeast(0.6f)
            val (nx, ny) = clampT(rawTx, rawTy, z, theta(), 0.3f)
            tx = nx; ty = ny
            invalidate()
        }

        private fun focus(ev: MotionEvent, skip: Int = -1): Pair<Float, Float> {
            var sx = 0f; var sy = 0f; var n = 0
            for (i in 0 until ev.pointerCount) {
                if (i == skip) continue
                sx += ev.getX(i); sy += ev.getY(i); n++
            }
            return if (n == 0) 0f to 0f else (sx / n) to (sy / n)
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            if (bmp == null || r <= 0f) return true
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) gesture = !busy
            if (!gesture) return true
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (anim != null) { anim?.cancel(); clampNow() }
                    rawZ = z; rawTx = tx; rawTy = ty
                    val (fx, fy) = focus(ev); lastFx = fx; lastFy = fy
                    onInteract?.invoke()
                    showGrid(true)
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    val (fx, fy) = focus(ev); lastFx = fx; lastFy = fy
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    val (fx, fy) = focus(ev, ev.actionIndex); lastFx = fx; lastFy = fy
                }
                MotionEvent.ACTION_MOVE -> {
                    val (fx, fy) = focus(ev)
                    if (anim == null) {
                        rawTx += fx - lastFx; rawTy += fy - lastFy
                        applyRaw()
                    }
                    lastFx = fx; lastFy = fy
                }
            }
            tapDetector.onTouchEvent(ev)
            if (anim == null) scaleDetector.onTouchEvent(ev)
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                gesture = false
                showGrid(false)
                settle()
            }
            return true
        }

        private fun settle() {
            if (anim != null) return
            val z0 = z; val tx0 = tx; val ty0 = ty
            val targetZ = z0.coerceIn(1f, zMax)
            val ratio = targetZ / z0
            val (ntx, nty) = clampT(tx0 * ratio, ty0 * ratio, targetZ, theta())
            if (abs(targetZ - z0) < 0.001f && abs(ntx - tx0) < 0.5f && abs(nty - ty0) < 0.5f) {
                z = targetZ; tx = ntx; ty = nty
                onChanged?.invoke()
                return
            }
            run(240, DecelerateInterpolator(), { f ->
                z = z0 + (targetZ - z0) * f
                tx = tx0 + (ntx - tx0) * f
                ty = ty0 + (nty - ty0) * f
            })
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val b = bmp
            if (b != null && r > 0f) {
                buildMatrix(m)
                canvas.save()
                if (mirror != 1f) canvas.scale(mirror, 1f, cx, cy)
                if (appear < 1f) {
                    val s = 0.94f + 0.06f * appear
                    canvas.scale(s, s, cx, cy)
                    bitmapPaint.alpha = (255 * appear).toInt()
                } else bitmapPaint.alpha = 255
                canvas.drawBitmap(b, m, bitmapPaint)
                canvas.restore()
            }
            if (r <= 0f) return
            canvas.drawPath(dimPath, dimPaint)
            if (gridAlpha > 0f) {
                canvas.save()
                canvas.clipPath(circlePath)
                gridPaint.alpha = (110 * gridAlpha).toInt()
                val step = 2f * r / 3f
                for (i in 1..2) {
                    val x = cx - r + step * i
                    val y = cy - r + step * i
                    canvas.drawLine(x, cy - r, x, cy + r, gridPaint)
                    canvas.drawLine(cx - r, y, cx + r, y, gridPaint)
                }
                canvas.restore()
            }
            canvas.drawCircle(cx, cy, r, ringPaint)
        }
    }

    private class AngleRuler(context: Context, private val accent: Int) : View(context) {

        var onChange: ((Float) -> Unit)? = null
        var onTouching: ((Boolean) -> Unit)? = null

        private val dp = resources.displayMetrics.density
        private val spacing = 9f * dp
        private var value = 0f
        private var lastX = 0f
        private var anim: ValueAnimator? = null
        private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; color = Color.WHITE }
        private val indicator = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND; strokeWidth = 2.5f * dp; color = accent
        }

        private val taps = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean { animateTo(0f); return true }
        })

        fun setValue(v: Float) {
            if (anim != null) return
            value = v.coerceIn(-45f, 45f)
            invalidate()
        }

        private fun animateTo(target: Float) {
            anim?.cancel()
            val from = value
            if (abs(from - target) < 0.01f) return
            anim = ValueAnimator.ofFloat(from, target).apply {
                duration = 220
                interpolator = DecelerateInterpolator()
                addUpdateListener { value = it.animatedValue as Float; onChange?.invoke(value); invalidate() }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) { anim = null }
                })
                start()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat(); val h = height.toFloat()
            val mid = w / 2f; val cyy = h / 2f
            for (deg in -45..45) {
                val x = mid + (deg - value) * spacing
                if (x < 0f || x > w) continue
                val major = deg % 5 == 0
                val len = when { deg == 0 -> 22f * dp; major -> 15f * dp; else -> 8f * dp }
                val edge = 1f - ((x - mid) / mid).let { it * it }
                tick.strokeWidth = (if (major) 1.8f else 1.2f) * dp
                tick.alpha = ((if (major) 200 else 110) * edge.coerceIn(0f, 1f)).toInt()
                canvas.drawLine(x, cyy - len / 2f, x, cyy + len / 2f, tick)
            }
            canvas.drawLine(mid, cyy - 14f * dp, mid, cyy + 14f * dp, indicator)
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            taps.onTouchEvent(ev)
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    anim?.cancel(); anim = null
                    lastX = ev.x
                    parent?.requestDisallowInterceptTouchEvent(true)
                    onTouching?.invoke(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    val before = value
                    value = (value - (ev.x - lastX) / spacing).coerceIn(-45f, 45f)
                    lastX = ev.x
                    if (floor(before / 5f) != floor(value / 5f)) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onChange?.invoke(value)
                    invalidate()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (abs(value) < 0.6f && value != 0f) animateTo(0f)
                    onTouching?.invoke(false)
                }
            }
            return true
        }
    }
}
