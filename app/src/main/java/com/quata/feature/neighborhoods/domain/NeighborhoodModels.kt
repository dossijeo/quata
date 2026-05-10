package com.quata.feature.neighborhoods.domain

data class NeighborhoodUser(
    val id: String,
    val displayName: String,
    val email: String,
    val neighborhood: String,
    val avatarUrl: String? = null
)

data class NeighborhoodCommunity(
    val name: String,
    val users: List<NeighborhoodUser>,
    val conversationId: String?,
    val lastMessagePreview: String?,
    val lastMessageAtMillis: Long?,
    val messageCount: Int
)
