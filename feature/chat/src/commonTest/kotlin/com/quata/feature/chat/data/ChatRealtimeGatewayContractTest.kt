package com.quata.feature.chat.data

import com.quata.core.model.Conversation
import com.quata.core.model.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import com.quata.feature.chat.domain.ChatSyncStatus

class ChatRealtimeGatewayContractTest {
    @Test
    fun responseStartedOnlineDoesNotHideLaterNetworkLoss() = runTest {
        val gateway = RecordingGateway()
        val response = CompletableDeferred<ChatPostgrestResponse>()
        val started = CompletableDeferred<Unit>()
        val repository = PostgrestChatRepository(
            transport = object : ChatPostgrestTransport {
                override suspend fun post(functionName: String, body: String): ChatPostgrestResponse {
                    started.complete(Unit)
                    return response.await()
                }
            },
            authenticatedUser = ChatAuthenticatedUserProvider { "profile-1" },
            attachmentUploader = ChatAttachmentUploader { _, _ -> error("not used") },
            realtimeGateway = gateway,
        )
        val request = async(start = CoroutineStart.UNDISPATCHED) { repository.getConversations() }
        started.await()
        gateway.setNetworkAvailable(false)
        repository.syncStatus.first { it == ChatSyncStatus.Offline }
        response.complete(ChatPostgrestResponse.Success("{}"))

        assertTrue(request.await().isSuccess)
        assertEquals(ChatSyncStatus.Offline, repository.syncStatus.value)
    }

    @Test
    fun platformNetworkObservationsGateRequestsAndRestoreConnectivity() = runTest {
        val gateway = RecordingGateway().apply { setNetworkAvailable(false) }
        var requests = 0
        val repository = PostgrestChatRepository(
            transport = object : ChatPostgrestTransport {
                override suspend fun post(functionName: String, body: String): ChatPostgrestResponse {
                    requests += 1
                    return ChatPostgrestResponse.Success("{}")
                }
            },
            authenticatedUser = ChatAuthenticatedUserProvider { "profile-1" },
            attachmentUploader = ChatAttachmentUploader { _, _ -> error("not used") },
            realtimeGateway = gateway,
        )

        // Drive the platform gateway, not repository setters: this was the missing path.
        assertFalse(repository.isDeviceNetworkAvailable.value)
        assertEquals(ChatSyncStatus.Offline, repository.syncStatus.value)
        assertTrue(repository.getConversations().isFailure)
        assertEquals(0, requests)

        gateway.setNetworkAvailable(true)
        assertTrue(repository.isDeviceNetworkAvailable.value)
        repository.syncStatus.first { it == ChatSyncStatus.Refreshing }
        assertTrue(repository.getConversations().isSuccess)
        assertEquals(1, requests)

        gateway.setNetworkAvailable(false)
        assertFalse(repository.isDeviceNetworkAvailable.value)
        repository.syncStatus.first { it == ChatSyncStatus.Offline }
        assertTrue(repository.getConversations().isFailure)
        assertEquals(1, requests)
        assertEquals(ChatSyncStatus.Offline, repository.syncStatus.value)
    }

    @Test
    fun shellNetworkStateAlsoTracksExplicitNetworkUpdatesWithoutAGateway() {
        val repository = PostgrestChatRepository(
            transport = object : ChatPostgrestTransport {
                override suspend fun post(functionName: String, body: String) =
                    ChatPostgrestResponse.Success("{}")
            },
            authenticatedUser = ChatAuthenticatedUserProvider { "profile-1" },
            attachmentUploader = ChatAttachmentUploader { _, _ -> error("not used") },
        )
        assertTrue(repository.isDeviceNetworkAvailable.value)
        repository.setDeviceNetworkAvailable(false)
        assertFalse(repository.isDeviceNetworkAvailable.value)
        repository.setDeviceNetworkAvailable(true)
        assertTrue(repository.isDeviceNetworkAvailable.value)
    }

