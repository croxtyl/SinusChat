package pl.sinusoide.sinuschat.util

object CodeHighlighter {

    enum class Kind { KEYWORD, STRING, NUMBER, COMMENT, FUNCTION, TYPE, ANNOTATION, TAG, ATTR, KEY, ADDED, REMOVED }

    class Token(val start: Int, val end: Int, val kind: Kind)

    enum class Family { CODE, MARKUP, CSS, JSON, YAML, INI, MARKDOWN, DIFF, PLAIN }

    class Syntax(
        val family: Family = Family.CODE,
        val keywords: Set<String> = emptySet(),
        val lineComments: List<String> = emptyList(),
        val blockComments: List<Pair<String, String>> = emptyList(),
        val quotes: String = "\"'",
        val tripleQuotes: Boolean = false,
        val backtick: Boolean = false,
        val charQuote: Boolean = false,
        val ignoreCase: Boolean = false,
        val annotations: Boolean = false,
        val preprocessor: Boolean = false,
        val variables: Boolean = false,
        val types: Boolean = true
    )

    class Lang(val name: String, val syntax: Syntax)

    private fun words(s: String) = s.trim().split(Regex("\\s+")).toSet()

    private val C_COMMENTS = listOf("//")
    private val C_BLOCK = listOf("/*" to "*/")

