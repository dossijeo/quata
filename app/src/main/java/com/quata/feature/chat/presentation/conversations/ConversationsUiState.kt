package com.quata.feature.chat.presentation.conversations

import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.User

data class ConversationsUiState(
    val isLoading: Boolean = true,
    val currentUser: User? = null,
    val conversations: List<Conversation> = emptyList(),
    val messagesByConversation: Map<String, List<Message>> = emptyMap(),
    val usersById: Map<String, User> = emptyMap(),
    val pendingDeletedConversation: Conversation? = null,
    val error: String? = null
)
