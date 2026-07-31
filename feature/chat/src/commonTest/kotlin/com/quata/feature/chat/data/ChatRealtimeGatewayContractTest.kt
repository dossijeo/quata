package com.quata.feature.chat.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
}

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
