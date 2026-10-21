package pl.sinusoide.sinuschat.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeSnippetsTest {

    private fun only(text: String): CodeSnippets.Segment {
        val segs = CodeSnippets.segments(text)!!
        return segs.single { it.code }
    }

    @Test
    fun fencedWithLanguage() {
        val segs = CodeSnippets.segments("Zobacz to:\n```kotlin\nval x = 1\nprintln(x)\n```\nI co?")!!
        assertEquals(3, segs.size)
        assertFalse(segs[0].code)
        assertEquals("Zobacz to:", segs[0].text)
        assertTrue(segs[1].code)
        assertEquals("Kotlin", segs[1].lang?.name)
        assertEquals("val x = 1\nprintln(x)", segs[1].text)
        assertEquals("I co?", segs[2].text)
    }

    @Test
    fun fencedAliasesAndGuess() {
        assertEquals("Python", only("```py\nprint(1)\n```").lang?.name)
        assertEquals("Shell", only("```bash\nls -la\n```").lang?.name)
        assertEquals("C#", only("```c#\nvar a = 1;\n```").lang?.name)
        assertEquals("JSON", only("```\n{\n  \"a\": 1,\n  \"b\": [true, null]\n}\n```").lang?.name)
        assertNull(only("```\nfoo bar\n```").lang)
    }

    @Test
    fun unclosedFenceRunsToEnd() {
        val seg = only("```js\nconst a = 1;\nconsole.log(a);")
        assertEquals("JavaScript", seg.lang?.name)
        assertEquals("const a = 1;\nconsole.log(a);", seg.text)
    }

    @Test
    fun detectsPastedCodeWithoutFences() {
        val kotlin = """
            fun main() {
                val name = "Ala"
                println("Hello, ${'$'}name")
            }
        """.trimIndent()
        assertEquals("Kotlin", only(kotlin).lang?.name)

        val python = "def add(a, b):\n    return a + b\n\nprint(add(1, 2))"
        assertEquals("Python", only(python).lang?.name)

        val html = "<div class=\"box\">\n  <p>Hej</p>\n</div>"
        assertEquals("HTML", only(html).lang?.name)

        val shell = "sudo apt update\nsudo systemctl restart nginx"
        assertEquals("Shell", only(shell).lang?.name)

        val sql = "SELECT id, name\nFROM users\nWHERE id = 1;"
        assertEquals("SQL", only(sql).lang?.name)
    }

    @Test
    fun keepsIntroTextSeparate() {
        val segs = CodeSnippets.segments("masz tu poprawkę:\nint main() {\n    printf(\"hi\");\n    return 0;\n}")!!
        assertEquals(2, segs.size)
        assertEquals("masz tu poprawkę:", segs[0].text)
        assertTrue(segs[1].code)
        assertTrue(segs[1].text.startsWith("int main()"))
    }

    @Test
    fun ignoresNormalMessages() {
        assertNull(CodeSnippets.segments("Cześć, co tam?"))
        assertNull(CodeSnippets.segments("Hej!\nJutro o 18:00 w kinie.\nWeź bilety (dwa)."))
        assertNull(CodeSnippets.segments("Lista zakupów:\n- mleko\n- chleb\n- masło"))
        assertNull(CodeSnippets.segments("https://sinusoide.pl\nzobacz to"))
        assertNull(CodeSnippets.segments("if you want, call me.\nfor sure I will be there.\nlet me know!"))
        assertNull(CodeSnippets.segments("x = 5\ny = 6"))
        assertNull(CodeSnippets.segments("ok ;)\nhaha :)\nno to do jutra"))
        assertNull(CodeSnippets.segments("Używam ``` w Markdownie"))
    }
}
