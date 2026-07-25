package com.quata.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BrowserDocumentOpenPolicyTest {
    @Test
    fun pdfBlobUsesSafeDownloadWhileRemotePdfCanUseViewer() {
        assertEquals("view", BrowserDocumentOpenPolicy.actionFor(DocumentPreviewKind.Pdf, "https://cdn.example/report.pdf"))
        assertEquals("download", BrowserDocumentOpenPolicy.actionFor(DocumentPreviewKind.Pdf, " blob:https://app.example/id "))
        assertEquals("download", BrowserDocumentOpenPolicy.actionFor(DocumentPreviewKind.Office, "https://cdn.example/report.docx"))
        assertEquals(null, BrowserDocumentOpenPolicy.actionFor(DocumentPreviewKind.Unsupported, "https://cdn.example/file.bin"))
    }

    @Test
    fun downloadNameRemovesPathAndControlCharacters() {
        assertEquals("quarterly_report_docx", BrowserDocumentOpenPolicy.downloadName(" quarterly/report\\docx\u0000 "))
    }

    @Test
    fun downloadNameUsesStableFallbackForMissingOrEmptyNames() {
        assertEquals("document", BrowserDocumentOpenPolicy.downloadName(null))
        assertEquals("document", BrowserDocumentOpenPolicy.downloadName("\u0000\u0001"))
    }

    @Test
    fun downloadNameHasBoundedLength() {
        assertEquals(180, BrowserDocumentOpenPolicy.downloadName("x".repeat(200)).length)
    }

    @Test
    fun documentReferenceNormalizerAllowsHttpAndSameOriginBlobCapabilities() {
        assertEquals(
            "https://cdn.quata.example/files/report.pdf",
            browserDocumentReferenceOrNull(" https://cdn.quata.example/files/report.pdf ", "https://web.quata.example"),
        )
        assertEquals(
            "blob:https://web.quata.example/document-id",
            browserDocumentReferenceOrNull("blob:https://web.quata.example/document-id", "https://web.quata.example"),
        )
    }

    @Test
    fun documentReferenceNormalizerRejectsExecutableSchemesAndForeignBlobCapabilities() {
        assertNull(browserDocumentReferenceOrNull("javascript:alert(1)", "https://web.quata.example"))
        assertNull(browserDocumentReferenceOrNull("data:application/pdf;base64,ZmFrZQ==", "https://web.quata.example"))
        assertNull(browserDocumentReferenceOrNull("blob:https://other.example/document-id", "https://web.quata.example"))
    }
}
