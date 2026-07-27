package com.quata.core.platform

/**
 * Small, self-owned documents that every Kotlin target can inspect without filesystem fixtures.
 *
 * The Office files are actual deterministic OPC packages: every part is ZIP_STORED, every local
 * header has a matching central-directory record and CRC, and the XML/rels are the minimum package
 * structures accepted by normal document consumers.  They are deliberately generated here rather
 * than being opaque Base64 blobs, so the contract is reviewable and portable to Wasm/iOS.
 */
internal object DocumentFixtureBytes {
    val pdf: ByteArray = pdfFixture()
    val rtf: ByteArray = "{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0 Arial;}}\\f0\\fs24 Quata fixture\\par}".encodeToByteArray()

    val docx: ByteArray = opcPackage(
        "[Content_Types].xml" to contentTypes(
            "word/document.xml" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml",
        ),
        "_rels/.rels" to rootRelationships("word/document.xml"),
        "word/document.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>Quata fixture</w:t></w:r></w:p><w:sectPr/></w:body></w:document>""",
    )

    val xlsx: ByteArray = opcPackage(
        "[Content_Types].xml" to contentTypes(
            "xl/workbook.xml" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml",
            "xl/worksheets/sheet1.xml" to "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml",
        ),
        "_rels/.rels" to rootRelationships("xl/workbook.xml"),
        "xl/workbook.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Quata" sheetId="1" r:id="rId1"/></sheets></workbook>""",
        "xl/_rels/workbook.xml.rels" to relationships("worksheet", "worksheets/sheet1.xml"),
        "xl/worksheets/sheet1.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>Quata fixture</t></is></c></row></sheetData></worksheet>""",
    )

    val pptx: ByteArray = opcPackage(
        "[Content_Types].xml" to contentTypes(
            "ppt/presentation.xml" to "application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml",
            "ppt/slides/slide1.xml" to "application/vnd.openxmlformats-officedocument.presentationml.slide+xml",
        ),
        "_rels/.rels" to rootRelationships("ppt/presentation.xml"),
        "ppt/presentation.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:sldIdLst><p:sldId id="256" r:id="rId1"/></p:sldIdLst><p:sldSz cx="9144000" cy="6858000" type="screen4x3"/><p:notesSz cx="6858000" cy="9144000"/></p:presentation>""",
        "ppt/_rels/presentation.xml.rels" to relationships("slide", "slides/slide1.xml"),
        "ppt/slides/slide1.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>""",
    )
}

/** A parser for the fixture contract, not a general ZIP implementation. */
internal fun documentFixtureKind(bytes: ByteArray): String? = when {
    isValidPdf(bytes) -> "pdf"
    isValidRtf(bytes) -> "rtf"
    else -> ooxmlCentralDirectoryEntries(bytes)?.let { entries ->
        when {
            entries.containsAll(setOf("[Content_Types].xml", "_rels/.rels", "word/document.xml")) -> "docx"
            entries.containsAll(setOf("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml")) -> "xlsx"
            entries.containsAll(setOf("[Content_Types].xml", "_rels/.rels", "ppt/presentation.xml")) -> "pptx"
            else -> "zip"
        }
    }
}

/**
 * Accept only a self-consistent, single-disk ZIP_STORED package.  In particular, central-directory
 * names alone are not evidence of a fixture: each referenced local record, data span and CRC must
 * match, and every XML part must decode as strict UTF-8 and be structurally well formed.
 */
internal fun ooxmlCentralDirectoryEntries(bytes: ByteArray): Set<String>? {
    val eocd = findEocd(bytes) ?: return null
    if (u16(bytes, eocd + 4) != 0 || u16(bytes, eocd + 6) != 0 || u16(bytes, eocd + 8) != u16(bytes, eocd + 10)) return null
    val count = u16(bytes, eocd + 10)
    val directorySize = u32(bytes, eocd + 12) ?: return null
    val directoryOffset = u32(bytes, eocd + 16) ?: return null
    if (count == 0 || count > 128 || directoryOffset + directorySize != eocd.toLong()) return null

    var cursor = directoryOffset.toInt()
    val entries = linkedSetOf<String>()
    repeat(count) {
        if (!hasRange(bytes, cursor, 46) || u32(bytes, cursor) != CentralDirectoryHeader) return null
        val method = u16(bytes, cursor + 10)
        val crc = u32(bytes, cursor + 16) ?: return null
        val compressedSize = u32(bytes, cursor + 20) ?: return null
        val uncompressedSize = u32(bytes, cursor + 24) ?: return null
        val nameSize = u16(bytes, cursor + 28)
        val extraSize = u16(bytes, cursor + 30)
        val commentSize = u16(bytes, cursor + 32)
        val localOffset = u32(bytes, cursor + 42) ?: return null
        val recordSize = 46 + nameSize + extraSize + commentSize
        if (method != 0 || compressedSize != uncompressedSize || !hasRange(bytes, cursor, recordSize)) return null
        val name = strictUtf8(bytes, cursor + 46, nameSize) ?: return null
        if (!validPartName(name) || !entries.add(name)) return null
        if (!validLocalEntry(bytes, localOffset, name, crc, uncompressedSize)) return null
        cursor += recordSize
    }
    if (cursor != eocd) return null
    return entries
}

