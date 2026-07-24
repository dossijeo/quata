package com.quata.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocumentSupportTest {
    @Test
    fun classifiesExtensionsBeforeMimeLikeExistingPreviewPolicy() {
        val descriptor = DocumentSupport.describe(
            source = "content://quata/report.pdf",
            fileName = "report.pdf",
            mimeType = "text/plain",
        )

        assertEquals(DocumentPreviewKind.Pdf, descriptor.kind)
        assertTrue(descriptor.isPreviewable)
        assertFalse(descriptor.isTextLike)
    }

    @Test
    fun classifiesMimeWhenTheReferenceHasNoExtension() {
        val descriptor = DocumentSupport.describe(
            source = "content://quata/attachment",
            mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        )

        assertEquals(DocumentPreviewKind.Office, descriptor.kind)
        assertTrue(descriptor.isPreviewable)
    }
}
