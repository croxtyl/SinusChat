package pl.sinusoide.sinuschat.util

object CodeSnippets {

    class Segment(val text: String, val code: Boolean, val lang: CodeHighlighter.Lang?)

    private const val MAX_SCAN = 200_000

    private val ALIASES: Map<String, String> = buildMap {
        fun add(ext: String, vararg names: String) = names.forEach { put(it, ext) }
        add("kt", "kotlin")
        add("js", "javascript", "node", "nodejs", "ecmascript")
        add("ts", "typescript")
        add("py", "python", "python3", "py3")
        add("cpp", "c++", "cxx")
        add("cs", "csharp", "c#", "dotnet")
        add("m", "objc", "objective-c", "objectivec")
        add("go", "golang")
        add("rs", "rust")
        add("rb", "ruby")
        add("pl", "perl")
        add("sh", "shell", "bash", "zsh", "console", "terminal", "shellscript")
        add("ps1", "powershell", "ps", "pwsh")
        add("bat", "batch", "cmd", "dos")
        add("sql", "mysql", "postgres", "postgresql", "sqlite", "plsql", "tsql")
        add("hs", "haskell")
        add("ex", "elixir")
        add("erl", "erlang")
        add("clj", "clojure")
        add("fs", "fsharp", "f#")
        add("vb", "vbnet", "vba", "visualbasic")
        add("asm", "assembly", "nasm", "x86")
        add("jl", "julia")
        add("sol", "solidity")
        add("proto", "protobuf")
        add("tf", "terraform")
        add("mk", "make", "makefile")
        add("html", "xhtml")
        add("xml", "xaml")
        add("scss", "sass", "less")
        add("yml", "yaml")
        add("conf", "nginx", "apache", "config")
        add("md", "markdown")
        add("diff", "patch")
        add("txt", "text", "plain", "plaintext")
    }

    fun alias(tag: String): CodeHighlighter.Lang? {
        val t = tag.trim().lowercase().removePrefix(".")
        if (t.isEmpty()) return null
        if (t == "dockerfile" || t == "docker") return CodeHighlighter.language("Dockerfile")
        return CodeHighlighter.language("x." + (ALIASES[t] ?: t))
    }

    fun segments(text: String): List<Segment>? {
        if (text.length > MAX_SCAN || text.isBlank()) return null
        val norm = text.replace("\r\n", "\n").replace('\r', '\n')
        if (norm.contains("```")) fenced(norm)?.let { return it }
        return detect(norm)
    }

    private val FENCE_TAG = Regex("""[A-Za-z0-9_+#.\-]{0,24}""")

    private fun fenced(text: String): List<Segment>? {
        val out = ArrayList<Segment>()
        var i = 0
        var found = false
        while (i < text.length) {
            val open = text.indexOf("```", i)
            if (open < 0) break
            val lineEnd = text.indexOf('\n', open + 3)
            if (lineEnd < 0) break
            val tag = text.substring(open + 3, lineEnd).trim()
            if (!FENCE_TAG.matches(tag)) {
                addText(out, text.substring(i, open + 3))
                i = open + 3
                continue
            }
            val close = text.indexOf("```", lineEnd + 1)
            val body = text.substring(lineEnd + 1, if (close < 0) text.length else close).trimStart('\n').trimEnd()
            addText(out, text.substring(i, open))
            if (body.isNotBlank()) {
                out += Segment(body, true, alias(tag) ?: guess(body))
                found = true
            }
            i = if (close < 0) text.length else close + 3
        }
        if (!found) return null
        if (i < text.length) addText(out, text.substring(i))
        return merge(out)
    }

    private fun merge(list: List<Segment>): List<Segment> {
        val out = ArrayList<Segment>()
        for (s in list) {
            val prev = out.lastOrNull()
            if (prev != null && !prev.code && !s.code) out[out.size - 1] = Segment(prev.text + "\n" + s.text, false, null)
            else out += s
        }
        return out
    }

