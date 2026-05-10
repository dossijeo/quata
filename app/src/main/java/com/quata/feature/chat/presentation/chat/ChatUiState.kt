package com.quata.feature.chat.presentation.chat

import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.User

data class ChatUiState(
    val messageText: String = "",
    val currentUser: User? = null,
    val conversation: Conversation? = null,
    val messages: List<Message> = emptyList(),
    val participantCandidates: List<User> = emptyList(),
    val participantSearch: String = "",
    val selectedParticipantIds: List<String> = emptyList(),
    val isAddParticipantsOpen: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null
)
