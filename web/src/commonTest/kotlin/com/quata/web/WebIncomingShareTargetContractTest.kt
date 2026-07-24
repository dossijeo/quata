package com.quata.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class WebIncomingShareTargetContractTest {
    @Test
    fun `normalizes title text and url without blank separators`() {
        assertEquals(
            "A title\nA message\nhttps://egquata.com/post/42",
            WebIncomingShareTargetContract.normalizeText(
                title = "  A title  ",
                text = "\n A message \t",
                url = " https://egquata.com/post/42 ",
            ),
        )
        assertEquals("Only text", WebIncomingShareTargetContract.normalizeText(" ", "Only text", ""))
    }

    @Test
    fun `accepts exactly eight files at the worker byte boundary`() {
        val files = List(WebIncomingShareTargetContract.maxFiles) {
            WebIncomingShareFile(WebIncomingShareTargetContract.maxFileBytes)
        }

        assertIs<WebIncomingShareValidation.Accepted>(
            WebIncomingShareTargetContract.validate(text = "", files = files),
        )
    }

    @Test
    fun `rejects empty oversized and too-many incoming shares`() {
        assertIs<WebIncomingShareValidation.Empty>(
            WebIncomingShareTargetContract.validate(text = " ", files = emptyList()),
        )
        assertIs<WebIncomingShareValidation.TooManyFiles>(
            WebIncomingShareTargetContract.validate(
                text = "text",
                files = List(WebIncomingShareTargetContract.maxFiles + 1) { WebIncomingShareFile(0) },
            ),
        )
        assertIs<WebIncomingShareValidation.FileTooLarge>(
            WebIncomingShareTargetContract.validate(
                text = "text",
                files = listOf(WebIncomingShareFile(WebIncomingShareTargetContract.maxFileBytes + 1)),
            ),
        )
    }

    @Test
    fun `reconstructs persisted payload and identifies only created blob urls for discard`() {
        val payload = WebIncomingShareTargetContract.payloadOrNull(
            WebPersistedIncomingShare(
                id = " share-42 ",
                text = "  from IndexedDB ",
                attachments = listOf(
                    WebPersistedIncomingShareAttachment("blob:quata-file", "  ", " image/png "),
                    WebPersistedIncomingShareAttachment("https://cdn.example/file", "remote", null),
                    WebPersistedIncomingShareAttachment(" ", "ignored", "text/plain"),
                ),
            ),
        )

        requireNotNull(payload)
        assertEquals("share-42", payload.id)
        assertEquals("from IndexedDB", payload.text)
        assertEquals(listOf("attachment", "remote"), payload.attachments.map { it.name })
        assertEquals(listOf("blob:quata-file"), WebIncomingShareTargetContract.blobUrlsToRevoke(payload))
    }

    @Test
    fun `does not expose malformed persisted shares to the launcher`() {
        assertNull(
            WebIncomingShareTargetContract.payloadOrNull(
                WebPersistedIncomingShare(id = "", text = "text", attachments = emptyList()),
            ),
        )
        assertNull(
            WebIncomingShareTargetContract.payloadOrNull(
                WebPersistedIncomingShare(id = "share-empty", text = " ", attachments = emptyList()),
            ),
        )
    }
}
