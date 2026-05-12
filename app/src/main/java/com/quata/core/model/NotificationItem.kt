package com.quata.core.model

data class NotificationItem(
    val id: String,
    val conversationId: String,
    val title: String,
    val body: String,
    val createdAt: String,
    val isRead: Boolean = false,
    val unreadCount: Int = 0
)
