package com.quata.feature.chat.presentation.conversations

import com.quata.core.model.Conversation

data class ConversationsUiState(
    val isLoading: Boolean = true,
    val conversations: List<Conversation> = emptyList(),
    val error: String? = null
)
