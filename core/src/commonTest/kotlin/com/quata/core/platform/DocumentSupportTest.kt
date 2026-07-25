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

    @Test
    fun thumbnailPolicyAcceptsPdfRtfAndOfficeButNotTextOnlyDocuments() {
        val supported = listOf(
            PlatformFile("file:///tmp/report.pdf", "report.pdf", "application/pdf"),
            PlatformFile("/tmp/notes.rtf", "notes.rtf", "text/rtf"),
            PlatformFile("file:///tmp/roadmap.docx", "roadmap.docx"),
            PlatformFile("file:///tmp/budget.xlsx", "budget.xlsx"),
            PlatformFile("file:///tmp/brief.pptx", "brief.pptx"),
        )

        supported.forEach { document -> assertTrue(DocumentThumbnailSupport.supports(document), document.reference) }
        assertFalse(DocumentThumbnailSupport.supports(PlatformFile("file:///tmp/readme.txt", "readme.txt", "text/plain")))
        assertFalse(DocumentThumbnailSupport.supports(PlatformFile("file:///tmp/photo.jpg", "photo.jpg", "image/jpeg")))
    }

    @Test
    fun thumbnailPolicyOnlyAdmitsLocalReferences() {
        assertTrue(DocumentThumbnailSupport.hasLocalReference(PlatformFile("file:///tmp/report.pdf")))
        assertTrue(DocumentThumbnailSupport.hasLocalReference(PlatformFile("/tmp/report.pdf")))
        assertFalse(DocumentThumbnailSupport.hasLocalReference(PlatformFile("file://")))
        assertFalse(DocumentThumbnailSupport.hasLocalReference(PlatformFile("https://cdn.quata.example/report.pdf")))
        assertFalse(DocumentThumbnailSupport.hasLocalReference(PlatformFile("content://quata/report.pdf")))
    }
}
