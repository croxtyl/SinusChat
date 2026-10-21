package pl.sinusoide.sinuschat.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File

object MediaManager {

    private const val TAG = "MediaManager"
    private const val MEDIA_DIR = "media"

    data class MediaFile(
        val file: File,
        val messageId: String,
        val sizeBytes: Long,
        val lastModified: Long
    ) {
        val name: String get() = file.name
        val ext: String get() = file.extension.lowercase()
        val isImage: Boolean get() = ext in listOf("jpg", "jpeg", "png", "webp", "gif")
        val isVideo: Boolean get() = ext in listOf("mp4", "mov", "avi", "mkv")
    }

    private fun convDirPath(context: Context, conversationId: String): File =
        File(context.filesDir, "$MEDIA_DIR/" + conversationId.replace(Regex("[^A-Za-z0-9_-]"), "_"))

    fun voiceCacheFile(context: Context, messageId: String): File =
        File(context.cacheDir, "voice_" + messageId.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".ogg")

    private fun getConvDir(context: Context, conversationId: String): File {
        val dir = convDirPath(context, conversationId)
        if (!dir.exists()) {
            dir.mkdirs()
            File(dir, ".nomedia").createNewFile()
        }
        return dir
    }

    fun getMediaFile(context: Context, conversationId: String, messageId: String, fileName: String): File {
        val ext = fileName.substringAfterLast('.', "dat")
            .filter { it.isLetterOrDigit() }.take(10).ifEmpty { "dat" }
        val safeId = messageId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(getConvDir(context, conversationId), "$safeId.$ext")
    }

    fun isDownloaded(context: Context, conversationId: String, messageId: String, fileName: String): Boolean {
        return getMediaFile(context, conversationId, messageId, fileName).exists()
    }

    fun saveMedia(context: Context, conversationId: String, messageId: String, fileName: String, data: ByteArray): File? {
        return try {
            val file = getMediaFile(context, conversationId, messageId, fileName)
            file.writeBytes(data)
            Log.d(TAG, "Saved: ${file.absolutePath} (${data.size} bytes)")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save media", e)
            null
        }
    }

    fun loadBitmap(context: Context, conversationId: String, messageId: String, fileName: String): Bitmap? {
        val file = getMediaFile(context, conversationId, messageId, fileName)
        if (!file.exists()) return null

        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            val maxDim = 1080
            var sampleSize = 1
            while (opts.outWidth / sampleSize > maxDim * 2 || opts.outHeight / sampleSize > maxDim * 2) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null
            scaleToMaxDim(decoded, maxDim)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bitmap", e)
            null
        }
    }

    private fun scaleToMaxDim(bmp: Bitmap, maxDim: Int): Bitmap {
        val longer = maxOf(bmp.width, bmp.height)
        if (longer <= maxDim) return bmp
        val ratio = maxDim.toFloat() / longer
        val w = (bmp.width * ratio).toInt().coerceAtLeast(1)
        val h = (bmp.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bmp, w, h, true)
    }

    fun loadBitmapFromFile(file: File, maxDim: Int = 1080): Bitmap? {
        if (!file.exists()) return null
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            var sampleSize = 1
            while (opts.outWidth / sampleSize > maxDim || opts.outHeight / sampleSize > maxDim) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bitmap from file", e)
            null
        }
    }

    fun loadBitmapFromBytes(bytes: ByteArray, maxDim: Int = 2048): Bitmap? {
        if (bytes.isEmpty()) return null
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            var sampleSize = 1
            while (opts.outWidth / sampleSize > maxDim || opts.outHeight / sampleSize > maxDim) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bitmap from bytes", e)
            null
        }
    }

    fun isImage(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in listOf("jpg", "jpeg", "png", "webp", "gif")
    }

    fun isVideo(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in listOf("mp4", "mov", "avi", "mkv", "3gp", "webm", "m4v")
    }

    fun listMedia(context: Context, conversationId: String): List<MediaFile> {
        val dir = convDirPath(context, conversationId)
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && it.name != ".nomedia" }
            ?.map { f ->
                MediaFile(
                    file = f,
                    messageId = f.nameWithoutExtension,
                    sizeBytes = f.length(),
                    lastModified = f.lastModified()
                )
            }
            ?.sortedByDescending { it.lastModified }
            ?: emptyList()
    }

    fun getTotalSize(context: Context, conversationId: String): Long {
        return listMedia(context, conversationId).sumOf { it.sizeBytes }
    }

    fun deleteMessageMedia(context: Context, conversationId: String, messageId: String): Boolean {
        VideoThumbs.delete(context, conversationId, messageId)
        val dir = convDirPath(context, conversationId)
        if (!dir.exists()) return false
        var deleted = false
        dir.listFiles()?.forEach { f ->
            if (f.nameWithoutExtension == messageId.replace(Regex("[^A-Za-z0-9_-]"), "_")) {
                if (f.delete()) deleted = true
            }
        }
        return deleted
    }

    fun clearConversationMedia(context: Context, conversationId: String) {
        VideoThumbs.clearConversation(context, conversationId)
        val dir = convDirPath(context, conversationId)
        if (dir.exists()) dir.deleteRecursively()
    }

    fun purgeOrphans(context: Context, minAgeMs: Long = 10 * 60_000L): Long {
        val root = File(context.filesDir, MEDIA_DIR)
        if (!root.exists()) return 0L
        val ids = try {
            pl.sinusoide.sinuschat.data.SinusChatDatabase.getInstance(context).messageDao().getAllIds()
                .mapTo(HashSet()) { it.replace(Regex("[^A-Za-z0-9_-]"), "_") }
        } catch (e: Exception) {
            Log.w(TAG, "purgeOrphans: database unavailable, skipping", e)
            return 0L
        }
        val cutoff = System.currentTimeMillis() - minAgeMs
        VideoThumbs.purgeOrphans(context, ids, cutoff)
        var freed = 0L
        root.listFiles()?.forEach { dir ->
            if (!dir.isDirectory) return@forEach
            dir.listFiles()?.forEach { f ->
                if (f.isFile && f.name != ".nomedia" &&
                    f.nameWithoutExtension !in ids && f.lastModified() < cutoff) {
                    val len = f.length()
                    if (f.delete()) freed += len
                }
            }
            val left = dir.listFiles()?.filter { it.name != ".nomedia" }.orEmpty()
            if (left.isEmpty()) dir.deleteRecursively()
        }
        if (freed > 0) Log.d(TAG, "purgeOrphans: freed $freed bytes")
        return freed
    }

    fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
            bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
