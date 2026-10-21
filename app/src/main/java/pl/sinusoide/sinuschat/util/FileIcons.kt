package pl.sinusoide.sinuschat.util

import pl.sinusoide.sinuschat.R
import java.io.File

object FileIcons {

    private const val SCAN_BYTES = 16 * 1024
    private val ARCHIVES = setOf(
        "zip", "zipx", "rar", "7z", "tar", "gz", "gzip", "tgz", "bz2", "bzip2", "tbz", "tbz2", "xz", "txz",
        "lz", "lzma", "tlz", "lz4", "zst", "tzst", "z", "taz", "br", "cab", "arj", "lzh", "lha", "ace",
        "cpio", "sit", "sitx", "wim", "zpaq", "pea", "uue", "xar"
    )
    private val APPS = setOf("apk", "xapk", "apks", "apkm")
    private val SPLIT_PART = Regex("""^[zr]\d{2}$|^\d{3}$""")
    private val TERMINAL = setOf("Shell", "PowerShell", "Batch")
    private val ADB_LINE = Regex("""^(\$\s+|>\s*|PS[^>]*>\s*)?(sudo\s+)?(\.[\\/])?(adb|fastboot)(\.exe)?\s+\S""", RegexOption.IGNORE_CASE)

    private val adbFiles: MutableMap<String, Boolean> = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, Boolean>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 256
        })

    fun isAdb(text: String): Boolean {
        var commands = 0
        var adb = 0
        for (raw in text.lineSequence().take(400)) {
            val t = raw.trim()
            if (t.isEmpty() || t.startsWith("#") || t.startsWith("::") || t.startsWith("//") ||
                t.startsWith("rem ", ignoreCase = true) || t.startsWith("@echo", ignoreCase = true)) continue
            commands++
            if (ADB_LINE.containsMatchIn(t)) adb++
        }
        return adb > 0 && adb * 2 >= commands
    }

    private fun forLang(lang: CodeHighlighter.Lang): Int? = when (lang.name) {
        "HTML" -> R.drawable.ic_html
        "JavaScript" -> R.drawable.ic_js
        "CSS", "SCSS" -> R.drawable.ic_css
        "PHP" -> R.drawable.ic_php
        "JSON" -> R.drawable.ic_json
        "SQL" -> R.drawable.ic_sql
        "XML" -> R.drawable.ic_xml
        in TERMINAL -> R.drawable.ic_terminal
        "", "CSV" -> null
        else -> R.drawable.ic_code
    }

    private fun mayBeAdb(lang: CodeHighlighter.Lang?) = lang != null && (lang.name in TERMINAL || lang.name.isEmpty())

    fun isArchive(fileName: String): Boolean {
        val lower = fileName.lowercase()
        val ext = lower.substringAfterLast('.', "")
        if (ext in ARCHIVES) return true
        if (!SPLIT_PART.matches(ext)) return false
        return ext[0].isLetter() || ARCHIVES.any { lower.contains(".$it.") }
    }

    fun forName(fileName: String): Int {
        if (fileName.substringAfterLast('.', "").lowercase() in APPS) return R.drawable.ic_apk
        if (isArchive(fileName)) return R.drawable.ic_zip
        val lang = CodeHighlighter.language(fileName) ?: return R.drawable.ic_document
        return forLang(lang) ?: R.drawable.ic_document
    }

    private fun key(file: File) = file.absolutePath + "|" + file.length() + "|" + file.lastModified()

    fun forFile(fileName: String, file: File?): Int {
        if (file != null && mayBeAdb(CodeHighlighter.language(fileName)) && adbFiles[key(file)] == true) return R.drawable.ic_adb
        return forName(fileName)
    }

    fun needsScan(fileName: String, file: File?): Boolean =
        file != null && mayBeAdb(CodeHighlighter.language(fileName)) && !adbFiles.containsKey(key(file))

    fun scan(fileName: String, file: File) {
        if (!mayBeAdb(CodeHighlighter.language(fileName))) return
        val k = key(file)
        if (adbFiles.containsKey(k)) return
        val text = try {
            file.inputStream().use { input ->
                val buf = ByteArray(minOf(SCAN_BYTES.toLong(), file.length()).toInt().coerceAtLeast(0))
                var off = 0
                while (off < buf.size) {
                    val n = input.read(buf, off, buf.size - off)
                    if (n < 0) break
                    off += n
                }
                if (buf.take(off).any { it == 0.toByte() }) "" else String(buf, 0, off, Charsets.UTF_8)
            }
        } catch (_: Exception) { "" }
        adbFiles[k] = isAdb(text)
    }

    fun forSnippet(seg: CodeSnippets.Segment): Int {
        val lang = seg.lang
        if ((lang == null || mayBeAdb(lang)) && isAdb(seg.text)) return R.drawable.ic_adb
        return lang?.let { forLang(it) } ?: R.drawable.ic_code
    }
}
