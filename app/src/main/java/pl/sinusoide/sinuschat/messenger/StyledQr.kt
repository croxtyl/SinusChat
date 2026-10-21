package pl.sinusoide.sinuschat.messenger

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object StyledQr {

    fun generate(
        data: String,
        sizePx: Int,
        logo: Bitmap? = null,
        fgColor: Int = Color.BLACK,
        bgColor: Int = Color.WHITE
    ): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.MARGIN to 1
        )
        val matrix = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val mW = matrix.width
        val mH = matrix.height

        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(bgColor)

        val cell = sizePx.toFloat() / mW
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fgColor; style = Paint.Style.FILL }

        val logoFrac = if (logo != null) 0.24f else 0f
        val logoStart = ((1f - logoFrac) / 2f * mW).toInt()
        val logoEnd = ((1f + logoFrac) / 2f * mW).toInt()

        for (y in 0 until mH) {
            for (x in 0 until mW) {
                if (!matrix.get(x, y)) continue
                if (logo != null && x in logoStart..logoEnd && y in logoStart..logoEnd) continue

                val px = x * cell
                val py = y * cell
                canvas.drawRoundRect(
                    px - 0.5f, py - 0.5f, px + cell + 0.5f, py + cell + 0.5f,
                    cell * 0.35f, cell * 0.35f, dot
                )
            }
        }

        drawFinder(canvas, 0f, 0f, cell, fgColor, bgColor)
        drawFinder(canvas, (mW - 7) * cell, 0f, cell, fgColor, bgColor)
        drawFinder(canvas, 0f, (mH - 7) * cell, cell, fgColor, bgColor)

        if (logo != null) {
            val logoArea = sizePx * logoFrac
            val areaTL = (sizePx - logoArea) / 2f
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fgColor }
            canvas.drawRect(areaTL, areaTL, areaTL + logoArea, areaTL + logoArea, bgPaint)
            val logoSize = logoArea * 0.78f
            val logoTL = (sizePx - logoSize) / 2f
            val dst = RectF(logoTL, logoTL, logoTL + logoSize, logoTL + logoSize)
            canvas.drawBitmap(logo, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }

        return roundCorners(bmp, sizePx * 0.08f)
    }

    private fun roundCorners(src: Bitmap, radius: Float): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = RectF(0f, 0f, src.width.toFloat(), src.height.toFloat())
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    private fun drawFinder(canvas: Canvas, x: Float, y: Float, cell: Float, fg: Int, bg: Int) {
        val outer = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fg; style = Paint.Style.FILL }
        val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg; style = Paint.Style.FILL }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fg; style = Paint.Style.FILL }
        val s = cell * 7
        val r = cell * 2f
        canvas.drawRoundRect(RectF(x, y, x + s, y + s), r, r, outer)
        canvas.drawRoundRect(RectF(x + cell, y + cell, x + s - cell, y + s - cell), r * 0.7f, r * 0.7f, inner)
        canvas.drawRoundRect(RectF(x + cell * 2, y + cell * 2, x + s - cell * 2, y + s - cell * 2),
            r * 0.5f, r * 0.5f, dot)
    }
}