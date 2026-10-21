package pl.sinusoide.sinuschat.util

import android.content.Context
import android.util.Log
import pl.sinusoide.sinuschat.SiniaFile
import java.io.File

object ModelArtifacts {

    data class Result(val text: String, val file: SiniaFile?)

    private const val TAG = "SiniaArtifacts"
    private val SLIDE_LINE = Regex("""(?i)(slajd|slide)\s*\d+\s*[:.-]?\s*(.*)""")
    private val BLOCK = Regex("""```[ \t]*([A-Za-z0-9_+#-]*)[ \t]*\r?\n(.*?)```""", RegexOption.DOT_MATCHES_ALL)

    private val EXTENSIONS = mapOf(
        "kotlin" to "kt", "kt" to "kt", "java" to "java", "python" to "py", "py" to "py",
        "javascript" to "js", "js" to "js", "typescript" to "ts", "ts" to "ts",
        "c" to "c", "cpp" to "cpp", "c++" to "cpp", "csharp" to "cs", "cs" to "cs",
        "php" to "php", "ruby" to "rb", "rb" to "rb", "go" to "go", "rust" to "rs", "rs" to "rs",
        "swift" to "swift", "sql" to "sql", "json" to "json", "xml" to "xml",
        "yaml" to "yaml", "yml" to "yml", "bash" to "sh", "sh" to "sh", "shell" to "sh",
        "css" to "css", "html" to "html", "htm" to "html", "markdown" to "md", "md" to "md"
    )

    fun hideBlocks(raw: String, building: String): String {
        val cut = raw.indexOf("```")
        if (cut < 0) return raw
        val head = raw.substring(0, cut).trimEnd()
        return if (head.isEmpty()) building else head + "\n\n" + building
    }

    fun parse(ctx: Context, answer: String): Result {
        val match = BLOCK.find(answer) ?: return Result(answer, null)
        val tag = match.groupValues[1].lowercase()
        val body = match.groupValues[2].trimEnd()
        if (body.lines().size < 2 && tag !in setOf("pptx", "docx", "xlsx")) return Result(answer, null)

        val text = (answer.removeRange(match.range)).replace(Regex("\n{3,}"), "\n\n").trim()
        val before = answer.substring(0, match.range.first)

        return try {
            val file = build(ctx, tag, body, before)
            Result(if (text.isEmpty()) "" else text, file)
        } catch (e: Throwable) {
            Log.e(TAG, "Nie udalo sie zbudowac pliku", e)
            Result(answer, null)
        }
    }

    private fun build(ctx: Context, tag: String, body: String, before: String): SiniaFile {
        val dir = File(ctx.filesDir, "sinia_files").apply { mkdirs() }
        val stamp = System.currentTimeMillis().toString().takeLast(6)

        return when (tag) {
            "pptx", "powerpoint", "prezentacja", "presentation" -> {
                val name = nameFrom(before, "pptx") ?: "prezentacja_$stamp.pptx"
                val out = File(dir, name)
                OfficeFiles.buildPptx(out, slidesFrom(body))
                SiniaFile(name, SiniaFile.Kind.PPTX, out.absolutePath, body)
            }
            "docx", "word", "dokument", "document" -> {
                val name = nameFrom(before, "docx") ?: "dokument_$stamp.docx"
                val out = File(dir, name)
                OfficeFiles.buildDocx(out, body.lines())
                SiniaFile(name, SiniaFile.Kind.DOCX, out.absolutePath, body)
            }
            "xlsx", "excel", "arkusz", "spreadsheet", "csv" -> {
                val name = nameFrom(before, "xlsx") ?: "arkusz_$stamp.xlsx"
                val out = File(dir, name)
                OfficeFiles.buildXlsx(out, rowsFrom(body))
                SiniaFile(name, SiniaFile.Kind.XLSX, out.absolutePath, body)
            }
            else -> {
                val ext = EXTENSIONS[tag] ?: "txt"
                val name = nameFrom(before, ext) ?: "sinia_$stamp.$ext"
                val out = File(dir, name)
                out.writeText(body)
                val kind = if (ext == "html") SiniaFile.Kind.HTML else SiniaFile.Kind.CODE
                SiniaFile(name, kind, out.absolutePath, body)
            }
        }
    }

    private fun nameFrom(before: String, ext: String): String? {
        val m = Regex("""([\w-]{1,40}\.$ext)\b""", RegexOption.IGNORE_CASE).findAll(before).lastOrNull()
        return m?.groupValues?.get(1)?.replace(Regex("""[^\w.-]"""), "")
    }

    private fun slidesFrom(body: String): List<OfficeFiles.Slide> {
        val slides = mutableListOf<OfficeFiles.Slide>()
        var title: String? = null
        val bullets = mutableListOf<String>()
        fun flush() {
            if (title != null) slides.add(OfficeFiles.Slide(title!!, bullets.toList()))
            bullets.clear()
        }
        for (raw in body.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val plainSlide = SLIDE_LINE.matchEntire(line)
            when {
                line.startsWith("#") -> { flush(); title = line.trimStart('#', ' ') }
                plainSlide != null -> { flush(); title = plainSlide.groupValues[2].ifBlank { line } }
                else -> bullets.add(line.trimStart('-', '*', '•', ' '))
            }
        }
        flush()
        return slides.ifEmpty { listOf(OfficeFiles.Slide("Prezentacja", body.lines().filter { it.isNotBlank() })) }
    }

    private fun rowsFrom(body: String): List<List<String>> {
        val lines = body.lines().filter { it.isNotBlank() }
        val sep = when {
            lines.any { it.contains('|') } -> '|'
            lines.any { it.contains('\t') } -> '\t'
            lines.any { it.contains(';') } -> ';'
            else -> ','
        }
        return lines
            .filterNot { it.replace(Regex("""[|\-: ]"""), "").isEmpty() }
            .map { line ->
                line.trim().trim('|').split(sep).map { it.trim() }
            }
    }
}
