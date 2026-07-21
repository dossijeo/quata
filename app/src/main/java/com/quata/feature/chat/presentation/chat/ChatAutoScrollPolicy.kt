package com.quata.feature.chat.presentation.chat

import com.quata.core.model.Message

internal data class ChatMessageLayoutKey(
    val composeKey: String,
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

internal fun Message.composeKey(): String =
    clientMessageId?.takeIf(String::isNotBlank) ?: id

internal fun Message.chatLayoutKey() = ChatMessageLayoutKey(
    composeKey = composeKey(),
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
    if (previous.isEmpty()) return true

    val previousByKey = previous.associateBy(ChatMessageLayoutKey::composeKey)
    val currentByKey = current.associateBy(ChatMessageLayoutKey::composeKey)
    val newItems = current.filter { it.composeKey !in previousByKey }
    val removedItems = previous.any { it.composeKey !in currentByKey }
    val existingItemChangedLayout = current.any { item ->
        previousByKey[item.composeKey]?.let { previousItem -> previousItem != item } == true
    }

    // Changes excluded from ChatMessageLayoutKey (server id, delivery state,
    // local-echo state, read/favorite state) cannot affect card dimensions.
    // Reordering the same unchanged visual items must not trigger a follow-up scroll either.
    if (newItems.isEmpty() && !removedItems && !existingItemChangedLayout) return false
    if (!userHasDetachedFromBottom) return true

    // A detached reader only follows a newly appended outgoing message. Loading
    // older history, enriching an existing card or receiving messages must not
    // take the reader back to the bottom.
    val previousLastIndex = current.indexOfFirst {
        it.composeKey == previous.last().composeKey
    }
    if (previousLastIndex < 0) return false
    return current.drop(previousLastIndex + 1).any { item ->
        item.composeKey !in previousByKey && item.isMine
    }
}
