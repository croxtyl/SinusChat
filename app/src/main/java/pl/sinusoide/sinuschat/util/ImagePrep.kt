package pl.sinusoide.sinuschat.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

object ImagePrep {

    private const val TAG = "ImagePrep"
    private const val MAX_SIDE = 4096

    private fun sampleFor(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        while (maxOf(width, height) / sample > maxSide) sample *= 2
        return sample
    }

    fun decodeSampled(bytes: ByteArray, maxSide: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null
        else BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight, maxSide)
        })
    } catch (e: Throwable) { null }

    fun decodeSampledFile(path: String, maxSide: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null
        else BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
            inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight, maxSide)
        })
    } catch (e: Throwable) { null }

    fun forSending(bytes: ByteArray): ByteArray? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outMimeType == "image/gif") null
        else {
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample })
            if (decoded == null) null else {
                val m = orientationMatrix(bytes)
                val upright = if (m.isIdentity) decoded
                    else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
                        .also { if (it !== decoded) decoded.recycle() }
                val out = ByteArrayOutputStream()
                if (bounds.outMimeType == "image/png") upright.compress(Bitmap.CompressFormat.PNG, 100, out)
                else upright.compress(Bitmap.CompressFormat.JPEG, 90, out)
                upright.recycle()
                out.toByteArray()
            }
        }
    } catch (e: Throwable) {
        Log.e(TAG, "Przygotowanie zdjęcia nieudane: ${e.message}")
        null
    }

    private fun orientationMatrix(bytes: ByteArray): Matrix {
        val m = Matrix()
        val o = try {
            ExifInterface(ByteArrayInputStream(bytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: Exception) { ExifInterface.ORIENTATION_NORMAL }
        when (o) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
        }
        return m
    }
}
