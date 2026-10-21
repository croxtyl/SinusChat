package pl.sinusoide.sinuschat.util

import android.content.Context
import java.io.File

object StorageUsage {

    data class LargeFile(
        val conversationId: String,
        val file: File,
        val bytes: Long
    )

    private fun mediaRoot(context: Context) = File(context.filesDir, "media")

    fun totalBytes(context: Context): Long {
        val root = mediaRoot(context)
        if (!root.exists()) return 0L
        return root.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    }

    fun filesLargerThan(context: Context, thresholdBytes: Long = 5L * 1024 * 1024): List<LargeFile> {
        val root = mediaRoot(context)
        if (!root.exists()) return emptyList()
        val out = mutableListOf<LargeFile>()
        root.listFiles { f -> f.isDirectory }?.forEach { dir ->
            dir.walkTopDown().filter { it.isFile && it.length() >= thresholdBytes }.forEach {
                out.add(LargeFile(dir.name, it, it.length()))
            }
        }
        return out.sortedByDescending { it.bytes }
    }

    enum class Kind { IMAGE, VIDEO, VOICE, DOCUMENT }

    private const val GLIDE_DIR = "image_manager_disk_cache"

    data class KindUsage(val kind: Kind, val count: Int, val bytes: Long)

    data class ConvDetail(val conversationId: String, val bytes: Long, val count: Int)

    data class Snapshot(
        val totalBytes: Long,
        val kinds: List<KindUsage>,
        val conversations: List<ConvDetail>,
        val cacheBytes: Long
    )

    private val imageExt = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp")
    private val videoExt = setOf("mp4", "mov", "avi", "mkv", "webm", "3gp")
    private val voiceExt = setOf("ogg", "opus", "m4a", "aac", "mp3", "wav", "amr")

    fun kindOf(file: File): Kind {
        val ext = file.extension.lowercase()
        return when (ext) {
            in imageExt -> Kind.IMAGE
            in videoExt -> Kind.VIDEO
            in voiceExt -> Kind.VOICE
            else -> Kind.DOCUMENT
        }
    }

    fun snapshot(context: Context): Snapshot {
        val root = mediaRoot(context)
        val kindBytes = LongArray(Kind.values().size)
        val kindCount = IntArray(Kind.values().size)
        val convs = mutableListOf<ConvDetail>()
        root.listFiles { f -> f.isDirectory }?.forEach { dir ->
            var bytes = 0L; var count = 0
            dir.listFiles()?.forEach { f ->
                if (!f.isFile || f.name == ".nomedia") return@forEach
                val len = f.length()
                val k = kindOf(f).ordinal
                kindBytes[k] += len; kindCount[k]++
                bytes += len; count++
            }
            if (count > 0) convs.add(ConvDetail(dir.name, bytes, count))
        }
        val kinds = Kind.values().map { KindUsage(it, kindCount[it.ordinal], kindBytes[it.ordinal]) }
        return Snapshot(
            totalBytes = kindBytes.sum(),
            kinds = kinds,
            conversations = convs.sortedByDescending { it.bytes },
            cacheBytes = cacheBytes(context)
        )
    }

    private fun clearableCacheFiles(context: Context, minAgeMs: Long): List<File> {
        val cutoff = System.currentTimeMillis() - minAgeMs
        return context.cacheDir.walkTopDown()
            .filter { it.isFile }
            .filter { !it.name.startsWith("voice_") && it.name != "migration_dec.tmp" }
            .filter { it.lastModified() < cutoff }
            .toList()
    }

    fun cacheBytes(context: Context): Long =
        try { clearableCacheFiles(context, 0L).sumOf { it.length() } } catch (_: Exception) { 0L }

    fun clearCache(context: Context): Long {
        var freed = 0L
        val glideDir = File(context.cacheDir, GLIDE_DIR)
        val glideBefore = glideDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        try { com.bumptech.glide.Glide.get(context).clearDiskCache() } catch (_: Exception) {}
        freed += (glideBefore - glideDir.walkTopDown().filter { it.isFile }.sumOf { it.length() })
            .coerceAtLeast(0L)
        try {
            for (f in clearableCacheFiles(context, 5 * 60_000L)) {
                if (f.path.contains("${File.separator}$GLIDE_DIR${File.separator}")) continue
                val len = f.length()
                if (f.delete()) freed += len
            }
            context.cacheDir.walkBottomUp()
                .filter { it.isDirectory && it != context.cacheDir && it.name != GLIDE_DIR &&
                    it.list()?.isEmpty() == true }
                .forEach { it.delete() }
        } catch (_: Exception) {}
        return freed
    }

    fun deviceFreeBytes(context: Context): Long =
        context.filesDir.freeSpace

    fun deviceTotalBytes(context: Context): Long =
        context.filesDir.totalSpace

    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.0f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format("%.1f GB", gb)
    }
}
