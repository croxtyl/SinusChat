package pl.sinusoide.sinuschat.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Base64
import android.util.Log
import android.util.LruCache
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

object VideoThumbs {

    class Info(val jpeg: ByteArray?, val durationMs: Long, val sizeBytes: Long, val width: Int, val height: Int)

    private const val TAG = "VideoThumbs"
    private const val DIR = "media_thumbs"
    private const val MAX_SIDE = 320
    private const val MAX_JPEG = 48 * 1024
    private const val MAX_B64 = 96 * 1024

    private val bitmaps = LruCache<String, Bitmap>(24)

    private fun safe(s: String) = s.replace(Regex("[^A-Za-z0-9_-]"), "_")

    private fun convDir(ctx: Context, conv: String) = File(ctx.filesDir, "$DIR/" + safe(conv))

    private fun file(ctx: Context, conv: String, id: String) = File(convDir(ctx, conv), safe(id) + ".json")

    fun load(ctx: Context, conv: String, id: String): Info? {
        val f = file(ctx, conv, id)
        if (!f.exists()) return null
        return try { fromJson(JSONObject(f.readText())) } catch (_: Exception) { null }
    }

    fun bitmap(ctx: Context, conv: String, id: String): Bitmap? {
        bitmaps.get("$conv|$id")?.let { return it }
        val jpeg = load(ctx, conv, id)?.jpeg ?: return null
        val bmp = ImagePrep.decodeSampled(jpeg, 1280) ?: return null
        bitmaps.put("$conv|$id", bmp)
        return bmp
    }

    private fun save(ctx: Context, conv: String, id: String, info: Info) {
        try {
            val dir = convDir(ctx, conv).apply { mkdirs() }
            File(dir, safe(id) + ".json").writeText(toJson(info).toString())
            bitmaps.remove("$conv|$id")
        } catch (e: Exception) {
            Log.w(TAG, "Nie zapisano miniatury: ${e.javaClass.simpleName}")
        }
    }

    fun ensure(ctx: Context, conv: String, id: String, fileName: String): Info? {
        if (!MediaManager.isVideo(fileName)) return null
        val existing = load(ctx, conv, id)
        val video = MediaManager.getMediaFile(ctx, conv, id, fileName)
        if (existing != null && (existing.jpeg != null || !video.exists())) return existing
        if (!video.exists()) return null
        val made = fromVideo(video) ?: return existing
        save(ctx, conv, id, made)
        return made
    }

    fun fromVideo(video: File): Info? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(video.absolutePath)
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
            val at = if (dur > 2000) 1_000_000L else 0L
            val frame = if (Build.VERSION.SDK_INT >= 27) {
                r.getScaledFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, MAX_SIDE, MAX_SIDE)
            } else {
                r.getFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { scale(it) }
            }
            Info(frame?.let { compress(it) }, dur, video.length(), w, h)
        } catch (e: Exception) {
            Log.w(TAG, "Nie odczytano wideo: ${e.javaClass.simpleName}")
            null
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
    }

    private fun scale(bmp: Bitmap): Bitmap {
        val longer = maxOf(bmp.width, bmp.height)
        if (longer <= MAX_SIDE) return bmp
        val k = MAX_SIDE.toFloat() / longer
        return Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt().coerceAtLeast(1),
            (bmp.height * k).toInt().coerceAtLeast(1), true).also { if (it !== bmp) bmp.recycle() }
    }

    private fun compress(bmp: Bitmap): ByteArray? {
        for (q in intArrayOf(70, 55, 40)) {
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, q, out)
            if (out.size() <= MAX_JPEG) return out.toByteArray()
        }
        return null
    }

    fun putInto(o: JSONObject, info: Info) {
        info.jpeg?.let { o.put("vthumb", Base64.encodeToString(it, Base64.NO_WRAP)) }
        if (info.durationMs > 0) o.put("vdur", info.durationMs)
        if (info.sizeBytes > 0) o.put("vsize", info.sizeBytes)
        if (info.width > 0 && info.height > 0) o.put("vw", info.width).put("vh", info.height)
    }

    fun saveFromPayload(ctx: Context, conv: String, id: String, fileName: String, src: JSONObject) {
        if (!MediaManager.isVideo(fileName) || id.isEmpty()) return
        if (!src.has("vthumb") && !src.has("vdur")) return
        val b64 = src.optString("vthumb")
        val jpeg = if (b64.isNotEmpty() && b64.length <= MAX_B64) {
            try {
                Base64.decode(b64, Base64.NO_WRAP).takeIf { bytes ->
                    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                    o.outWidth in 1..1024 && o.outHeight in 1..1024
                }
            } catch (_: Exception) { null }
        } else null
        val dur = src.optLong("vdur", 0L).coerceIn(0L, 24 * 3_600_000L)
        val size = src.optLong("vsize", 0L).coerceIn(0L, 4L * 1024 * 1024 * 1024)
        val w = src.optInt("vw", 0).coerceIn(0, 16384)
        val h = src.optInt("vh", 0).coerceIn(0, 16384)
        save(ctx, conv, id, Info(jpeg, dur, size, w, h))
    }

    private fun toJson(info: Info) = JSONObject().also { putInto(it, info) }

    private fun fromJson(o: JSONObject): Info {
        val jpeg = o.optString("vthumb").takeIf { it.isNotEmpty() }
            ?.let { try { Base64.decode(it, Base64.NO_WRAP) } catch (_: Exception) { null } }
        return Info(jpeg, o.optLong("vdur", 0L), o.optLong("vsize", 0L), o.optInt("vw", 0), o.optInt("vh", 0))
    }

    fun delete(ctx: Context, conv: String, id: String) {
        file(ctx, conv, id).delete()
        bitmaps.remove("$conv|$id")
    }

    fun clearConversation(ctx: Context, conv: String) {
        convDir(ctx, conv).deleteRecursively()
        bitmaps.evictAll()
    }

    fun purgeOrphans(ctx: Context, ids: Set<String>, cutoff: Long) {
        File(ctx.filesDir, DIR).listFiles()?.forEach { dir ->
            if (!dir.isDirectory) return@forEach
            dir.listFiles()?.forEach { f -> if (f.nameWithoutExtension !in ids && f.lastModified() < cutoff) f.delete() }
            if (dir.listFiles().isNullOrEmpty()) dir.delete()
        }
    }

    fun formatDuration(ms: Long): String {
        val total = (ms + 500) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
