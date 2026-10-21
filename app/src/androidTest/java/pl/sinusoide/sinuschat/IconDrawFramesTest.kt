package pl.sinusoide.sinuschat

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.util.Log
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import pl.sinusoide.sinuschat.ui.IconDrawView
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class IconDrawFramesTest {

    @Test
    fun translateFrames() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val outline = IconDrawView.vectorPath(ctx, R.drawable.ic_translate)
        val b = RectF().also { outline?.computeBounds(it, true) }
        Log.i("TRTEST", "translate outline null=${outline == null} bounds=$b")
        val lang = IconDrawView.vectorPath(ctx, R.drawable.ic_language)
        Log.i("TRTEST", "language outline null=${lang == null}")
        val d = IconDrawView.translate(ctx)
        Log.i("TRTEST", "drawing fill null=${d.fill == null} pops=${d.pops.size} parts=${d.parts.size}")

        val dir = File(ctx.getExternalFilesDir(null), "iconframes").apply { mkdirs() }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = IconDrawView(ctx)
            view.setColors(Color.WHITE, Color.WHITE)
            view.setDrawing(d)
            val size = 600
            view.measure(View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, size, size)
            val field = IconDrawView::class.java.getDeclaredField("startNs").apply { isAccessible = true }
            for (ms in listOf(500, 900, 1300, 1700, 2100, 2600, 3200)) {
                field.setLong(view, System.nanoTime() - ms * 1_000_000L)
                val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                c.drawColor(0xFF17212B.toInt())
                view.draw(c)
                FileOutputStream(File(dir, "f$ms.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        Log.i("TRTEST", "frames in ${dir.absolutePath}")
    }
}
