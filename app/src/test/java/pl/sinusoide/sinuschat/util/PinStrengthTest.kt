package pl.sinusoide.sinuschat.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinStrengthTest {

    @Test
    fun weakPinsAreFlagged() {
        val weak = listOf(
            "0000", "1111", "6767", "2137", "2115", "1410", "0997", "1234", "4321", "1212", "1122", "1112", "7890", "0987",
            "2468", "1357", "1221", "1001", "1990", "2005", "2580", "0852", "1337", "5683",
            "12345", "13579", "11111", "12321", "12121",
            "123456", "654321", "111111", "123123", "121212", "112233", "159753", "147258",
            "696969", "123321", "000000", "314159",
            "12345678", "11223344", "12341234", "87654321"
        )
        for (pin in weak) assertTrue("powinien być słaby: $pin", PinStrength.isWeak(pin))
    }

    @Test
    fun randomLookingPinsPass() {
        val ok = listOf("7392", "3857", "9153", "4827", "60294", "482915", "830471", "59201846", "7391046285")
        for (pin in ok) assertFalse("nie powinien być słaby: $pin", PinStrength.isWeak(pin))
    }
}