    private val KOTLIN = words("""
        package import class interface object fun val var if else when for while do return break continue try catch
        finally throw is in as null true false this super override open abstract sealed data enum companion private
        protected public internal lateinit by suspend inline reified const typealias init constructor where out vararg
        operator infix tailrec annotation inner crossinline noinline external expect actual value""")
    private val JAVA = words("""
        abstract assert boolean break byte case catch char class const continue default do double else enum extends
        final finally float for goto if implements import instanceof int interface long native new package private
        protected public return short static strictfp super switch synchronized this throw throws transient try void
        volatile while true false null var record yield sealed permits""")
    private val JS = words("""
        break case catch class const continue debugger default delete do else export extends finally for function if
        import in instanceof let new return super switch this throw try typeof var void while with yield async await
        of static get set true false null undefined NaN Infinity from""")
    private val TS = JS + words("""
        interface type enum implements namespace declare abstract private protected public readonly as any unknown
        never keyof infer is module string number boolean""")
    private val PYTHON = words("""
        False None True and as assert async await break class continue def del elif else except finally for from global
        if import in is lambda nonlocal not or pass raise return try while with yield match case self""")
    private val C = words("""
        auto break case char const continue default do double else enum extern float for goto if inline int long
        register restrict return short signed sizeof static struct switch typedef union unsigned void volatile while
        bool true false NULL""")
    private val CPP = C + words("""
        class namespace template typename public private protected virtual override final new delete this throw try
        catch using operator friend explicit constexpr noexcept static_cast dynamic_cast reinterpret_cast const_cast
        decltype mutable nullptr std""")
    private val CSHARP = words("""
        abstract as base bool break byte case catch char checked class const continue decimal default delegate do
        double else enum event explicit extern false finally fixed float for foreach goto if implicit in int interface
        internal is lock long namespace new null object operator out override params private protected public readonly
        ref return sbyte sealed short sizeof stackalloc static string struct switch this throw true try typeof uint
        ulong unchecked unsafe ushort using virtual void volatile while var async await get set record init yield""")
    private val GO = words("""
        break case chan const continue default defer else fallthrough for func go goto if import interface map package
        range return select struct switch type var true false nil iota""")
    private val RUST = words("""
        as async await break const continue crate dyn else enum extern false fn for if impl in let loop match mod move
        mut pub ref return self Self static struct super trait true type unsafe use where while""")
    private val SWIFT = words("""
        associatedtype class deinit enum extension fileprivate func import init inout internal let open operator private
        protocol public static struct subscript typealias var break case continue default defer do else fallthrough for
        guard if in repeat return switch where while as Any catch false is nil rethrows super self Self throw throws
        true try async await""")
    private val DART = words("""
        abstract as assert async await break case catch class const continue default do dynamic else enum export extends
        extension external factory false final finally for get if implements import in is late library mixin new null
        on operator part required rethrow return set static super switch this throw true try typedef var void while
        with yield""")
    private val PHP = words("""
        abstract and array as break callable case catch class clone const continue declare default do echo else elseif
        empty extends final finally fn for foreach function global goto if implements include include_once instanceof
        interface isset list match namespace new or print private protected public readonly require require_once
        return static switch throw trait try unset use var while xor yield true false null""")
    private val RUBY = words("""
        alias and begin break case class def do else elsif end ensure false for if in module next nil not or redo
        rescue retry return self super then true undef unless until when while yield require attr_accessor puts""")
    private val SHELL = words("""
        if then else elif fi case esac for select while until do done in function time return exit export local
        readonly declare unset shift source echo cd set""")
    private val POWERSHELL = words("""
        begin break catch class continue data do dynamicparam else elseif end exit filter finally for foreach from
        function if in param process return switch throw trap try until using while""")
    private val BATCH = words("""
        echo set if else for in do goto call exit not exist defined errorlevel setlocal endlocal pause cls start""")
    private val SQL = words("""
        select from where insert into values update set delete create table alter drop index view join inner left right
        outer full on and or not null is in exists between like limit offset order by group having as distinct union
        all primary key foreign references default unique check constraint begin commit rollback transaction case when
        then else end if count sum avg min max with returning integer int varchar text boolean serial bigint""")
    private val LUA = words("""
        and break do else elseif end false for function goto if in local nil not or repeat return then true until while""")
    private val R = words("if else repeat while function for in next break TRUE FALSE NULL Inf NaN NA library return")
    private val HASKELL = words("""
        case class data default deriving do else if import in infix infixl infixr instance let module newtype of then
        type where""")
    private val ELIXIR = words("def defp defmodule do end if else unless case cond fn when import alias require use true false nil")
    private val SCALA = JAVA + words("def val var object trait match case implicit lazy given using then type with yield")
    private val GROOVY = JAVA + words("def as in it trait")
    private val PERL = words("my our local sub if elsif else unless while until for foreach last next redo return use package print")
    private val ZIG = words("const var fn pub return if else while for break continue struct enum union error try catch defer comptime true false null undefined")
    private val SOLIDITY = words("pragma contract function returns return public private internal external view pure payable mapping address uint int bool string memory storage event emit modifier require if else for while true false")
    private val GRAPHQL = words("query mutation subscription fragment on type input enum interface union scalar schema extend implements true false null")
    private val PROTO = words("syntax package import option message enum service rpc returns repeated optional required oneof map reserved true false")
    private val TERRAFORM = words("resource data variable output module provider locals terraform true false null for in if")
    private val CMAKE = words("if else elseif endif foreach endforeach function endfunction macro endmacro set project add_executable add_library target_link_libraries include")
    private val MAKE = words("ifeq ifneq ifdef ifndef else endif include define endef export")
    private val DOCKER = words("FROM RUN CMD LABEL EXPOSE ENV ADD COPY ENTRYPOINT VOLUME USER WORKDIR ARG ONBUILD STOPSIGNAL HEALTHCHECK SHELL AS")
    private val NIM_JULIA = words("proc func var let const if elif else for while return import type object end function module using true false nothing")
    private val ASM = words("mov add sub mul div jmp je jne jz jnz call ret push pop cmp lea and or xor not inc dec int nop")

    private fun code(keywords: Set<String>, block: Boolean = true, charQuote: Boolean = false, backtick: Boolean = false,
                     annotations: Boolean = false, preprocessor: Boolean = false, triple: Boolean = false,
                     variables: Boolean = false) =
        Syntax(Family.CODE, keywords, C_COMMENTS, if (block) C_BLOCK else emptyList(),
            tripleQuotes = triple, backtick = backtick, charQuote = charQuote, annotations = annotations,
            preprocessor = preprocessor, variables = variables)

    private fun hash(keywords: Set<String>, triple: Boolean = false, variables: Boolean = false, ignoreCase: Boolean = false,
                     annotations: Boolean = false) =
        Syntax(Family.CODE, keywords, listOf("#"), tripleQuotes = triple, variables = variables, ignoreCase = ignoreCase,
            annotations = annotations)

