package com.quata.feature.chat.presentation.conversations

import com.quata.core.model.Conversation
import com.quata.core.model.Message

data class ConversationsUiState(
    val isLoading: Boolean = true,
    val conversations: List<Conversation> = emptyList(),
    val messagesByConversation: Map<String, List<Message>> = emptyMap(),
    val error: String? = null
)
