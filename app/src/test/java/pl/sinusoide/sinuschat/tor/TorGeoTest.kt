package pl.sinusoide.sinuschat.tor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class TorGeoTest {

    private val db = File("src/main/assets/geoip4.bin")

    private fun cc(ip: String) = db.inputStream().use { TorGeo.lookup(it, ip) }

    @Test
    fun knownAddresses() {
        assertEquals("US", cc("8.8.8.8"))
        assertEquals("DE", cc("185.220.101.1"))
        assertEquals("FR", cc("51.75.0.1"))
        assertEquals("SE", cc("193.11.114.43"))
        assertEquals("PL", cc("146.59.80.192"))
    }

    @Test
    fun privateAndInvalid() {
        assertNull(cc("10.0.0.1"))
        assertNull(cc("255.255.255.255"))
        assertNull(cc("not an ip"))
        assertNull(cc("300.1.1.1"))
    }
}
