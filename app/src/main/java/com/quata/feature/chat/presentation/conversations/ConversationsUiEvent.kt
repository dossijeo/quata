package com.quata.feature.chat.presentation.conversations

sealed class ConversationsUiEvent { data object Refresh : ConversationsUiEvent() }
