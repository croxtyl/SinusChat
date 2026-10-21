package pl.sinusoide.sinuschat

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pl.sinusoide.sinuschat.util.Translations
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TranslationEngineTest {

    private val ctx: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun detect(text: String): String? {
        val latch = CountDownLatch(1)
        var out: String? = null
        Translations.detect(text) { out = it; latch.countDown() }
        latch.await(30, TimeUnit.SECONDS)
        Log.i("TRTEST", "detect [$text] -> $out")
        return out
    }

    private fun translate(text: String, from: String, to: String): String? {
        val latch = CountDownLatch(1)
        var out: String? = null
        val start = System.currentTimeMillis()
        Translations.translate(ctx, text, from, to) { out = it; latch.countDown() }
        latch.await(120, TimeUnit.SECONDS)
        Log.i("TRTEST", "$from>$to [$text] -> [$out] in ${System.currentTimeMillis() - start} ms")
        return out
    }

    @Test
    fun shortPhrases() {
        Translations.attach(ctx)
        for (s in listOf("I'm good", "ok", "Thanks a lot", "See you", "Dobrze", "Co robisz?", "Jasne, dzięki",
                "Wie geht's?", "Danke", "Ahoj, jak se máš?", "Дякую", "haha", "xD", "Spoko")) {
            val latch = CountDownLatch(1)
            Translations.detectRaw(s) { d ->
                Log.i("TRTEST", "raw [$s] -> ${d?.lang} ${d?.confidence} long=${d?.long}")
                latch.countDown()
            }
            latch.await(30, TimeUnit.SECONDS)
        }
    }

    private fun detectRaw(text: String): Translations.Detected? {
        val latch = CountDownLatch(1)
        var out: Translations.Detected? = null
        Translations.detectRaw(text) { out = it; latch.countDown() }
        latch.await(30, TimeUnit.SECONDS)
        Log.i("TRTEST", "raw [$text] -> ${out?.lang} ${out?.confidence} long=${out?.long}")
        return out
    }

    @Test
    fun ukrainianShortPhrases() {
        Translations.attach(ctx)
        for (s in listOf("Добрий день", "Доброго ранку", "Як справи?", "Привіт", "Дякую, все добре", "Добраніч")) {
            val d = detectRaw(s)
            assertEquals(s, "uk", d?.lang)
            assertTrue(s, (d?.confidence ?: 0f) >= 0.5f)
        }
        assertEquals(null, detectRaw("Добрый день, как дела?")?.lang)
    }

    @Test
    fun detectsAndTranslates() {
        Translations.attach(ctx)
        val prefs = ctx.getSharedPreferences("sinuschat_translate", Context.MODE_PRIVATE)
        val before = prefs.getStringSet("langs", null)?.toSet()
        prefs.edit().putStringSet("langs", setOf("en", "de")).commit()
        Translations.refresh(ctx)
        try {
            Log.i("TRTEST", "app=${Translations.appLang(ctx)} supported=${Translations.supported()}")
            assertEquals("pl", detect("Dzień dobry, jak się masz dzisiaj?"))
            assertEquals("de", detect("Guten Morgen, wie geht es dir heute?"))
            assertEquals("uk", detect("Привіт, як у тебе справи сьогодні?"))
            assertEquals("en", detect("Good morning, how are you doing today?"))
            assertNotNull(translate("Good morning, how are you doing today?", "en", "pl"))
            assertNotNull(translate("Jutro idziemy do kina, przyjdziesz z nami?", "pl", "en"))
            assertNotNull(translate("Guten Morgen, wie geht es dir heute?", "de", "pl"))
            assertNotNull(translate("Spotkajmy się w sobotę o osiemnastej przed dworcem.", "pl", "de"))
        } finally {
            prefs.edit().apply { if (before == null) remove("langs") else putStringSet("langs", before) }.commit()
            Translations.refresh(ctx)
        }
    }
}
