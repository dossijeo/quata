package com.quata.core.model

data class Conversation(
    val id: String,
    val title: String,
    val avatarUrl: String? = null,
    val lastMessagePreview: String,
    val unreadCount: Int = 0,
    val updatedAt: String = ""
)