    private fun addText(out: MutableList<Segment>, s: String) {
        val t = s.trim('\n', ' ', '\t')
        if (t.isNotEmpty()) out += Segment(t, false, null)
    }

    private fun detect(text: String): List<Segment>? {
        val lines = text.split('\n')
        if (lines.size < 2) return null
        val kinds = IntArray(lines.size) { lineKind(lines[it]) }
        var first = kinds.indexOfFirst { it == 1 }
        if (first < 0) return null
        var last = kinds.indexOfLast { it == 1 }
        while (first > 0 && kinds[first - 1] == 0 && lines[first - 1].isNotBlank()) first--
        while (last < lines.size - 1 && kinds[last + 1] == 0 && lines[last + 1].isNotBlank()) last++
        var code = 0
        var prose = 0
        for (k in first..last) {
            if (kinds[k] == 1) code++ else if (kinds[k] < 0) prose++
        }
        if (code < 2 || code * 10 < (code + prose) * 7) return null
        val body = lines.subList(first, last + 1).joinToString("\n").trimEnd()
        val lang = guess(body)
        if (code < 3 && (lang == null || prose > 0)) return null
        val out = ArrayList<Segment>()
        addText(out, lines.subList(0, first).joinToString("\n"))
        out += Segment(body, true, lang)
        addText(out, lines.subList(last + 1, lines.size).joinToString("\n"))
        return out
    }

    private val URL_LINE = Regex("""^(https?://|www\.)\S+$""", RegexOption.IGNORE_CASE)
    private val CLOSERS = setOf("}", "};", "})", "});", "},", ")", ");", "]", "];", "],", "end", "fi", "done", "esac", "endif")
    private val PREPROC = Regex("""^#(include|define|import|pragma|ifdef|ifndef|endif|if|else|elif|undef|region|endregion)\b""")
    private val TAG_LINE = Regex("""^(<!DOCTYPE|<\?xml|<\?php|<!--|</?[A-Za-z][\w:.\-]*(\s[^<>]*)?/?>)""", RegexOption.IGNORE_CASE)
    private val PY_LINE = Regex(
        """^((async\s+)?def\s+\w+\s*\(.*\)\s*(->.*)?|class\s+\w+(\(.*\))?|(if|elif|while)\s+.*[=<>!()\[\].].*|""" +
            """for\s+\w+(\s*,\s*\w+)*\s+in\s+\S.*|else|try|finally|except(\s+.*)?|with\s+.+\s+as\s+\w+)\s*:$""")
    private val OPERATORS = Regex("""==|!=|&&|\|\||::|:=|=>|\+=|-=|</[A-Za-z]|/>""")
    private val JSON_KEY = Regex("""^"[^"]+"\s*:""")
    private val ANNOTATION = Regex(
        """^@(Override|Composable|Test|Suppress\w*|Jvm\w+|Inject|Deprecated|Volatile|Synchronized|Preview|property|""" +
            """staticmethod|classmethod|dataclass|Autowired|Bean|Component|Service|Entity|\w+Mapping)\b.*$|^@\w+\(.*\)$""")
    private val SHELL_LINE = Regex(
        """^(\$\s+\S.*|sudo\s+\S.*|(\.[\\/])?adb(\.exe)?\s+(shell|devices|install|install-multiple|uninstall|push|pull|logcat|reboot|tcpip|""" +
            """connect|disconnect|kill-server|start-server|root|unroot|remount|sideload|backup|restore|forward|reverse|exec-out|""" +
            """wait-for-device|get-state|get-serialno|pair|-s|-d|-e)\b.*|(\.[\\/])?fastboot(\.exe)?\s+(flash|flashing|reboot|devices|oem|boot|""" +
            """erase|getvar|set_active|format|-w)\b.*|(apt|apt-get|npm|npx|yarn|pnpm|pip3?|docker|systemctl|journalctl|chmod|chown|mkdir|curl|""" +
            """wget|ssh|scp|cd|ls|cat|grep|nano|vim|tail|kill|ufw|certbot|python3?|node|adb|brew|cargo|gradle|\./[\w./\-]+)\s+.*[\-/.|>\$=~].*|""" +
            """git\s+(status|add|commit|push|pull|clone|checkout|switch|merge|log|diff|branch|init|fetch|reset|rebase|stash|remote|tag)\b.*)$""")
    private val CALL_LINE = Regex("""^[A-Za-z_\$][\w\$]*(\.[A-Za-z_\$][\w\$]*)*(<[^<>]*>)?\(.*\)[;,]?$""")
    private val ASSIGN_LINE = Regex(
        """^((const|let|var|val|final|int|long|float|double|bool|boolean|char|String|string|auto|my|local|export)\s+)*""" +
            """[A-Za-z_\$][\w\$]*(\.[\w\$]+|\[[^\]]*\])*\s*(:\s*[\w<>?,\[\] ]+)?\s*[\-+*/%|\&]?=\s*[^=\s].*$""")
    private val KEYWORD_START = Regex(
        """^(import|from|package|using|return|throw|export|public|private|protected|internal|static|const|let|var|val|""" +
            """fun|func|fn|function|def|class|struct|enum|interface|impl|trait|namespace|module|require|async|await|if|else|""" +
            """elif|for|foreach|while|switch|case|try|catch|except|finally|echo|print|printf|println|console|override|abstract|""" +
            """sealed|object|typealias|lateinit|suspend|void|int|unsigned|char|double|float|bool|boolean|String|auto|new|""" +
            """yield|lambda|when|SELECT|INSERT|UPDATE|DELETE|CREATE|ALTER|DROP|FROM|WHERE)\b""")
    private val SQL_LINE = Regex("""^(SELECT|INSERT INTO|UPDATE|DELETE FROM|CREATE (TABLE|INDEX|VIEW)|ALTER TABLE|DROP|FROM|WHERE|(LEFT |RIGHT |INNER |OUTER )?JOIN|ORDER BY|GROUP BY|HAVING|VALUES|SET|LIMIT)\b|^select\b.+\bfrom\b""")
    private val CODE_PUNCT = Regex("""[()\{\}\[\];=<>"\$]|\w\.\w|\w\.\*""")
    private val STRONG_PUNCT = Regex("""[()\{\};=]""")

