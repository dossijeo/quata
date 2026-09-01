package com.quata.core.platform

import kotlin.coroutines.cancellation.CancellationException

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

    data class Presented(
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

fun documentViewerOpeningState(file: PlatformFile): DocumentViewerState.Opening =
    DocumentViewerState.Opening(
        file = file,
        descriptor = DocumentSupport.describe(file.reference, file.displayName, file.mimeType),
    )

suspend fun DocumentOpenService.openWithViewerState(file: PlatformFile): DocumentViewerOpenResult {
    return openPlatformDocumentWithViewerState(file = file, open = { open(it) })
}

suspend fun openPlatformDocumentWithViewerState(
    file: PlatformFile,
    open: suspend (PlatformFile) -> PlatformResult<Unit>,
    allowPlatformFallbackForUnsupportedFormat: Boolean = false,
): DocumentViewerOpenResult {
    val started = documentViewerOpeningState(file)
    val descriptor = started.descriptor
    if (!descriptor.isPreviewable && !allowPlatformFallbackForUnsupportedFormat) {
        return DocumentViewerOpenResult(
            started = started,
            completed = DocumentViewerState.Failed(
                file = file,
                descriptor = descriptor,
                reason = DocumentViewerFailureReason.UnsupportedFormat,
            ),
        )
    }
    val platformResult = try {
        open(file)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        PlatformResult.Failure(error.message ?: "document_open_failed")
    }
    return DocumentViewerOpenResult(
        started = started,
        completed = when (val result = platformResult) {
            is PlatformResult.Success -> DocumentViewerState.Presented(file, descriptor)
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