    private val MARKUP = Syntax(Family.MARKUP)
    private val CSS = Syntax(Family.CSS, words("important media import keyframes font-face supports"),
        blockComments = C_BLOCK, annotations = true, types = false)
    private val SCSS = Syntax(Family.CSS, words("important media import keyframes font-face supports mixin include extend if else each"),
        lineComments = C_COMMENTS, blockComments = C_BLOCK, annotations = true, variables = true, types = false)
    private val JSON = Syntax(Family.JSON, words("true false null"), quotes = "\"", types = false)
    private val JSONC = Syntax(Family.JSON, words("true false null"), C_COMMENTS, C_BLOCK, quotes = "\"", types = false)
    private val YAML = Syntax(Family.YAML, words("true false null yes no on off"), listOf("#"), types = false)
    private val INI = Syntax(Family.INI, words("true false yes no on off"), listOf("#", ";"), types = false)
    private val MARKDOWN = Syntax(Family.MARKDOWN)
    private val DIFF = Syntax(Family.DIFF)
    private val PLAIN = Syntax(Family.PLAIN)

    val GENERIC: Syntax = code(KOTLIN + JAVA + JS + PYTHON + words("func fn let mut impl use echo then fi done end"), backtick = true)

    private val BY_EXT: Map<String, Lang> = buildMap {
        fun add(name: String, syntax: Syntax, vararg ext: String) = ext.forEach { put(it, Lang(name, syntax)) }
        add("Kotlin", code(KOTLIN, charQuote = true, annotations = true, triple = true), "kt", "kts")
        add("Java", code(JAVA, charQuote = true, annotations = true, triple = true), "java")
        add("JavaScript", code(JS, backtick = true), "js", "mjs", "cjs", "jsx")
        add("TypeScript", code(TS, backtick = true, annotations = true), "ts", "tsx", "mts", "cts")
        add("Python", hash(PYTHON, triple = true, annotations = true), "py", "pyw", "pyi")
        add("C", code(C, charQuote = true, preprocessor = true), "c", "h")
        add("C++", code(CPP, charQuote = true, preprocessor = true), "cpp", "cc", "cxx", "hpp", "hh", "hxx", "ino")
        add("C#", code(CSHARP, charQuote = true, preprocessor = true), "cs", "csx")
        add("Objective-C", code(C + words("interface implementation end property self nil YES NO"), charQuote = true, preprocessor = true, annotations = true), "m", "mm")
        add("Go", code(GO, charQuote = true, backtick = true), "go")
        add("Rust", code(RUST, charQuote = true), "rs")
        add("Swift", code(SWIFT, annotations = true, triple = true), "swift")
        add("Dart", code(DART, annotations = true, triple = true), "dart")
        add("Scala", code(SCALA, charQuote = true, annotations = true, triple = true), "scala", "sc")
        add("Groovy", code(GROOVY, annotations = true, triple = true), "groovy", "gradle")
        add("PHP", Syntax(Family.CODE, PHP, listOf("//", "#"), C_BLOCK, variables = true), "php")
        add("Ruby", hash(RUBY, variables = false), "rb", "rake", "gemspec")
        add("Perl", hash(PERL, variables = true), "pl", "pm")
        add("Shell", hash(SHELL, variables = true), "sh", "bash", "zsh", "fish", "ksh")
        add("PowerShell", Syntax(Family.CODE, POWERSHELL, listOf("#"), listOf("<#" to "#>"), variables = true, ignoreCase = true), "ps1", "psm1", "psd1")
        add("Batch", Syntax(Family.CODE, BATCH, listOf("::", "rem ", "REM "), variables = false, ignoreCase = true, types = false), "bat", "cmd")
        add("SQL", Syntax(Family.CODE, SQL, listOf("--"), C_BLOCK, quotes = "'\"", ignoreCase = true, types = false), "sql")
        add("Lua", Syntax(Family.CODE, LUA, listOf("--"), listOf("--[[" to "]]")), "lua")
        add("R", hash(R), "r", "R")
        add("Haskell", Syntax(Family.CODE, HASKELL, listOf("--"), listOf("{-" to "-}"), charQuote = true), "hs")
        add("Elixir", hash(ELIXIR, triple = true), "ex", "exs")
        add("Erlang", Syntax(Family.CODE, words("module export fun case of end if when receive after try catch"), listOf("%")), "erl")
        add("Clojure", Syntax(Family.CODE, words("defn def let fn if do loop recur ns require nil true false"), listOf(";"), quotes = "\""), "clj", "cljs", "edn")
        add("F#", Syntax(Family.CODE, words("let mutable fun function match with type module open if then else for in do yield rec"), C_COMMENTS, listOf("(*" to "*)")), "fs", "fsx")
        add("Visual Basic", Syntax(Family.CODE, words("Dim As If Then Else End Sub Function Return For Next While Class Module Public Private"), listOf("'"), quotes = "\"", ignoreCase = true), "vb", "vbs")
        add("Assembly", Syntax(Family.CODE, ASM, listOf(";", "#"), ignoreCase = true, types = false), "asm", "s")
        add("Zig", code(ZIG, charQuote = true), "zig")
        add("Nim", hash(NIM_JULIA, triple = true), "nim")
        add("Julia", hash(NIM_JULIA, triple = true), "jl")
        add("Solidity", code(SOLIDITY), "sol")
        add("GraphQL", hash(GRAPHQL), "graphql", "gql")
        add("Protocol Buffers", code(PROTO), "proto")
        add("Terraform", Syntax(Family.CODE, TERRAFORM, listOf("#", "//"), C_BLOCK), "tf", "tfvars", "hcl")
        add("CMake", hash(CMAKE, ignoreCase = true), "cmake")
        add("Makefile", hash(MAKE, variables = true), "mk", "make")
        add("HTML", MARKUP, "html", "htm", "xhtml")
        add("XML", MARKUP, "xml", "svg", "plist", "xaml", "xsd", "xsl", "csproj", "fxml")
        add("Vue", MARKUP, "vue")
        add("Svelte", MARKUP, "svelte")
        add("CSS", CSS, "css")
        add("SCSS", SCSS, "scss", "sass", "less")
        add("JSON", JSON, "json", "geojson", "webmanifest")
        add("JSON", JSONC, "jsonc", "json5")
        add("YAML", YAML, "yml", "yaml")
        add("TOML", INI, "toml")
        add("INI", INI, "ini", "cfg", "conf", "properties", "env", "editorconfig", "gitattributes")
        add("Markdown", MARKDOWN, "md", "markdown", "mdx")
        add("Diff", DIFF, "diff", "patch")
        add("CSV", PLAIN, "csv", "tsv")
        add("", PLAIN, "txt", "log", "text", "gitignore")
    }

