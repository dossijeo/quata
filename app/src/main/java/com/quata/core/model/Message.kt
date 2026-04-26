package com.quata.core.model

data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val sentAt: String,
    val isMine: Boolean = false
)
