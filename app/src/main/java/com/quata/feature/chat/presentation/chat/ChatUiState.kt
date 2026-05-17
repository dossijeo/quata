package com.quata.feature.chat.presentation.chat

import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.User

data class ChatUiState(
    val messageText: String = "",
    val attachmentUri: String? = null,
    val attachmentName: String? = null,
    val attachmentMimeType: String? = null,
    val currentUser: User? = null,
    val conversation: Conversation? = null,
    val messages: List<Message> = emptyList(),
    val participantCandidates: List<User> = emptyList(),
    val participantSearch: String = "",
    val selectedParticipantIds: List<String> = emptyList(),
    val selectedMessageId: String? = null,
    val replyToMessage: Message? = null,
    val editingMessage: Message? = null,
    val isForwardDialogOpen: Boolean = false,
    val selectedForwardConversationIds: List<String> = emptyList(),
    val availableForwardConversations: List<Conversation> = emptyList(),
    val isAddParticipantsOpen: Boolean = false,
    val isLoading: Boolean = true,
    val isConversationActionInProgress: Boolean = false,
    val shouldCloseConversation: Boolean = false,
    val error: String? = null
)