    private val BY_NAME: Map<String, Lang> = mapOf(
        "dockerfile" to Lang("Dockerfile", Syntax(Family.CODE, DOCKER, listOf("#"), variables = true, types = false)),
        "makefile" to Lang("Makefile", hash(MAKE, variables = true)),
        "gnumakefile" to Lang("Makefile", hash(MAKE, variables = true)),
        "cmakelists.txt" to Lang("CMake", hash(CMAKE, ignoreCase = true)),
        "jenkinsfile" to Lang("Groovy", code(GROOVY, annotations = true, triple = true)),
        "gemfile" to Lang("Ruby", hash(RUBY)),
        "rakefile" to Lang("Ruby", hash(RUBY)),
        "podfile" to Lang("Ruby", hash(RUBY)),
        ".gitignore" to Lang("", PLAIN),
        ".env" to Lang("INI", INI),
        ".bashrc" to Lang("Shell", hash(SHELL, variables = true)),
        ".zshrc" to Lang("Shell", hash(SHELL, variables = true))
    )

    fun language(fileName: String): Lang? {
        val base = fileName.substringAfterLast('/').substringAfterLast('\\').trim()
        val lower = base.lowercase()
        BY_NAME[lower]?.let { return it }
        if (lower.startsWith("dockerfile")) return BY_NAME["dockerfile"]
        val ext = base.substringAfterLast('.', "")
        if (ext.isEmpty()) return null
        return BY_EXT[ext] ?: BY_EXT[ext.lowercase()]
    }

    fun isCode(fileName: String): Boolean = language(fileName) != null

    fun tokenize(text: String, syntax: Syntax): List<Token> = when (syntax.family) {
        Family.PLAIN -> emptyList()
        Family.MARKUP -> markup(text, 0, text.length)
        Family.MARKDOWN -> markdown(text)
        Family.DIFF -> diff(text)
        Family.INI -> ini(text, syntax)
        else -> scan(text, 0, text.length, syntax)
    }

    private fun isIdentStart(c: Char) = c.isLetter() || c == '_'
    private fun isIdentPart(c: Char) = c.isLetterOrDigit() || c == '_'

    private fun nextNonSpace(text: String, from: Int, end: Int): Int {
        var j = from
        while (j < end && (text[j] == ' ' || text[j] == '\t')) j++
        return j
    }

