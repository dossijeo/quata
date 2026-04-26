package com.quata.feature.chat.presentation.chat

import com.quata.core.model.Message

data class ChatUiState(
    val messageText: String = "",
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)
