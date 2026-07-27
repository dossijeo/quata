package com.quata.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertIs

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

    @Test
    fun inertFixtureFormatMatrixKeepsQuickLookAndBrowserFallbackDistinct() {
        val fixtures = listOf("fixture.pdf" to "application/pdf", "fixture.docx" to null, "fixture.pptx" to null, "fixture.xlsx" to null, "fixture.rtf" to "text/rtf")
        fixtures.forEach { (name, mime) ->
            val file = PlatformFile("https://fixtures.invalid/$name", name, mime)
            assertIs<DocumentPreviewAdmission.Open>(DocumentPreviewAdmissions.admit(file, DocumentPreviewAdmissions.QuickLook))
            val browser = DocumentPreviewAdmissions.admit(file, DocumentPreviewAdmissions.BrowserFallback)
            if (name.endsWith("pdf")) assertIs<DocumentPreviewAdmission.Open>(browser) else assertIs<DocumentPreviewAdmission.Download>(browser)
        }
    }
}
