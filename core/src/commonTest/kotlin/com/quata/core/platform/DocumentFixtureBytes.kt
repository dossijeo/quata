package com.quata.core.platform

/**
 * Small, self-owned binary fixtures used by every Kotlin target.  The OOXML payloads are valid
 * ZIP containers (not just `PK` prefixes) with the package entries a consumer needs to identify
 * their document family.  Keeping them inline makes common tests independent from filesystem and
 * resource loading differences on Android, Wasm and iOS.
 */
internal object DocumentFixtureBytes {
    val pdf: ByteArray = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n2 0 obj\n<< /Type /Pages /Kids [] /Count 0 >>\nendobj\nxref\n0 3\n0000000000 65535 f \n0000000009 00000 n \n0000000058 00000 n \ntrailer\n<< /Size 3 /Root 1 0 R >>\nstartxref\n110\n%%EOF\n".encodeToByteArray()
    val rtf: ByteArray = "{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0 Arial;}}\\f0\\fs24 Quata fixture\\par}".encodeToByteArray()

    val docx: ByteArray = base64Decode(
        "UEsDBBQAAAAIAFmY+1zuR1hmHwAAAB0AAAATAAAAW0NvbnRlbnRfVHlwZXNdLnhtbLOxr8jNUShLLSrOzM+zVTLUM1Cyt7MJqSxILda3AwBQSwMEFAAAAAgAWZj7XGFyWp4mAAAAJQAAAAsAAABfcmVscy8ucmVsc7Oxr8jNUShLLSrOzM+zVTLUM1Cyt7MJSs1JLAEKFGdkFhTr2wEAUEsDBBQAAAAIAFmY+1wQzV0NNgAAADUAAAARAAAAd29yZC9kb2N1bWVudC54bWyzsa/IzVEoSy0qzszPs1Uy1DNQsrezKbdKyU8uzU3NK1EASucVW5XbKpUW5VmVpBaXKOnbAQBQSwECFAAUAAAACABZmPtc7kdYZh8AAAAdAAAAEwAAAAAAAAAAAAAAgAEAAAAAW0NvbnRlbnRfVHlwZXNdLnhtbFBLAQIUABQAAAAIAFmY+1xhclqeJgAAACUAAAALAAAAAAAAAAAAAACAAVAAAABfcmVscy8ucmVsc1BLAQIUABQAAAAIAFmY+1wQzV0NNgAAADUAAAARAAAAAAAAAAAAAACAAZ8AAAB3b3JkL2RvY3VtZW50LnhtbFBLBQYAAAAAAwADALkAAAAEAQAAAAA=",
    )
    val xlsx: ByteArray = base64Decode(
        "UEsDBBQAAAAIAFmY+1zuR1hmHwAAAB0AAAATAAAAW0NvbnRlbnRfVHlwZXNdLnhtbLOxr8jNUShLLSrOzM+zVTLUM1Cyt7MJqSxILda3AwBQSwMEFAAAAAgAWZj7XGFyWp4mAAAAJQAAAAsAAABfcmVscy8ucmVsc7Oxr8jNUShLLSrOzM+zVTLUM1Cyt7MJSs1JLAEKFGdkFhTr2wEAUEsDBBQAAAAIAFmY+1yHA3iCMgAAADEAAAAPAAAAeGwvd29ya2Jvb2sueG1ss7GvyM1RKEstKs7Mz7NVMtQzULK3synPL8pOys/PVgBK5hXbKpUW5VmVpBaXKOnbAQBQSwECFAAUAAAACABZmPtc7kdYZh8AAAAdAAAAEwAAAAAAAAAAAAAAgAEAAAAAW0NvbnRlbnRfVHlwZXNdLnhtbFBLAQIUABQAAAAIAFmY+1xhclqeJgAAACUAAAALAAAAAAAAAAAAAACAAVAAAABfcmVscy8ucmVsc1BLAQIUABQAAAAIAFmY+1yHA3iCMgAAADEAAAAPAAAAAAAAAAAAAACAAZ8AAAB4bC93b3JrYm9vay54bWxQSwUGAAAAAAMAAwC3AAAA/gAAAAAA",
    )
    val pptx: ByteArray = base64Decode(
        "UEsDBBQAAAAIAFmY+1zuR1hmHwAAAB0AAAATAAAAW0NvbnRlbnRfVHlwZXNdLnhtbLOxr8jNUShLLSrOzM+zVTLUM1Cyt7MJqSxILda3AwBQSwMEFAAAAAgAWZj7XGFyWp4mAAAAJQAAAAsAAABfcmVscy8ucmVsc7Oxr8jNUShLLSrOzM+zVTLUM1Cyt7MJSs1JLAEKFGdkFhTr2wEAUEsDBBQAAAAIAFmY+1x+TN+gOQAAADkAAAAUAAAAcHB0L3ByZXNlbnRhdGlvbi54bWyzsa/IzVEoSy0qzszPs1Uy1DNQsrezKbAqKEotTs0rSSwBCisAleQVWxXYKpUW5VmVpBaXKOnbAQBQSwECFAAUAAAACABZmPtc7kdYZh8AAAAdAAAAEwAAAAAAAAAAAAAAgAEAAAAAW0NvbnRlbnRfVHlwZXNdLnhtbFBLAQIUABQAAAAIAFmY+1xhclqeJgAAACUAAAALAAAAAAAAAAAAAACAAVAAAABfcmVscy8ucmVsc1BLAQIUABQAAAAIAFmY+1x+TN+gOQAAADkAAAAUAAAAAAAAAAAAAACAAZ8AAABwcHQvcHJlc2VudGF0aW9uLnhtbFBLBQYAAAAAAwADALwAAAAKAQAAAAA=",
    )
}

