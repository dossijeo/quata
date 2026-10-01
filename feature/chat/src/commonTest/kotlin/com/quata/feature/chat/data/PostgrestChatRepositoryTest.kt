package com.quata.feature.chat.data

import com.quata.core.navigation.AppDestinations
import com.quata.core.model.MessageDeliveryState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import com.quata.feature.chat.domain.ChatSyncStatus
import com.quata.feature.chat.domain.SosRateLimitException
import com.quata.core.platform.PreferenceStore
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent

class PostgrestChatRepositoryTest {
    @Test
    fun inboxCursorAppendsDeepPageAndForegroundRefreshPreservesIt() = runTest {
        var firstPageCalls = 0
        val resumedRefresh = CompletableDeferred<Unit>()
        val repository = PostgrestChatRepository(
            transport = object : ChatPostgrestTransport {
                override suspend fun post(functionName: String, body: String): ChatPostgrestResponse {
                    assertEquals("quata_chat_get_inbox_page", functionName)
                    if (body.contains("\"p_before_thread_id\":2")) {
                        return ChatPostgrestResponse.Success(
                            """{"threads":[{"id":1,"type":"private","updated_at_millis":1}],"has_more":false,"next_cursor":null}""",
                        )
                    }
                    firstPageCalls += 1
                    if (firstPageCalls >= 2) resumedRefresh.complete(Unit)
                    val ids = if (firstPageCalls == 1) "3,2" else "4,3"
                    val threads = ids.split(',').joinToString(",") { id ->
                        """{"id":$id,"type":"private","updated_at_millis":$id}"""
                    }
                    return ChatPostgrestResponse.Success(
                        """{"threads":[$threads],"has_more":true,"next_cursor":{"last_message_at":"2026-09-25T10:00:00Z","updated_at":"2026-09-25T09:00:00Z","thread_id":2}}""",
                    )
                }
            },
            authenticatedUser = ChatAuthenticatedUserProvider { "profile-1" },
            attachmentUploader = ChatAttachmentUploader { _, _ -> error("not used") },
        )

        val first = repository.loadConversationPage(limit = 2).getOrThrow()
        assertTrue(first.hasMore)
        assertEquals(listOf("sb:3", "sb:2"), first.conversations.map { it.id })
        val second = repository.loadConversationPage(first.nextCursor, limit = 2).getOrThrow()
        assertFalse(second.hasMore)
        assertEquals(listOf("sb:1"), second.conversations.map { it.id })

        repository.setAppForeground(false)
        repository.setAppForeground(true)
        withTimeout(5_000L) { resumedRefresh.await() }

        assertEquals(
            listOf("sb:4", "sb:3", "sb:2", "sb:1"),
            repository.observeConversations().first().map { it.id },
        )
    }

