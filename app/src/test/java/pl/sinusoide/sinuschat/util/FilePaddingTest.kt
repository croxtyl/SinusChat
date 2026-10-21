package pl.sinusoide.sinuschat.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FilePaddingTest {

    @Test
    fun wracaTenSamPlik() {
        for (n in listOf(0, 1, 100, 4083, 4084, 4085, 5000, 123_457, 3_000_001)) {
            val data = Random(n).nextBytes(n)
            val padded = FilePadding.pad(data)
            assertEquals(FilePadding.paddedSize((n + 12).toLong()).toInt(), padded.size)
            assertArrayEquals("n=$n", data, FilePadding.unpad(padded))
        }
    }

    @Test
    fun narzutNajwyzej12Procent() {
        var len = 4097L
        while (len < 60_000_000L) {
            val p = FilePadding.paddedSize(len)
            assertTrue("len=$len", p >= len)
            assertTrue("len=$len p=$p", p <= len * 1.12 + 1)
            len = len * 13 / 10 + 7
        }
    }

    @Test
    fun podobneRozmiaryWygladajaTakSamo() {
        val sizes = (1_000_000L..1_010_000L).map { FilePadding.paddedSize(it) }.toSet()
        assertTrue(sizes.size <= 2)
        assertEquals(4096L, FilePadding.paddedSize(10))
    }

    @Test
    fun starePlikiBezNaglowkaBezZmian() {
        val legacy = "zwykły plik bez wyrównania".toByteArray()
        assertArrayEquals(legacy, FilePadding.unpad(legacy))
        val short = byteArrayOf(1, 2, 3)
        assertArrayEquals(short, FilePadding.unpad(short))
    }
}
