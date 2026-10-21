package pl.sinusoide.sinuschat

data class SiniaFile(
    val name: String,
    val kind: Kind,
    val path: String,
    val source: String = ""
) {
    enum class Kind { CODE, HTML, DOCX, XLSX, PPTX }

    val mime: String get() = when (kind) {
        Kind.HTML -> "text/html"
        Kind.DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        Kind.XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        Kind.PPTX -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        Kind.CODE -> "text/plain"
    }
}
