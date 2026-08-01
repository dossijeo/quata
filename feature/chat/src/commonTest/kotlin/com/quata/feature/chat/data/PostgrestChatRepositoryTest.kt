package com.quata.feature.chat.data

import com.quata.core.platform.PlatformFile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

class PostgrestChatRepositoryTest {
    @Test
    fun candidatePageUsesPortableRpcShapeAndSafeDefaults() {
        val page = """
            {"items":[
              {"profile_id":"p-1","display_name":"","existing_thread_id":44},
              {"display_name":"missing id"}
            ],"has_more":false}
        """.trimIndent().toChatConversationCandidatePage(requestOffset = 10)

        assertEquals(1, page.candidates.size)
        assertEquals("p-1", page.candidates.single().profileId)
        assertEquals("Usuario", page.candidates.single().displayName)
        assertEquals("sb:44", page.candidates.single().existingConversationId)
        assertEquals(11, page.nextOffset)
        assertFalse(page.hasMore)
        assertNull(page.candidates.single().avatarUrl)
    }

    @Test
    fun `failed inbox rpc is surfaced instead of emitted as an empty inbox`() = runTest {
        val repository = chatRepository(ChatPostgrestResponse.Failure(IllegalStateException("rpc_unavailable")))

        val error = assertFailsWith<IllegalStateException> {
            repository.observeConversations().first()
        }

        assertEquals("rpc_unavailable", error.message)
    }

    @Test
    fun `cancelling a hung inbox observation cancels its transport call`() = runTest {
        val cancelled = CompletableDeferred<Unit>()
        val repository = PostgrestChatRepository(
            transport = object : ChatPostgrestTransport {
                override suspend fun post(functionName: String, body: String): ChatPostgrestResponse {
                    try {
                        awaitCancellation()
                    } finally {
                        cancelled.complete(Unit)
                    }
                }
            },
            authenticatedUser = ChatAuthenticatedUserProvider { "profile-1" },
            attachmentUploader = ChatAttachmentUploader { _, file -> unusedUpload(file) },
        )
        val observation = launch { repository.observeConversations().first() }
        runCurrent()

        observation.cancelAndJoin()

        assertEquals(true, cancelled.isCompleted)
    }

    private fun chatRepository(response: ChatPostgrestResponse): PostgrestChatRepository = PostgrestChatRepository(
        transport = object : ChatPostgrestTransport {
            override suspend fun post(functionName: String, body: String): ChatPostgrestResponse = response
        },
        authenticatedUser = ChatAuthenticatedUserProvider { "profile-1" },
        attachmentUploader = ChatAttachmentUploader { _, file -> unusedUpload(file) },
    )

    private fun unusedUpload(file: PlatformFile) = UploadedChatAttachment(
        storagePath = file.reference,
        publicUrl = file.reference,
        mimeType = "application/octet-stream",
        sizeBytes = null,
        name = "unused",
        extension = "",
    )
}
