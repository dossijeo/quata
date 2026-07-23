package com.quata.feature.chat.presentation.conversations

/** Shared conversations intents. */

sealed class ConversationsUiEvent {
    data object Refresh : ConversationsUiEvent()
    data object RestoreDeletedConversation : ConversationsUiEvent()
    data object FinalizeDeletedConversation : ConversationsUiEvent()
}