    private fun lineKind(raw: String): Int {
        val t = raw.trim()
        if (t.isEmpty()) return 0
        if (URL_LINE.matches(t)) return -1
        if (t in CLOSERS) return 1
        if (t.startsWith("//") || t.startsWith("/*") || t.startsWith("*/") || t.startsWith("#!") || t.startsWith("<!--")) return 1
        if (PREPROC.containsMatchIn(t)) return 1
        if (t.startsWith("#") || t.startsWith("* ") || t == "*" || t.startsWith("--")) return 0
        if (TAG_LINE.containsMatchIn(t)) return 1
        if (t.endsWith("{")) return 1
        if (t.endsWith("}") && (t.contains('{') || t.startsWith("}"))) return 1
        if (t.endsWith(";") && (CODE_PUNCT.containsMatchIn(t.dropLast(1)) || t.contains(':'))) return 1
        if (PY_LINE.matches(t)) return 1
        if (OPERATORS.containsMatchIn(t)) return 1
        if (JSON_KEY.containsMatchIn(t)) return 1
        if (ANNOTATION.matches(t)) return 1
        if (SHELL_LINE.matches(t)) return 1
        if (CALL_LINE.matches(t)) return 1
        if (SQL_LINE.containsMatchIn(t)) return 1
        if (ASSIGN_LINE.matches(t)) return 1
        val sentence = t.endsWith('.') || t.endsWith('?') || t.endsWith('!')
        if (KEYWORD_START.containsMatchIn(t) && (if (sentence) STRONG_PUNCT else CODE_PUNCT).containsMatchIn(t)) return 1
        val indented = raw.startsWith("\t") || raw.startsWith("  ")
        if (indented && !sentence && (CODE_PUNCT.containsMatchIn(t) || KEYWORD_START.containsMatchIn(t))) return 1
        return -1
    }