    @Test
    fun repositorySubscribesAndForwardsTypingAndLifecycle() {
        val gateway = RecordingGateway()
        PostgrestChatRepository(
            transport = object : ChatPostgrestTransport {
                override suspend fun post(functionName: String, body: String) =
                    ChatPostgrestResponse.Success("{}")
            },
            authenticatedUser = ChatAuthenticatedUserProvider { "profile-1" },
            attachmentUploader = ChatAttachmentUploader { _, _ -> error("not used") },
            realtimeGateway = gateway,
        ).apply {
            assertEquals(1, gateway.subscriptionReads)
            setActiveConversation("sb:7")
            setTyping("sb:7", true)
            setAppForeground(false)
            setDeviceNetworkAvailable(false)
        }
        assertEquals("sb:7", gateway.visibleConversation)
        assertEquals("sb:7" to true, gateway.lastTyping)
        assertFalse(gateway.foreground)
        assertFalse(gateway.networkAvailable)
    }

    @Test
    fun hidingAStaleScreenDoesNotClearTheCurrentlyVisibleConversation() {
        val gateway = RecordingGateway()
        val repository = PostgrestChatRepository(
            transport = object : ChatPostgrestTransport {
                override suspend fun post(functionName: String, body: String) =
                    ChatPostgrestResponse.Success("{}")
            },
            authenticatedUser = ChatAuthenticatedUserProvider { "profile-1" },
            attachmentUploader = ChatAttachmentUploader { _, _ -> error("not used") },
            realtimeGateway = gateway,
        )

        repository.setConversationVisible("sb:7", true)
        repository.setConversationVisible("sb:8", true)
        repository.setConversationVisible("sb:7", false)

        assertEquals("sb:8", repository.activeConversationId.value)
        assertEquals("sb:8", gateway.visibleConversation)

        repository.setConversationVisible("sb:8", false)
        assertEquals(null, repository.activeConversationId.value)
        assertEquals(null, gateway.visibleConversation)
    }

    @Test
    fun lifecycleDisconnectsAndReconnectsOnlyWhenAllRequirementsHold() {
        assertTrue(shouldConnectChatRealtime(true, true, true))
        assertFalse(shouldConnectChatRealtime(false, true, true))
        assertFalse(shouldConnectChatRealtime(true, false, true))
        assertFalse(shouldConnectChatRealtime(true, true, false))
        assertFalse(shouldConnectChatRealtime(true, true, true, closed = true))
    }

    @Test
    fun reconnectBackoffIsBounded() {
        assertEquals(1_000L, chatRealtimeReconnectDelayMillis(0))
        assertEquals(2_000L, chatRealtimeReconnectDelayMillis(1))
        assertEquals(30_000L, chatRealtimeReconnectDelayMillis(6))
        assertEquals(30_000L, chatRealtimeReconnectDelayMillis(100))
    }

    @Test
    fun cleanupOnlyTargetsEmptyPrivateThreads() {
        val private = conversation(isGroup = false, isEmergency = false)
        assertTrue(shouldCleanupEmptyPrivateConversation(private, emptyList()))
        assertFalse(shouldCleanupEmptyPrivateConversation(conversation(isGroup = true, isEmergency = false), emptyList()))
        assertFalse(shouldCleanupEmptyPrivateConversation(conversation(isGroup = false, isEmergency = true), emptyList()))
        assertFalse(shouldCleanupEmptyPrivateConversation(private, listOf(message())))
    }

