package com.quata.core.model

data class Conversation(
    val id: String,
    val title: String,
    val avatarUrl: String? = null,
    val lastMessagePreview: String,
    val unreadCount: Int = 0,
    val updatedAt: String = "",
    val updatedAtMillis: Long? = null,
    val participantIds: List<String> = emptyList(),
    val participantNames: List<String> = emptyList(),
    val isGroup: Boolean = false,
    val isEmergency: Boolean = false,
    val communityName: String? = null,
    val isMuted: Boolean = false,
    val isVisible: Boolean = true
)
