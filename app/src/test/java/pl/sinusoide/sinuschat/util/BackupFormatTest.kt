package pl.sinusoide.sinuschat.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.random.Random

class BackupFormatTest {

    private val words = File("src/main/res/raw/backup_words.txt").readLines().filter { it.isNotBlank() }

    private val vectors = listOf(
        "00000000000000000000000000000000" to
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
        "7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F" to
            "legal winner thank year wave sausage worth useful legal winner thank yellow",
        "80808080808080808080808080808080" to
            "letter advice cage absurd amount doctor acoustic avoid letter advice cage above",
        "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF" to
            "zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong",
        "9E885D952AD362CAEB4EFE34A8E91BD2" to
            "ozone drill grab fiber curtain grace pudding thank cruise elder eight picnic"
    )

    @Test
    fun wordListIsBip39() {
        assertEquals(2048, words.size)
        assertEquals(words.sorted(), words)
        assertEquals(2048, words.map { it.take(4) }.toSet().size)
    }

    @Test
    fun wordsMatchBip39Vectors() {
        for ((hex, phrase) in vectors) {
            val key = RecoveryKey.fromStorage(hex)!!
            assertEquals(phrase, key.words(words).joinToString(" "))
            val back = RecoveryKey.fromWords(words, phrase.split(" "))
            assertEquals(hex, back!!.toStorage())
        }
    }

    @Test
    fun checksumCatchesMistakes() {
        assertNull(RecoveryKey.fromWords(words, List(12) { "abandon" }))
        assertNull(RecoveryKey.fromWords(words, List(11) { "abandon" }))
        assertNull(RecoveryKey.fromWords(words, List(11) { "abandon" } + "notaword"))
    }

    @Test
    fun typingIsForgiving() {
        assertEquals("abandon", RecoveryKey.resolve(words, "aban"))
        assertEquals("act", RecoveryKey.resolve(words, " ACT "))
        assertEquals("action", RecoveryKey.resolve(words, "acti"))
        assertNull(RecoveryKey.resolve(words, "ab"))
        val pasted = "1. legal 2. winner 3. thank 4. year 5. wave 6. sausage " +
            "7. worth 8. useful 9. legal 10. winner 11. thank 12. yellow"
        assertEquals("7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F",
            RecoveryKey.fromWords(words, RecoveryKey.tokens(pasted))!!.toStorage())
    }

    @Test
    fun qrCodeRoundTrip() {
        val key = RecoveryKey.generate()
        val payload = key.qrPayload()
        assertTrue(payload.all { it in "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-:" })
        assertEquals(key.toStorage(), RecoveryKey.fromQr(words, payload)!!.toStorage())
        val broken = payload.dropLast(3) + (if (payload[payload.length - 3] == '0') '1' else '0') + payload.takeLast(2)
        assertNull(RecoveryKey.fromQr(words, broken))
        assertEquals(key.toStorage(), RecoveryKey.fromQr(words, key.words(words).joinToString(" "))!!.toStorage())
    }

    @Test
    fun backupRoundTrip() {
        val key = RecoveryKey.generate()
        val manifest = """{"format":"sinuschat-backup"}""".toByteArray()
        val messages = ("[" + (1..5000).joinToString(",") { """{"id":$it,"text":"wiadomość $it"}""" } + "]").toByteArray()
        val media = Random(7).nextBytes(2_600_000)
        val mediaFile = File.createTempFile("media", ".bin").apply { writeBytes(media); deleteOnExit() }

        val file = ByteArrayOutputStream()
        val header = BackupCrypto.Header.create(BackupCrypto.FLAG_MEDIA, key)
        file.write(header.bytes())
        BackupArchive.Writer(BackupCrypto.encryptingStream(file, key, header)).use {
            it.bytes(BackupArchive.MANIFEST, "manifest.json", manifest)
            it.bytes(BackupArchive.MESSAGES, "messages.json", messages)
            it.file("media/images/a.jpg", mediaFile)
        }
        val bytes = file.toByteArray()
        assertEquals("SINUSCHAT", String(bytes, 0, 9, Charsets.US_ASCII))

        val inp = ByteArrayInputStream(bytes)
        val read = BackupCrypto.Header.read(inp)
        assertTrue(read.withMedia)
        assertTrue(key.matches(read.keyId))
        BackupArchive.Reader(BackupCrypto.decryptingStream(inp, key, read)).use { r ->
            var e = r.next()!!
            assertEquals(BackupArchive.MANIFEST, e.type)
            assertArrayEquals(manifest, r.readBytes())
            e = r.next()!!
            assertEquals("messages.json", e.name)
            e = r.next()!!
            assertEquals("media/images/a.jpg", e.name)
            assertArrayEquals(media, r.readBytes())
            assertNull(r.next())
        }
    }

