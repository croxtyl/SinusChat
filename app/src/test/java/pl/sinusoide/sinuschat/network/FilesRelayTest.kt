package pl.sinusoide.sinuschat.network

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class FilesRelayTest {

    private fun relay(url: String) = ApiClient.viaRelay(Request.Builder().url(url).build()).url.toString()

    @Test
    fun uploadGoesThroughRelay() {
        assertEquals("https://api.sinusoide.pl/api/files/upload.php?action=download",
            relay("https://files.sinusoide.pl/api/upload.php?action=download"))
    }

    @Test
    fun avatarKeepsPathAndQuery() {
        assertEquals("https://api.sinusoide.pl/api/files/uploads/avatars/0123456789abcdef0123456789abcdef.bin?v=17",
            relay("https://files.sinusoide.pl/api/uploads/avatars/0123456789abcdef0123456789abcdef.bin?v=17"))
    }

    @Test
    fun otherHostsAreUntouched() {
        val req = Request.Builder().url("https://api.sinusoide.pl/api/upload-token").build()
        assertSame(req, ApiClient.viaRelay(req))
        assertEquals("https://files.sinusoide.pl/ai/model.bin", relay("https://files.sinusoide.pl/ai/model.bin"))
        assertEquals("https://example.com/api/x", relay("https://example.com/api/x"))
    }
}
