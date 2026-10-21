package pl.sinusoide.sinuschat.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File

object DownloadsSaver {

    private const val TAG = "DownloadsSaver"
    const val FOLDER = "SinusChat"

    fun save(ctx: Context, src: File, name: String, mime: String): String? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveViaMediaStore(ctx, src, name, mime)
        else saveLegacy(ctx, src, name, mime)
    } catch (e: Throwable) {
        Log.e(TAG, "Zapis do folderu SinusChat nie wyszedł", e)
        null
    }

    fun locationFor(ctx: Context, mime: String): String {
        val res = when {
            mime.startsWith("image/") -> pl.sinusoide.sinuschat.R.string.folder_pictures
            mime.startsWith("video/") -> pl.sinusoide.sinuschat.R.string.folder_movies
            else -> pl.sinusoide.sinuschat.R.string.folder_downloads
        }
        return ctx.getString(res) + "/" + FOLDER
    }

    private fun publicDir(mime: String): String = when {
        mime.startsWith("image/") -> Environment.DIRECTORY_PICTURES
        mime.startsWith("video/") -> Environment.DIRECTORY_MOVIES
        else -> Environment.DIRECTORY_DOWNLOADS
    }

    private fun saveViaMediaStore(ctx: Context, src: File, name: String, mime: String): String? {
        val collection: Uri = when {
            mime.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            mime.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, publicDir(mime) + "/" + FOLDER)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = ctx.contentResolver
        val uri = resolver.insert(collection, values) ?: return null
        val ok = resolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) }; true } ?: false
        if (!ok) { resolver.delete(uri, null, null); return null }
        values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return name
    }

    private fun saveLegacy(ctx: Context, src: File, name: String, mime: String): String? {
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(publicDir(mime)), FOLDER)
        if (!dir.exists() && !dir.mkdirs()) return null
        val base = name.substringBeforeLast('.'); val ext = name.substringAfterLast('.', "")
        var out = File(dir, name); var n = 1
        while (out.exists()) out = File(dir, if (ext.isEmpty()) "$base ($n)" else "$base ($n).$ext").also { n++ }
        src.copyTo(out)
        MediaScannerConnection.scanFile(ctx, arrayOf(out.absolutePath), arrayOf(mime), null)
        return out.name
    }
}
