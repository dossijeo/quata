package com.quata.core.platform

/** Opens a selected or remote document through the platform's real document handling surface. */
interface DocumentOpenService {
    suspend fun open(file: PlatformFile): PlatformResult<Unit>
}

object UnsupportedDocumentOpenService : DocumentOpenService {
    override suspend fun open(file: PlatformFile): PlatformResult<Unit> = PlatformResult.Unsupported
}

enum class DocumentViewerFailureReason {
    Cancelled,
    UnsupportedFormat,
    PlatformUnsupported,
    OpenFailed,
}

sealed interface DocumentViewerState {
    data object Idle : DocumentViewerState
    data class Opening(
        val file: PlatformFile,
        val descriptor: DocumentPreviewDescriptor,
    ) : DocumentViewerState

    data class Opened(
        val file: PlatformFile,
        val descriptor: DocumentPreviewDescriptor,
    ) : DocumentViewerState

    data class Failed(
        val file: PlatformFile,
        val descriptor: DocumentPreviewDescriptor,
        val reason: DocumentViewerFailureReason,
        val detail: String? = null,
    ) : DocumentViewerState
}

data class DocumentViewerOpenResult(
    val started: DocumentViewerState.Opening,
    val completed: DocumentViewerState,
)

suspend fun DocumentOpenService.openWithViewerState(file: PlatformFile): DocumentViewerOpenResult {
    val descriptor = DocumentSupport.describe(file.reference, file.displayName, file.mimeType)
    val started = DocumentViewerState.Opening(file = file, descriptor = descriptor)
    if (!descriptor.isPreviewable) {
        return DocumentViewerOpenResult(
            started = started,
            completed = DocumentViewerState.Failed(
                file = file,
                descriptor = descriptor,
                reason = DocumentViewerFailureReason.UnsupportedFormat,
            ),
        )
    }
    return DocumentViewerOpenResult(
        started = started,
        completed = when (val result = open(file)) {
            is PlatformResult.Success -> DocumentViewerState.Opened(file, descriptor)
            PlatformResult.Cancelled -> DocumentViewerState.Failed(
                file = file,
                descriptor = descriptor,
                reason = DocumentViewerFailureReason.Cancelled,
            )
            PlatformResult.Unsupported -> DocumentViewerState.Failed(
                file = file,
                descriptor = descriptor,
                reason = DocumentViewerFailureReason.PlatformUnsupported,
            )
            is PlatformResult.Failure -> DocumentViewerState.Failed(
                file = file,
                descriptor = descriptor,
                reason = DocumentViewerFailureReason.OpenFailed,
                detail = result.reason,
            )
        },
    )
}
