package com.quata.feature.chat.domain

/** Shared conversation identity rule. */

import com.quata.core.model.Conversation

fun Conversation.isExactPrivateConversation(
    currentProfileId: String,
    peerProfileId: String
): Boolean {
    if (isGroup || currentProfileId == peerProfileId || participantIds.size != 2) return false
    return participantIds.toSet() == setOf(currentProfileId, peerProfileId)
}
