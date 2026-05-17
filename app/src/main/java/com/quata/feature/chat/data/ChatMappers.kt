package com.quata.feature.chat.data

import com.quata.bettermessages.BmMessage
import com.quata.bettermessages.BmThread
import com.quata.bettermessages.BmUser
import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.data.supabase.CommunityMessage
import com.quata.data.supabase.CommunityWallStats
import java.time.Instant
import java.time.format.DateTimeParseException

const val BetterMessagesConversationPrefix = "bm:"
const val WallConversationPrefix = "wall:"

fun betterMessagesConversationId(threadId: Int): String = "$BetterMessagesConversationPrefix$threadId"
fun wallConversationId(wallId: String): String = "$WallConversationPrefix$wallId"
fun String.betterMessagesThreadIdOrNull(): Int? =
    takeIf { it.startsWith(BetterMessagesConversationPrefix) }
        ?.removePrefix(BetterMessagesConversationPrefix)
        ?.toIntOrNull()
        ?.takeIf { it > 0 }
fun String.wallIdOrNull(): String? =
    takeIf { it.startsWith(WallConversationPrefix) }
        ?.removePrefix(WallConversationPrefix)
        ?.takeIf { it.isNotBlank() }

fun CommunityWallStats.toConversation(): Conversation =
    Conversation(
        id = wallConversationId(id),
        title = name ?: slug ?: "Comunidad",
        lastMessagePreview = "",
        updatedAt = chat_last_at.orEmpty(),
        updatedAtMillis = chat_last_at?.toEpochMillisOrNull(),
        isGroup = true,
        communityName = name,
        isVisible = true,
        canMembersInvite = false
    )

fun BmThread.toConversation(
    title: String,
    peerProfileId: String?,
    peerName: String,
    peerAvatarUrl: String?,
    currentProfileId: String
): Conversation =
    Conversation(
        id = betterMessagesConversationId(threadId),
        title = title.ifBlank { subject ?: this.title ?: peerName },
        avatarUrl = peerAvatarUrl,
        lastMessagePreview = "",
        unreadCount = unread ?: 0,
        updatedAt = lastTime?.toDisplayTime().orEmpty(),
        updatedAtMillis = lastTime?.toEpochMillisFromBetterMessagesOrNull(),
        participantIds = listOfNotNull(currentProfileId, peerProfileId).distinct(),
        participantNames = listOf(peerName),
        isGroup = type == "group" || participants.size > 2,
        isMuted = isMuted == true,
        isVisible = isHidden != 1 && isDeleted != 1,
        moderatorIds = emptyList(),
        canMembersInvite = meta?.allowInvite == true || permissions?.canInvite == true
    )

fun CommunityMessage.toDomain(
    conversationId: String,
    senderName: String,
    currentProfileId: String
): Message =
    Message(
        id = id,
        conversationId = conversationId,
        senderId = profile_id.orEmpty(),
        senderName = senderName,
        text = body.orEmpty(),
        sentAt = created_at.orEmpty(),
        sentAtMillis = created_at?.toEpochMillisOrNull(),
        isMine = profile_id == currentProfileId,
        isRead = true
    )

fun BmMessage.toDomain(
    usersByWpId: Map<Int, BmUser>,
    currentWpUserId: Int?,
    replyLookup: Map<Int, BmMessage> = emptyMap(),
    isRead: Boolean = true
): Message {
    val firstFile = meta.files.firstOrNull()
    val reply = meta.replyTo?.let { replyLookup[it] }
    return Message(
        id = bmMessageDomainId(threadId, messageId),
        conversationId = betterMessagesConversationId(threadId),
        senderId = senderId.toString(),
        senderName = usersByWpId[senderId]?.name ?: "Usuario",
        text = message.stripHtml(),
        sentAt = created_at.toDisplayTime(),
        sentAtMillis = created_at.toEpochMillisFromBetterMessages(),
        isMine = currentWpUserId != null && senderId == currentWpUserId,
        isRead = isRead,
        isEdited = updated_at != null && updated_at != created_at,
        isFavorite = favorited == 1,
        replyToMessageId = meta.replyTo?.let { bmMessageDomainId(threadId, it) },
        replyToSenderName = reply?.let { usersByWpId[it.senderId]?.name },
        replyToText = reply?.message?.stripHtml(),
        forwardedFromSenderId = meta.forwardedFromUser?.toString(),
        attachmentUri = firstFile?.url,
        attachmentName = firstFile?.name,
        attachmentMimeType = firstFile?.mimeType
    )
}

fun bmMessageDomainId(threadId: Int, messageId: Int): String = "bm:$threadId:$messageId"

fun String.bmMessagePartsOrNull(): Pair<Int, Int>? {
    val parts = split(":")
    if (parts.size != 3 || parts[0] != "bm") return null
    val threadId = parts[1].toIntOrNull() ?: return null
    val messageId = parts[2].toIntOrNull() ?: return null
    return threadId to messageId
}

fun Long.toEpochMillisFromBetterMessages(): Long =
    toEpochMillisFromBetterMessagesOrNull() ?: 0L

fun Long.toEpochMillisFromBetterMessagesOrNull(): Long? {
    if (this <= 0L) return null
    var value = if (this < 10_000_000_000L) this * 1000L else this
    while (value > MAX_REASONABLE_EPOCH_MILLIS) {
        value /= 10L
    }
    return value.takeIf { it > 0L }
}

fun Long.toDisplayTime(): String =
    toEpochMillisFromBetterMessagesOrNull()
        ?.let { Instant.ofEpochMilli(it).toString() }
        .orEmpty()

private fun String.toEpochMillisOrNull(): Long? =
    try {
        Instant.parse(this).toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }

private fun String.stripHtml(): String = replace(Regex("<[^>]*>"), "").trim()

private const val MAX_REASONABLE_EPOCH_MILLIS = 4_102_444_800_000L
