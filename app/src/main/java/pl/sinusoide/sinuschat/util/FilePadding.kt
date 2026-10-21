package pl.sinusoide.sinuschat.util

object FilePadding {

    private val MAGIC = byteArrayOf('S'.code.toByte(), 'C'.code.toByte(), 'P'.code.toByte(), '1'.code.toByte())
    private const val HEADER = 12
    private const val MIN_SIZE = 4096L

    fun paddedSize(len: Long): Long {
        if (len <= MIN_SIZE) return MIN_SIZE
        val e = 63 - java.lang.Long.numberOfLeadingZeros(len)
        val s = 64 - java.lang.Long.numberOfLeadingZeros(e.toLong())
        val lastBits = e - s
        val mask = (1L shl lastBits) - 1
        return (len + mask) and mask.inv()
    }

    const val HEADER_SIZE = HEADER

    fun header(length: Long): ByteArray {
        val out = ByteArray(HEADER)
        System.arraycopy(MAGIC, 0, out, 0, 4)
        for (i in 0 until 8) out[4 + i] = (length ushr (56 - 8 * i)).toByte()
        return out
    }

    fun headerLength(head: ByteArray): Long {
        if (head.size < HEADER) return -1L
        for (i in 0 until 4) if (head[i] != MAGIC[i]) return -1L
        var n = 0L
        for (i in 0 until 8) n = (n shl 8) or (head[4 + i].toLong() and 0xff)
        return if (n < 0) -1L else n
    }

    fun pad(data: ByteArray, maxTotal: Int = Int.MAX_VALUE): ByteArray {
        val total = paddedSize((data.size + HEADER).toLong())
            .coerceAtMost(maxTotal.toLong()).coerceAtLeast((data.size + HEADER).toLong()).toInt()
        val out = ByteArray(total)
        System.arraycopy(MAGIC, 0, out, 0, 4)
        val n = data.size.toLong()
        for (i in 0 until 8) out[4 + i] = (n ushr (56 - 8 * i)).toByte()
        System.arraycopy(data, 0, out, HEADER, data.size)
        return out
    }

    fun unpad(data: ByteArray): ByteArray {
        if (data.size < HEADER) return data
        for (i in 0 until 4) if (data[i] != MAGIC[i]) return data
        var n = 0L
        for (i in 0 until 8) n = (n shl 8) or (data[4 + i].toLong() and 0xff)
        if (n < 0 || n > data.size - HEADER) return data
        return data.copyOfRange(HEADER, HEADER + n.toInt())
    }
}