    @Test
    fun phoenixPresenceDiffTracksExactJoinsLeavesAndConversation() {
        val state = ChatTypingPresenceSnapshot().reduce(
            "presence_state",
            Json.parseToJsonElement("""{"self":{"metas":[{"phx_ref":"s1","conversation_id":"sb:7","typing":true}]},"peer":{"metas":[{"phx_ref":"p1","conversation_id":"sb:7","typing":true},{"phx_ref":"p2","conversation_id":"sb:8","typing":true}]}}"""),
        )
        assertEquals(setOf("peer"), state.typingProfileIds("sb:7", "self"))
        assertEquals(setOf("peer"), state.typingProfileIds("sb:8", "self"))

        val afterFirstLeave = state.reduce(
            "presence_diff",
            Json.parseToJsonElement("""{"joins":{"next":{"metas":[{"phx_ref":"n1","conversation_id":"sb:7","typing":true}]}},"leaves":{"peer":{"metas":[{"phx_ref":"p1","conversation_id":"sb:7","typing":true}]}}}"""),
        )
        assertEquals(setOf("next"), afterFirstLeave.typingProfileIds("sb:7", "self"))
        assertEquals(setOf("peer"), afterFirstLeave.typingProfileIds("sb:8", "self"))

        val afterFinalLeave = afterFirstLeave.reduce(
            "presence_diff",
            Json.parseToJsonElement("""{"joins":{},"leaves":{"peer":{"metas":[{"phx_ref":"p2","conversation_id":"sb:8","typing":true}]}}}"""),
        )
        assertEquals(emptySet(), afterFinalLeave.typingProfileIds("sb:8", "self"))
    }

    @Test
    fun postgresChangeExtractsTableAndThreadFromNewOrOldRecord() {
        assertEquals(
            ChatRealtimeChange("chat_messages", 17L),
            parseChatRealtimeChange(
                "postgres_changes",
                Json.parseToJsonElement("""{"data":{"table":"chat_messages","record":{"thread_id":17}}}"""),
            ),
        )
        assertEquals(
            ChatRealtimeChange("chat_participants", 9L),
            parseChatRealtimeChange(
                "postgres_changes",
                Json.parseToJsonElement("""{"data":{"table":"chat_participants","old_record":{"thread_id":9}}}"""),
            ),
        )
        assertEquals(null, parseChatRealtimeChange("broadcast", Json.parseToJsonElement("{}")))
    }

    @Test
    fun typingBroadcastMatchesThePublishedAndroidEnvelope() {
        assertEquals(
            ChatTypingBroadcast("peer", true),
            parseChatTypingBroadcast(
                "broadcast",
                Json.parseToJsonElement("""{"type":"broadcast","event":"typing","payload":{"profile_id":"peer","is_typing":true}}"""),
            ),
        )
        assertEquals(null, parseChatTypingBroadcast("broadcast", Json.parseToJsonElement("""{"event":"other","payload":{}}""")))
    }
}

private fun conversation(isGroup: Boolean, isEmergency: Boolean) = Conversation(
    id = "sb:7", title = "Chat", lastMessagePreview = "", isGroup = isGroup, isEmergency = isEmergency,
)

private fun message() = Message(
    id = "1", conversationId = "sb:7", senderId = "p", senderName = "P", text = "hola", sentAt = "now",
)

private class RecordingGateway : ChatRealtimeGateway {
    override val isNetworkAvailable = MutableStateFlow(true)
    override val isOnline = MutableStateFlow(false)
    override val typingProfileIds = MutableStateFlow<Set<String>>(emptySet())
    private val events = MutableSharedFlow<ChatRealtimeChange>()
    var subscriptionReads = 0
    override val changes: Flow<ChatRealtimeChange>
        get() { subscriptionReads += 1; return events }
    var foreground = true
    var networkAvailable = true
    var visibleConversation: String? = null
    var lastTyping: Pair<String, Boolean>? = null
    override fun setForeground(isForeground: Boolean) { foreground = isForeground }
    override fun setNetworkAvailable(isAvailable: Boolean) { networkAvailable = isAvailable; isNetworkAvailable.value = isAvailable }
    override fun setVisibleConversation(conversationId: String?) { visibleConversation = conversationId }
    override fun setTyping(conversationId: String, isTyping: Boolean) { lastTyping = conversationId to isTyping }
    override fun close() = Unit
}