private fun validLocalEntry(bytes: ByteArray, offsetLong: Long, centralName: String, crc: Long, size: Long): Boolean {
    if (offsetLong > Int.MAX_VALUE) return false
    val offset = offsetLong.toInt()
    if (!hasRange(bytes, offset, 30) || u32(bytes, offset) != LocalFileHeader || u16(bytes, offset + 8) != 0) return false
    val localCrc = u32(bytes, offset + 14) ?: return false
    val compressedSize = u32(bytes, offset + 18) ?: return false
    val uncompressedSize = u32(bytes, offset + 22) ?: return false
    val nameSize = u16(bytes, offset + 26)
    val extraSize = u16(bytes, offset + 28)
    if (localCrc != crc || compressedSize != size || uncompressedSize != size) return false
    val localName = strictUtf8(bytes, offset + 30, nameSize) ?: return false
    if (localName != centralName) return false
    val dataOffset = offset + 30 + nameSize + extraSize
    if (size > Int.MAX_VALUE || !hasRange(bytes, dataOffset, size.toInt())) return false
    val data = bytes.copyOfRange(dataOffset, dataOffset + size.toInt())
    if (crc32(data) != crc) return false
    return !centralName.endsWith(".xml") || isWellFormedXml(strictUtf8(data, 0, data.size) ?: return false)
}

private fun isValidPdf(bytes: ByteArray): Boolean {
    if (!bytes.startsWith("%PDF-".encodeToByteArray())) return false
    val text = strictUtf8(bytes, 0, bytes.size) ?: return false
    val marker = "startxref\n"
    val index = text.lastIndexOf(marker)
    if (index < 0 || !text.endsWith("%%EOF\n")) return false
    val offset = text.substring(index + marker.length, text.length - "%%EOF\n".length).trim().toIntOrNull() ?: return false
    return offset in bytes.indices && text.substring(offset).startsWith("xref\n")
}

private fun isValidRtf(bytes: ByteArray): Boolean = strictUtf8(bytes, 0, bytes.size)?.let { it.startsWith("{\\rtf") && it.endsWith('}') } == true

private fun isWellFormedXml(xml: String): Boolean {
    if (!xml.startsWith("<?xml") || !xml.contains("?>")) return false
    val stack = ArrayList<String>()
    var i = 0
    while (i < xml.length) {
        val start = xml.indexOf('<', i)
        if (start < 0) return stack.isEmpty()
        if (start > i && xml.substring(i, start).contains('&') && !xml.substring(i, start).contains("&amp;")) return false
        when {
            xml.startsWith("<?", start) -> { val end = xml.indexOf("?>", start + 2); if (end < 0) return false; i = end + 2 }
            xml.startsWith("<!--", start) -> { val end = xml.indexOf("-->", start + 4); if (end < 0) return false; i = end + 3 }
            xml.startsWith("<![CDATA[", start) -> { val end = xml.indexOf("]]>", start + 9); if (end < 0) return false; i = end + 3 }
            else -> {
                val end = xml.indexOf('>', start + 1); if (end < 0) return false
                val raw = xml.substring(start + 1, end).trim()
                if (raw.startsWith('/')) {
                    val name = raw.drop(1).trim()
                    if (name.isEmpty() || stack.isEmpty() || stack.removeAt(stack.lastIndex) != name) return false
                } else if (!raw.endsWith('/')) {
                    val name = raw.takeWhile { !it.isWhitespace() && it != '/' }
                    if (name.isEmpty() || raw.count { it == '"' } % 2 != 0 || raw.count { it == '\'' } % 2 != 0) return false
                    stack += name
                }
                i = end + 1
            }
        }
    }
    return stack.isEmpty()
}

private fun pdfFixture(): ByteArray {
    val one = "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n"
    val two = "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n"
    val three = "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>\nendobj\n"
    val header = "%PDF-1.4\n"
    val offsets = intArrayOf(header.length, header.length + one.length, header.length + one.length + two.length)
    val xrefOffset = header.length + one.length + two.length + three.length
    return (header + one + two + three + "xref\n0 4\n0000000000 65535 f \n" + offsets.joinToString("") { it.toString().padStart(10, '0') + " 00000 n \n" } + "trailer\n<< /Size 4 /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n").encodeToByteArray()
}

