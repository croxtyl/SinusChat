package pl.sinusoide.sinuschat.util

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

object AudioWaveform {

    private const val TAG = "AudioWaveform"
    const val BAR_COUNT = 46

    fun generate(path: String, barCount: Int = BAR_COUNT): FloatArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(path)
            if (extractor.trackCount == 0) return null

            var format: MediaFormat? = null
            var trackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    format = f; trackIndex = i; break
                }
            }
            if (format == null || trackIndex < 0) return null
            extractor.selectTrack(trackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val amplitudes = ArrayList<Float>()
            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEOS = false
            var sawOutputEOS = false

            while (!sawOutputEOS) {
                if (!sawInputEOS) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex) ?: continue
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEOS = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, sampleSize,
                                extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outIndex >= 0) {
                    val outBuf = codec.getOutputBuffer(outIndex)
                    if (outBuf != null && bufferInfo.size > 0) {
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        var sum = 0.0
                        var count = 0
                        val shorts = outBuf.order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        while (shorts.hasRemaining()) {
                            val v = shorts.get().toInt()
                            sum += (v * v).toDouble()
                            count++
                        }
                        if (count > 0) {
                            val rms = sqrt(sum / count) / 32768.0
                            amplitudes.add(rms.toFloat())
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawOutputEOS = true
                    }
                }
            }

            if (amplitudes.isEmpty()) return null
            return downsampleAndNormalize(amplitudes, barCount)

        } catch (e: Exception) {
            Log.e(TAG, "Błąd generowania waveformu: ${e.message}")
            return null
        } finally {
            try { codec?.stop(); codec?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    private fun downsampleAndNormalize(src: List<Float>, barCount: Int): FloatArray {
        val out = FloatArray(barCount)
        val bucketSize = src.size.toFloat() / barCount
        for (i in 0 until barCount) {
            val start = (i * bucketSize).toInt()
            val end = ((i + 1) * bucketSize).toInt().coerceAtMost(src.size)
            var max = 0f
            for (j in start until end) max = maxOf(max, abs(src[j]))
            out[i] = max
        }
        val peak = out.maxOrNull() ?: 1f
        if (peak > 0f) for (i in out.indices) out[i] = (out[i] / peak).coerceIn(0.05f, 1f)
        return out
    }
}
