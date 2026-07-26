package com.quata.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DocumentPreviewContractTest {
    private val pdf = DocumentSupport.describe("https://cdn.quata.example/report.pdf")

    @Test
    fun thumbnailResultPreservesPortableFailureCategories() {
        assertEquals(
            DocumentPreviewError.RendererFailed,
            assertIs<DocumentPreviewRenderState.Unavailable>(
                PlatformResult.Failure("decoder-specific-detail").toDocumentThumbnailPreview().renderState,
            ).error,
        )
        assertEquals(
            DocumentPreviewError.Cancelled,
            assertIs<DocumentPreviewRenderState.Unavailable>(
                PlatformResult.Cancelled.toDocumentThumbnailPreview().renderState,
            ).error,
        )
        assertEquals(
            DocumentPreviewError.Unsupported,
            assertIs<DocumentPreviewRenderState.Unavailable>(
                PlatformResult.Unsupported.toDocumentThumbnailPreview().renderState,
            ).error,
        )
    }

    @Test
    fun actionsOnlyExposeSafeOpenOrRetryIntent() {
        assertEquals(
            setOf(DocumentPreviewAction.Open),
            DocumentPreviewActions.availableFor(pdf, DocumentPreviewRenderState.Ready),
        )
        assertEquals(
            setOf(DocumentPreviewAction.Retry),
            DocumentPreviewActions.availableFor(
                pdf,
                DocumentPreviewRenderState.Unavailable(DocumentPreviewError.RendererFailed),
            ),
        )
        assertTrue(
            DocumentPreviewActions.availableFor(
                pdf,
                DocumentPreviewRenderState.Unavailable(DocumentPreviewError.Unsupported),
            ).isEmpty(),
        )
    }
}