    private class Hint(val ext: String, val weight: Int, val regex: Regex)

    private fun h(ext: String, weight: Int, pattern: String, ignoreCase: Boolean = false) = Hint(ext, weight,
        Regex(pattern, if (ignoreCase) setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE) else setOf(RegexOption.MULTILINE)))

    private val HINTS = listOf(
        h("kt", 3, """\bfun\s+(<[^>]*>\s*)?[\w.]+\s*\("""),
        h("kt", 2, """\bval\s+\w+\s*[:=]"""),
        h("kt", 3, """\b(data|sealed|enum|open|inner)\s+class\b|\bcompanion\s+object\b|\boverride\s+fun\b"""),
        h("kt", 2, """\?\.(let|also|apply)\b|\bwhen\s*(\(.*\))?\s*\{|!!\.|\blateinit\b"""),
        h("java", 3, """\bpublic\s+(static\s+)?(final\s+)?(class|void|int|String|boolean)\b"""),
        h("java", 4, """System\.out\.print|\bString\[\]\s+\w+"""),
        h("java", 2, """^import\s+[\w.]+(\.\*)?;\s*$"""),
        h("java", 1, """@Override"""),
        h("cs", 5, """^using\s+System|Console\.Write|\{\s*get;\s*set;\s*\}"""),
        h("cs", 2, """^namespace\s+[\w.]+\s*[\{;]?\s*$"""),
        h("js", 3, """\bfunction\s*\w*\s*\("""),
        h("js", 2, """\b(const|let)\s+\w+\s*="""),
        h("js", 4, """console\.log\(|document\.\w+|module\.exports|require\(['"]|addEventListener\("""),
        h("js", 2, """===|!==|=>\s*\{"""),
        h("ts", 4, """:\s*(string|number|boolean|any|void|unknown)\b\s*[;,)=\]\{]|^(export\s+)?(interface|type)\s+\w+\s*[=\{]"""),
        h("py", 4, """^\s*(async\s+)?def\s+\w+\s*\(.*\)\s*(->.*)?:\s*$|^\s*elif\b|__name__|__init__|\bself\.\w+"""),
        h("py", 2, """^\s*(from\s+[\w.]+\s+import\s+\w+|import\s+\w+(\s+as\s+\w+)?)\s*$"""),
        h("py", 1, """\bprint\(|\bNone\b|\bTrue\b|\bFalse\b|^\s*#\s"""),
        h("c", 5, """#include\s*<\w+\.h>"""),
        h("c", 2, """\bprintf\(|\bint\s+main\s*\(|\bmalloc\("""),
        h("cpp", 5, """#include\s*<\w+>|\bstd::|\bcout\s*<<|\bnullptr\b"""),
        h("cpp", 2, """\btemplate\s*<"""),
        h("go", 4, """^package\s+\w+\s*$|\bfmt\.\w+\(|\berr\s*!=\s*nil\b|\bfunc\s+(\(\w+\s+\*?\w+\)\s*)?\w+\(.*\)\s*[\w(\[\{*]"""),
        h("go", 2, """:=|\bgo\s+func\b"""),
        h("rs", 5, """\bfn\s+\w+\s*(<[^>]*>)?\(|\blet\s+mut\b|\bprintln!\(|^use\s+(std|crate)::|&str\b|\bimpl\b.*\{"""),
        h("swift", 6, """^import\s+(SwiftUI|UIKit|Foundation)\b|\bguard\s+let\b|\bvar\s+body\s*:\s*some\s+View"""),
        h("swift", 2, """\bfunc\s+\w+\s*\(.*\)\s*(->\s*\w+\s*)?\{|\bif\s+let\b"""),
        h("dart", 6, """Widget\s+build\(|^import\s+'package:|setState\(\s*\(\)"""),
        h("php", 8, """<\?php"""),
        h("php", 3, """\${'$'}this->|function\s+\w+\s*\(\s*\$|\$\w+\s*=\s*\$|\becho\s+\$"""),
        h("rb", 4, """\bputs\b|\.each\s+do\b|\battr_accessor\b|^\s*require\s+['"]|^\s*def\s+\w+[^:(]*$"""),
        h("sh", 8, """^#!.*\b(ba|z)?sh\b"""),
        h("sh", 2, """^\s*(\$\s+)?(sudo|adb|fastboot|apt|apt-get|systemctl|journalctl|chmod|chown|mkdir|cd|ls|grep|curl|wget|npm|npx|git|docker|pip3?|ufw|certbot|nano|tail|cat|echo|export|source|kill|ssh|scp)\s"""),
        h("sh", 3, """^\s*(fi|done|esac)\s*$|;\s*then\s*$|\$\{?\w+\}?\s*\]"""),
        h("ps1", 5, """\b(Get|Set|New|Remove|Write|Start|Stop|Invoke|Test|Import)-[A-Z]\w+|\s-(eq|ne|gt|lt|like|match)\s"""),
        h("bat", 8, """^@echo\s+off""", true),
        h("bat", 3, """%\w+%|^\s*goto\s+\w+""", true),
        h("sql", 5, """\bselect\b[\s\S]*?\bfrom\b|\binsert\s+into\b|\bcreate\s+(table|index|view|database)\b|\bupdate\s+\w+\s+set\b|\bdelete\s+from\b|\balter\s+table\b""", true),
        h("css", 3, """^\s*[.#@:]?[\w\-]+([\s,>+~]+[.#:]?[\w\-]+)*\s*\{\s*$"""),
        h("css", 3, """^\s*[a-z\-]+\s*:\s*[^;\{\}()]+;\s*$"""),
        h("css", 3, """@media\b|!important|\b\d+(px|rem|em|vh|vw)\b"""),
        h("html", 5, """<!doctype html|<html\b|<(div|span|body|head|p|a|ul|ol|li|script|style|meta|link|button|input|form|table|tr|td|h[1-6]|img|section|header|footer|nav)\b[^>]*>""", true),
        h("xml", 6, """<\?xml\b|\bxmlns(:\w+)?="""),
        h("xml", 2, """</?\w+:\w+"""),
        h("diff", 8, """^@@ .* @@|^diff --git"""),
        h("diff", 3, """^(\+\+\+|---) \S"""),
        h("lua", 4, """\blocal\s+\w+\s*=|\bfunction\s+\w+[.:]\w+\(|~="""),
        h("Dockerfile", 3, """^(FROM|RUN|CMD|COPY|ADD|ENTRYPOINT|WORKDIR|EXPOSE)\s""")
    )

    private val JSON_LIKE = Regex("""^\s*[\[\{][\s\S]*"\s*:[\s\S]*[\]\}]\s*$""")

    fun guess(code: String): CodeHighlighter.Lang? {
        if (JSON_LIKE.matches(code) && !code.contains(';') && !code.contains('=') && !code.contains("function")) {
            return CodeHighlighter.language("x.json")
        }
        val scores = LinkedHashMap<String, Int>()
        for (hint in HINTS) {
            val n = hint.regex.findAll(code).take(3).count()
            if (n > 0) scores[hint.ext] = (scores[hint.ext] ?: 0) + n * hint.weight
        }
        val best = scores.maxByOrNull { it.value } ?: return null
        if (best.value < 3) return null
        return if (best.key == "Dockerfile") CodeHighlighter.language("Dockerfile") else CodeHighlighter.language("x." + best.key)
    }
}
