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

class ChatRealtimeGatewayContractTest {
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
}

private fun conversation(isGroup: Boolean, isEmergency: Boolean) = Conversation(
    id = "sb:7", title = "Chat", lastMessagePreview = "", isGroup = isGroup, isEmergency = isEmergency,
)

private fun message() = Message(
    id = "1", conversationId = "sb:7", senderId = "p", senderName = "P", text = "hola", sentAt = "now",
)

private class RecordingGateway : ChatRealtimeGateway {
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
    override fun setNetworkAvailable(isAvailable: Boolean) { networkAvailable = isAvailable }
    override fun setVisibleConversation(conversationId: String?) { visibleConversation = conversationId }
    override fun setTyping(conversationId: String, isTyping: Boolean) { lastTyping = conversationId to isTyping }
    override fun close() = Unit
}
