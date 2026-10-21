package pl.sinusoide.sinuschat.util

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

object VideoPrep {

    private const val TAG = "VideoPrep"
    private val REMUXABLE = setOf("mp4", "m4v", "mov", "3gp", "3gpp")

    fun canStrip(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in REMUXABLE

    fun stripInPlace(file: File, isCancelled: () -> Boolean = { false }): Boolean {
        val out = File(file.parentFile, file.name + ".clean")
        val ok = try { remux(file, out, isCancelled) } catch (e: Throwable) {
            Log.w(TAG, "remux: ${e.message}")
            false
        }
        if (!ok || out.length() == 0L) { out.delete(); return false }
        if (out.renameTo(file)) return true
        return try {
            out.inputStream().use { input -> file.outputStream().use { input.copyTo(it, 256 * 1024) } }
            true
        } catch (e: Exception) {
            Log.w(TAG, "replace: ${e.message}")
            false
        } finally {
            out.delete()
        }
    }

    private fun rotationOf(file: File): Int {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.absolutePath)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        } catch (_: Exception) { 0 } finally { try { r.release() } catch (_: Exception) {} }
    }

    private fun remux(src: File, dst: File, isCancelled: () -> Boolean): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        try {
            extractor.setDataSource(src.absolutePath)
            val m = MediaMuxer(dst.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = m
            val tracks = HashMap<Int, Int>()
            var hasVideo = false
            var bufferSize = 8 shl 20
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                val video = mime.startsWith("video/")
                if (!video && !mime.startsWith("audio/")) continue
                tracks[i] = m.addTrack(format)
                extractor.selectTrack(i)
                if (video) hasVideo = true
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE))
                    bufferSize = maxOf(bufferSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
            }
            if (!hasVideo) return false
            m.setOrientationHint(rotationOf(src).let { if (it in setOf(0, 90, 180, 270)) it else 0 })
            m.start()
            started = true
            val buffer = ByteBuffer.allocateDirect(bufferSize)
            val info = MediaCodec.BufferInfo()
            while (true) {
                if (isCancelled()) return false
                val track = extractor.sampleTrackIndex
                if (track < 0) break
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val target = tracks[track]
                if (target != null) {
                    info.offset = 0
                    info.size = size
                    info.presentationTimeUs = maxOf(0L, extractor.sampleTime)
                    info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                        MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                    m.writeSampleData(target, buffer, info)
                }
                extractor.advance()
            }
            m.stop()
            started = false
            return true
        } finally {
            if (started) try { muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            extractor.release()
        }
    }
}
