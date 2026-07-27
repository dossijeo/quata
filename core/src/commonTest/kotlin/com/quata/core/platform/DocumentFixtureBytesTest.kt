package com.quata.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentFixtureBytesTest {
    @Test fun recognizesRealPortableFixtures() {
        assertEquals("pdf", documentFixtureKind(DocumentFixtureBytes.pdf))
        assertEquals("rtf", documentFixtureKind(DocumentFixtureBytes.rtf))
        assertEquals("docx", documentFixtureKind(DocumentFixtureBytes.docx))
        assertEquals("xlsx", documentFixtureKind(DocumentFixtureBytes.xlsx))
        assertEquals("pptx", documentFixtureKind(DocumentFixtureBytes.pptx))
    }

    @Test fun ooxmlFixturesContainActualOpcParts() {
        assertTrue(ooxmlCentralDirectoryEntries(DocumentFixtureBytes.docx)!!.containsAll(setOf("[Content_Types].xml", "_rels/.rels", "word/document.xml")))
        assertTrue(ooxmlCentralDirectoryEntries(DocumentFixtureBytes.xlsx)!!.containsAll(setOf("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/worksheets/sheet1.xml")))
        assertTrue(ooxmlCentralDirectoryEntries(DocumentFixtureBytes.pptx)!!.containsAll(setOf("[Content_Types].xml", "_rels/.rels", "ppt/presentation.xml", "ppt/_rels/presentation.xml.rels", "ppt/slides/slide1.xml")))
    }

    @Test fun rejectsZipPrefixAndCentralDirectoryOnlySpoofs() {
        assertNull(documentFixtureKind(byteArrayOf(0x50, 0x4b, 0x03, 0x04)))
        val centralOnly = DocumentFixtureBytes.docx.copyOfRange(DocumentFixtureBytes.docx.indexOfCentralDirectory(), DocumentFixtureBytes.docx.size)
        assertNull(ooxmlCentralDirectoryEntries(centralOnly))
        assertNull(documentFixtureKind(centralOnly))
    }

    @Test fun rejectsCorruptCrcAndInvalidUtf8InsteadOfTrustingMetadata() {
        val crcCorrupt = DocumentFixtureBytes.xlsx.copyOf()
        crcCorrupt[crcCorrupt.indexOf("Quata fixture".encodeToByteArray())] = 'X'.code.toByte()
        assertNull(ooxmlCentralDirectoryEntries(crcCorrupt))
        val nameCorrupt = DocumentFixtureBytes.docx.copyOf()
        val centralName = nameCorrupt.lastIndexOf("[Content_Types].xml".encodeToByteArray())
        assertTrue(centralName >= 0)
        nameCorrupt[centralName] = 0xff.toByte()
        assertNull(ooxmlCentralDirectoryEntries(nameCorrupt))
    }

    @Test fun rejectsMalformedXmlEvenWhenZipHeadersAndCrcAreCoherent() {
        val malformed = DocumentFixtureBytes.docx.copyOf()
        val localOffset = malformed.localEntryOffset("word/document.xml")
        val dataOffset = localOffset + 30 + malformed.u16At(localOffset + 26) + malformed.u16At(localOffset + 28)
        val dataSize = malformed.u32At(localOffset + 22).toInt()
        val bodyMarker = malformed.indexOf("<w:body>".encodeToByteArray(), dataOffset)
        assertTrue(bodyMarker >= dataOffset)
        malformed[bodyMarker + "<w:bod".length] = '!'.code.toByte() // closing tag remains w:body
        val crc = malformed.crc32ForTest(dataOffset, dataSize)
        malformed.putU32(localOffset + 14, crc)
        malformed.putU32(malformed.centralEntryOffset("word/document.xml") + 16, crc)
        assertNull(ooxmlCentralDirectoryEntries(malformed))
        assertNull(documentFixtureKind(malformed))
    }

    @Test fun canonicalMimeClassifiesFixturesRegardlessOfFilenameExtension() {
        val cases = listOf(
            "application/pdf" to DocumentPreviewKind.Pdf,
            "application/rtf" to DocumentPreviewKind.RichText,
            "text/rtf" to DocumentPreviewKind.RichText,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to DocumentPreviewKind.Office,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to DocumentPreviewKind.Office,
            "application/vnd.openxmlformats-officedocument.presentationml.presentation" to DocumentPreviewKind.Office,
        )
        cases.forEach { (mime, expected) -> assertEquals(expected, DocumentSupport.describe("https://files.quata.test/opaque-download", null, "$mime; charset=binary").kind) }
    }
}

private fun ByteArray.indexOfCentralDirectory(): Int { for (i in 0..size - 4) if ((this[i].toInt() and 255) == 0x50 && (this[i + 1].toInt() and 255) == 0x4b && (this[i + 2].toInt() and 255) == 1 && (this[i + 3].toInt() and 255) == 2) return i; return -1 }
private fun ByteArray.indexOf(needle: ByteArray, startAt: Int = 0): Int { for (offset in startAt..size - needle.size) if (needle.indices.all { this[offset + it] == needle[it] }) return offset; return -1 }
private fun ByteArray.lastIndexOf(needle: ByteArray): Int { for (offset in size - needle.size downTo 0) if (needle.indices.all { this[offset + it] == needle[it] }) return offset; return -1 }
private fun ByteArray.localEntryOffset(name: String): Int { val needle = name.encodeToByteArray(); for (offset in 0..size - 30) if (u32At(offset) == 0x04034b50L && u16At(offset + 26) == needle.size && needle.indices.all { this[offset + 30 + it] == needle[it] }) return offset; error("missing local $name") }
private fun ByteArray.centralEntryOffset(name: String): Int { val needle = name.encodeToByteArray(); for (offset in 0..size - 46) if (u32At(offset) == 0x02014b50L && u16At(offset + 28) == needle.size && needle.indices.all { this[offset + 46 + it] == needle[it] }) return offset; error("missing central $name") }
private fun ByteArray.u16At(offset: Int): Int = (this[offset].toInt() and 255) or ((this[offset + 1].toInt() and 255) shl 8)
private fun ByteArray.u32At(offset: Int): Long = (this[offset].toLong() and 255) or ((this[offset + 1].toLong() and 255) shl 8) or ((this[offset + 2].toLong() and 255) shl 16) or ((this[offset + 3].toLong() and 255) shl 24)
private fun ByteArray.putU32(offset: Int, value: Long) { repeat(4) { this[offset + it] = ((value ushr (it * 8)) and 255).toByte() } }
private fun ByteArray.crc32ForTest(offset: Int, size: Int): Long { var crc = 0xffffffffL; for (index in offset until offset + size) { crc = crc xor (this[index].toLong() and 255); repeat(8) { crc = if ((crc and 1L) != 0L) (crc ushr 1) xor 0xedb88320L else crc ushr 1 } }; return crc xor 0xffffffffL }
