package com.quata.core.model

data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val sentAt: String,
    val sentAtMillis: Long? = null,
    val isMine: Boolean = false,
    val isRead: Boolean = true,
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    val isFavorite: Boolean = false,
    val replyToMessageId: String? = null,
    val replyToSenderName: String? = null,
    val replyToText: String? = null,
    val forwardedFromSenderId: String? = null,
    val forwardedFromSenderName: String? = null
)
