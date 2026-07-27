package com.quata.core.platform

/** Test-only portable signature parser; platform loaders provide fixture bytes. */
internal fun documentFixtureKind(bytes: ByteArray): String? = when {
    bytes.startsWith("%PDF-".encodeToByteArray()) -> "pdf"
    bytes.startsWith("{\\rtf".encodeToByteArray()) -> "rtf"
    bytes.startsWith(byteArrayOf(0x50, 0x4b, 0x03, 0x04)) -> "zip"
    else -> null
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
