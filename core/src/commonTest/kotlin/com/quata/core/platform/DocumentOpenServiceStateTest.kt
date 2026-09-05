package com.quata.core.platform

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DocumentOpenServiceStateTest {
    @Test
    fun successfulOpenReportsCommonOpeningAndPresentedStates() = runTest {
        val file = PlatformFile(
            reference = "https://cdn.quata.test/legal/privacy_es.docx",
            displayName = "privacy_es.docx",
            mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        )
        val service = RecordingDocumentOpenService(PlatformResult.Success(Unit))

        val result = service.openWithViewerState(file)

        assertEquals(DocumentPreviewKind.Office, result.started.descriptor.kind)
        assertEquals(file, result.started.file)
        val presented = assertIs<DocumentViewerState.Presented>(result.completed)
        assertEquals(DocumentPreviewKind.Office, presented.descriptor.kind)
        assertEquals(listOf(file), service.openedFiles)
    }

    @Test
    fun callbackOpenReportsTheSameCommonPresentedState() = runTest {
        val file = PlatformFile(
            reference = "https://cdn.quata.test/chat/brief.pdf",
            displayName = "brief.pdf",
            mimeType = "application/pdf",
        )
        val openedFiles = mutableListOf<PlatformFile>()

        val result = openPlatformDocumentWithViewerState(file = file, open = {
            openedFiles += it
            PlatformResult.Success(Unit)
        })

        assertEquals(DocumentPreviewKind.Pdf, result.started.descriptor.kind)
        val presented = assertIs<DocumentViewerState.Presented>(result.completed)
        assertEquals(file, presented.file)
        assertEquals(listOf(file), openedFiles)
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
    fun unsupportedFileCanUseExplicitPlatformFallback() = runTest {
        val file = PlatformFile(
            reference = "https://cdn.quata.test/archive.bin",
            displayName = "archive.bin",
            mimeType = "application/octet-stream",
        )
        val openedFiles = mutableListOf<PlatformFile>()

        val result = openPlatformDocumentWithViewerState(
            file = file,
            open = {
                openedFiles += it
                PlatformResult.Success(Unit)
            },
            allowPlatformFallbackForUnsupportedFormat = true,
        )

        assertEquals(DocumentPreviewKind.Unsupported, result.started.descriptor.kind)
        val presented = assertIs<DocumentViewerState.Presented>(result.completed)
        assertEquals(file, presented.file)
        assertEquals(listOf(file), openedFiles)
    }

    @Test
    fun platformExceptionMapsToCommonOpenFailure() = runTest {
        val file = PlatformFile("https://cdn.quata.test/legal/privacy.pdf", displayName = "privacy.pdf")

        val result = openPlatformDocumentWithViewerState(file = file, open = {
            error("boom")
        })

        val failed = assertIs<DocumentViewerState.Failed>(result.completed)
        assertEquals(DocumentViewerFailureReason.OpenFailed, failed.reason)
        assertEquals("boom", failed.detail)
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
