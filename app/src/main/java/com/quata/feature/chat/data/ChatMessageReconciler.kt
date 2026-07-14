package com.quata.feature.chat.data

import com.quata.core.model.Message

internal fun reconcileChatMessages(
    incoming: List<Message>,
    existing: List<Message> = emptyList(),
    retainUnmatchedExisting: Boolean = true
): List<Message> {
    val existingById = existing.associateBy { it.id }
    val existingByClientId = existing
        .mapNotNull { message -> message.clientMessageId?.takeIf(String::isNotBlank)?.let { it to message } }
        .toMap()
    val incomingIds = incoming.mapTo(mutableSetOf()) { it.id }
    val incomingClientIds = incoming.mapNotNullTo(mutableSetOf()) { it.clientMessageId?.takeIf(String::isNotBlank) }

    val reconciledIncoming = incoming.map { message ->
        val previous = existingById[message.id]
            ?: message.clientMessageId?.takeIf(String::isNotBlank)?.let(existingByClientId::get)
        message.preserveReplySnapshotFrom(previous)
    }
    val retained = if (retainUnmatchedExisting) {
        existing.filterNot { message ->
            message.id in incomingIds || message.clientMessageId?.takeIf(String::isNotBlank) in incomingClientIds
        }
    } else {
        emptyList()
    }

    return (retained + reconciledIncoming)
        .distinctBy { it.clientMessageId?.takeIf(String::isNotBlank)?.let { id -> "client:$id" } ?: "message:${it.id}" }
        .sortedBy { it.sentAtMillis ?: Long.MAX_VALUE }
        .withResolvedReplyContext()
}

private fun Message.preserveReplySnapshotFrom(previous: Message?): Message {
    if (replyToMessageId == null || replyToMessageId != previous?.replyToMessageId) return this
    return copy(
        replyToSenderName = replyToSenderName ?: previous.replyToSenderName,
        replyToText = replyToText ?: previous.replyToText
    )
}

private fun List<Message>.withResolvedReplyContext(): List<Message> {
    val byId = associateBy { it.id }
    return map { message ->
        val repliedMessage = message.replyToMessageId?.let(byId::get) ?: return@map message
        message.copy(
            replyToSenderName = repliedMessage.senderName,
            replyToText = repliedMessage.text
        )
    }
}
