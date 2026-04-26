package com.quata.feature.chat.presentation.chat

sealed class ChatUiEvent {
    data class MessageChanged(val value: String) : ChatUiEvent()
    data object Send : ChatUiEvent()
}