    private fun lineStartOnlySpaces(text: String, i: Int): Boolean {
        var j = i - 1
        while (j >= 0 && text[j] != '\n') {
            if (text[j] != ' ' && text[j] != '\t') return false
            j--
        }
        return true
    }

    private fun scan(text: String, from: Int, to: Int, s: Syntax): List<Token> {
        val out = ArrayList<Token>()
        val kw = if (s.ignoreCase) s.keywords.mapTo(HashSet()) { it.lowercase() } else s.keywords
        val keyed = s.family == Family.JSON || s.family == Family.YAML || s.family == Family.CSS
        var depth = 0
        var i = from
        loop@ while (i < to) {
            val c = text[i]
            if (c == '\n') { i++; continue }
            for ((open, close) in s.blockComments) {
                if (text.startsWith(open, i)) {
                    val e = text.indexOf(close, i + open.length).let { if (it < 0 || it >= to) to else it + close.length }
                    out += Token(i, e, Kind.COMMENT); i = e; continue@loop
                }
            }
            for (lc in s.lineComments) {
                if (text.regionMatches(i, lc, 0, lc.length, ignoreCase = s.ignoreCase)) {
                    if (lc == "#" && i > from && !text[i - 1].isWhitespace()) continue
                    if (lc.endsWith(" ") && !lineStartOnlySpaces(text, i)) continue
                    if (s.preprocessor && lc == "#") continue
                    val e = text.indexOf('\n', i).let { if (it < 0 || it > to) to else it }
                    out += Token(i, e, Kind.COMMENT); i = e; continue@loop
                }
            }
            if (s.preprocessor && c == '#' && lineStartOnlySpaces(text, i)) {
                var j = i + 1
                while (j < to && (text[j] == ' ' || text[j] == '\t')) j++
                while (j < to && isIdentPart(text[j])) j++
                out += Token(i, j, Kind.ANNOTATION); i = j; continue
            }
            if (s.tripleQuotes && (text.startsWith("\"\"\"", i) || text.startsWith("'''", i))) {
                val q = text.substring(i, i + 3)
                val e = text.indexOf(q, i + 3).let { if (it < 0 || it >= to) to else it + 3 }
                out += Token(i, e, Kind.STRING); i = e; continue
            }
            if (c in s.quotes || (c == '`' && s.backtick)) {
                if (c == '\'' && s.charQuote) {
                    val close = when {
                        i + 1 < to && text[i + 1] == '\\' ->
                            (i + 3 until minOf(to, i + 13)).firstOrNull { text[it] == '\'' || text[it] == '\n' }
                                ?.takeIf { text[it] == '\'' } ?: -1
                        i + 2 < to && text[i + 1] != '\n' && text[i + 2] == '\'' -> i + 2
                        i + 3 < to && text[i + 1].isHighSurrogate() && text[i + 3] == '\'' -> i + 3
                        else -> -1
                    }
                    if (close < 0) { i++; continue }
                    out += Token(i, close + 1, Kind.STRING); i = close + 1; continue
                }
                var j = i + 1
                while (j < to) {
                    val d = text[j]
                    if (d == '\\') { j += 2; continue }
                    if (d == c) { j++; break }
                    if (d == '\n' && c != '`') break
                    j++
                }
                val e = minOf(j, to)
                val kind = if (keyed && s.family != Family.CSS && text.getOrNull(nextNonSpace(text, e, to)) == ':') Kind.KEY else Kind.STRING
                out += Token(i, e, kind); i = e; continue
            }
            if (s.variables && c == '$' && i + 1 < to) {
                var j = i + 1
                if (text[j] == '{') {
                    val e = text.indexOf('}', j).let { if (it < 0 || it >= to) j else it + 1 }
                    j = e
                } else {
                    while (j < to && isIdentPart(text[j])) j++
                }
                if (j > i + 1) { out += Token(i, j, Kind.ATTR); i = j; continue }
            }
            if (s.family == Family.CSS) {
                if (c == '{') depth++ else if (c == '}') depth = maxOf(0, depth - 1)
                if (c == '#' && depth > 0) {
                    var j = i + 1
                    while (j < to && (text[j].isDigit() || text[j].lowercaseChar() in 'a'..'f')) j++
                    if (j - i - 1 in setOf(3, 4, 6, 8)) { out += Token(i, j, Kind.NUMBER); i = j; continue }
                }
            }
            if (c.isDigit() && (i == from || !isIdentPart(text[i - 1]))) {
                var j = i + 1
                while (j < to) {
                    val d = text[j]
                    if (isIdentPart(d) || d == '.') { j++; continue }
                    if ((d == '+' || d == '-') && (text[j - 1] == 'e' || text[j - 1] == 'E') &&
                        !text.substring(i, j).startsWith("0x", ignoreCase = true)) { j++; continue }
                    if (d == '%' && s.family == Family.CSS) { j++; continue }
                    break
                }
                out += Token(i, j, Kind.NUMBER); i = j; continue
            }
            if (isIdentStart(c) || (c == '@' && s.annotations && i + 1 < to && isIdentStart(text[i + 1])) ||
                (c == '-' && s.family == Family.CSS && i + 1 < to && isIdentStart(text[i + 1]))) {
                var j = i + 1
                while (j < to && (isIdentPart(text[j]) || ((s.family == Family.CSS || s.family == Family.YAML) && text[j] == '-'))) j++
                val word = text.substring(i, j)
                val after = nextNonSpace(text, j, to)
                val next = text.getOrNull(after)
                val kind = when {
                    c == '@' -> Kind.ANNOTATION
                    (if (s.ignoreCase) word.lowercase() else word) in kw -> Kind.KEYWORD
                    keyed && next == ':' && text.getOrNull(after + 1) != ':' && (s.family != Family.CSS || depth > 0) -> Kind.KEY
                    s.family == Family.CODE && next == '(' -> Kind.FUNCTION
                    s.family == Family.CODE && s.types && word.length > 1 && word[0].isUpperCase() &&
                        word.any { it.isLowerCase() } -> Kind.TYPE
                    else -> null
                }
                if (kind != null) out += Token(i, j, kind)
                i = j; continue
            }
            i++
        }
        return out
    }

