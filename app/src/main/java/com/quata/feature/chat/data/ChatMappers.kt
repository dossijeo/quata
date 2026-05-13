package com.quata.feature.chat.data

import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.network.supabase.SupabaseConversationDto
import com.quata.core.network.supabase.SupabaseMessageDto
import java.time.Instant
import java.time.format.DateTimeParseException

fun SupabaseConversationDto.toDomain(): Conversation = Conversation(
    id = id,
    title = title ?: "Conversacion",
    lastMessagePreview = lastMessagePreview ?: "",
    unreadCount = 0,
    updatedAt = updatedAt ?: "",
    updatedAtMillis = updatedAt?.toEpochMillisOrNull(),
    participantIds = participantIds.orEmpty(),
    participantNames = participantNames.orEmpty(),
    isGroup = (participantNames?.size ?: participantIds?.size ?: 0) > 2,
    isEmergency = title == "\uD83D\uDEA8 SOS",
    communityName = communityName,
    isMuted = isMuted == true,
    isVisible = isVisible != false
)

fun SupabaseMessageDto.toDomain(currentUserId: String): Message = Message(
    id = id,
    conversationId = conversationId,
    senderId = senderId,
    senderName = senderName ?: "Usuario",
    text = text,
    sentAt = createdAt ?: "",
    isMine = senderId == currentUserId,
    isRead = senderId == currentUserId || isRead == true
)

private fun String.toEpochMillisOrNull(): Long? =
    try {
        Instant.parse(this).toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }
