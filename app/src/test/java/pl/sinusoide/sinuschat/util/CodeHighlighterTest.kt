package pl.sinusoide.sinuschat.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeHighlighterTest {

    private fun kinds(name: String, text: String): Map<String, CodeHighlighter.Kind> {
        val lang = CodeHighlighter.language(name)!!
        return CodeHighlighter.tokenize(text, lang.syntax).associate { text.substring(it.start, it.end) to it.kind }
    }

    @Test
    fun detectsLanguages() {
        assertEquals("Kotlin", CodeHighlighter.language("Main.kt")?.name)
        assertEquals("Kotlin", CodeHighlighter.language("build.gradle.kts")?.name)
        assertEquals("Python", CodeHighlighter.language("script.PY")?.name)
        assertEquals("C++", CodeHighlighter.language("a.hpp")?.name)
        assertEquals("Dockerfile", CodeHighlighter.language("Dockerfile")?.name)
        assertEquals("Makefile", CodeHighlighter.language("Makefile")?.name)
        assertEquals("JSON", CodeHighlighter.language("package.json")?.name)
        assertEquals("", CodeHighlighter.language("notes.txt")?.name)
        assertNull(CodeHighlighter.language("photo.jpg"))
        assertNull(CodeHighlighter.language("archive.zip"))
        assertNull(CodeHighlighter.language("README"))
    }

    @Test
    fun kotlinTokens() {
        val k = kinds("a.kt", "@Suppress(\"x\")\nfun main() {\n    val n = 0x1F // note\n    println(\"hi \\\"you\\\"\")\n    val c = 'a'\n}")
        assertEquals(CodeHighlighter.Kind.ANNOTATION, k["@Suppress"])
        assertEquals(CodeHighlighter.Kind.KEYWORD, k["fun"])
        assertEquals(CodeHighlighter.Kind.FUNCTION, k["main"])
        assertEquals(CodeHighlighter.Kind.NUMBER, k["0x1F"])
        assertEquals(CodeHighlighter.Kind.COMMENT, k["// note"])
        assertEquals(CodeHighlighter.Kind.STRING, k["\"hi \\\"you\\\"\""])
        assertEquals(CodeHighlighter.Kind.STRING, k["'a'"])
    }

    @Test
    fun rustLifetimeIsNotAString() {
        val text = "fn f<'a>(x: &'a str) -> &'a str { x }"
        val k = kinds("a.rs", text)
        assertTrue(k.values.none { it == CodeHighlighter.Kind.STRING })
        assertEquals(CodeHighlighter.Kind.KEYWORD, k["fn"])
    }

    @Test
    fun pythonHashAndTripleQuotes() {
        val k = kinds("a.py", "def f(x):\n    \"\"\"doc\nmore\"\"\"\n    return x  # done\nurl = \"a#b\"")
        assertEquals(CodeHighlighter.Kind.KEYWORD, k["def"])
        assertEquals(CodeHighlighter.Kind.STRING, k["\"\"\"doc\nmore\"\"\""])
        assertEquals(CodeHighlighter.Kind.COMMENT, k["# done"])
        assertEquals(CodeHighlighter.Kind.STRING, k["\"a#b\""])
    }

    @Test
    fun jsonKeysAndValues() {
        val k = kinds("a.json", "{\"name\": \"Sinus\", \"n\": 12, \"ok\": true, \"x\": null}")
        assertEquals(CodeHighlighter.Kind.KEY, k["\"name\""])
        assertEquals(CodeHighlighter.Kind.STRING, k["\"Sinus\""])
        assertEquals(CodeHighlighter.Kind.NUMBER, k["12"])
        assertEquals(CodeHighlighter.Kind.KEYWORD, k["true"])
        assertEquals(CodeHighlighter.Kind.KEYWORD, k["null"])
    }

    @Test
    fun htmlWithScript() {
        val text = "<!-- c --><div class=\"a\" @click=\"go\"><script>const x = 1;</script></div>"
        val k = kinds("a.html", text)
        assertEquals(CodeHighlighter.Kind.COMMENT, k["<!-- c -->"])
        assertEquals(CodeHighlighter.Kind.TAG, k["<div"])
        assertEquals(CodeHighlighter.Kind.ATTR, k["class"])
        assertEquals(CodeHighlighter.Kind.ATTR, k["@click"])
        assertEquals(CodeHighlighter.Kind.STRING, k["\"a\""])
        assertEquals(CodeHighlighter.Kind.KEYWORD, k["const"])
        assertEquals(CodeHighlighter.Kind.NUMBER, k["1"])
    }

    @Test
    fun cssPropertiesAndColors() {
        val k = kinds("a.css", "a:hover { color: #fff; margin: 10px; }\n/* c */")
        assertEquals(CodeHighlighter.Kind.KEY, k["color"])
        assertEquals(CodeHighlighter.Kind.NUMBER, k["#fff"])
        assertEquals(CodeHighlighter.Kind.NUMBER, k["10px"])
        assertEquals(CodeHighlighter.Kind.COMMENT, k["/* c */"])
        assertNull(k["a"])
    }

    @Test
    fun sqlIgnoresCase() {
        val k = kinds("q.sql", "SELECT id FROM users -- all\nwhere name = 'x';")
        assertEquals(CodeHighlighter.Kind.KEYWORD, k["SELECT"])
        assertEquals(CodeHighlighter.Kind.KEYWORD, k["where"])
        assertEquals(CodeHighlighter.Kind.COMMENT, k["-- all"])
        assertEquals(CodeHighlighter.Kind.STRING, k["'x'"])
    }

    @Test
    fun shellVariablesAndIniSections() {
        val sh = kinds("a.sh", "echo \"\$HOME\" \${PATH} # c")
        assertEquals(CodeHighlighter.Kind.KEYWORD, sh["echo"])
        assertEquals(CodeHighlighter.Kind.ATTR, sh["\${PATH}"])
        assertEquals(CodeHighlighter.Kind.COMMENT, sh["# c"])
        val ini = kinds("a.ini", "[main]\nport = 8080\n; c\nname = \"x\"")
        assertEquals(CodeHighlighter.Kind.KEYWORD, ini["[main]"])
        assertEquals(CodeHighlighter.Kind.KEY, ini["port"])
        assertEquals(CodeHighlighter.Kind.NUMBER, ini["8080"])
        assertEquals(CodeHighlighter.Kind.COMMENT, ini["; c"])
    }

    @Test
    fun markdownAndDiff() {
        val md = kinds("a.md", "# Title\nUse `code` and [link](https://x)\n```\nfenced\n```")
        assertEquals(CodeHighlighter.Kind.KEYWORD, md["# Title"])
        assertEquals(CodeHighlighter.Kind.STRING, md["`code`"])
        assertEquals(CodeHighlighter.Kind.FUNCTION, md["[link]"])
        assertEquals(CodeHighlighter.Kind.STRING, md["```\nfenced\n```"])
        val d = kinds("a.diff", "@@ -1 +1 @@\n-old\n+new")
        assertEquals(CodeHighlighter.Kind.REMOVED, d["-old"])
        assertEquals(CodeHighlighter.Kind.ADDED, d["+new"])
    }

    @Test
    fun brokenInputNeverHangsOrOverflows() {
        val samples = listOf("\"unterminated", "/* open", "'", "\${", "<div attr=\"x", "#", "0x", "@", "```", "[a](b")
        for (name in listOf("a.kt", "a.py", "a.html", "a.css", "a.json", "a.sh", "a.md", "a.ini", "a.yaml", "a.diff", "a.rs", "a.sql")) {
            for (text in samples) {
                val lang = CodeHighlighter.language(name)!!
                for (t in CodeHighlighter.tokenize(text, lang.syntax)) {
                    assertTrue("$name $text", t.start in 0..text.length && t.end in t.start..text.length)
                }
            }
        }
    }
}
