package com.quata.core.platform

/**
 * Reads text-like documents after a platform picker or repository has supplied a [PlatformFile].
 * This is intentionally not a PDF, RTF or Office renderer.
 */
interface DocumentTextReader {
    suspend fun readText(file: PlatformFile, maxCharacters: Int = DefaultMaxCharacters): PlatformResult<String>

    companion object {
        const val DefaultMaxCharacters = 250_000
    }
}

object UnsupportedDocumentTextReader : DocumentTextReader {
    override suspend fun readText(file: PlatformFile, maxCharacters: Int): PlatformResult<String> = PlatformResult.Unsupported
}
