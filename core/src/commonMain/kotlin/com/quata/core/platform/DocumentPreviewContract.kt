package com.quata.core.platform

/**
 * Platform-neutral state for a document thumbnail/first-page preview.
 *
 * Renderers remain platform adapters: this contract deliberately carries no Bitmap, UIImage,
 * browser canvas or reader implementation. A [Ready] state only says an injected renderer can
 * draw the opaque [PlatformFile] supplied by [DocumentThumbnailPreview].
 */
sealed interface DocumentPreviewRenderState {
    data object Loading : DocumentPreviewRenderState
    data object Ready : DocumentPreviewRenderState
    data class Unavailable(val error: DocumentPreviewError) : DocumentPreviewRenderState
}

/** Stable failure categories shared presentation can localize without exposing adapter details. */
enum class DocumentPreviewError {
    Unsupported,
    Cancelled,
    RendererFailed,
}

/**
 * Result passed from a document thumbnail adapter to shared presentation.
 *
 * The [file] remains opaque to common UI. Each platform decodes it in an injected image slot.
 */
data class DocumentThumbnailPreview(
    val file: PlatformFile? = null,
    val renderState: DocumentPreviewRenderState,
)

fun PlatformResult<PlatformFile>.toDocumentThumbnailPreview(): DocumentThumbnailPreview = when (this) {
    is PlatformResult.Success -> DocumentThumbnailPreview(
        file = value,
        renderState = DocumentPreviewRenderState.Ready,
    )
    is PlatformResult.Failure -> DocumentThumbnailPreview(
        renderState = DocumentPreviewRenderState.Unavailable(DocumentPreviewError.RendererFailed),
    )
    PlatformResult.Cancelled -> DocumentThumbnailPreview(
        renderState = DocumentPreviewRenderState.Unavailable(DocumentPreviewError.Cancelled),
    )
    PlatformResult.Unsupported -> DocumentThumbnailPreview(
        renderState = DocumentPreviewRenderState.Unavailable(DocumentPreviewError.Unsupported),
    )
}

/** User intentions a shared document preview may expose to a platform host. */
sealed interface DocumentPreviewAction {
    data object Open : DocumentPreviewAction
    data object Retry : DocumentPreviewAction
}

/** Keeps retry/open availability consistent while hosts retain their actual reader implementations. */
object DocumentPreviewActions {
    fun availableFor(
        descriptor: DocumentPreviewDescriptor,
        renderState: DocumentPreviewRenderState,
    ): Set<DocumentPreviewAction> = when (renderState) {
        DocumentPreviewRenderState.Loading -> emptySet()
        DocumentPreviewRenderState.Ready -> if (descriptor.isPreviewable) setOf(DocumentPreviewAction.Open) else emptySet()
        is DocumentPreviewRenderState.Unavailable -> when (renderState.error) {
            DocumentPreviewError.RendererFailed,
            DocumentPreviewError.Cancelled,
            -> if (descriptor.isPreviewable) setOf(DocumentPreviewAction.Retry) else emptySet()

            DocumentPreviewError.Unsupported -> emptySet()
        }
    }
}
