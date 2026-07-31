package com.quata.feature.chat.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-owned Supabase Realtime connection for chat.  The common repository owns mapping
 * and refreshes; hosts own sockets, lifecycle and the current bearer token.
 */
interface ChatRealtimeGateway {
    val isOnline: StateFlow<Boolean>
    val typingProfileIds: StateFlow<Set<String>>
    val changes: Flow<ChatRealtimeChange>

    fun setForeground(isForeground: Boolean)
    fun setNetworkAvailable(isAvailable: Boolean)
    fun setVisibleConversation(conversationId: String?)
    fun setTyping(conversationId: String, isTyping: Boolean)
    fun close()
}

data class ChatRealtimeChange(
    val table: String,
    val threadId: Long? = null,
)

