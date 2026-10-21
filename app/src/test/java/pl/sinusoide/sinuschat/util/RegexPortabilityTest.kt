package pl.sinusoide.sinuschat.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

class RegexPortabilityTest {

    private fun patternsOf(target: Any): List<String> {
        val out = ArrayList<String>()
        fun collect(v: Any?) {
            when (v) {
                is Regex -> out += v.pattern
                is Iterable<*> -> v.forEach { collect(it) }
                null -> {}
                else -> if (v.javaClass.name.startsWith("pl.sinusoide") && v !== target) {
                    for (f in v.javaClass.declaredFields) {
                        if (Modifier.isStatic(f.modifiers)) continue
                        f.isAccessible = true
                        (f.get(v) as? Regex)?.let { out += it.pattern }
                    }
                }
            }
        }
        for (f in target.javaClass.declaredFields) {
            f.isAccessible = true
            collect(f.get(if (Modifier.isStatic(f.modifiers)) null else target))
        }
        return out
    }

    private val quantifier = Regex("""\{\d+(,\d*)?\}""")

    private fun androidProblems(p: String): List<String> {
        val issues = ArrayList<String>()
        var i = 0
        var inClass = false
        var classBody = -1
        while (i < p.length) {
            val c = p[i]
            if (c == '\\') { i += 2; continue }
            if (inClass) {
                when {
                    c == ']' && i > classBody -> inClass = false
                    c == '[' || c == '{' || c == '}' || c == '$' -> issues += "'$c' in class at $i"
                    c == '&' -> issues += "'&' in class at $i"
                    c == '-' -> {
                        val prev = p.getOrNull(i - 1)
                        val next = p.getOrNull(i + 1)
                        val prevEscaped = i >= 2 && p[i - 2] == '\\'
                        val range = i - 1 >= classBody && prev != null && next != null &&
                            prev.isLetterOrDigit() && next.isLetterOrDigit() && !prevEscaped
                        if (!range) issues += "bare '-' in class at $i"
                    }
                }
                i++
                continue
            }
            when (c) {
                '[' -> {
                    inClass = true
                    i++
                    if (p.getOrNull(i) == '^') i++
                    classBody = i
                    continue
                }
                ']' -> issues += "']' outside class at $i"
                '}' -> issues += "'}' outside quantifier at $i"
                '{' -> {
                    val m = quantifier.find(p, i)
                    if (m == null || m.range.first != i) issues += "'{' outside quantifier at $i"
                    else { i = m.range.last + 1; continue }
                }
            }
            i++
        }
        if (inClass) issues += "unclosed class"
        return issues
    }

    private fun check(name: String, target: Any, minimum: Int) {
        val patterns = patternsOf(target)
        assertTrue("$name: found only ${patterns.size} patterns", patterns.size >= minimum)
        val bad = patterns.mapNotNull { p -> androidProblems(p).takeIf { it.isNotEmpty() }?.let { "$p -> $it" } }
        assertEquals("$name has patterns Android's regex engine rejects:\n" + bad.joinToString("\n"), 0, bad.size)
    }

    @Test
    fun codeSnippetPatternsAreAndroidSafe() = check("CodeSnippets", CodeSnippets, 40)

    @Test
    fun fileIconPatternsAreAndroidSafe() = check("FileIcons", FileIcons, 2)

    @Test
    fun checkerCatchesKnownCrash() {
        assertTrue(androidProblems("""\{\s*get;\s*set;\s*}""").isNotEmpty())
        assertTrue(androidProblems("""String\[]""").isNotEmpty())
        assertTrue(androidProblems("""[(){}]""").isNotEmpty())
        assertTrue(androidProblems("""[\w-]""").isNotEmpty())
        assertTrue(androidProblems("""[A-Za-z0-9_\-]{0,24}\{\s*\}""").isEmpty())
    }
}
