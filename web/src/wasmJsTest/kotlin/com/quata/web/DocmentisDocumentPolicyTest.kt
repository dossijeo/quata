package com.quata.web

import com.quata.core.platform.PlatformFile
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocmentisDocumentPolicyTest {
    @Test
    fun accepts_only_formats_advertised_by_the_pinned_viewer() {
        assertTrue(DocmentisDocumentPolicy.supports(PlatformFile("https://cdn.example/report.pdf")))
        assertTrue(DocmentisDocumentPolicy.supports(PlatformFile("https://cdn.example/report.docx")))
        assertTrue(DocmentisDocumentPolicy.supports(PlatformFile("https://cdn.example/deck.pptx")))
        assertTrue(DocmentisDocumentPolicy.supports(PlatformFile("https://cdn.example/data.xlsx")))
    }

    @Test
    fun leaves_legacy_office_and_rich_text_to_the_safe_browser_fallback() {
        assertFalse(DocmentisDocumentPolicy.supports(PlatformFile("https://cdn.example/legacy.doc")))
        assertFalse(DocmentisDocumentPolicy.supports(PlatformFile("https://cdn.example/legacy.xls")))
        assertFalse(DocmentisDocumentPolicy.supports(PlatformFile("https://cdn.example/legacy.ppt")))
        assertFalse(DocmentisDocumentPolicy.supports(PlatformFile("https://cdn.example/letter.rtf")))
    }
}