    @Test
    fun wrongKeyTamperingAndTruncationAreCaught() {
        val key = RecoveryKey.generate()
        val file = ByteArrayOutputStream()
        val header = BackupCrypto.Header.create(0, key)
        file.write(header.bytes())
        BackupArchive.Writer(BackupCrypto.encryptingStream(file, key, header)).use {
            it.bytes(BackupArchive.MANIFEST, "manifest.json", Random(1).nextBytes(3_000_000), compress = false)
        }
        val good = file.toByteArray()

        fun readAll(bytes: ByteArray, k: RecoveryKey) {
            val inp = ByteArrayInputStream(bytes)
            val h = BackupCrypto.Header.read(inp)
            BackupArchive.Reader(BackupCrypto.decryptingStream(inp, k, h)).use { r ->
                while (r.next() != null) r.readBytes()
            }
        }

        readAll(good, key)
        expectFailure<BackupCrypto.WrongKeyException> { readAll(good, RecoveryKey.generate()) }
        expectFailure<Exception> { readAll(good.copyOf().also { it[2_000_000] = (it[2_000_000] + 1).toByte() }, key) }
        expectFailure<Exception> { readAll(good.copyOf(good.size - 100), key) }
        val oneChunk = BackupCrypto.HEADER_LEN + 4 + (1 shl 20) + 16
        expectFailure<IOException> { readAll(good.copyOf(oneChunk), key) }
        expectFailure<BackupCrypto.LegacyFormatException> {
            BackupCrypto.Header.read(ByteArrayInputStream("SCBK".toByteArray() + ByteArray(40)))
        }
        expectFailure<IOException> { BackupCrypto.Header.read(ByteArrayInputStream("%PDF-1.4".toByteArray())) }
    }

    private inline fun <reified T : Throwable> expectFailure(block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            if (t is T) return
            fail("Oczekiwano ${T::class.simpleName}, a było ${t.javaClass.simpleName}: ${t.message}")
        }
        fail("Oczekiwano ${T::class.simpleName}, a nie było błędu")
    }

    @Test
    fun moveCodeRoundTrip() {
        val key = RecoveryKey.generate()
        val code = DeviceTransfer.Code(listOf("192.168.1.23", "10.42.0.1"), 41234, key)
        val text = code.encode()
        assertTrue(text.all { it in "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:" })
        val back = DeviceTransfer.parse(text)!!
        assertEquals(listOf("192.168.1.23", "10.42.0.1"), back.hosts)
        assertEquals(41234, back.port)
        assertEquals(key.toStorage(), back.key.toStorage())
        assertNull(DeviceTransfer.parse("SINUSCHAT-MOVE:1:999.1.1.1/80/" + key.toStorage()))
        assertNull(DeviceTransfer.parse("SINUSCHAT-KEY:1:" + key.toStorage()))
        assertNull(DeviceTransfer.parse(text.replace("/41234/", "/0/")))
    }

    @Test
    fun keyIdIsStableAndShort() {
        val key = RecoveryKey.fromStorage("00000000000000000000000000000000")
        assertNotNull(key)
        assertEquals(key!!.shortId, RecoveryKey.formatId(key.id))
        assertTrue(Regex("[0-9A-F]{4}-[0-9A-F]{4}").matches(key.shortId))
    }
}
