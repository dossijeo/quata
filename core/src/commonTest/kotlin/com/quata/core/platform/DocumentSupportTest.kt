package com.quata.core.platform

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocumentSupportTest {
    @Test
    fun recognisesDocumentsByFileNameAndMimeType() {
        assertTrue(DocumentSupport.canPreview("https://cdn.quata.com/report.pdf"))
        assertTrue(DocumentSupport.canPreview("", "brief.DOCX", null))
        assertTrue(DocumentSupport.canPreview("", null, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        assertTrue(DocumentSupport.canPreview("", null, "text/plain; charset=utf-8"))
    }

    @Test
    fun rejectsUnsupportedBinaryAttachments() {
        assertFalse(DocumentSupport.canPreview("https://cdn.quata.com/archive.zip", null, "application/zip"))
    }
}
