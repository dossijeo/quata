package com.quata.core.platform

object DocumentSupport {
    private val officeExtensions = setOf("doc", "docx", "xls", "xlsx", "ppt", "pptx")
    private val richTextExtensions = setOf("rtf")
    private val plainTextExtensions = setOf(
        "txt", "text", "csv", "log", "md", "markdown", "json", "xml", "html", "htm", "xhtml", "css",
        "js", "mjs", "cjs", "ts", "tsx", "jsx", "kt", "kts", "java", "gradle", "properties", "ini",
        "conf", "cfg", "yaml", "yml", "sql", "sh", "bat", "ps1", "svg",
    )
    private val textExtensions = richTextExtensions + plainTextExtensions
    private val supportedExtensions = officeExtensions + textExtensions + "pdf"
    private val supportedMimes = setOf(
        "application/pdf", "application/msword", "application/vnd.ms-word",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation", "application/rtf", "text/rtf",
        "text/csv", "application/csv", "text/plain", "text/html", "text/xml", "text/css", "text/javascript",
        "text/markdown", "text/yaml", "application/json", "application/xml", "application/xhtml+xml",
        "application/javascript", "application/x-javascript", "application/x-yaml", "image/svg+xml",
    )

    fun canPreview(source: String, fileName: String? = null, mimeType: String? = null): Boolean {
        extensionFrom(source, fileName)?.let { return it in supportedExtensions }
        val mime = normalizedMime(mimeType)
        return mime in supportedMimes || isPlainTextMime(mime)
    }

    fun isTextLike(source: String = "", fileName: String? = null, mimeType: String? = null): Boolean =
        extensionFrom(source, fileName)?.let { it in plainTextExtensions } == true || isPlainTextMime(normalizedMime(mimeType))

    fun extensionForMimeType(mimeType: String?): String? = when (normalizedMime(mimeType)) {
        "application/pdf" -> "pdf"
        "application/msword", "application/vnd.ms-word" -> "doc"
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
        "application/vnd.ms-excel" -> "xls"
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx"
        "application/vnd.ms-powerpoint" -> "ppt"
        "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx"
        "application/rtf", "text/rtf" -> "rtf"
        "text/csv", "application/csv" -> "csv"
        "text/html" -> "html"
        "text/xml", "application/xml" -> "xml"
        "application/xhtml+xml" -> "xhtml"
        "text/css" -> "css"
        "application/json" -> "json"
        "text/markdown" -> "md"
        "text/yaml", "application/x-yaml" -> "yaml"
        "text/javascript", "application/javascript", "application/x-javascript" -> "js"
        "image/svg+xml" -> "svg"
        "text/plain" -> "txt"
        else -> null
    }

    private fun extensionFrom(source: String, fileName: String?): String? =
        (fileName ?: source.substringBefore('?').substringAfterLast('/', missingDelimiterValue = source))
            .substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
            .takeIf { it.isNotBlank() }

    private fun normalizedMime(mimeType: String?): String? = mimeType?.lowercase()?.substringBefore(';')

    private fun isPlainTextMime(mimeType: String?): Boolean = when {
        mimeType == null || mimeType == "text/rtf" -> false
        mimeType.startsWith("text/") -> true
        mimeType in setOf("application/json", "application/xml", "application/xhtml+xml", "application/javascript", "application/x-javascript", "application/x-yaml", "image/svg+xml") -> true
        else -> false
    }
}
