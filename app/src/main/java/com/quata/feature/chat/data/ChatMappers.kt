package com.quata.feature.chat.data

import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.network.supabase.SupabaseConversationDto
import com.quata.core.network.supabase.SupabaseMessageDto

fun SupabaseConversationDto.toDomain(): Conversation = Conversation(
    id = id,
    title = title ?: "Conversación",
    lastMessagePreview = lastMessagePreview ?: "",
    unreadCount = unreadCount ?: 0,
    updatedAt = updatedAt ?: ""
)

fun SupabaseMessageDto.toDomain(currentUserId: String): Message = Message(
    id = id,
    conversationId = conversationId,
    senderId = senderId,
    senderName = senderName ?: "Usuario",
    text = text,
    sentAt = createdAt ?: "",
    isMine = senderId == currentUserId
)
