package com.quata.feature.chat.presentation.chat

import com.quata.core.model.Message

internal data class ChatMessageLayoutKey(
    val id: String,
    val senderName: String,
    val text: String,
    val replyToSenderName: String?,
    val replyToText: String?,
    val forwardedFromSenderName: String?,
    val attachmentUri: String?,
    val attachmentName: String?,
    val attachmentMimeType: String?,
    val isEdited: Boolean,
    val isDeleted: Boolean,
    val isMine: Boolean
)

internal fun Message.chatLayoutKey() = ChatMessageLayoutKey(
    id = id,
    senderName = senderName,
    text = text,
    replyToSenderName = replyToSenderName,
    replyToText = replyToText,
    forwardedFromSenderName = forwardedFromSenderName,
    attachmentUri = attachmentUri,
    attachmentName = attachmentName,
    attachmentMimeType = attachmentMimeType,
    isEdited = isEdited,
    isDeleted = isDeleted,
    isMine = isMine
)

internal fun shouldFollowChatLayoutUpdate(
    previous: List<ChatMessageLayoutKey>,
    current: List<ChatMessageLayoutKey>,
    userHasDetachedFromBottom: Boolean
): Boolean {
    if (current.isEmpty() || current == previous) return false
    if (previous.isEmpty() || !userHasDetachedFromBottom) return true
    val previousIds = previous.mapTo(mutableSetOf()) { it.id }
    return current.any { it.id !in previousIds && it.isMine }
}
