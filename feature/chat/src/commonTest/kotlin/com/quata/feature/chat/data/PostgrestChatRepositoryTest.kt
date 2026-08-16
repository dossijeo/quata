package com.quata.feature.chat.data

import com.quata.core.navigation.AppDestinations
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

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
    fun retryAfterSendFailureReusesRegisteredAttachment() = runTest {
        val calls = mutableListOf<Pair<String, String>>()
        var sendAttempts = 0
        var uploads = 0
        val deletedStoragePaths = mutableListOf<String>()
        val attachmentUploader = object : ChatAttachmentUploader {
            override suspend fun upload(profileId: String, file: com.quata.core.platform.PlatformFile): UploadedChatAttachment {
                uploads += 1
                return UploadedChatAttachment(
                    storagePath = "profile-1/${file.displayName}",
                    publicUrl = "https://project.supabase.co/storage/v1/object/public/chat-attachments/profile-1/${file.displayName}",
                    mimeType = file.mimeType ?: "image/jpeg",
                    sizeBytes = 42,
                    name = file.displayName ?: "photo.jpg",
                    extension = "jpg",
                )
            }

            override suspend fun deleteUploadedAttachment(uploaded: UploadedChatAttachment): Boolean {
                deletedStoragePaths += uploaded.storagePath
                return true
            }
        }
        val repository = PostgrestChatRepository(
            transport = object : ChatPostgrestTransport {
                override suspend fun post(functionName: String, body: String): ChatPostgrestResponse {
                    calls += functionName to body
                    return when (functionName) {
                        "quata_chat_register_attachment" -> ChatPostgrestResponse.Success("""{"id":123}""")
                        "quata_chat_send_message" -> {
                            sendAttempts += 1
                            if (sendAttempts == 1) {
                                ChatPostgrestResponse.Failure(IllegalStateException("send_failed_after_attachment_registered"))
                            } else {
                                ChatPostgrestResponse.Success("{}")
                            }
                        }
                        else -> ChatPostgrestResponse.Success("{}")
                    }
                }
            },
            authenticatedUser = ChatAuthenticatedUserProvider { "profile-1" },
            attachmentUploader = attachmentUploader,
        )

        assertTrue(
            repository.sendMessage(
                conversationId = "sb:77",
                text = "",
                attachmentUri = "local-photo",
                attachmentName = "photo.jpg",
                attachmentMimeType = "image/jpeg",
                clientMessageId = "client-1",
            ).isFailure,
        )
        assertTrue(repository.retryPendingMessage("client-1").isSuccess)

        assertEquals(1, uploads)
        assertEquals(emptyList(), deletedStoragePaths)
        assertEquals(1, calls.count { it.first == "quata_chat_register_attachment" })
        assertEquals(2, calls.count { it.first == "quata_chat_send_message" })
        assertTrue(calls.filter { it.first == "quata_chat_send_message" }.all { it.second.contains("\"p_file_ids\":[123]") })
    }

    @Test
    fun registerFailureAfterAttachmentUploadDeletesTheOrphanStorageObject() = runTest {
        val calls = mutableListOf<String>()
        val deletedStoragePaths = mutableListOf<String>()
        val repository = PostgrestChatRepository(
            transport = object : ChatPostgrestTransport {
                override suspend fun post(functionName: String, body: String): ChatPostgrestResponse {
                    calls += functionName
                    return when (functionName) {
                        "quata_chat_register_attachment" -> ChatPostgrestResponse.Failure(IllegalStateException("register_failed_after_upload"))
                        else -> ChatPostgrestResponse.Success("{}")
                    }
                }
            },
            authenticatedUser = ChatAuthenticatedUserProvider { "profile-1" },
            attachmentUploader = object : ChatAttachmentUploader {
                override suspend fun upload(profileId: String, file: com.quata.core.platform.PlatformFile): UploadedChatAttachment =
                    UploadedChatAttachment(
                        storagePath = "$profileId/${file.displayName}",
                        publicUrl = "https://project.supabase.co/storage/v1/object/public/chat-attachments/$profileId/${file.displayName}",
                        mimeType = file.mimeType ?: "image/jpeg",
                        sizeBytes = 42,
                        name = file.displayName ?: "photo.jpg",
                        extension = "jpg",
                    )

                override suspend fun deleteUploadedAttachment(uploaded: UploadedChatAttachment): Boolean {
                    deletedStoragePaths += uploaded.storagePath
                    return true
                }
            },
        )

        assertTrue(
            repository.sendMessage(
                conversationId = "sb:77",
                text = "",
                attachmentUri = "local-photo",
                attachmentName = "photo.jpg",
                attachmentMimeType = "image/jpeg",
                clientMessageId = "client-register-fail",
            ).isFailure,
        )

        assertEquals(listOf("quata_chat_register_attachment"), calls)
        assertEquals(listOf("profile-1/photo.jpg"), deletedStoragePaths)
    }

    @Test
    fun favoriteMessageLoadFailureIsNotEmittedAsAnEmptySnapshot() = runTest {
        val repository = PostgrestChatRepository(
            transport = object : ChatPostgrestTransport {
                override suspend fun post(functionName: String, body: String): ChatPostgrestResponse {
                    return when (functionName) {
                        "quata_chat_get_favorites" -> ChatPostgrestResponse.Failure(IllegalStateException("favorites_load_failed"))
                        else -> ChatPostgrestResponse.Success("{}")
                    }
                }
            },
            authenticatedUser = ChatAuthenticatedUserProvider { "profile-1" },
            attachmentUploader = ChatAttachmentUploader { _, _ ->
                error("attachment uploader should not be used")
            },
        )

        assertFailsWith<IllegalStateException> {
            repository.observeMessages(AppDestinations.FavoriteMessagesConversationId).first()
        }.also { error ->
            assertEquals("favorites_load_failed", error.message)
        }
    }

    @Test
    fun toggleFavoriteRefreshesTheSharedFavoriteConversationImmediately() = runTest {
        val calls = mutableListOf<String>()
        var favoriteEnabled = false
        val repository = PostgrestChatRepository(
            transport = object : ChatPostgrestTransport {
                override suspend fun post(functionName: String, body: String): ChatPostgrestResponse {
                    calls += functionName
                    return when (functionName) {
                        "quata_chat_get_thread" -> ChatPostgrestResponse.Success(chatPayload(favoriteEnabled))
                        "quata_chat_set_favorite" -> {
                            favoriteEnabled = body.contains("\"p_favorite\":true")
                            ChatPostgrestResponse.Success("{}")
                        }
                        "quata_chat_get_favorites" -> {
                            val body = if (favoriteEnabled) chatPayload(favorited = true) else """{"messages":[]}"""
                            ChatPostgrestResponse.Success(body)
                        }
                        else -> ChatPostgrestResponse.Success("{}")
                    }
                }
            },
            authenticatedUser = ChatAuthenticatedUserProvider { "profile-1" },
            attachmentUploader = ChatAttachmentUploader { _, _ ->
                error("attachment uploader should not be used")
            },
            pollIntervalMillis = 5_000L,
        )

        repository.setActiveConversation("sb:77")
        assertEquals(false, repository.observeMessages("sb:77").first().single().isFavorite)

        assertTrue(repository.toggleFavoriteMessage("123").isSuccess)

        assertEquals(
            listOf(
                "quata_chat_get_thread",
                "quata_chat_set_favorite",
                "quata_chat_get_thread",
                "quata_chat_get_favorites",
            ),
            calls,
        )
        assertEquals("123", repository.observeMessages(AppDestinations.FavoriteMessagesConversationId).first().single().id)
    }

    private fun chatPayload(favorited: Boolean): String = """
        {"messages":[{
          "id":123,
          "thread_id":77,
          "sender_profile_id":"profile-1",
          "body":"favorite me",
          "created_at":"2026-08-07T09:00:00Z",
          "created_at_millis":1000,
          "favorited":$favorited,
          "sender":{"id":"profile-1","display_name":"Gabrielo"}
        }]}
    """.trimIndent()
}
