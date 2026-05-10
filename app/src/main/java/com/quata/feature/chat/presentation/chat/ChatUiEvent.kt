package com.quata.feature.chat.presentation.chat

sealed class ChatUiEvent {
    data class MessageChanged(val value: String) : ChatUiEvent()
    data class ParticipantSearchChanged(val value: String) : ChatUiEvent()
    data class ParticipantSelectionToggled(val userId: String) : ChatUiEvent()
    data class ConversationMutedChanged(val muted: Boolean) : ChatUiEvent()
    data object OpenAddParticipants : ChatUiEvent()
    data object CloseAddParticipants : ChatUiEvent()
    data object AddSelectedParticipants : ChatUiEvent()
    data object HideConversation : ChatUiEvent()
    data object Send : ChatUiEvent()
}
