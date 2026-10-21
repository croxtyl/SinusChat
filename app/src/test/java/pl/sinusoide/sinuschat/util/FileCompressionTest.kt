package pl.sinusoide.sinuschat.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.random.Random

class FileCompressionTest {

    private fun temp(name: String) = File.createTempFile("sc_$name", ".bin").apply { deleteOnExit() }

    @Test
    fun pakujeIRozpakowujeTenSamPlik() {
        val text = buildString { repeat(20_000) { append("linia logu numer ").append(it).append('\n') } }.toByteArray()
        val packed = temp("packed")
        assertTrue(FileCompression.pack(ByteArrayInputStream(text), packed, limit = text.size.toLong()))
        assertTrue(packed.length() < text.size / 4)
        assertTrue(FileCompression.isPacked(packed))
        val back = temp("back")
        assertTrue(FileCompression.unpack(packed, back))
        assertArrayEquals(text, back.readBytes())
    }

    @Test
    fun przerywaGdyPoKompresjiZaDuzy() {
        val noise = Random(5).nextBytes(400_000)
        val packed = temp("noise")
        assertFalse(FileCompression.pack(ByteArrayInputStream(noise), packed, limit = 200_000))
        assertFalse(packed.exists())
    }

    @Test
    fun zwyklyPlikNieJestSpakowany() {
        val plain = temp("plain").apply { writeBytes("zwykły dokument".toByteArray()) }
        assertFalse(FileCompression.isPacked(plain))
        val tiny = temp("tiny").apply { writeBytes(byteArrayOf(1, 2)) }
        assertFalse(FileCompression.isPacked(tiny))
    }

    @Test
    fun zwyklyZipZostajeZipem() {
        val zip = temp("zip").apply {
            java.util.zip.ZipOutputStream(outputStream()).use { z ->
                z.putNextEntry(java.util.zip.ZipEntry("a.txt"))
                z.write("treść".toByteArray())
                z.closeEntry()
            }
        }
        assertFalse(FileCompression.isPacked(zip))
    }
}
