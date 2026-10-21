package pl.sinusoide.sinuschat.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkTrustTest {

    @Test fun sinusoideIsTrusted() {
        assertTrue(LinkPreviewHelper.isTrusted("https://sinusoide.pl"))
        assertTrue(LinkPreviewHelper.isTrusted("https://sinusoide.pl/pomoc.html"))
        assertTrue(LinkPreviewHelper.isTrusted("https://www.sinusoide.pl/"))
        assertTrue(LinkPreviewHelper.isTrusted("https://SINUSOIDE.PL/"))
        assertTrue(LinkPreviewHelper.isTrusted("https://sinusoide.pl:443/"))
    }

    @Test fun lookalikesAreNotTrusted() {
        assertFalse(LinkPreviewHelper.isTrusted("http://sinusoide.pl"))
        assertFalse(LinkPreviewHelper.isTrusted("https://sinusoide.pl.evil.com"))
        assertFalse(LinkPreviewHelper.isTrusted("https://evilsinusoide.pl"))
        assertFalse(LinkPreviewHelper.isTrusted("https://sinusoide.pl@evil.com/"))
        assertFalse(LinkPreviewHelper.isTrusted("https://evil.com/sinusoide.pl"))
        assertFalse(LinkPreviewHelper.isTrusted("https://evil.com/?u=https://sinusoide.pl"))
        assertFalse(LinkPreviewHelper.isTrusted("https://sinusoide.pl:8443/"))
        assertFalse(LinkPreviewHelper.isTrusted("ftp://sinusoide.pl"))
        assertFalse(LinkPreviewHelper.isTrusted("not a url"))
    }
}
