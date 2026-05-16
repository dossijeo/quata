package com.quata.wordpress

/**
 * Parser mínimo para extraer campos simples de respuestas WordPress sin añadir Moshi/Gson.
 * En producción puedes sustituirlo por kotlinx.serialization, Moshi o Gson.
 */
internal object JsonLite {
    fun bool(json: String, key: String): Boolean? {
        val regex = Regex(""""${Regex.escape(key)}"\s*:\s*(true|false)""")
        return regex.find(json)?.groupValues?.getOrNull(1)?.toBooleanStrictOrNull()
    }

    fun int(json: String, key: String): Int? {
        val regex = Regex(""""${Regex.escape(key)}"\s*:\s*(-?\d+)""")
        return regex.find(json)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    fun long(json: String, key: String): Long? {
        val regex = Regex(""""${Regex.escape(key)}"\s*:\s*(-?\d+)""")
        return regex.find(json)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    fun string(json: String, key: String): String? {
        val regex = Regex(""""${Regex.escape(key)}"\s*:\s*"((?:\\.|[^"\\])*)"""")
        return regex.find(json)?.groupValues?.getOrNull(1)?.let(::unescape)
    }

    fun objectBody(json: String, key: String): String? {
        val startKey = """"$key""""
        val keyIndex = json.indexOf(startKey)
        if (keyIndex < 0) return null
        val braceStart = json.indexOf('{', keyIndex)
        if (braceStart < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in braceStart until json.length) {
            val c = json[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (c == '\\' && inString) {
                escaped = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (c == '{') depth++
                if (c == '}') {
                    depth--
                    if (depth == 0) return json.substring(braceStart, i + 1)
                }
            }
        }
        return null
    }

    private fun unescape(value: String): String {
        return value
            .replace("\\/", "/")
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")
    }
}
