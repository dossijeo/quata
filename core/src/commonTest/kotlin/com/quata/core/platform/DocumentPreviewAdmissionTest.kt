package com.quata.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DocumentPreviewAdmissionTest {
    @Test
    fun quickLookAdmitsPdfRtfAndOfficeForOpen() {
        listOf("report.pdf", "notes.rtf", "plan.docx", "budget.xlsx", "brief.pptx").forEach { name ->
            val admission = DocumentPreviewAdmissions.admit(
                file = PlatformFile(reference = "file:///sandbox/$name", displayName = name),
                capabilities = DocumentPreviewAdmissions.QuickLook,
            )
            assertIs<DocumentPreviewAdmission.Open>(admission, name)
        }
    }

    @Test
    fun browserFallbackDistinguishesOpenDownloadAndUnavailable() {
        val pdf = DocumentPreviewAdmissions.admit(
            PlatformFile("https://cdn.quata.example/report.pdf", "report.pdf"),
            DocumentPreviewAdmissions.BrowserFallback,
        )
        val office = DocumentPreviewAdmissions.admit(
            PlatformFile("https://cdn.quata.example/report.docx", "report.docx"),
            DocumentPreviewAdmissions.BrowserFallback,
        )
        val unsupported = DocumentPreviewAdmissions.admit(
            PlatformFile("https://cdn.quata.example/photo.jpg", "photo.jpg"),
            DocumentPreviewAdmissions.BrowserFallback,
        )
        val unavailableOnPdfOnlyHost = DocumentPreviewAdmissions.admit(
            PlatformFile("https://cdn.quata.example/report.docx", "report.docx"),
            DocumentPreviewCapabilities(openKinds = setOf(DocumentPreviewKind.Pdf)),
        )

        assertIs<DocumentPreviewAdmission.Open>(pdf)
        assertIs<DocumentPreviewAdmission.Download>(office)
        assertEquals(
            DocumentPreviewAdmissionReason.UnsupportedFormat,
            assertIs<DocumentPreviewAdmission.Unavailable>(unsupported).reason,
        )
        assertEquals(
            DocumentPreviewAdmissionReason.PlatformUnsupported,
            assertIs<DocumentPreviewAdmission.Unavailable>(unavailableOnPdfOnlyHost).reason,
        )
    }
}