    @Test
    fun actorBoundSosAndRecoveryRejectSessionChangesBeforeTransport() = runTest {
        val calls = mutableListOf<String>()
        val repository = PostgrestChatRepository(
            transport = object : ChatPostgrestTransport {
                override suspend fun post(functionName: String, body: String): ChatPostgrestResponse {
                    calls += functionName
                    return ChatPostgrestResponse.Success("{}")
                }
            },
            authenticatedUser = ChatAuthenticatedUserProvider { "profile-2" },
            attachmentUploader = ChatAttachmentUploader { _, _ -> error("not used") },
        )

        assertTrue(repository.sendSosMessage(
            contactIds = listOf("contact-1"),
            text = "SOS",
            expectedActorId = "profile-1",
        ).isFailure)
        assertTrue(repository.sendMessage(
            conversationId = "sb:7",
            text = "location",
            clientMessageId = "sos-location-profile-1-7",
            expectedActorId = "profile-1",
        ).isFailure)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun sosRateLimitEnvelopePreservesTypedFailureAndDoesNotRequireThreadPayload() = runTest {
        val calls = mutableListOf<String>()
        val repository = PostgrestChatRepository(
            transport = object : ChatPostgrestTransport {
                override suspend fun post(functionName: String, body: String): ChatPostgrestResponse {
                    calls += functionName
                    return ChatPostgrestResponse.Success(
                        """{"rate_limited":true,"remaining_millis":123456,"sent":0,"errors":[]}""",
                    )
                }
            },
            authenticatedUser = ChatAuthenticatedUserProvider { "profile-1" },
            attachmentUploader = ChatAttachmentUploader { _, _ -> error("not used") },
        )

        val failure = assertFailsWith<SosRateLimitException> {
            repository.sendSosMessage(listOf("contact-1"), "SOS", null, null, null).getOrThrow()
        }

        assertEquals(123456L, failure.remainingMillis)
        assertEquals(listOf("quata_chat_send_sos"), calls)
    }

    @Test
    fun inboxReceiptFailureDoesNotDiscardReceivedMessages() = runTest {
        verifyDeliveryReceipt("inbox_refresh", inbox = true)
    }

    @Test
    fun threadReceiptFailureDoesNotDiscardReceivedMessages() = runTest {
        verifyDeliveryReceipt("thread_refresh", inbox = false)
    }

    @Test
    fun readLifecycleUsesThreadReadRpcAndProjectsReadForTheSender() = runTest {
        val calls = mutableListOf<Pair<String, String>>()
        val repository = PostgrestChatRepository(
            transport = object : ChatPostgrestTransport {
                override suspend fun post(functionName: String, body: String): ChatPostgrestResponse {
                    calls += functionName to body
                    return when (functionName) {
                        "quata_chat_get_thread" -> ChatPostgrestResponse.Success(
                            """{"threads":[{"id":7,"type":"private"}],"messages":[{"id":42,"thread_id":7,"sender_profile_id":"profile-1","body":"read me","delivery_state":"READ"}]}""",
                        )
                        else -> ChatPostgrestResponse.Success("{}")
                    }
                }
            },
            authenticatedUser = ChatAuthenticatedUserProvider { "profile-1" },
            attachmentUploader = ChatAttachmentUploader { _, _ -> error("not used") },
        )

        assertTrue(repository.markConversationRead("sb:7").isSuccess)
        assertEquals(
            Json.parseToJsonElement("""{"p_actor_profile_id":"profile-1","p_thread_id":7}""").jsonObject,
            Json.parseToJsonElement(calls.single { it.first == "quata_chat_mark_thread_read" }.second).jsonObject,
        )
        repository.setActiveConversation("sb:7")
        val message = repository.observeMessages("sb:7").first().single()
        assertTrue(message.isMine)
        assertEquals(MessageDeliveryState.Read, message.deliveryState)
    }

    private suspend fun verifyDeliveryReceipt(source: String, inbox: Boolean) {
        val receipt = CompletableDeferred<String>()
        val releaseReceipt = CompletableDeferred<Unit>()
        val finishedReceipt = CompletableDeferred<Unit>()
        val repository = PostgrestChatRepository(
            transport = object : ChatPostgrestTransport {
                override suspend fun post(functionName: String, body: String): ChatPostgrestResponse {
                    if (functionName == "quata_chat_mark_messages_state") {
                        receipt.complete(body)
                        releaseReceipt.await()
                        finishedReceipt.complete(Unit)
                        return ChatPostgrestResponse.Failure(IllegalStateException("receipt unavailable"))
                    }
                    return ChatPostgrestResponse.Success("""{
                        "threads":[{"id":7,"type":"private"}],
                        "messages":[{"id":11,"thread_id":7,"sender_profile_id":"peer","body":"Received"}]
                    }""")
                }
            },
            authenticatedUser = ChatAuthenticatedUserProvider { "profile-1" },
            attachmentUploader = ChatAttachmentUploader { _, _ -> error("not used") },
        )
        repository.setActiveConversation("sb:7")
        try {
            // The read completes while its receipt transport is still suspended.
            if (inbox) assertTrue(repository.getConversations().isSuccess)
            else assertEquals("Received", repository.observeMessages("sb:7").first().single().text)
            assertEquals(
                Json.parseToJsonElement("""{"p_actor_profile_id":"profile-1","p_message_ids":[11],"p_status":"DELIVERED","p_source":"$source"}""").jsonObject,
                Json.parseToJsonElement(receipt.await()).jsonObject,
            )
            releaseReceipt.complete(Unit)
            finishedReceipt.await()
            assertEquals(ChatSyncStatus.Online, repository.syncStatus.value)
            assertEquals("Received", repository.observeMessages("sb:7").first().single().text)
        } finally {
            releaseReceipt.complete(Unit)
        }
    }

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
            ).isSuccess,
        )
        assertTrue(repository.isMessagePending("client-1"))
        assertTrue(repository.retryPendingMessage("client-1").isSuccess)
        assertFalse(repository.isMessagePending("client-1"))

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
            ).isSuccess,
        )

        assertEquals(listOf("quata_chat_register_attachment"), calls)
        assertEquals(listOf("profile-1/photo.jpg"), deletedStoragePaths)
        assertTrue(repository.isMessagePending("client-register-fail"))
    }

    @Test
    fun registerFailureAfterAttachmentUploadFailsClosedWhenOrphanCleanupReturnsFalse() = runTest {
        val repository = repositoryWithRegisterFailureCleanup(cleanup = { false })

        val result = repository.sendMessage(
            conversationId = "sb:77",
            text = "",
            attachmentUri = "local-photo",
            attachmentName = "photo.jpg",
            attachmentMimeType = "image/jpeg",
            clientMessageId = "client-cleanup-false",
        )

        assertTrue(result.isFailure)
        assertEquals("web_chat_attachment_orphan_cleanup_failed", result.exceptionOrNull()?.message)
        assertTrue(repository.retryPendingMessage("client-cleanup-false").isFailure)
    }

    @Test
    fun registerFailureAfterAttachmentUploadFailsClosedWhenOrphanCleanupThrows() = runTest {
        val repository = repositoryWithRegisterFailureCleanup(cleanup = { error("delete_storage_failed") })

        val result = repository.sendMessage(
            conversationId = "sb:77",
            text = "",
            attachmentUri = "local-photo",
            attachmentName = "photo.jpg",
            attachmentMimeType = "image/jpeg",
            clientMessageId = "client-cleanup-throws",
        )

        assertTrue(result.isFailure)
        assertEquals("web_chat_attachment_orphan_cleanup_failed", result.exceptionOrNull()?.message)
        assertTrue(repository.retryPendingMessage("client-cleanup-throws").isFailure)
    }

    @Test
    fun orphanCleanupFailureBlocksAnAlreadyQueuedRetryWithoutReuploading() = runTest {
        var uploadAttempts = 0
        val repository = PostgrestChatRepository(
            transport = object : ChatPostgrestTransport {
                override suspend fun post(functionName: String, body: String): ChatPostgrestResponse {
                    return when (functionName) {
                        "quata_chat_register_attachment" -> ChatPostgrestResponse.Failure(IllegalStateException("register_failed_after_upload"))
                        else -> ChatPostgrestResponse.Success("{}")
                    }
                }
            },
            authenticatedUser = ChatAuthenticatedUserProvider { "profile-1" },
            attachmentUploader = object : ChatAttachmentUploader {
                override suspend fun upload(profileId: String, file: com.quata.core.platform.PlatformFile): UploadedChatAttachment {
                    uploadAttempts += 1
                    if (uploadAttempts == 1) error("network_failed_before_upload")
                    return UploadedChatAttachment(
                        storagePath = "$profileId/${file.displayName}",
                        publicUrl = "https://project.supabase.co/storage/v1/object/public/chat-attachments/$profileId/${file.displayName}",
                        mimeType = file.mimeType ?: "image/jpeg",
                        sizeBytes = 42,
                        name = file.displayName ?: "photo.jpg",
                        extension = "jpg",
                    )
                }

                override suspend fun deleteUploadedAttachment(uploaded: UploadedChatAttachment): Boolean = false
            },
        )

        assertTrue(
            repository.sendMessage(
                conversationId = "sb:77",
                text = "",
                attachmentUri = "local-photo",
                attachmentName = "photo.jpg",
                attachmentMimeType = "image/jpeg",
                clientMessageId = "client-existing-retry-cleanup-fail",
            ).isSuccess,
        )
        assertTrue(repository.isMessagePending("client-existing-retry-cleanup-fail"))

        val retryResult = repository.retryPendingMessage("client-existing-retry-cleanup-fail")

        assertTrue(retryResult.isFailure)
        assertEquals("web_chat_attachment_orphan_cleanup_failed", retryResult.exceptionOrNull()?.message)
        assertTrue(repository.retryPendingMessage("client-existing-retry-cleanup-fail").isFailure)
        assertEquals(2, uploadAttempts)
    }

    @Test
    fun orphanCleanupIsReconciledBeforeAReplacementUploadCanSend() = runTest {
        var uploads = 0
        var registrations = 0
        var deletes = 0
        var sends = 0
        val repository = PostgrestChatRepository(
            transport = ChatPostgrestTransport { functionName, _ ->
                when (functionName) {
                    "quata_chat_register_attachment" -> {
                        registrations += 1
                        if (registrations == 1) ChatPostgrestResponse.Failure(IllegalStateException("register_failed"))
                        else ChatPostgrestResponse.Success("""{"id":91}""")
                    }
                    "quata_chat_send_message" -> { sends += 1; ChatPostgrestResponse.Success("{}") }
                    else -> ChatPostgrestResponse.Success("{}")
                }
            },
            authenticatedUser = ChatAuthenticatedUserProvider { "profile-1" },
            attachmentUploader = object : ChatAttachmentUploader {
                override suspend fun upload(profileId: String, file: com.quata.core.platform.PlatformFile): UploadedChatAttachment {
                    uploads += 1
                    return UploadedChatAttachment(
                        "$profileId/upload-$uploads.jpg",
                        "https://example.test/upload-$uploads.jpg",
                        "image/jpeg",
                        3L,
                        "photo.jpg",
                        "jpg",
                    )
                }

                override suspend fun deleteUploadedAttachment(uploaded: UploadedChatAttachment): Boolean {
                    deletes += 1
                    return deletes >= 2
                }
            },
        )

        assertTrue(repository.sendMessage("sb:77", "", "local-photo", "photo.jpg", "image/jpeg", "orphan-reconcile").isFailure)
        assertEquals(1, uploads)
        assertEquals(0, sends)

        assertTrue(repository.retryPendingMessage("orphan-reconcile").isSuccess)
        assertEquals(2, deletes)
        assertEquals(2, uploads)
        assertEquals(2, registrations)
        assertEquals(1, sends)
        assertFalse(repository.isMessagePending("orphan-reconcile"))
    }

    @Test
    fun suspendedUploadKeepsSecondRepositoryOutUntilTheFirstFinishesPastLeaseExpiry() = runTest {
        val store = MemoryChatOutgoingStore()
        val files = MemoryChatFileCacheService()
        val uploadStarted = CompletableDeferred<Unit>()
        val releaseUpload = CompletableDeferred<Unit>()
        var now = 1L
        var firstUploads = 0
        var secondUploads = 0
        var sends = 0
        fun transport() = ChatPostgrestTransport { functionName, _ ->
            when (functionName) {
                "quata_chat_register_attachment" -> ChatPostgrestResponse.Success("""{"id":81}""")
                "quata_chat_send_message" -> { sends += 1; ChatPostgrestResponse.Success("{}") }
                else -> ChatPostgrestResponse.Success("{}")
            }
        }
        val first = PostgrestChatRepository(
            transport = transport(),
            authenticatedUser = ChatAuthenticatedUserProvider { "profile-1" },
            attachmentUploader = ChatAttachmentUploader { _, _ ->
                firstUploads += 1
                uploadStarted.complete(Unit)
                releaseUpload.await()
                UploadedChatAttachment("profile-1/first.jpg", "https://example.test/first.jpg", "image/jpeg", 3L, "first.jpg", "jpg")
            },
            outgoingStore = store,
            outboxFiles = files,
            nowMillis = { now },
        )
        val second = PostgrestChatRepository(
            transport = transport(),
            authenticatedUser = ChatAuthenticatedUserProvider { "profile-1" },
            attachmentUploader = ChatAttachmentUploader { _, _ ->
                secondUploads += 1
                UploadedChatAttachment("profile-1/second.jpg", "https://example.test/second.jpg", "image/jpeg", 3L, "second.jpg", "jpg")
            },
            outgoingStore = store,
            outboxFiles = files,
            nowMillis = { now },
        )

        val firstSend = async {
            first.sendMessage("sb:77", "", "blob:lease", "lease.jpg", "image/jpeg", "lease-client")
        }
        uploadStarted.await()
        now = 10 * 60_000L
        val secondFlush = async { second.flushPendingMessages() }
        runCurrent()
        assertEquals(1, firstUploads)
        assertEquals(0, secondUploads)
        assertEquals(0, sends)

        releaseUpload.complete(Unit)
        assertTrue(firstSend.await().isSuccess)
        assertTrue(secondFlush.await())
        assertEquals(0, secondUploads)
        assertEquals(1, sends)
        assertTrue(store.load("profile-1").isEmpty())
    }

    @Test
    fun offlineTextOutboxSurvivesRepositoryRecreationAndReplaysExactlyOnce() = runTest {
        val store = MemoryChatOutgoingStore()
        val files = MemoryChatFileCacheService()
        val sendBodies = mutableListOf<String>()
        val transport = object : ChatPostgrestTransport {
            override suspend fun post(functionName: String, body: String): ChatPostgrestResponse {
                if (functionName == "quata_chat_send_message") sendBodies += body
                return ChatPostgrestResponse.Success("{}")
            }
        }
        fun repository() = PostgrestChatRepository(
            transport = transport,
            authenticatedUser = ChatAuthenticatedUserProvider { "profile-1" },
            attachmentUploader = ChatAttachmentUploader { _, _ -> error("not used") },
            outgoingStore = store,
            outboxFiles = files,
            nowMillis = { 1234L },
        )

        val firstProcess = repository().also { it.setDeviceNetworkAvailable(false) }
        assertTrue(
            firstProcess.sendMessage(
                conversationId = "sb:77",
                text = "durable offline",
                clientMessageId = "offline-client-1",
            ).isSuccess,
        )
        assertTrue(firstProcess.isMessagePending("offline-client-1"))
        assertTrue(sendBodies.isEmpty())

        val restoredProcess = repository().also {
            it.setDeviceNetworkAvailable(false)
            it.setActiveConversation("sb:77")
        }
        val restored = restoredProcess.observeMessages("sb:77").first().single()
        assertEquals("offline-client-1", restored.clientMessageId)
        assertEquals(MessageDeliveryState.Pending, restored.deliveryState)

        val onlineProcess = repository()
        assertTrue(onlineProcess.flushPendingMessages())
        assertFalse(onlineProcess.isMessagePending("offline-client-1"))
        assertTrue(onlineProcess.flushPendingMessages())
        assertEquals(1, sendBodies.size)
        assertTrue(sendBodies.single().contains("\"p_client_message_id\":\"offline-client-1\""))
    }

    @Test
    fun durableOutboxIsActorScopedAcrossRepositoryRecreation() = runTest {
        val store = MemoryChatOutgoingStore()
        val first = PostgrestChatRepository(
            transport = ChatPostgrestTransport { _, _ -> error("offline transport must not run") },
            authenticatedUser = ChatAuthenticatedUserProvider { "profile-a" },
            attachmentUploader = ChatAttachmentUploader { _, _ -> error("not used") },
            outgoingStore = store,
        ).also { it.setDeviceNetworkAvailable(false) }
        assertTrue(first.sendMessage("sb:7", "actor A", clientMessageId = "actor-a-client").isSuccess)

        var actorBCalls = 0
        val second = PostgrestChatRepository(
            transport = ChatPostgrestTransport { _, _ ->
                actorBCalls += 1
                ChatPostgrestResponse.Success("{}")
            },
            authenticatedUser = ChatAuthenticatedUserProvider { "profile-b" },
            attachmentUploader = ChatAttachmentUploader { _, _ -> error("not used") },
            outgoingStore = store,
        )
        assertFalse(second.isMessagePending("actor-a-client"))
        assertTrue(second.flushPendingMessages())
        assertEquals(0, actorBCalls)
        assertEquals(listOf("actor-a-client"), store.load("profile-a").map(StoredChatOutgoing::clientMessageId))
    }

    @Test
    fun offlineAttachmentBytesSurviveRepositoryRecreationUntilConfirmed() = runTest {
        val store = MemoryChatOutgoingStore()
        val files = MemoryChatFileCacheService()
        var uploadedReference: String? = null
        val first = PostgrestChatRepository(
            transport = ChatPostgrestTransport { _, _ -> error("offline transport must not run") },
            authenticatedUser = ChatAuthenticatedUserProvider { "profile-1" },
            attachmentUploader = ChatAttachmentUploader { _, _ -> error("offline upload must not run") },
            outgoingStore = store,
            outboxFiles = files,
        ).also { it.setDeviceNetworkAvailable(false) }
        assertTrue(
            first.sendMessage(
                conversationId = "sb:77",
                text = "",
                attachmentUri = "blob:original",
                attachmentName = "photo.jpg",
                attachmentMimeType = "image/jpeg",
                clientMessageId = "attachment-client-1",
            ).isSuccess,
        )

        val second = PostgrestChatRepository(
            transport = ChatPostgrestTransport { functionName, _ ->
                when (functionName) {
                    "quata_chat_register_attachment" -> ChatPostgrestResponse.Success("""{"id":901}""")
                    else -> ChatPostgrestResponse.Success("{}")
                }
            },
            authenticatedUser = ChatAuthenticatedUserProvider { "profile-1" },
            attachmentUploader = ChatAttachmentUploader { _, file ->
                uploadedReference = file.reference
                UploadedChatAttachment(
                    storagePath = "profile-1/photo.jpg",
                    publicUrl = "https://example.test/photo.jpg",
                    mimeType = "image/jpeg",
                    sizeBytes = 42,
                    name = "photo.jpg",
                    extension = "jpg",
                )
            },
            outgoingStore = store,
            outboxFiles = files,
        )
        assertTrue(second.flushPendingMessages())
        assertEquals("blob:original", uploadedReference)
        assertFalse(second.isMessagePending("attachment-client-1"))
        assertTrue(files.get("chat-outbox-attachment-client-1") is com.quata.core.platform.PlatformResult.Failure)
    }

    @Test
    fun deliveredAttachmentCleanupFailureNeverResendsAndRemainsDurableUntilCleaned() = runTest {
        val store = MemoryChatOutgoingStore()
        val delegateFiles = MemoryChatFileCacheService()
        var removeCalls = 0
        val files = object : com.quata.core.platform.FileCacheService {
            override suspend fun store(cacheKey: String, file: com.quata.core.platform.PlatformFile) =
                delegateFiles.store(cacheKey, file)
            override suspend fun get(cacheKey: String) = delegateFiles.get(cacheKey)
            override suspend fun remove(cacheKey: String): com.quata.core.platform.PlatformResult<Unit> {
                removeCalls += 1
                return if (removeCalls == 1) com.quata.core.platform.PlatformResult.Failure("disk_busy")
                else delegateFiles.remove(cacheKey)
            }
        }
        val offline = PostgrestChatRepository(
            transport = ChatPostgrestTransport { _, _ -> error("offline") },
            authenticatedUser = ChatAuthenticatedUserProvider { "profile-1" },
            attachmentUploader = ChatAttachmentUploader { _, _ -> error("offline") },
            outgoingStore = store,
            outboxFiles = files,
        ).also { it.setDeviceNetworkAvailable(false) }
        assertTrue(offline.sendMessage("sb:77", "", "blob:one", "one.jpg", "image/jpeg", clientMessageId = "cleanup-1").isSuccess)

        var sends = 0
        val online = PostgrestChatRepository(
            transport = ChatPostgrestTransport { functionName, _ ->
                when (functionName) {
                    "quata_chat_register_attachment" -> ChatPostgrestResponse.Success("""{"id":77}""")
                    "quata_chat_send_message" -> { sends += 1; ChatPostgrestResponse.Success("{}") }
                    else -> ChatPostgrestResponse.Success("{}")
                }
            },
            authenticatedUser = ChatAuthenticatedUserProvider { "profile-1" },
            attachmentUploader = ChatAttachmentUploader { _, _ ->
                UploadedChatAttachment("profile-1/one.jpg", "https://example.test/one.jpg", "image/jpeg", 3L, "one.jpg", "jpg")
            },
            outgoingStore = store,
            outboxFiles = files,
        )

        assertFalse(online.flushPendingMessages())
        assertEquals(1, sends)
        assertFalse(online.isMessagePending("cleanup-1"))
        assertTrue(store.load("profile-1").single().deliveredAwaitingCleanup)
        assertTrue(online.flushPendingMessages())
        assertEquals(1, sends)
        assertTrue(store.load("profile-1").isEmpty())
    }

    @Test
    fun preferenceOutboxRoundTripsByActorAndRejectsForeignRecords() = runTest {
        val preferences = TestPreferenceStore()
        val first = PreferenceChatOutgoingStore(preferences)
        val message = StoredChatOutgoing(
            actorId = "profile-1",
            conversationId = "sb:77",
            text = "persisted",
            clientMessageId = "client-1",
            createdAtMillis = 42L,
        )
        assertTrue(first.insert(message))

        assertEquals(listOf(message), PreferenceChatOutgoingStore(preferences).load("profile-1"))
        assertTrue(PreferenceChatOutgoingStore(preferences).load("profile-2").isEmpty())
        assertTrue(first.removeClaimed("profile-2", "client-1", "unused"))
        assertEquals(listOf(message), first.load("profile-1"))
    }

    @Test
    fun preferenceOutboxMergesIndependentWritersAndClaimsOnlyOnce() = runTest {
        val preferences = TestPreferenceStore()
        val first = PreferenceChatOutgoingStore(preferences)
        val second = PreferenceChatOutgoingStore(preferences)
        val one = StoredChatOutgoing("profile-1", "sb:7", "one", clientMessageId = "one", createdAtMillis = 1L)
        val two = StoredChatOutgoing("profile-1", "sb:7", "two", clientMessageId = "two", createdAtMillis = 2L)

        assertTrue(first.insert(one))
        assertTrue(second.insert(two))

        assertEquals(listOf("one", "two"), first.load("profile-1").map(StoredChatOutgoing::clientMessageId))
        val claimedA = first.claim("profile-1", "one", "lease-a", 10L, 100L)!!
        assertEquals("lease-a", claimedA.leaseToken)
        assertNull(second.claim("profile-1", "one", "lease-b", 11L, 101L))
        assertFalse(second.updateClaimed(claimedA.copy(text = "stale-b"), "lease-b"))
        assertFalse(second.removeClaimed("profile-1", "one", "lease-b"))
        val claimedB = second.claim("profile-1", "one", "lease-b", 100L, 200L)!!
        assertEquals("lease-b", claimedB.leaseToken)
        assertFalse(first.updateClaimed(claimedA.copy(text = "stale-a"), "lease-a"))
        assertFalse(first.removeClaimed("profile-1", "one", "lease-a"))
        assertTrue(second.updateClaimed(claimedB.copy(text = "owned"), "lease-b"))
        assertEquals("owned", first.load("profile-1").first { it.clientMessageId == "one" }.text)
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

    private fun repositoryWithRegisterFailureCleanup(
        cleanup: suspend (UploadedChatAttachment) -> Boolean,
    ) = PostgrestChatRepository(
        transport = object : ChatPostgrestTransport {
            override suspend fun post(functionName: String, body: String): ChatPostgrestResponse {
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

            override suspend fun deleteUploadedAttachment(uploaded: UploadedChatAttachment): Boolean =
                cleanup(uploaded)
        },
    )

    private class TestPreferenceStore : PreferenceStore {
        private val values = mutableMapOf<String, String>()
        override suspend fun getString(key: String): String? = values[key]
        override suspend fun putString(key: String, value: String) { values[key] = value }
        override suspend fun remove(key: String) { values.remove(key) }
    }
}
