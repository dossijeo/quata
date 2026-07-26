package com.quata.feature.externalshare

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ExternalShareInboxContractTest {
    @Test
    fun `accepts bounded manifest and resolves only claimed file names`() {
        val result = persistedExternalSharePayload(
            PersistedExternalShare(
                id = "share-123",
                text = " hello ",
                attachments = listOf(PersistedExternalShareAttachment("asset.pdf", "report.pdf", "application/pdf")),
            ),
            attachmentUri = { "file:///claimed/$it" },
        )

        val accepted = assertIs<PersistedExternalShareResult.Accepted>(result)
        assertEquals("hello", accepted.payload.text)
        assertEquals("file:///claimed/asset.pdf", accepted.payload.attachments.single().uri)
    }

    @Test
    fun `rejects traversal and excessive files`() {
        assertEquals(
            PersistedExternalShareResult.Invalid,
            persistedExternalSharePayload(
                PersistedExternalShare("share-1", "", listOf(PersistedExternalShareAttachment("../secret", "secret.pdf", "application/pdf"))),
                attachmentUri = { it },
            ),
        )
        assertEquals(
            PersistedExternalShareResult.TooManyFiles,
            persistedExternalSharePayload(
                PersistedExternalShare(
                    "share-2",
                    "",
                    List(MaxExternalShareFiles + 1) { PersistedExternalShareAttachment("$it.pdf", "$it.pdf", "application/pdf") },
                ),
                attachmentUri = { it },
            ),
        )
        assertEquals(
            PersistedExternalShareResult.Invalid,
            persistedExternalSharePayload(
                PersistedExternalShare("x".repeat(MaxExternalShareIdChars + 1), "text", emptyList()),
                attachmentUri = { it },
            ),
        )
        assertEquals(
            PersistedExternalShareResult.Invalid,
            persistedExternalSharePayload(
                PersistedExternalShare(
                    "share-3",
                    "",
                    listOf(PersistedExternalShareAttachment("asset.pdf", "bad\nname.pdf", "application/pdf")),
                ),
                attachmentUri = { it },
            ),
        )
    }

    @Test
    fun `does not manufacture an empty or unsupported payload`() {
        assertEquals(
            PersistedExternalShareResult.Empty,
            persistedExternalSharePayload(PersistedExternalShare("share-1", " ", emptyList()), attachmentUri = { it }),
        )
        assertEquals(
            PersistedExternalShareResult.Unsupported,
            persistedExternalSharePayload(
                PersistedExternalShare("share-2", "", listOf(PersistedExternalShareAttachment("archive.bin", "archive.bin", "application/octet-stream"))),
                attachmentUri = { it },
            ),
        )
    }
}