    private val JS_SYNTAX get() = BY_EXT.getValue("js").syntax
    private val CSS_SYNTAX get() = BY_EXT.getValue("css").syntax

    private fun markup(text: String, from: Int, to: Int): List<Token> {
        val out = ArrayList<Token>()
        var i = from
        while (i < to) {
            if (text.startsWith("<!--", i)) {
                val e = text.indexOf("-->", i + 4).let { if (it < 0 || it >= to) to else it + 3 }
                out += Token(i, e, Kind.COMMENT); i = e; continue
            }
            if (text.startsWith("<![CDATA[", i)) {
                val e = text.indexOf("]]>", i).let { if (it < 0 || it >= to) to else it + 3 }
                out += Token(i, e, Kind.STRING); i = e; continue
            }
            if (text[i] == '<' && i + 1 < to && (text[i + 1].isLetter() || text[i + 1] in "/!?")) {
                var j = i + 1
                if (text[j] in "/!?") j++
                val nameStart = j
                while (j < to && (text[j].isLetterOrDigit() || text[j] in "-_:.")) j++
                val tagName = text.substring(nameStart, j).lowercase()
                val closing = text[i + 1] == '/'
                out += Token(i, j, Kind.TAG)
                while (j < to && text[j] != '>') {
                    val c = text[j]
                    when {
                        c == '"' || c == '\'' -> {
                            val e = text.indexOf(c, j + 1).let { if (it < 0 || it >= to) to else it + 1 }
                            out += Token(j, e, Kind.STRING); j = e
                        }
                        c.isLetter() || c in "_:@#" -> {
                            val s = j
                            while (j < to && (text[j].isLetterOrDigit() || text[j] in "-_:.@#")) j++
                            out += Token(s, j, Kind.ATTR)
                        }
                        c == '/' || c == '?' -> { out += Token(j, j + 1, Kind.TAG); j++ }
                        else -> j++
                    }
                }
                if (j < to) { out += Token(j, j + 1, Kind.TAG); j++ }
                if (!closing && (tagName == "script" || tagName == "style")) {
                    val end = text.indexOf("</$tagName", j, ignoreCase = true).let { if (it < 0 || it > to) to else it }
                    out += scan(text, j, end, if (tagName == "script") JS_SYNTAX else CSS_SYNTAX)
                    j = end
                }
                i = j; continue
            }
            if (text[i] == '&') {
                val e = text.indexOf(';', i)
                if (e in (i + 2)..(i + 10) && e < to) { out += Token(i, e + 1, Kind.NUMBER); i = e + 1; continue }
            }
            i++
        }
        return out
    }