/** Test-only portable signature parser.  ZIP is accepted only after its central directory parses. */
internal fun documentFixtureKind(bytes: ByteArray): String? = when {
    bytes.startsWith("%PDF-".encodeToByteArray()) -> "pdf"
    bytes.startsWith("{\\rtf".encodeToByteArray()) -> "rtf"
    else -> ooxmlCentralDirectoryEntries(bytes)?.let { entries ->
        when {
            entries.containsAll(setOf("[Content_Types].xml", "_rels/.rels", "word/document.xml")) -> "docx"
            entries.containsAll(setOf("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml")) -> "xlsx"
            entries.containsAll(setOf("[Content_Types].xml", "_rels/.rels", "ppt/presentation.xml")) -> "pptx"
            else -> "zip"
        }
    }
}

internal fun ooxmlCentralDirectoryEntries(bytes: ByteArray): Set<String>? {
    val eocd = findEocd(bytes) ?: return null
    if (u16(bytes, eocd + 4) != 0 || u16(bytes, eocd + 6) != 0) return null // multi-disk ZIP
    val count = u16(bytes, eocd + 10)
    val directorySize = u32(bytes, eocd + 12) ?: return null
    val directoryOffset = u32(bytes, eocd + 16) ?: return null
    if (count > 128 || directoryOffset + directorySize > eocd.toLong()) return null

    var cursor = directoryOffset.toInt()
    val directoryEnd = (directoryOffset + directorySize).toInt()
    val entries = linkedSetOf<String>()
    repeat(count) {
        if (cursor + 46 > directoryEnd || u32(bytes, cursor) != CentralDirectoryHeader) return null
        val nameSize = u16(bytes, cursor + 28)
        val extraSize = u16(bytes, cursor + 30)
        val commentSize = u16(bytes, cursor + 32)
        val recordSize = 46 + nameSize + extraSize + commentSize
        if (recordSize < 46 || cursor + recordSize > directoryEnd) return null
        // OOXML part names are UTF-8.  A malformed name is an invalid package, not a reason for
        // a test/helper caller to crash while deciding whether it may be admitted.
        val name = runCatching {
            bytes.copyOfRange(cursor + 46, cursor + 46 + nameSize).decodeToString(throwOnInvalidSequence = true)
        }.getOrElse { return null }
        if (name.isEmpty() || name.startsWith('/') || name.contains("..")) return null
        entries += name
        cursor += recordSize
    }
    return entries.takeIf { cursor == directoryEnd }
}

private fun findEocd(bytes: ByteArray): Int? {
    val first = (bytes.size - 65_557).coerceAtLeast(0)
    for (index in bytes.size - 22 downTo first) if (u32(bytes, index) == EndOfCentralDirectory) return index
    return null
}

private fun u16(bytes: ByteArray, offset: Int): Int =
    (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

private fun u32(bytes: ByteArray, offset: Int): Long? =
    if (offset < 0 || offset + 4 > bytes.size) null else
        (bytes[offset].toLong() and 0xff) or
            ((bytes[offset + 1].toLong() and 0xff) shl 8) or
            ((bytes[offset + 2].toLong() and 0xff) shl 16) or
            ((bytes[offset + 3].toLong() and 0xff) shl 24)

private fun ByteArray.startsWith(prefix: ByteArray): Boolean = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

private fun base64Decode(encoded: String): ByteArray {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val output = ArrayList<Byte>(encoded.length * 3 / 4)
    var bits = 0
    var bitCount = 0
    for (character in encoded) {
        if (character == '=') break
        val value = alphabet.indexOf(character)
        require(value >= 0) { "Invalid fixture Base64" }
        bits = (bits shl 6) or value
        bitCount += 6
        while (bitCount >= 8) {
            bitCount -= 8
            output += ((bits shr bitCount) and 0xff).toByte()
        }
    }
    return output.toByteArray()
}

private const val CentralDirectoryHeader = 0x02014b50L
private const val EndOfCentralDirectory = 0x06054b50L
