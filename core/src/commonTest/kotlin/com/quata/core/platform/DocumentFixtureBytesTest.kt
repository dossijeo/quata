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

    @Test fun ooxmlFixturesContainRequiredPackageParts() {
        assertTrue(ooxmlCentralDirectoryEntries(DocumentFixtureBytes.docx)!!.containsAll(setOf("[Content_Types].xml", "_rels/.rels", "word/document.xml")))
        assertTrue(ooxmlCentralDirectoryEntries(DocumentFixtureBytes.xlsx)!!.containsAll(setOf("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml")))
        assertTrue(ooxmlCentralDirectoryEntries(DocumentFixtureBytes.pptx)!!.containsAll(setOf("[Content_Types].xml", "_rels/.rels", "ppt/presentation.xml")))
    }

    @Test fun rejectsZipPrefixWithoutACentralDirectory() {
        assertNull(documentFixtureKind(byteArrayOf(0x50, 0x4b, 0x03, 0x04)))
    }

    @Test fun rejectsCentralDirectoryWithANonUtf8PartNameWithoutThrowing() {
        val corrupt = DocumentFixtureBytes.docx.copyOf()
        val partName = "[Content_Types].xml".encodeToByteArray()
        val centralDirectoryName = corrupt.lastIndexOf(partName)
        assertTrue(centralDirectoryName >= 0)
        corrupt[centralDirectoryName] = 0xff.toByte()
        assertNull(ooxmlCentralDirectoryEntries(corrupt))
        assertNull(documentFixtureKind(corrupt))
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
        cases.forEach { (mime, expected) ->
            assertEquals(expected, DocumentSupport.describe("https://files.quata.test/opaque-download", null, "$mime; charset=binary").kind)
        }
    }
}

private fun ByteArray.lastIndexOf(needle: ByteArray): Int {
    for (offset in size - needle.size downTo 0) {
        if (needle.indices.all { this[offset + it] == needle[it] }) return offset
    }
    return -1
}