    private inline fun eachLine(text: String, block: (start: Int, end: Int) -> Unit) {
        var s = 0
        while (s <= text.length) {
            val e = text.indexOf('\n', s).let { if (it < 0) text.length else it }
            block(s, e)
            if (e >= text.length) break
            s = e + 1
        }
    }

    private fun ini(text: String, s: Syntax): List<Token> {
        val out = ArrayList<Token>()
        val kw = s.keywords
        eachLine(text) { a, b ->
            val st = nextNonSpace(text, a, b)
            if (st >= b) return@eachLine
            val c = text[st]
            when {
                c == '#' || c == ';' -> out += Token(st, b, Kind.COMMENT)
                c == '[' -> out += Token(st, b, Kind.KEYWORD)
                else -> {
                    val eq = (st until b).firstOrNull { text[it] == '=' || text[it] == ':' }
                    if (eq != null) {
                        var ke = eq
                        while (ke > st && text[ke - 1].isWhitespace()) ke--
                        if (ke > st) out += Token(st, ke, Kind.KEY)
                        val vs = nextNonSpace(text, eq + 1, b)
                        if (vs < b) {
                            val v = text.substring(vs, b).trim()
                            val kind = when {
                                v.startsWith("\"") || v.startsWith("'") -> Kind.STRING
                                v.lowercase() in kw -> Kind.KEYWORD
                                v.firstOrNull()?.let { it.isDigit() || it == '-' && v.length > 1 && v[1].isDigit() } == true -> Kind.NUMBER
                                else -> null
                            }
                            if (kind != null) out += Token(vs, vs + v.length, kind)
                        }
                    }
                }
            }
        }
        return out
    }

    private fun markdown(text: String): List<Token> {
        val out = ArrayList<Token>()
        var fence = -1
        eachLine(text) { a, b ->
            val st = nextNonSpace(text, a, b)
            val trimmedStartsFence = text.startsWith("```", st) || text.startsWith("~~~", st)
            when {
                fence >= 0 -> {
                    if (trimmedStartsFence) { out += Token(fence, b, Kind.STRING); fence = -1 }
                }
                trimmedStartsFence -> fence = a
                st < b && text[st] == '#' -> out += Token(st, b, Kind.KEYWORD)
                st < b && text[st] == '>' -> out += Token(st, b, Kind.COMMENT)
                else -> {
                    if (st < b && (text[st] == '-' || text[st] == '*' || text[st] == '+') && st + 1 < b && text[st + 1] == ' ')
                        out += Token(st, st + 1, Kind.NUMBER)
                    var i = a
                    while (i < b) {
                        val c = text[i]
                        if (c == '`') {
                            val e = text.indexOf('`', i + 1)
                            if (e in (i + 1) until b) { out += Token(i, e + 1, Kind.STRING); i = e + 1; continue }
                        }
                        if (c == '*' && text.startsWith("**", i)) {
                            val e = text.indexOf("**", i + 2)
                            if (e in (i + 2) until b) { out += Token(i, e + 2, Kind.TYPE); i = e + 2; continue }
                        }
                        if (c == '[') {
                            val close = text.indexOf("](", i)
                            val end = if (close in i until b) text.indexOf(')', close) else -1
                            if (end in close until b) {
                                out += Token(i, close + 1, Kind.FUNCTION)
                                out += Token(close + 1, end + 1, Kind.STRING)
                                i = end + 1; continue
                            }
                        }
                        i++
                    }
                }
            }
        }
        if (fence >= 0) out += Token(fence, text.length, Kind.STRING)
        return out
    }

    private fun diff(text: String): List<Token> {
        val out = ArrayList<Token>()
        eachLine(text) { a, b ->
            if (a >= b) return@eachLine
            val kind = when {
                text.startsWith("+++", a) || text.startsWith("---", a) -> Kind.KEYWORD
                text.startsWith("@@", a) -> Kind.FUNCTION
                text[a] == '+' -> Kind.ADDED
                text[a] == '-' -> Kind.REMOVED
                text.startsWith("diff ", a) || text.startsWith("index ", a) -> Kind.COMMENT
                else -> null
            }
            if (kind != null) out += Token(a, b, kind)
        }
        return out
    }
}
