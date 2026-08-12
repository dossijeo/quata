package com.quata.core.platform

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DocumentOpenServiceStateTest {
    @Test
    fun successfulOpenReportsCommonOpeningAndOpenedStates() = runTest {
        val file = PlatformFile(
            reference = "https://cdn.quata.test/legal/privacy_es.docx",
            displayName = "privacy_es.docx",
            mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        )
        val service = RecordingDocumentOpenService(PlatformResult.Success(Unit))

        val result = service.openWithViewerState(file)

        assertEquals(DocumentPreviewKind.Office, result.started.descriptor.kind)
        assertEquals(file, result.started.file)
        val opened = assertIs<DocumentViewerState.Opened>(result.completed)
        assertEquals(DocumentPreviewKind.Office, opened.descriptor.kind)
        assertEquals(listOf(file), service.openedFiles)
    }

    @Test
    fun unsupportedFileFailsBeforeCallingPlatform() = runTest {
        val file = PlatformFile(
            reference = "https://cdn.quata.test/archive.bin",
            displayName = "archive.bin",
            mimeType = "application/octet-stream",
        )
        val service = RecordingDocumentOpenService(PlatformResult.Success(Unit))

        val result = service.openWithViewerState(file)

        val failed = assertIs<DocumentViewerState.Failed>(result.completed)
        assertEquals(DocumentPreviewKind.Unsupported, failed.descriptor.kind)
        assertEquals(DocumentViewerFailureReason.UnsupportedFormat, failed.reason)
        assertEquals(emptyList(), service.openedFiles)
    }

    @Test
    fun platformResultsMapToCommonFailureReasons() = runTest {
        val file = PlatformFile("https://cdn.quata.test/legal/privacy.pdf", displayName = "privacy.pdf")
        val cases = listOf(
            PlatformResult.Unsupported to DocumentViewerFailureReason.PlatformUnsupported,
            PlatformResult.Cancelled to DocumentViewerFailureReason.Cancelled,
            PlatformResult.Failure("network") to DocumentViewerFailureReason.OpenFailed,
        )

        cases.forEach { (platformResult, expectedReason) ->
            val result = RecordingDocumentOpenService(platformResult).openWithViewerState(file)
            val failed = assertIs<DocumentViewerState.Failed>(result.completed)
            assertEquals(expectedReason, failed.reason)
        }
    }
}

private class RecordingDocumentOpenService(
    private val result: PlatformResult<Unit>,
) : DocumentOpenService {
    val openedFiles = mutableListOf<PlatformFile>()

    override suspend fun open(file: PlatformFile): PlatformResult<Unit> {
        openedFiles += file
        return result
    }
}
