package com.quata.feature.chat.presentation

import com.quata.core.model.Conversation

/** Product title contract shared by Android, Wasm and iOS conversation surfaces. */
fun Conversation.chatDisplayTitle(): String = when {
    isEmergency -> "🚨 SOS"
    !communityName.isNullOrBlank() -> communityName.orEmpty()
    isGroup && participantNames.isNotEmpty() && title.isGeneratedChatTitle(id) -> participantNames.joinToString(", ")
    title.isNotBlank() -> title
    isGroup && participantNames.isNotEmpty() -> participantNames.joinToString(", ")
    else -> ""
}

private fun String.isGeneratedChatTitle(conversationId: String): Boolean {
    val numericId = conversationId.substringAfterLast(':', missingDelimiterValue = "")
    return numericId.isNotBlank() && this == "Chat $numericId"
}