private fun contentTypes(vararg overrides: Pair<String, String>): String = buildString {
    append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/>")
    overrides.forEach { (part, contentType) -> append("<Override PartName=\"/$part\" ContentType=\"$contentType\"/>") }
    append("</Types>")
}

private fun rootRelationships(target: String): String = relationships("officeDocument", target)
private fun relationships(type: String, target: String): String = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/$type\" Target=\"$target\"/></Relationships>"

private fun opcPackage(vararg parts: Pair<String, String>): ByteArray {
    val output = ByteArrayBuilder()
    val records = parts.map { (name, content) ->
        val nameBytes = name.encodeToByteArray()
        val data = content.encodeToByteArray()
        val crc = crc32(data)
        val offset = output.size
        output.u32(LocalFileHeader); output.u16(20); output.u16(0); output.u16(0); output.u16(0); output.u16(0)
        output.u32(crc); output.u32(data.size.toLong()); output.u32(data.size.toLong()); output.u16(nameBytes.size); output.u16(0); output.bytes(nameBytes); output.bytes(data)
        ZipRecord(nameBytes, data, crc, offset)
    }
    val centralOffset = output.size
    records.forEach { record ->
        output.u32(CentralDirectoryHeader); output.u16(20); output.u16(20); output.u16(0); output.u16(0); output.u16(0); output.u16(0)
        output.u32(record.crc); output.u32(record.data.size.toLong()); output.u32(record.data.size.toLong()); output.u16(record.name.size); output.u16(0); output.u16(0); output.u16(0); output.u16(0); output.u32(0); output.u32(record.offset.toLong()); output.bytes(record.name)
    }
    val centralSize = output.size - centralOffset
    output.u32(EndOfCentralDirectory); output.u16(0); output.u16(0); output.u16(records.size); output.u16(records.size); output.u32(centralSize.toLong()); output.u32(centralOffset.toLong()); output.u16(0)
    return output.toByteArray()
}

private data class ZipRecord(val name: ByteArray, val data: ByteArray, val crc: Long, val offset: Int)
private class ByteArrayBuilder { private val values = ArrayList<Byte>(); val size get() = values.size; fun u16(value: Int) { values += (value and 0xff).toByte(); values += ((value ushr 8) and 0xff).toByte() }; fun u32(value: Long) { repeat(4) { values += ((value ushr (it * 8)) and 0xff).toByte() } }; fun bytes(value: ByteArray) { value.forEach { values += it } }; fun toByteArray() = values.toByteArray() }

private fun crc32(bytes: ByteArray): Long { var crc = 0xffffffffL; bytes.forEach { byte -> crc = crc xor (byte.toLong() and 0xff); repeat(8) { crc = if ((crc and 1L) != 0L) (crc ushr 1) xor 0xedb88320L else crc ushr 1 } }; return crc xor 0xffffffffL }
private fun findEocd(bytes: ByteArray): Int? { val first = (bytes.size - 65_557).coerceAtLeast(0); for (index in bytes.size - 22 downTo first) if (u32(bytes, index) == EndOfCentralDirectory) return index; return null }
private fun strictUtf8(bytes: ByteArray, offset: Int, count: Int): String? = if (hasRange(bytes, offset, count)) runCatching { bytes.copyOfRange(offset, offset + count).decodeToString(throwOnInvalidSequence = true) }.getOrNull() else null
private fun validPartName(name: String): Boolean = name.isNotEmpty() && !name.startsWith('/') && !name.contains("..") && !name.contains('\\')
private fun hasRange(bytes: ByteArray, offset: Int, count: Int): Boolean = offset >= 0 && count >= 0 && offset <= bytes.size - count
private fun u16(bytes: ByteArray, offset: Int): Int = if (hasRange(bytes, offset, 2)) (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8) else -1
private fun u32(bytes: ByteArray, offset: Int): Long? = if (!hasRange(bytes, offset, 4)) null else (bytes[offset].toLong() and 0xff) or ((bytes[offset + 1].toLong() and 0xff) shl 8) or ((bytes[offset + 2].toLong() and 0xff) shl 16) or ((bytes[offset + 3].toLong() and 0xff) shl 24)
private fun ByteArray.startsWith(prefix: ByteArray): Boolean = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
private const val LocalFileHeader = 0x04034b50L
private const val CentralDirectoryHeader = 0x02014b50L
private const val EndOfCentralDirectory = 0x06054b50L
