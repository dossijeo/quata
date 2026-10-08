package com.quata.feature.chat.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosChatAttachmentDownloaderCompletionTest {
    @Test
    fun httpStatusMatrixFailsClosedWithTheExactStatus() {
        listOf(401, 403, 404, 408, 429, 500, 503).forEach { status ->
            assertEquals(
                "ios_chat_attachment_http_$status",
                completionFailure(statusCode = status),
            )
        }
    }

    @Test
    fun redirectTransportAndMissingResponseRemainDistinct() {
        assertEquals(
            "ios_chat_attachment_redirect_rejected",
            completionFailure(redirectRejected = true),
        )
        assertEquals(
            "timed out",
            completionFailure(transportError = "timed out"),
        )
        assertEquals(
            "ios_chat_attachment_response_missing",
            completionFailure(responsePresent = false),
        )
    }

    @Test
    fun emptyDeclaredOversizeAndStreamedOversizeFailClosed() {
        assertEquals(
            "ios_chat_attachment_size_invalid",
            completionFailure(bytesReceived = 0),
        )
        assertEquals(
            "ios_chat_attachment_size_invalid",
            completionFailure(declaredLength = 50L * 1024L * 1024L + 1L),
        )
        assertEquals(
            "ios_chat_attachment_size_invalid",
            completionFailure(bytesReceived = 50L * 1024L * 1024L + 1L),
        )
    }

    @Test
    fun successfulBoundedResponseHasNoFailureReason() {
        assertNull(completionFailure())
        assertNull(completionFailure(statusCode = 299, declaredLength = -1, bytesReceived = 7))
    }

    @Test
    fun earlierTransportGuardTakesPrecedenceOverHttpMetadata() {
        assertEquals(
            "ios_chat_attachment_size_invalid",
            completionFailure(
                terminalReason = "ios_chat_attachment_size_invalid",
                redirectRejected = true,
                transportError = "connection reset",
                statusCode = 503,
            ),
        )
    }

    private fun completionFailure(
        terminalReason: String? = null,
        redirectRejected: Boolean = false,
        transportError: String? = null,
        responsePresent: Boolean = true,
        statusCode: Int? = 200,
        declaredLength: Long = 7,
        bytesReceived: Long = 7,
    ): String? = iosChatAttachmentCompletionFailureReason(
        terminalReason = terminalReason,
        redirectRejected = redirectRejected,
        transportError = transportError,
        responsePresent = responsePresent,
        statusCode = statusCode,
        declaredLength = declaredLength,
        bytesReceived = bytesReceived,
    )
}
